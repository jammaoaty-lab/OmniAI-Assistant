package com.omniai.assistant.knowledge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.omniai.assistant.common.Constants;
import com.omniai.assistant.nativebridge.LlamaBridge;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmbeddingEngine {

    private static final String TAG = "EmbeddingEngine";
    private static final int DEFAULT_DIMENSION = 768;
    private static final int EMBEDDING_CTX_SIZE = 512;
    private static final int EMBEDDING_N_THREADS = 4;

    private final LlamaBridge bridge;
    private final Context appContext;
    private final SharedPreferences prefs;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    private long modelHandle;
    private boolean isInitialized;
    private int embeddingDimension;
    private boolean useSimulationMode;

    private EmbeddingProgressCallback progressCallback;
    private int totalToEmbed;
    private int embeddedCount;

    public interface EmbeddingProgressCallback {
        void onEmbeddingProgress(int current, int total, String currentText);
        void onEmbeddingComplete(int totalEmbedded);
        void onEmbeddingError(String error);
    }

    public EmbeddingEngine(Context context) {
        this.bridge = LlamaBridge.getInstance();
        this.appContext = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EmbeddingThread");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.modelHandle = -1;
        this.isInitialized = false;
        this.embeddingDimension = DEFAULT_DIMENSION;
        this.useSimulationMode = prefs.getBoolean(Constants.PREF_KEY_USE_SIMULATION, true);
    }

    public EmbeddingEngine() {
        this.bridge = LlamaBridge.getInstance();
        this.appContext = null;
        this.prefs = null;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.modelHandle = -1;
        this.isInitialized = false;
        this.embeddingDimension = DEFAULT_DIMENSION;
        this.useSimulationMode = true;
    }

    public void initialize(String modelPath) {
        initialize(modelPath, null);
    }

    public void initialize(String modelPath, InitCallback callback) {
        if (isInitialized) {
            shutdown();
        }

        if (modelPath == null || modelPath.isEmpty()) {
            tryAutoDetectModel(callback);
            return;
        }

        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            if (callback != null) callback.onError("Embedding模型文件不存在: " + modelPath);
            return;
        }
        if (modelFile.length() == 0) {
            if (callback != null) callback.onError("Embedding模型文件为空: " + modelPath);
            return;
        }

        if (useSimulationMode) {
            isInitialized = true;
            embeddingDimension = DEFAULT_DIMENSION;
            if (callback != null) callback.onInitialized(embeddingDimension);
            return;
        }

        executorService.execute(() -> {
            try {
                modelHandle = bridge.nativeLoadModel(modelPath, EMBEDDING_N_THREADS, EMBEDDING_CTX_SIZE, true, false);
                if (modelHandle <= 0) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("加载Embedding模型失败: " + modelPath);
                    });
                    return;
                }

                float[] testEmbedding = generateEmbeddingInternal("test");
                if (testEmbedding != null && testEmbedding.length > 0) {
                    embeddingDimension = testEmbedding.length;
                }

                isInitialized = true;
                mainHandler.post(() -> {
                    if (callback != null) callback.onInitialized(embeddingDimension);
                });
            } catch (UnsatisfiedLinkError e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("推理引擎未正确安装，请重新安装应用");
                });
            } catch (OutOfMemoryError e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("设备内存不足，无法加载Embedding模型");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("初始化Embedding引擎失败: " + e.getMessage());
                });
            }
        });
    }

    private void tryAutoDetectModel(InitCallback callback) {
        File modelsDir = new File(appContext.getFilesDir(), Constants.MODEL_DIR);
        if (!modelsDir.exists()) {
            if (callback != null) callback.onError("模型目录不存在");
            return;
        }

        String[] embeddingModelNames = {
            "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            "bge-small-zh-v1.5-Q8_0.gguf",
            "nomic-embed-text-v1.5-Q4_K_M.gguf",
            "all-MiniLM-L6-v2-Q8_0.gguf"
        };

        String foundPath = null;
        for (String name : embeddingModelNames) {
            File f = new File(modelsDir, name);
            if (f.exists() && f.length() > 0) {
                foundPath = f.getAbsolutePath();
                break;
            }
        }

        if (foundPath == null) {
            File[] files = modelsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().toLowerCase().contains("embed") && f.getName().endsWith(".gguf")) {
                        foundPath = f.getAbsolutePath();
                        break;
                    }
                }
                if (foundPath == null) {
                    for (File f : files) {
                        if (f.getName().endsWith(".gguf") && f.length() > 0) {
                            foundPath = f.getAbsolutePath();
                            break;
                        }
                    }
                }
            }
        }

        if (foundPath != null) {
            initialize(foundPath, callback);
        } else {
            if (callback != null) callback.onError("未找到可用的Embedding模型，请先下载模型");
        }
    }

    public float[] embed(String text) {
        if (!isInitialized) {
            throw new IllegalStateException("EmbeddingEngine未初始化，请先调用initialize()");
        }
        if (text == null || text.trim().isEmpty()) {
            return new float[embeddingDimension];
        }

        if (useSimulationMode) {
            return generateSimulatedEmbedding(text);
        }

        return generateEmbeddingInternal(text);
    }

    public void embedAsync(String text, EmbedCallback callback) {
        executorService.execute(() -> {
            try {
                float[] result = embed(text);
                mainHandler.post(() -> {
                    if (callback != null) callback.onResult(result);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Embedding生成失败: " + e.getMessage());
                });
            }
        });
    }

    public List<float[]> embedBatch(List<String> texts) {
        return embedBatch(texts, null);
    }

    public List<float[]> embedBatch(List<String> texts, EmbeddingProgressCallback progressCb) {
        if (!isInitialized) {
            throw new IllegalStateException("EmbeddingEngine未初始化，请先调用initialize()");
        }

        this.progressCallback = progressCb;
        this.totalToEmbed = texts.size();
        this.embeddedCount = 0;

        List<float[]> results = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null || text.trim().isEmpty()) {
                results.add(new float[embeddingDimension]);
                embeddedCount++;
                continue;
            }

            try {
                float[] embedding = embed(text);
                results.add(embedding);
            } catch (Exception e) {
                results.add(new float[embeddingDimension]);
            }

            embeddedCount++;
            if (progressCallback != null) {
                int current = embeddedCount;
                int total = totalToEmbed;
                String preview = text.length() > 50 ? text.substring(0, 50) + "..." : text;
                mainHandler.post(() -> {
                    progressCallback.onEmbeddingProgress(current, total, preview);
                });
            }
        }

        if (progressCallback != null) {
            int total = embeddedCount;
            mainHandler.post(() -> {
                progressCallback.onEmbeddingComplete(total);
            });
        }

        return results;
    }

    public void embedBatchAsync(List<String> texts, EmbeddingProgressCallback progressCb, BatchEmbedCallback callback) {
        executorService.execute(() -> {
            try {
                List<float[]> results = embedBatch(texts, progressCb);
                mainHandler.post(() -> {
                    if (callback != null) callback.onResults(results);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("批量Embedding失败: " + e.getMessage());
                });
            }
        });
    }

    private float[] generateEmbeddingInternal(String text) {
        if (modelHandle <= 0) {
            return new float[embeddingDimension];
        }

        long ctxHandle = 0;
        try {
            ctxHandle = bridge.nativeCreateContext(modelHandle, EMBEDDING_CTX_SIZE);
            if (ctxHandle <= 0) {
                return new float[embeddingDimension];
            }

            float[] result = bridge.nativeEmbed(ctxHandle, text);
            if (result == null || result.length == 0) {
                return new float[embeddingDimension];
            }

            float[] normalized = new float[result.length];
            float norm = 0f;
            for (float v : result) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < result.length; i++) {
                    normalized[i] = result[i] / norm;
                }
            }
            return normalized;
        } catch (UnsatisfiedLinkError e) {
            return new float[embeddingDimension];
        } catch (Exception e) {
            return new float[embeddingDimension];
        } finally {
            if (ctxHandle > 0) {
                try {
                    bridge.nativeFreeContext(ctxHandle);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to free embedding context", e);
                }
            }
        }
    }

    private float[] generateSimulatedEmbedding(String text) {
        float[] embedding = new float[embeddingDimension];
        int hash = text.hashCode();
        long seed = hash != 0 ? Math.abs(hash) : 42;
        for (int i = 0; i < embeddingDimension; i++) {
            seed = (seed * 1103515245L + 12345L) & 0x7fffffffL;
            embedding[i] = ((seed / (float) 0x7fffffff) - 0.5f) * 0.1f;
        }
        float norm = 0f;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = embedding[i] / norm;
            }
        }
        return embedding;
    }

    public void shutdown() {
        if (isInitialized && modelHandle > 0 && !useSimulationMode) {
            try {
                bridge.nativeFreeModel(modelHandle);
            } catch (Exception e) {
                Log.w(TAG, "Failed to free embedding model", e);
            }
            modelHandle = -1;
        }
        isInitialized = false;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setSimulationMode(boolean enabled) {
        this.useSimulationMode = enabled;
        if (prefs != null) {
            prefs.edit().putBoolean(Constants.PREF_KEY_USE_SIMULATION, enabled).apply();
        }
    }

    public boolean isSimulationMode() {
        return useSimulationMode;
    }

    public long getModelHandle() {
        return modelHandle;
    }

    public interface InitCallback {
        void onInitialized(int dimension);
        void onError(String error);
    }

    public interface EmbedCallback {
        void onResult(float[] embedding);
        void onError(String error);
    }

    public interface BatchEmbedCallback {
        void onResults(List<float[]> embeddings);
        void onError(String error);
    }
}
