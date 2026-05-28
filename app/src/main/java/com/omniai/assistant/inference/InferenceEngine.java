package com.omniai.assistant.inference;

import android.content.Context;
import android.content.SharedPreferences;

import com.omniai.assistant.OmniAIApplication;
import com.omniai.assistant.common.Constants;
import com.omniai.assistant.model.AIModel;
import com.omniai.assistant.model.LoraWeight;
import com.omniai.assistant.modelmgmt.ModelVerifier;
import com.omniai.assistant.nativebridge.LlamaBridge;
import com.omniai.assistant.scheduler.InferenceParams;
import com.omniai.assistant.simulation.SimulatedInferenceEngine;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class InferenceEngine {

    private static volatile InferenceEngine instance;

    private LlamaBridge bridge;
    private long modelHandle;
    private long contextHandle;
    private AIModel currentModel;
    private boolean isInitialized;
    private AtomicBoolean isInferencing = new AtomicBoolean(false);
    private ExecutorService inferenceExecutor;
    private List<InferenceListener> listeners;

    private SimulatedInferenceEngine simulatedEngine;
    private boolean useSimulationMode;
    private Context appContext;

    public interface InferenceListener {
        void onModelLoaded(AIModel model);
        void onModelUnloaded();
        void onInferenceStarted();
        void onInferenceProgress(String partialText);
        void onInferenceCompleted(String result);
        void onInferenceError(String error);
    }

    public interface LoadCallback {
        void onLoaded(AIModel model);
        void onError(String error);
    }

    public interface InferenceCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public interface StreamCallback {
        void onToken(String token);
        void onComplete(String fullResult);
        void onError(String error);
    }

    private InferenceEngine() {
        this.bridge = LlamaBridge.getInstance();
        this.modelHandle = 0L;
        this.contextHandle = 0L;
        this.isInitialized = false;
        this.inferenceExecutor = Executors.newSingleThreadExecutor();
        this.listeners = new CopyOnWriteArrayList<>();
        this.appContext = OmniAIApplication.getInstance();
        this.simulatedEngine = SimulatedInferenceEngine.getInstance(appContext);
        loadSimulationModePreference();
    }

    public static InferenceEngine getInstance() {
        if (instance == null) {
            synchronized (InferenceEngine.class) {
                if (instance == null) {
                    instance = new InferenceEngine();
                }
            }
        }
        return instance;
    }

    private void loadSimulationModePreference() {
        SharedPreferences prefs = appContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.useSimulationMode = prefs.getBoolean(Constants.PREF_KEY_USE_SIMULATION, true);
    }

    public void setUseSimulationMode(boolean enabled) {
        this.useSimulationMode = enabled;
        SharedPreferences.Editor editor = appContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putBoolean(Constants.PREF_KEY_USE_SIMULATION, enabled);
        editor.apply();
        unloadModel();
    }

    public boolean isUsingSimulationMode() {
        return useSimulationMode;
    }

    public void loadModel(AIModel model, LoadCallback callback) {
        if (useSimulationMode) {
            loadModelSimulation(model, callback);
        } else {
            loadModelNative(model, callback);
        }
    }

    private String validateModelFile(AIModel model) {
        if (model == null || model.getFilePath() == null) return "模型路径为空";
        File file = new File(model.getFilePath());
        if (!file.exists()) return "模型文件不存在: " + model.getFilePath();
        if (file.length() == 0) return "模型文件为空: " + model.getFilePath();
        ModelVerifier verifier = new ModelVerifier();
        if (!verifier.verifyGguf(model.getFilePath())) return "模型文件格式无效，请确认是有效的GGUF文件";
        return null;
    }

    private void loadModelNative(AIModel model, LoadCallback callback) {
        String validationError = validateModelFile(model);
        if (validationError != null) {
            if (callback != null) callback.onError(validationError);
            return;
        }
        inferenceExecutor.execute(() -> {
            try {
                if (modelHandle != 0L) {
                    unloadModel();
                }
                java.util.concurrent.Future<?> loadFuture = inferenceExecutor.submit(() -> {
                    modelHandle = bridge.nativeLoadModel(
                            model.getFilePath(),
                            Runtime.getRuntime().availableProcessors(),
                            2048,
                            true,
                            model.isGpuAccelerated()
                    );
                });
                try {
                    loadFuture.get(120, TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    if (callback != null) callback.onError("模型加载超时，请尝试使用更小的模型");
                    return;
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof OutOfMemoryError) {
                        if (callback != null) callback.onError("设备内存不足，无法加载模型");
                        return;
                    }
                    if (callback != null) callback.onError(cause.getMessage());
                    return;
                }
                if (modelHandle == 0L) {
                    if (callback != null) callback.onError("Failed to load model: " + model.getFilePath());
                    return;
                }
                currentModel = model;
                currentModel.setLoaded(true);
                isInitialized = true;
                for (InferenceListener l : listeners) {
                    l.onModelLoaded(model);
                }
                if (callback != null) callback.onLoaded(model);
            } catch (UnsatisfiedLinkError e) {
                if (callback != null) callback.onError("推理引擎未正确安装");
            } catch (OutOfMemoryError e) {
                if (callback != null) callback.onError("设备内存不足，无法加载模型");
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    private void loadModelSimulation(AIModel model, LoadCallback callback) {
        simulatedEngine.loadModel(model, new SimulatedInferenceEngine.LoadCallback() {
            @Override
            public void onProgress(int current, int total) {}

            @Override
            public void onSuccess() {
                currentModel = model;
                currentModel.setLoaded(true);
                isInitialized = true;
                for (InferenceListener l : listeners) {
                    l.onModelLoaded(model);
                }
                if (callback != null) callback.onLoaded(model);
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    public void unloadModel() {
        if (useSimulationMode) {
            simulatedEngine.unloadModel();
        } else {
            if (contextHandle != 0L) {
                destroyContext();
            }
            if (modelHandle != 0L) {
                bridge.nativeFreeModel(modelHandle);
                modelHandle = 0L;
            }
        }
        if (currentModel != null) {
            currentModel.setLoaded(false);
        }
        currentModel = null;
        isInitialized = false;
        for (InferenceListener l : listeners) {
            l.onModelUnloaded();
        }
    }

    public long createContext(int nCtx) {
        if (useSimulationMode) return 0L;
        if (modelHandle == 0L) return 0L;
        contextHandle = bridge.nativeCreateContext(modelHandle, nCtx);
        return contextHandle;
    }

    public void destroyContext() {
        if (useSimulationMode) return;
        if (contextHandle != 0L) {
            bridge.nativeFreeContext(contextHandle);
            contextHandle = 0L;
        }
    }

    public void complete(String prompt, InferenceParams params, InferenceCallback callback) {
        if (useSimulationMode) {
            completeSimulation(prompt, params, callback);
        } else {
            completeNative(prompt, params, callback);
        }
    }

    private void completeNative(String prompt, InferenceParams params, InferenceCallback callback) {
        if (!isModelLoaded()) {
            if (callback != null) callback.onError("Model not loaded");
            return;
        }
        if (!isInferencing.compareAndSet(false, true)) {
            if (callback != null) callback.onError("Inference already in progress");
            return;
        }
        inferenceExecutor.execute(() -> {
            try {
                for (InferenceListener l : listeners) {
                    l.onInferenceStarted();
                }
                if (contextHandle == 0L) {
                    createContext(params.getNCtx());
                }
                String result = bridge.nativeComplete(
                        contextHandle,
                        prompt,
                        params.getNPredict(),
                        params.getTemperature(),
                        params.getTopP(),
                        params.getTopK(),
                        params.getRepeatPenalty()
                );
                if (result == null || result.isEmpty()) {
                    for (InferenceListener l : listeners) {
                        l.onInferenceError("模型未返回有效结果");
                    }
                    if (callback != null) callback.onError("模型未返回有效结果");
                    return;
                }
                for (InferenceListener l : listeners) {
                    l.onInferenceCompleted(result);
                }
                if (callback != null) callback.onSuccess(result);
            } catch (OutOfMemoryError e) {
                for (InferenceListener l : listeners) {
                    l.onInferenceError("推理过程中内存不足，请尝试使用更小的模型");
                }
                if (callback != null) callback.onError("推理过程中内存不足，请尝试使用更小的模型");
            } catch (UnsatisfiedLinkError e) {
                for (InferenceListener l : listeners) {
                    l.onInferenceError("推理引擎未正确安装");
                }
                if (callback != null) callback.onError("推理引擎未正确安装");
            } catch (Exception e) {
                for (InferenceListener l : listeners) {
                    l.onInferenceError(e.getMessage());
                }
                if (callback != null) callback.onError(e.getMessage());
            } finally {
                isInferencing.set(false);
            }
        });
    }

    private void completeSimulation(String prompt, InferenceParams params, InferenceCallback callback) {
        if (!isModelLoaded()) {
            if (callback != null) callback.onError("Model not loaded");
            return;
        }
        simulatedEngine.complete(prompt, params.getNPredict(), params.getTemperature(),
                new SimulatedInferenceEngine.CompletionCallback() {
                    @Override
                    public void onPartialResult(String text) {
                        for (InferenceListener l : listeners) {
                            l.onInferenceProgress(text);
                        }
                    }

                    @Override
                    public void onComplete(String text) {
                        for (InferenceListener l : listeners) {
                            l.onInferenceCompleted(text);
                        }
                        if (callback != null) callback.onSuccess(text);
                    }

                    @Override
                    public void onError(String error) {
                        for (InferenceListener l : listeners) {
                            l.onInferenceError(error);
                        }
                        if (callback != null) callback.onError(error);
                    }
                });
    }

    public void streamComplete(String prompt, InferenceParams params, StreamCallback callback) {
        if (useSimulationMode) {
            simulatedEngine.complete(prompt, params.getNPredict(), params.getTemperature(),
                    new SimulatedInferenceEngine.CompletionCallback() {
                        @Override
                        public void onPartialResult(String text) {
                            if (callback != null) {
                                callback.onToken(text);
                            }
                        }

                        @Override
                        public void onComplete(String text) {
                            if (callback != null) {
                                callback.onComplete(text);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (callback != null) {
                                callback.onError(error);
                            }
                        }
                    });
        } else {
            streamCompleteNative(prompt, params, callback);
        }
    }

    private void streamCompleteNative(String prompt, InferenceParams params, StreamCallback callback) {
        if (!isModelLoaded()) {
            if (callback != null) callback.onError("Model not loaded");
            return;
        }
        if (!isInferencing.compareAndSet(false, true)) {
            if (callback != null) callback.onError("Inference already in progress");
            return;
        }
        inferenceExecutor.execute(() -> {
            try {
                for (InferenceListener l : listeners) {
                    l.onInferenceStarted();
                }
                if (contextHandle == 0L) {
                    createContext(params.getNCtx());
                }
                StringBuilder fullResult = new StringBuilder();
                int generated = 0;
                int maxTokens = params.getNPredict();
                while (generated < maxTokens && isInferencing.get()) {
                    String token = bridge.nativeComplete(
                            contextHandle,
                            prompt + fullResult.toString(),
                            1,
                            params.getTemperature(),
                            params.getTopP(),
                            params.getTopK(),
                            params.getRepeatPenalty()
                    );
                    if (token == null || token.isEmpty()) break;
                    boolean shouldStop = false;
                    for (String stop : params.getStopTokens()) {
                        if (token.contains(stop)) {
                            shouldStop = true;
                            break;
                        }
                    }
                    if (shouldStop) break;
                    fullResult.append(token);
                    generated++;
                    if (callback != null) callback.onToken(token);
                }
                String finalResult = fullResult.toString();
                if (finalResult.isEmpty()) {
                    if (callback != null) callback.onError("模型未返回有效结果");
                    for (InferenceListener l : listeners) {
                        l.onInferenceError("模型未返回有效结果");
                    }
                } else {
                    if (callback != null) callback.onComplete(finalResult);
                    for (InferenceListener l : listeners) {
                        l.onInferenceCompleted(finalResult);
                    }
                }
            } catch (OutOfMemoryError e) {
                for (InferenceListener l : listeners) {
                    l.onInferenceError("推理过程中内存不足，请尝试使用更小的模型");
                }
                if (callback != null) callback.onError("推理过程中内存不足，请尝试使用更小的模型");
            } catch (UnsatisfiedLinkError e) {
                for (InferenceListener l : listeners) {
                    l.onInferenceError("推理引擎未正确安装");
                }
                if (callback != null) callback.onError("推理引擎未正确安装");
            } catch (Exception e) {
                for (InferenceListener l : listeners) {
                    l.onInferenceError(e.getMessage());
                }
                if (callback != null) callback.onError(e.getMessage());
            } finally {
                isInferencing.set(false);
            }
        });
    }

    public void abortCompletion() {
        if (isInferencing.get() && !useSimulationMode && contextHandle != 0L) {
            bridge.nativeAbortCompletion(contextHandle);
        }
    }

    public boolean isModelLoaded() {
        if (useSimulationMode) {
            return simulatedEngine.isModelLoaded();
        }
        return isInitialized && modelHandle != 0L;
    }

    public boolean isGpuAvailable() {
        if (useSimulationMode) return false;
        return bridge.nativeIsGpuAvailable();
    }

    public long getDeviceMemory() {
        if (useSimulationMode) return simulatedEngine.getAvailableMemoryMb();
        return bridge.nativeGetDeviceMemory();
    }

    public float getDeviceTemperature() {
        if (useSimulationMode) return simulatedEngine.getDeviceTemperature();
        return bridge.nativeGetDeviceTemperature();
    }

    public float[] getEmbedding(String text) {
        if (useSimulationMode) {
            return simulatedEngine.getEmbedding(text);
        }
        if (modelHandle == 0L) return new float[0];
        long ctxHandle = bridge.nativeCreateContext(modelHandle, 512);
        if (ctxHandle == 0L) return new float[0];
        try {
            float[] result = bridge.nativeEmbed(ctxHandle, text);
            if (result == null || result.length == 0) return new float[0];
            float norm = 0f;
            for (float v : result) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                float[] normalized = new float[result.length];
                for (int i = 0; i < result.length; i++) normalized[i] = result[i] / norm;
                return normalized;
            }
            return result;
        } catch (Exception e) {
            return new float[0];
        } finally {
            bridge.nativeFreeContext(ctxHandle);
        }
    }

    public void applyLora(LoraWeight lora, float scale) {
        if (useSimulationMode) return;
        if (modelHandle == 0L) return;
        bridge.nativeApplyLora(modelHandle, lora.getFilePath(), scale);
    }

    public void removeLora() {
        if (useSimulationMode) return;
        if (modelHandle == 0L) return;
        bridge.nativeRemoveLora(modelHandle);
    }

    public int[] tokenize(String text) {
        if (useSimulationMode) {
            List<String> tokens = simulatedEngine.tokenize(text);
            int[] result = new int[tokens.size()];
            for (int i = 0; i < tokens.size(); i++) {
                result[i] = tokens.get(i).hashCode();
            }
            return result;
        }
        if (modelHandle == 0L) return new int[0];
        return bridge.nativeTokenize(modelHandle, text, true);
    }

    public AIModel getLoadedModel() {
        return currentModel;
    }

    public void addListener(InferenceListener listener) {
        listeners.add(listener);
    }

    public void removeListener(InferenceListener listener) {
        listeners.remove(listener);
    }

    public boolean isInferencing() {
        return isInferencing.get();
    }

    public void shutdown() {
        abortCompletion();
        unloadModel();
        inferenceExecutor.shutdownNow();
    }
}
