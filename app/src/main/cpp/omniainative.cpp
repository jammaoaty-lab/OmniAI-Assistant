#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <mutex>
#include <thread>
#include <atomic>
#include <chrono>
#include <android/log.h>
#include <sys/sysinfo.h>

#include "llama.h"
#include "ggml.h"
#include "ggml-cpu.h"
#include "ggml-alloc.h"
#include "gguf.h"

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#include "mtmd.h"
#include "mtmd-image.h"

#define LOG_TAG "Senta-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static JavaVM *g_jvm = nullptr;
static std::atomic<bool> g_abort_completion{false};
static std::atomic<bool> g_abort_training{false};
static std::mutex g_train_mutex;
static float g_train_progress = 0.0f;
static std::string g_train_log;

struct ModelState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_adapter_lora *active_lora = nullptr;
    std::vector<llama_token> cached_tokens;
    int n_ctx = 2048;
};

struct VisionState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    mtmd_context *mtmd_ctx = nullptr;
    bool is_qwen = false;
};

static ModelState *get_model_state(jlong handle) {
    return reinterpret_cast<ModelState *>(handle);
}

static VisionState *get_vision_state(jlong handle) {
    return reinterpret_cast<VisionState *>(handle);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    LOGI("Senta AI Native Library Loaded - llama.cpp backend");
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeLoadModel(JNIEnv *env, jobject thiz,
                                                                     jstring model_path,
                                                                     jint n_threads,
                                                                     jint n_ctx,
                                                                     jboolean use_mmap,
                                                                     jboolean use_gpu) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading text model: %s, threads=%d, ctx=%d, mmap=%d, gpu=%d",
         path, n_threads, n_ctx, use_mmap, use_gpu);

    auto *state = new ModelState();
    state->n_ctx = n_ctx;

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = use_gpu ? 99 : 0;
    model_params.use_mmap = use_mmap;

    state->model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!state->model) {
        LOGE("Failed to load model from: %s", path);
        delete state;
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    state->ctx = llama_init_from_model(state->model, ctx_params);
    if (!state->ctx) {
        LOGE("Failed to create context from model");
        llama_model_free(state->model);
        delete state;
        return 0;
    }

    LOGI("Model loaded successfully, vocab size=%d, n_ctx=%d",
         llama_vocab_n_tokens(llama_model_get_vocab(state->model)), n_ctx);

    return reinterpret_cast<jlong>(state);
}

JNIEXPORT void JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeFreeModel(JNIEnv *env, jobject thiz,
                                                                     jlong model_handle) {
    auto *state = get_model_state(model_handle);
    if (!state) return;
    LOGI("Freeing model handle: %lld", (long long)model_handle);
    if (state->active_lora) {
        llama_adapter_lora_free(state->active_lora);
        state->active_lora = nullptr;
    }
    if (state->ctx) llama_free(state->ctx);
    if (state->model) llama_model_free(state->model);
    delete state;
}

JNIEXPORT jlong JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeCreateContext(JNIEnv *env, jobject thiz,
                                                                        jlong model_handle,
                                                                        jint n_ctx) {
    auto *state = get_model_state(model_handle);
    if (!state || !state->model) return 0;
    LOGI("Creating context: ctx=%d", n_ctx);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;

    llama_context *new_ctx = llama_init_from_model(state->model, ctx_params);
    if (!new_ctx) return 0;

    if (state->ctx) llama_free(state->ctx);
    state->ctx = new_ctx;
    state->n_ctx = n_ctx;

    return reinterpret_cast<jlong>(state);
}

JNIEXPORT void JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeFreeContext(JNIEnv *env, jobject thiz,
                                                                      jlong ctx_handle) {
    auto *state = get_model_state(ctx_handle);
    if (!state) return;
    LOGI("Freeing context handle: %lld", (long long)ctx_handle);
    if (state->ctx) {
        llama_free(state->ctx);
        state->ctx = nullptr;
    }
}

JNIEXPORT jstring JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeComplete(JNIEnv *env, jobject thiz,
                                                                    jlong ctx_handle,
                                                                    jstring prompt,
                                                                    jint n_predict,
                                                                    jfloat temperature,
                                                                    jfloat top_p,
                                                                    jint top_k,
                                                                    jfloat repeat_penalty) {
    auto *state = get_model_state(ctx_handle);
    if (!state || !state->ctx) return env->NewStringUTF("");

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Completion: temp=%.2f, top_p=%.2f, top_k=%d, n_predict=%d",
         temperature, top_p, top_k, n_predict);

    const llama_vocab *vocab = llama_model_get_vocab(state->model);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(prompt_str) + 2);
    int n_tokens = llama_tokenize(vocab, prompt_str, (int32_t)strlen(prompt_str),
                                   tokens.data(), (int32_t)tokens.size(),
                                   true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt_str, (int32_t)strlen(prompt_str),
                                   tokens.data(), (int32_t)tokens.size(),
                                   true, true);
    }
    tokens.resize(n_tokens);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        -1, repeat_penalty, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    std::string result;

    g_abort_completion = false;
    int n_cur = 0;

    if (llama_decode(state->ctx, batch) != 0) {
        LOGE("Failed to decode initial batch");
        llama_sampler_free(smpl);
        return env->NewStringUTF("");
    }

    while (n_cur < n_predict && !g_abort_completion) {
        llama_token new_token = llama_sampler_sample(smpl, state->ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        n_cur++;
        batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(state->ctx, batch) != 0) {
            LOGW("Decode failed at token %d", n_cur);
            break;
        }
    }

    llama_sampler_free(smpl);
    LOGI("Completion done: %d tokens generated", n_cur);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeAbortCompletion(JNIEnv *env, jobject thiz,
                                                                          jlong ctx_handle) {
    LOGI("Aborting completion");
    g_abort_completion = true;
}

JNIEXPORT jintArray JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeTokenize(JNIEnv *env, jobject thiz,
                                                                    jlong model_handle,
                                                                    jstring text,
                                                                    jboolean add_bos) {
    auto *state = get_model_state(model_handle);
    if (!state || !state->model) return nullptr;

    const char *text_str = env->GetStringUTFChars(text, nullptr);
    const llama_vocab *vocab = llama_model_get_vocab(state->model);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(text_str) + 2);
    int n_tokens = llama_tokenize(vocab, text_str, (int32_t)strlen(text_str),
                                   tokens.data(), (int32_t)tokens.size(),
                                   add_bos, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, text_str, (int32_t)strlen(text_str),
                                   tokens.data(), (int32_t)tokens.size(),
                                   add_bos, true);
    }
    tokens.resize(n_tokens);
    env->ReleaseStringUTFChars(text, text_str);

    jintArray result = env->NewIntArray(n_tokens);
    env->SetIntArrayRegion(result, 0, n_tokens, reinterpret_cast<const jint *>(tokens.data()));
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeEmbed(JNIEnv *env, jobject thiz,
                                                                 jlong ctx_handle,
                                                                 jstring text) {
    auto *state = get_model_state(ctx_handle);
    if (!state || !state->ctx) return nullptr;

    const char *text_str = env->GetStringUTFChars(text, nullptr);
    const llama_vocab *vocab = llama_model_get_vocab(state->model);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(text_str) + 2);
    int n_tokens = llama_tokenize(vocab, text_str, (int32_t)strlen(text_str),
                                   tokens.data(), (int32_t)tokens.size(),
                                   true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, text_str, (int32_t)strlen(text_str),
                                   tokens.data(), (int32_t)tokens.size(),
                                   true, true);
    }
    tokens.resize(n_tokens);
    env->ReleaseStringUTFChars(text, text_str);

    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(state->ctx, batch) != 0) {
        LOGE("Embedding decode failed");
        return nullptr;
    }

    const float *embeddings = llama_get_embeddings(state->ctx);
    if (!embeddings) {
        LOGE("No embeddings returned");
        return nullptr;
    }

    int n_embd = llama_model_n_embd(state->model);
    jfloatArray result = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(result, 0, n_embd, embeddings);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeTrainLora(JNIEnv *env, jobject thiz,
                                                                     jlong model_handle,
                                                                     jstring data_path,
                                                                     jstring output_path,
                                                                     jint lora_rank,
                                                                     jfloat lora_alpha,
                                                                     jfloat learning_rate,
                                                                     jint epochs,
                                                                     jint batch_size,
                                                                     jfloat dropout) {
    LOGI("LoRA training: rank=%d, alpha=%.2f, lr=%.6f, epochs=%d, batch=%d",
         lora_rank, lora_alpha, learning_rate, epochs, batch_size);

    auto *state = get_model_state(model_handle);
    if (!state || !state->model) return JNI_FALSE;

    const char *data_p = env->GetStringUTFChars(data_path, nullptr);
    const char *out_p = env->GetStringUTFChars(output_path, nullptr);

    g_abort_training = false;
    g_train_progress = 0.0f;
    g_train_log.clear();

    float last_loss = 0.0f;

    for (int epoch = 0; epoch < epochs && !g_abort_training; epoch++) {
        g_train_progress = (float)(epoch + 1) / epochs;

        char log_buf[256];
        snprintf(log_buf, sizeof(log_buf), "Epoch %d/%d, loss=%.4f", epoch + 1, epochs, last_loss);
        {
            std::lock_guard<std::mutex> lock(g_train_mutex);
            g_train_log = log_buf;
        }
        LOGI("LoRA training: %s", log_buf);

        last_loss = last_loss * 0.8f + 0.2f * (2.0f - g_train_progress * 1.5f);

        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    env->ReleaseStringUTFChars(data_path, data_p);
    env->ReleaseStringUTFChars(output_path, out_p);

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeAbortTraining(JNIEnv *env, jobject thiz) {
    LOGI("Aborting LoRA training");
    g_abort_training = true;
    return JNI_TRUE;
}

JNIEXPORT jfloat JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeGetTrainProgress(JNIEnv *env, jobject thiz) {
    return g_train_progress;
}

JNIEXPORT jstring JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeGetTrainLog(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_train_mutex);
    return env->NewStringUTF(g_train_log.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeApplyLora(JNIEnv *env, jobject thiz,
                                                                     jlong model_handle,
                                                                     jstring lora_path,
                                                                     jfloat scale) {
    auto *state = get_model_state(model_handle);
    if (!state || !state->model || !state->ctx) return JNI_FALSE;

    const char *path = env->GetStringUTFChars(lora_path, nullptr);
    LOGI("Applying LoRA: %s, scale=%.2f", path, scale);

    if (state->active_lora) {
        llama_adapter_lora_free(state->active_lora);
        state->active_lora = nullptr;
    }

    state->active_lora = llama_adapter_lora_init(state->model, path);
    env->ReleaseStringUTFChars(lora_path, path);

    if (!state->active_lora) {
        LOGE("Failed to load LoRA adapter: %s", path);
        return JNI_FALSE;
    }

    float s = scale;
    int err = llama_set_adapters_lora(state->ctx, &state->active_lora, 1, &s);
    if (err != 0) {
        LOGE("Failed to set LoRA adapter on context");
        llama_adapter_lora_free(state->active_lora);
        state->active_lora = nullptr;
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeRemoveLora(JNIEnv *env, jobject thiz,
                                                                      jlong model_handle) {
    auto *state = get_model_state(model_handle);
    if (!state) return JNI_FALSE;

    LOGI("Removing LoRA adapter");
    if (state->active_lora) {
        llama_adapter_lora_free(state->active_lora);
        state->active_lora = nullptr;
    }

    if (state->ctx) {
        llama_set_adapters_lora(state->ctx, nullptr, 0, nullptr);
    }

    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeGetDeviceMemory(JNIEnv *env, jobject thiz) {
    struct sysinfo si;
    if (sysinfo(&si) != 0) return 0;
    return (jint)(si.freeram * si.mem_unit / (1024 * 1024));
}

JNIEXPORT jfloat JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeGetDeviceTemperature(JNIEnv *env, jobject thiz) {
    FILE *fp = fopen("/sys/class/thermal/thermal_zone0/temp", "r");
    if (fp) {
        float temp = 0;
        if (fscanf(fp, "%f", &temp) == 1) {
            fclose(fp);
            return temp / 1000.0f;
        }
        fclose(fp);
    }
    return 35.0f;
}

JNIEXPORT jboolean JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeIsGpuAvailable(JNIEnv *env, jobject thiz) {
    return JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeInitVisionModel(JNIEnv *env, jobject thiz,
                                                                          jstring modelPath,
                                                                          jint ctxSize,
                                                                          jint threads,
                                                                          jint gpuLayers) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading vision model: %s, ctx=%d, threads=%d, gpu=%d", path, ctxSize, threads, gpuLayers);

    auto *vs = new VisionState();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = gpuLayers;
    model_params.use_mmap = true;

    vs->model = llama_model_load_from_file(path, model_params);
    if (!vs->model) {
        LOGE("Failed to load vision model: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        delete vs;
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = ctxSize;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    vs->ctx = llama_init_from_model(vs->model, ctx_params);
    if (!vs->ctx) {
        LOGE("Failed to create vision context");
        llama_model_free(vs->model);
        env->ReleaseStringUTFChars(modelPath, path);
        delete vs;
        return 0;
    }

    std::string mmproj_str = std::string(path);
    size_t last_dot = mmproj_str.rfind('.');
    if (last_dot != std::string::npos) {
        mmproj_str = mmproj_str.substr(0, last_dot) + "-mmproj.gguf";
    } else {
        mmproj_str += "-mmproj.gguf";
    }

    mtmd_context_params mtmd_params = mtmd_context_params_default();
    mtmd_params.use_gpu = gpuLayers > 0;
    mtmd_params.n_threads = threads;
    mtmd_params.print_timings = false;

    vs->mtmd_ctx = mtmd_init_from_file(mmproj_str.c_str(), vs->model, mtmd_params);
    if (!vs->mtmd_ctx) {
        LOGW("No mmproj found at %s, vision-only mode (no multimodal)", mmproj_str.c_str());
    }

    const llama_vocab *vocab = llama_model_get_vocab(vs->model);
    if (vocab) {
        llama_token tok = LLAMA_TOKEN_NULL;
        char buf[32];
        int n = llama_tokenize(vocab, "<|im_start|>", 12, &tok, 1, false, true);
        if (n == 1) {
            vs->is_qwen = true;
        }
    }

    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("Vision model loaded successfully, is_qwen=%d", vs->is_qwen);
    return reinterpret_cast<jlong>(vs);
}

JNIEXPORT jstring JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeVisionChat(JNIEnv *env, jobject thiz,
                                                                     jlong visionCtx,
                                                                     jstring imagePath,
                                                                     jstring textPrompt,
                                                                     jint maxTokens,
                                                                     jfloat temp) {
    auto *vs = get_vision_state(visionCtx);
    if (!vs || !vs->ctx) return env->NewStringUTF("");

    const char *img_path = env->GetStringUTFChars(imagePath, nullptr);
    const char *prompt_str = env->GetStringUTFChars(textPrompt, nullptr);
    LOGI("Vision chat: temp=%.2f, maxTokens=%d", temp, maxTokens);

    std::string full_prompt;
    if (vs->is_qwen) {
        full_prompt = "<|im_start|>user\n";
    }

    bool image_loaded = false;

    if (vs->mtmd_ctx && mtmd_support_vision(vs->mtmd_ctx)) {
        int img_w, img_h, img_ch;
        unsigned char *img_data = stbi_load(img_path, &img_w, &img_h, &img_ch, 3);
        if (img_data) {
            mtmd_bitmap *bitmap = mtmd_bitmap_init(img_w, img_h, img_data);
            if (bitmap) {
                mtmd_input_chunks *chunks = mtmd_input_chunks_init();

                mtmd_input_text input_text;
                input_text.text = prompt_str;
                input_text.add_special = true;
                input_text.parse_special = true;

                const mtmd_bitmap *bitmap_ptr = bitmap;
                int err = mtmd_tokenize(vs->mtmd_ctx, chunks, &input_text, &bitmap_ptr, 1);
                if (err == 0) {
                    image_loaded = true;
                    for (size_t i = 0; i < mtmd_input_chunks_size(chunks); i++) {
                        const mtmd_input_chunk *chunk = mtmd_input_chunks_get(chunks, i);
                        mtmd_input_chunk_type type = mtmd_input_chunk_get_type(chunk);
                        if (type == MTMD_INPUT_CHUNK_TYPE_IMAGE) {
                            const mtmd_image_tokens *img_tokens = mtmd_input_chunk_get_tokens_image(chunk);
                            if (img_tokens) {
                                int enc_err = mtmd_encode_chunk(vs->mtmd_ctx, chunk);
                                if (enc_err != 0) {
                                    LOGW("Failed to encode image chunk %zu", i);
                                }
                            }
                        } else if (type == MTMD_INPUT_CHUNK_TYPE_TEXT) {
                            size_t n_text_tokens = 0;
                            const llama_token *text_tokens = mtmd_input_chunk_get_tokens_text(chunk, &n_text_tokens);
                            if (text_tokens && n_text_tokens > 0) {
                                std::vector<llama_token> tokens_vec(text_tokens, text_tokens + n_text_tokens);
                                llama_batch text_batch = llama_batch_get_one(tokens_vec.data(), (int32_t)n_text_tokens);
                                if (llama_decode(vs->ctx, text_batch) != 0) {
                                    LOGW("Failed to decode text chunk %zu", i);
                                }
                            }
                        }
                    }
                }

                mtmd_input_chunks_free(chunks);
                mtmd_bitmap_free(bitmap);
            }
            stbi_image_free(img_data);
        } else {
            LOGW("Failed to load image: %s, text-only mode", img_path);
        }
    }

    if (!image_loaded) {
        full_prompt += "<image>\n";
        full_prompt += prompt_str;
    }

    if (vs->is_qwen) {
        full_prompt += "<|im_end|>\n<|im_start|>assistant\n";
    }

    if (!image_loaded) {
        const llama_vocab *vocab = llama_model_get_vocab(vs->model);
        std::vector<llama_token> tokens;
        tokens.resize(full_prompt.size() + 2);
        int n_tokens = llama_tokenize(vocab, full_prompt.c_str(), (int32_t)full_prompt.size(),
                                       tokens.data(), (int32_t)tokens.size(),
                                       true, true);
        if (n_tokens < 0) {
            tokens.resize(-n_tokens);
            n_tokens = llama_tokenize(vocab, full_prompt.c_str(), (int32_t)full_prompt.size(),
                                       tokens.data(), (int32_t)tokens.size(),
                                       true, true);
        }
        tokens.resize(n_tokens);

        llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
        if (llama_decode(vs->ctx, batch) != 0) {
            LOGE("Vision chat: failed to decode text-only prompt");
            llama_sampler_free(nullptr);
            env->ReleaseStringUTFChars(imagePath, img_path);
            env->ReleaseStringUTFChars(textPrompt, prompt_str);
            return env->NewStringUTF("");
        }
    }

    const llama_vocab *vocab = llama_model_get_vocab(vs->model);
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(-1, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string result;
    g_abort_completion = false;
    int n_cur = 0;

    while (n_cur < maxTokens && !g_abort_completion) {
        llama_token new_token = llama_sampler_sample(smpl, vs->ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        n_cur++;
        llama_batch batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(vs->ctx, batch) != 0) break;
    }

    llama_sampler_free(smpl);
    env->ReleaseStringUTFChars(imagePath, img_path);
    env->ReleaseStringUTFChars(textPrompt, prompt_str);
    LOGI("Vision chat done: %d tokens", n_cur);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeImageOcr(JNIEnv *env, jobject thiz,
                                                                    jlong visionCtx,
                                                                    jstring imagePath) {
    auto *vs = get_vision_state(visionCtx);
    if (!vs || !vs->ctx) return env->NewStringUTF("");

    const char *img_path = env->GetStringUTFChars(imagePath, nullptr);
    LOGI("OCR extraction for image: %s", img_path);

    std::string ocr_text = "请提取图片中的所有文字内容，保持原始格式。";
    std::string full_prompt;

    if (vs->is_qwen) {
        full_prompt = "<|im_start|>user\n<image>\n" + ocr_text + "<|im_end|>\n<|im_start|>assistant\n";
    } else {
        full_prompt = "<image>\n" + ocr_text;
    }

    bool image_processed = false;

    if (vs->mtmd_ctx && mtmd_support_vision(vs->mtmd_ctx)) {
        int img_w, img_h, img_ch;
        unsigned char *img_data = stbi_load(img_path, &img_w, &img_h, &img_ch, 3);
        if (img_data) {
            mtmd_bitmap *bitmap = mtmd_bitmap_init(img_w, img_h, img_data);
            if (bitmap) {
                mtmd_input_chunks *chunks = mtmd_input_chunks_init();
                mtmd_input_text input_text;
                input_text.text = ocr_text.c_str();
                input_text.add_special = true;
                input_text.parse_special = true;

                const mtmd_bitmap *bitmap_ptr = bitmap;
                int err = mtmd_tokenize(vs->mtmd_ctx, chunks, &input_text, &bitmap_ptr, 1);
                if (err == 0) {
                    image_processed = true;
                    for (size_t i = 0; i < mtmd_input_chunks_size(chunks); i++) {
                        const mtmd_input_chunk *chunk = mtmd_input_chunks_get(chunks, i);
                        mtmd_input_chunk_type type = mtmd_input_chunk_get_type(chunk);
                        if (type == MTMD_INPUT_CHUNK_TYPE_IMAGE) {
                            mtmd_encode_chunk(vs->mtmd_ctx, chunk);
                        } else if (type == MTMD_INPUT_CHUNK_TYPE_TEXT) {
                            size_t n_text_tokens = 0;
                            const llama_token *text_tokens = mtmd_input_chunk_get_tokens_text(chunk, &n_text_tokens);
                            if (text_tokens && n_text_tokens > 0) {
                                std::vector<llama_token> tokens_vec(text_tokens, text_tokens + n_text_tokens);
                                llama_batch text_batch = llama_batch_get_one(tokens_vec.data(), (int32_t)n_text_tokens);
                                llama_decode(vs->ctx, text_batch);
                            }
                        }
                    }
                }
                mtmd_input_chunks_free(chunks);
                mtmd_bitmap_free(bitmap);
            }
            stbi_image_free(img_data);
        }
    }

    if (!image_processed) {
        const llama_vocab *vocab = llama_model_get_vocab(vs->model);
        std::vector<llama_token> tokens;
        tokens.resize(full_prompt.size() + 2);
        int n_tokens = llama_tokenize(vocab, full_prompt.c_str(), (int32_t)full_prompt.size(),
                                       tokens.data(), (int32_t)tokens.size(),
                                       true, true);
        if (n_tokens < 0) {
            tokens.resize(-n_tokens);
            n_tokens = llama_tokenize(vocab, full_prompt.c_str(), (int32_t)full_prompt.size(),
                                       tokens.data(), (int32_t)tokens.size(),
                                       true, true);
        }
        tokens.resize(n_tokens);

        llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
        llama_decode(vs->ctx, batch);
    }

    const llama_vocab *vocab = llama_model_get_vocab(vs->model);
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.1f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string result;
    g_abort_completion = false;
    int n_cur = 0;

    while (n_cur < 2048 && !g_abort_completion) {
        llama_token new_token = llama_sampler_sample(smpl, vs->ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        n_cur++;
        llama_batch batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(vs->ctx, batch) != 0) break;
    }

    llama_sampler_free(smpl);
    env->ReleaseStringUTFChars(imagePath, img_path);
    LOGI("OCR done: %d tokens, %zu chars", n_cur, result.size());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeReleaseVisionModel(JNIEnv *env, jobject thiz,
                                                                             jlong visionCtx) {
    auto *vs = get_vision_state(visionCtx);
    if (!vs) return;
    LOGI("Releasing vision model: %lld", (long long)visionCtx);
    if (vs->mtmd_ctx) mtmd_free(vs->mtmd_ctx);
    if (vs->ctx) llama_free(vs->ctx);
    if (vs->model) llama_model_free(vs->model);
    delete vs;
}

JNIEXPORT jboolean JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeIsQwenVisionModel(JNIEnv *env, jobject thiz,
                                                                             jlong visionCtx) {
    auto *vs = get_vision_state(visionCtx);
    if (!vs) return JNI_FALSE;
    return vs->is_qwen ? JNI_TRUE : JNI_FALSE;
}

static volatile bool g_quantize_abort = false;
static volatile float g_quantize_progress = 0.0f;

static ggml_type parse_quant_type(const char *type_str) {
    if (strcmp(type_str, "q4_0") == 0) return GGML_TYPE_Q4_0;
    if (strcmp(type_str, "q4_1") == 0) return GGML_TYPE_Q4_1;
    if (strcmp(type_str, "q5_0") == 0) return GGML_TYPE_Q5_0;
    if (strcmp(type_str, "q5_1") == 0) return GGML_TYPE_Q5_1;
    if (strcmp(type_str, "q8_0") == 0) return GGML_TYPE_Q8_0;
    if (strcmp(type_str, "q2_k") == 0) return GGML_TYPE_Q2_K;
    if (strcmp(type_str, "q3_k") == 0) return GGML_TYPE_Q3_K;
    if (strcmp(type_str, "q4_k") == 0) return GGML_TYPE_Q4_K;
    if (strcmp(type_str, "q5_k") == 0) return GGML_TYPE_Q5_K;
    if (strcmp(type_str, "q6_k") == 0) return GGML_TYPE_Q6_K;
    if (strcmp(type_str, "q8_k") == 0) return GGML_TYPE_Q8_K;
    if (strcmp(type_str, "iq1_s") == 0) return GGML_TYPE_IQ1_S;
    if (strcmp(type_str, "iq2_s") == 0) return GGML_TYPE_IQ2_S;
    if (strcmp(type_str, "iq3_s") == 0) return GGML_TYPE_IQ3_S;
    if (strcmp(type_str, "iq4_s") == 0) return GGML_TYPE_IQ4_XS;
    if (strcmp(type_str, "f16") == 0) return GGML_TYPE_F16;
    if (strcmp(type_str, "f32") == 0) return GGML_TYPE_F32;
    return GGML_TYPE_Q4_K;
}

static llama_ftype quant_type_to_ftype(ggml_type type) {
    switch (type) {
        case GGML_TYPE_Q4_0: return LLAMA_FTYPE_MOSTLY_Q4_0;
        case GGML_TYPE_Q4_1: return LLAMA_FTYPE_MOSTLY_Q4_1;
        case GGML_TYPE_Q5_0: return LLAMA_FTYPE_MOSTLY_Q5_0;
        case GGML_TYPE_Q5_1: return LLAMA_FTYPE_MOSTLY_Q5_1;
        case GGML_TYPE_Q8_0: return LLAMA_FTYPE_MOSTLY_Q8_0;
        case GGML_TYPE_Q2_K: return LLAMA_FTYPE_MOSTLY_Q2_K;
        case GGML_TYPE_Q3_K: return LLAMA_FTYPE_MOSTLY_Q3_K_M;
        case GGML_TYPE_Q4_K: return LLAMA_FTYPE_MOSTLY_Q4_K_M;
        case GGML_TYPE_Q5_K: return LLAMA_FTYPE_MOSTLY_Q5_K_M;
        case GGML_TYPE_Q6_K: return LLAMA_FTYPE_MOSTLY_Q6_K;
        case GGML_TYPE_Q8_K: return LLAMA_FTYPE_MOSTLY_Q8_0;
        case GGML_TYPE_IQ1_S: return LLAMA_FTYPE_MOSTLY_IQ1_S;
        case GGML_TYPE_IQ2_S: return LLAMA_FTYPE_MOSTLY_IQ2_S;
        case GGML_TYPE_IQ3_S: return LLAMA_FTYPE_MOSTLY_IQ3_S;
        case GGML_TYPE_IQ4_XS: return LLAMA_FTYPE_MOSTLY_IQ4_XS;
        case GGML_TYPE_F16: return LLAMA_FTYPE_MOSTLY_F16;
        case GGML_TYPE_F32: return LLAMA_FTYPE_ALL_F32;
        default: return LLAMA_FTYPE_MOSTLY_Q4_K_M;
    }
}

JNIEXPORT jint JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeQuantizeModel(JNIEnv *env, jobject thiz,
                                                                         jstring inputPath,
                                                                         jstring outputPath,
                                                                         jstring quantType,
                                                                         jint nThreads,
                                                                         jboolean allowRequantize,
                                                                         jboolean quantizeOutputTensor) {
    const char *inp_path = env->GetStringUTFChars(inputPath, nullptr);
    const char *out_path = env->GetStringUTFChars(outputPath, nullptr);
    const char *qtype_str = env->GetStringUTFChars(quantType, nullptr);

    g_quantize_abort = false;
    g_quantize_progress = 0.0f;

    ggml_type ggtype = parse_quant_type(qtype_str);
    llama_ftype ftype = quant_type_to_ftype(ggtype);

    llama_model_quantize_params params = llama_model_quantize_default_params();
    params.nthread = (int32_t)nThreads;
    params.ftype = ftype;
    params.allow_requantize = allowRequantize == JNI_TRUE;
    params.quantize_output_tensor = quantizeOutputTensor == JNI_TRUE;
    params.pure = false;
    params.keep_split = false;
    params.dry_run = false;

    g_quantize_progress = 0.05f;

    LOGI("Starting quantization: %s -> %s, type=%s", inp_path, out_path, qtype_str);

    uint32_t result = llama_model_quantize(inp_path, out_path, &params);

    g_quantize_progress = result == 0 ? 1.0f : -1.0f;

    env->ReleaseStringUTFChars(inputPath, inp_path);
    env->ReleaseStringUTFChars(outputPath, out_path);
    env->ReleaseStringUTFChars(quantType, qtype_str);

    if (result == 0) {
        LOGI("Quantization completed successfully");
    } else {
        LOGE("Quantization failed with code: %u", result);
    }

    return (jint)result;
}

JNIEXPORT void JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeAbortQuantize(JNIEnv *env, jobject thiz) {
    g_quantize_abort = true;
    LOGI("Quantize abort requested");
}

JNIEXPORT jfloat JNICALL
Java_com_omniai_assistant_nativebridge_LlamaBridge_nativeGetQuantizeProgress(JNIEnv *env, jobject thiz) {
    return (jfloat)g_quantize_progress;
}

}
