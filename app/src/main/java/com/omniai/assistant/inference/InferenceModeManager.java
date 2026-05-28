package com.omniai.assistant.inference;

import com.omniai.assistant.scheduler.InferenceParams;

public class InferenceModeManager {

    private static volatile InferenceModeManager instance;

    private InferenceSpeedMode currentMode;

    private InferenceModeManager() {
        this.currentMode = InferenceSpeedMode.BALANCED;
    }

    public static InferenceModeManager getInstance() {
        if (instance == null) {
            synchronized (InferenceModeManager.class) {
                if (instance == null) {
                    instance = new InferenceModeManager();
                }
            }
        }
        return instance;
    }

    public void setMode(InferenceSpeedMode mode) {
        this.currentMode = mode;
    }

    public InferenceSpeedMode getMode() {
        return currentMode;
    }

    public InferenceParams getParamsForMode(InferenceSpeedMode mode) {
        int maxThreads = Runtime.getRuntime().availableProcessors();
        int halfThreads = Math.max(1, maxThreads / 2);

        switch (mode) {
            case FAST:
                return new InferenceParams.Builder()
                        .nThreads(maxThreads)
                        .temperature(0.3f)
                        .topP(0.8f)
                        .topK(40)
                        .repeatPenalty(1.1f)
                        .build();
            case BALANCED:
                return new InferenceParams.Builder()
                        .nThreads(halfThreads)
                        .temperature(0.7f)
                        .topP(0.9f)
                        .topK(50)
                        .repeatPenalty(1.15f)
                        .build();
            case PRECISION:
                return new InferenceParams.Builder()
                        .nThreads(halfThreads)
                        .temperature(0.2f)
                        .topP(0.95f)
                        .topK(80)
                        .repeatPenalty(1.2f)
                        .build();
            default:
                return new InferenceParams.Builder()
                        .nThreads(halfThreads)
                        .temperature(0.7f)
                        .topP(0.9f)
                        .topK(50)
                        .repeatPenalty(1.15f)
                        .build();
        }
    }

    public enum InferenceSpeedMode {
        FAST,
        BALANCED,
        PRECISION
    }
}
