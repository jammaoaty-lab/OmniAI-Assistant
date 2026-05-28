package com.omniai.assistant.nativebridge;

import android.util.Log;

public class LlamaBridge {

    private static final String TAG = "LlamaBridge";

    static {
        System.loadLibrary("omniainative");
    }

    public native long nativeLoadModel(String modelPath, int nThreads, int nCtx, boolean useMmap, boolean useGpu);

    public native void nativeFreeModel(long modelHandle);

    public native long nativeCreateContext(long modelHandle, int nCtx);

    public native void nativeFreeContext(long ctxHandle);

    public native String nativeComplete(long ctxHandle, String prompt, int nPredict, float temperature, float topP, int topK, float repeatPenalty);

    public native void nativeAbortCompletion(long ctxHandle);

    public native int[] nativeTokenize(long modelHandle, String text, boolean addBos);

    public native float[] nativeEmbed(long ctxHandle, String text);

    public native boolean nativeTrainLora(long modelHandle, String dataPath, String outputPath, int loraRank, float loraAlpha, float learningRate, int epochs, int batchSize, float dropout);

    public native void nativeAbortTraining();

    public native float nativeGetTrainProgress();

    public native String nativeGetTrainLog();

    public native boolean nativeApplyLora(long modelHandle, String loraPath, float scale);

    public native boolean nativeRemoveLora(long modelHandle);

    public native int nativeGetDeviceMemory();

    public native float nativeGetDeviceTemperature();

    public native boolean nativeIsGpuAvailable();

    public native long nativeInitVisionModel(String modelPath, int ctxSize, int threads, int gpuLayers);

    public native String nativeVisionChat(long visionCtx, String imagePath, String textPrompt, int maxTokens, float temp);

    public native String nativeImageOcr(long visionCtx, String imagePath);

    public native void nativeReleaseVisionModel(long visionCtx);

    public native boolean nativeIsQwenVisionModel(long visionCtx);

    public native int nativeQuantizeModel(String inputPath, String outputPath, String quantType, int nThreads, boolean allowRequantize, boolean quantizeOutputTensor);

    public native void nativeAbortQuantize();

    public native float nativeGetQuantizeProgress();

    private static volatile LlamaBridge instance;

    private LlamaBridge() {}

    public static LlamaBridge getInstance() {
        if (instance == null) {
            synchronized (LlamaBridge.class) {
                if (instance == null) {
                    instance = new LlamaBridge();
                }
            }
        }
        return instance;
    }

    public long loadModel(String modelPath, int nThreads, int nCtx, boolean useMmap, boolean useGpu) {
        return nativeLoadModel(modelPath, nThreads, nCtx, useMmap, useGpu);
    }

    public void freeModel(long modelHandle) {
        nativeFreeModel(modelHandle);
    }

    public long createContext(long modelHandle, int nCtx) {
        return nativeCreateContext(modelHandle, nCtx);
    }

    public void freeContext(long ctxHandle) {
        nativeFreeContext(ctxHandle);
    }

    public String complete(long ctxHandle, String prompt, int nPredict, float temperature, float topP, int topK, float repeatPenalty) {
        return nativeComplete(ctxHandle, prompt, nPredict, temperature, topP, topK, repeatPenalty);
    }

    public void abortCompletion(long ctxHandle) {
        nativeAbortCompletion(ctxHandle);
    }

    public int[] tokenize(long modelHandle, String text, boolean addBos) {
        return nativeTokenize(modelHandle, text, addBos);
    }

    public float[] embed(long ctxHandle, String text) {
        return nativeEmbed(ctxHandle, text);
    }

    public boolean trainLora(long modelHandle, String dataPath, String outputPath, int loraRank, float loraAlpha, float learningRate, int epochs, int batchSize, float dropout) {
        return nativeTrainLora(modelHandle, dataPath, outputPath, loraRank, loraAlpha, learningRate, epochs, batchSize, dropout);
    }

    public void abortTraining() {
        nativeAbortTraining();
    }

    public float getTrainProgress() {
        return nativeGetTrainProgress();
    }

    public String getTrainLog() {
        return nativeGetTrainLog();
    }

    public boolean applyLora(long modelHandle, String loraPath, float scale) {
        return nativeApplyLora(modelHandle, loraPath, scale);
    }

    public boolean removeLora(long modelHandle) {
        return nativeRemoveLora(modelHandle);
    }

    public int getDeviceMemory() {
        return nativeGetDeviceMemory();
    }

    public float getDeviceTemperature() {
        return nativeGetDeviceTemperature();
    }

    public boolean isGpuAvailable() {
        return nativeIsGpuAvailable();
    }

    public long initVisionModel(String modelPath, int ctxSize, int threads, int gpuLayers) {
        try {
            return nativeInitVisionModel(modelPath, ctxSize, threads, gpuLayers);
        } catch (Exception e) {
            return 0L;
        }
    }

    public String visionChat(long visionCtx, String imagePath, String prompt, int maxTokens, float temp) {
        try {
            return nativeVisionChat(visionCtx, imagePath, prompt, maxTokens, temp);
        } catch (Exception e) {
            return null;
        }
    }

    public String imageOcr(long visionCtx, String imagePath) {
        try {
            return nativeImageOcr(visionCtx, imagePath);
        } catch (Exception e) {
            return null;
        }
    }

    public void releaseVisionModel(long visionCtx) {
        try {
            nativeReleaseVisionModel(visionCtx);
        } catch (Exception e) {
            Log.w(TAG, "Failed to release vision model", e);
        }
    }

    public boolean isQwenVisionModel(long visionCtx) {
        try {
            return nativeIsQwenVisionModel(visionCtx);
        } catch (Exception e) {
            return false;
        }
    }

    public int quantizeModel(String inputPath, String outputPath, String quantType, int nThreads, boolean allowRequantize, boolean quantizeOutputTensor) {
        try {
            return nativeQuantizeModel(inputPath, outputPath, quantType, nThreads, allowRequantize, quantizeOutputTensor);
        } catch (UnsatisfiedLinkError e) {
            return -1;
        } catch (Exception e) {
            return -2;
        }
    }

    public void abortQuantize() {
        try {
            nativeAbortQuantize();
        } catch (Exception e) {
            Log.w(TAG, "Failed to abort quantize", e);
        }
    }

    public float getQuantizeProgress() {
        try {
            return nativeGetQuantizeProgress();
        } catch (Exception e) {
            return 0f;
        }
    }
}
