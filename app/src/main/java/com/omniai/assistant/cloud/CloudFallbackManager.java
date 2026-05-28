package com.omniai.assistant.cloud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.omniai.assistant.inference.InferenceEngine;
import com.omniai.assistant.inference.VisionInferenceEngine;
import com.omniai.assistant.inference.ThermalMonitor;
import com.omniai.assistant.util.NetworkUtil;

public class CloudFallbackManager {

    private static volatile CloudFallbackManager instance;

    private boolean isCloudActive;
    private boolean isVisionFallback;
    private String fallbackReason;
    private CloudInferenceClient cloudClient;
    private InferenceEngine localEngine;
    private VisionInferenceEngine visionEngine;
    private Context context;
    private FallbackListener listener;
    private Handler handler;

    private static final long RESTORE_CHECK_INTERVAL_MS = 30_000L;
    private static final long VISION_INFERENCE_TIMEOUT_MS = 120_000L;

    private final Runnable restoreCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isCloudActive && !isVisionFallback) return;
            if (shouldRestoreToLocal()) {
                restoreLocal();
            } else {
                handler.postDelayed(this, RESTORE_CHECK_INTERVAL_MS);
            }
        }
    };

    public interface FallbackListener {
        void onFallbackToCloud(String reason);
        void onRestoredToLocal();
    }

    private CloudFallbackManager() {
        this.cloudClient = new CloudInferenceClient();
        this.localEngine = InferenceEngine.getInstance();
        this.visionEngine = VisionInferenceEngine.getInstance();
        this.handler = new Handler(Looper.getMainLooper());
        this.isCloudActive = false;
        this.isVisionFallback = false;
        this.fallbackReason = "";
    }

    public static CloudFallbackManager getInstance() {
        if (instance == null) {
            synchronized (CloudFallbackManager.class) {
                if (instance == null) {
                    instance = new CloudFallbackManager();
                }
            }
        }
        return instance;
    }

    public void setContext(Context context) {
        this.context = context.getApplicationContext();
    }

    public void checkAndFallback(String reason) {
        if (isCloudActive) return;
        if (!cloudClient.isAvailable()) {
            cloudClient.checkAvailability();
        }
        if (!cloudClient.isAvailable()) return;

        isCloudActive = true;
        fallbackReason = reason;
        if (listener != null) {
            listener.onFallbackToCloud(reason);
        }
        handler.postDelayed(restoreCheckRunnable, RESTORE_CHECK_INTERVAL_MS);
    }

    public void checkAndFallbackVision(String reason) {
        if (isVisionFallback) return;
        if (!cloudClient.isAvailable()) {
            cloudClient.checkAvailability();
        }
        if (!cloudClient.checkVisionAvailability()) return;

        isVisionFallback = true;
        fallbackReason = reason;
        if (listener != null) {
            listener.onFallbackToCloud(reason);
        }
        handler.postDelayed(restoreCheckRunnable, RESTORE_CHECK_INTERVAL_MS);
    }

    public void restoreLocal() {
        if (!isCloudActive && !isVisionFallback) return;
        isCloudActive = false;
        isVisionFallback = false;
        fallbackReason = "";
        handler.removeCallbacks(restoreCheckRunnable);
        if (listener != null) {
            listener.onRestoredToLocal();
        }
    }

    public void restoreLocalVision() {
        if (!isVisionFallback) return;
        isVisionFallback = false;
        fallbackReason = "";
        handler.removeCallbacks(restoreCheckRunnable);
        if (listener != null) {
            listener.onRestoredToLocal();
        }
    }

    public boolean isCloudActive() {
        return isCloudActive;
    }

    public boolean isVisionCloudActive() {
        return isVisionFallback;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackListener(FallbackListener listener) {
        this.listener = listener;
    }

    public boolean shouldFallback() {
        if (!localEngine.isModelLoaded()) {
            return true;
        }
        if (isMemoryLow()) {
            return true;
        }
        ThermalMonitor.ThermalStatus thermalStatus = ThermalMonitor.getInstance().checkThermalStatus();
        if (thermalStatus == ThermalMonitor.ThermalStatus.HIGH || thermalStatus == ThermalMonitor.ThermalStatus.CRITICAL) {
            return true;
        }
        if (!visionEngine.isVisionModelLoaded()) {
            return true;
        }
        return false;
    }

    public boolean shouldFallbackVision() {
        if (!visionEngine.isVisionModelLoaded()) {
            return true;
        }
        if (isVisionModelCorrupted()) {
            return true;
        }
        if (isVisionInferenceTimeout()) {
            return true;
        }
        return false;
    }

    private boolean isVisionModelCorrupted() {
        try {
            com.omniai.assistant.model.AIModel currentModel = visionEngine.getCurrentVisionModel();
            if (currentModel == null || currentModel.getFilePath() == null) return true;
            java.io.File file = new java.io.File(currentModel.getFilePath());
            return !file.exists() || file.length() == 0;
        } catch (Exception e) {
            return true;
        }
    }

    private long lastVisionInferenceStartTime = 0;

    private boolean isVisionInferenceTimeout() {
        if (lastVisionInferenceStartTime == 0) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - lastVisionInferenceStartTime;
        return elapsed > VISION_INFERENCE_TIMEOUT_MS;
    }

    public void markVisionInferenceStart() {
        lastVisionInferenceStartTime = System.currentTimeMillis();
    }

    public void markVisionInferenceEnd() {
        lastVisionInferenceStartTime = 0;
    }

    private boolean shouldRestoreToLocal() {
        if (!localEngine.isModelLoaded()) {
            return false;
        }
        if (isMemoryLow()) {
            return false;
        }
        ThermalMonitor.ThermalStatus thermalStatus = ThermalMonitor.getInstance().checkThermalStatus();
        if (thermalStatus == ThermalMonitor.ThermalStatus.HIGH || thermalStatus == ThermalMonitor.ThermalStatus.CRITICAL) {
            return false;
        }
        if (context != null && !NetworkUtil.isNetworkAvailable(context)) {
            return false;
        }
        if (isVisionFallback && !visionEngine.isVisionModelLoaded()) {
            return false;
        }
        return true;
    }

    private boolean isMemoryLow() {
        long deviceMemory = localEngine.getDeviceMemory();
        return deviceMemory > 0 && deviceMemory < 512 * 1024 * 1024;
    }

    public CloudInferenceClient getCloudClient() {
        return cloudClient;
    }
}
