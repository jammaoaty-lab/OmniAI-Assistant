package com.omniai.assistant.scheduler;

import com.omniai.assistant.cloud.CloudFallbackManager;
import com.omniai.assistant.cloud.CloudInferenceClient;
import com.omniai.assistant.inference.InferenceEngine;
import com.omniai.assistant.inference.ThermalMonitor;

public class AIScheduler {

    private static volatile AIScheduler instance;

    private InferenceMode currentMode;
    private InferenceEngine localEngine;
    private CloudInferenceClient cloudClient;
    private ThermalMonitor thermalMonitor;
    private CloudFallbackManager fallbackManager;
    private SchedulerCallback callback;

    public enum InferenceMode {
        LOCAL,
        CLOUD,
        HYBRID
    }

    public interface InferenceCallback {
        void onToken(String token);
        void onComplete(String result);
        void onError(String error);
        void onModeSwitched(InferenceMode mode);
    }

    public interface SchedulerCallback {
        void onModeChanged(InferenceMode newMode, InferenceMode oldMode);
        void onLocalUnavailable(String reason);
        void onCloudFallback(String reason);
    }

    private AIScheduler() {
        this.currentMode = InferenceMode.LOCAL;
        this.localEngine = InferenceEngine.getInstance();
        this.cloudClient = new CloudInferenceClient();
        this.thermalMonitor = ThermalMonitor.getInstance();
        this.fallbackManager = CloudFallbackManager.getInstance();
    }

    public static AIScheduler getInstance() {
        if (instance == null) {
            synchronized (AIScheduler.class) {
                if (instance == null) {
                    instance = new AIScheduler();
                }
            }
        }
        return instance;
    }

    public void dispatch(String prompt, InferenceParams params, InferenceCallback callback) {
        if (shouldFallbackToCloud()) {
            switchToCloud("Local inference unavailable");
            dispatchToCloud(prompt, params, callback);
            return;
        }

        switch (currentMode) {
            case LOCAL:
                dispatchToLocal(prompt, params, callback);
                break;
            case CLOUD:
                dispatchToCloud(prompt, params, callback);
                break;
            case HYBRID:
                if (isLocalAvailable()) {
                    dispatchToLocal(prompt, params, callback);
                } else {
                    dispatchToCloud(prompt, params, callback);
                }
                break;
        }
    }

    private void dispatchToLocal(String prompt, InferenceParams params, InferenceCallback callback) {
        localEngine.streamComplete(prompt, params, new InferenceEngine.StreamCallback() {
            @Override
            public void onToken(String token) {
                if (callback != null) callback.onToken(token);
            }

            @Override
            public void onComplete(String fullResult) {
                if (callback != null) callback.onComplete(fullResult);
            }

            @Override
            public void onError(String error) {
                if (callback != null) {
                    if (shouldFallbackToCloud()) {
                        switchToCloud("Local inference error: " + error);
                        dispatchToCloud(prompt, params, callback);
                    } else {
                        callback.onError(error);
                    }
                }
            }
        });
    }

    private void dispatchToCloud(String prompt, InferenceParams params, InferenceCallback callback) {
        cloudClient.streamComplete(prompt, params, new CloudInferenceClient.StreamCallback() {
            @Override
            public void onToken(String token) {
                if (callback != null) callback.onToken(token);
            }

            @Override
            public void onComplete(String fullResult) {
                if (callback != null) callback.onComplete(fullResult);
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    public void switchToLocal() {
        InferenceMode oldMode = currentMode;
        currentMode = InferenceMode.LOCAL;
        fallbackManager.restoreLocal();
        if (callback != null && oldMode != currentMode) {
            callback.onModeChanged(currentMode, oldMode);
        }
    }

    public void switchToCloud(String reason) {
        InferenceMode oldMode = currentMode;
        currentMode = InferenceMode.CLOUD;
        fallbackManager.checkAndFallback(reason);
        if (callback != null) {
            if (oldMode != currentMode) {
                callback.onModeChanged(currentMode, oldMode);
            }
            callback.onCloudFallback(reason);
        }
    }

    public InferenceMode getCurrentMode() {
        return currentMode;
    }

    public void setInferenceMode(InferenceMode mode) {
        InferenceMode oldMode = currentMode;
        currentMode = mode;
        if (callback != null && oldMode != mode) {
            callback.onModeChanged(mode, oldMode);
        }
    }

    public boolean isLocalAvailable() {
        if (!localEngine.isModelLoaded()) {
            return false;
        }
        ThermalMonitor.ThermalStatus thermalStatus = thermalMonitor.checkThermalStatus();
        if (thermalStatus == ThermalMonitor.ThermalStatus.HIGH || thermalStatus == ThermalMonitor.ThermalStatus.CRITICAL) {
            return false;
        }
        long deviceMemory = localEngine.getDeviceMemory();
        if (deviceMemory > 0 && deviceMemory < 512 * 1024 * 1024) {
            return false;
        }
        return true;
    }

    public boolean shouldFallbackToCloud() {
        if (!localEngine.isModelLoaded()) {
            return true;
        }
        ThermalMonitor.ThermalStatus thermalStatus = thermalMonitor.checkThermalStatus();
        if (thermalStatus == ThermalMonitor.ThermalStatus.HIGH || thermalStatus == ThermalMonitor.ThermalStatus.CRITICAL) {
            return true;
        }
        long deviceMemory = localEngine.getDeviceMemory();
        if (deviceMemory > 0 && deviceMemory < 512 * 1024 * 1024) {
            return true;
        }
        return false;
    }

    public void setSchedulerCallback(SchedulerCallback callback) {
        this.callback = callback;
    }
}
