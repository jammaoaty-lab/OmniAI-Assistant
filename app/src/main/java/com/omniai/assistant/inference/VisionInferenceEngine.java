package com.omniai.assistant.inference;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

import com.omniai.assistant.OmniAIApplication;
import com.omniai.assistant.common.Constants;
import com.omniai.assistant.model.AIModel;
import com.omniai.assistant.nativebridge.LlamaBridge;
import com.omniai.assistant.scheduler.InferenceParams;
import com.omniai.assistant.settings.InferenceSpeedMode;
import com.omniai.assistant.simulation.SimulatedVisionEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VisionInferenceEngine {

    private static volatile VisionInferenceEngine instance;

    private LlamaBridge bridge;
    private long visionModelHandle;
    private AIModel currentVisionModel;
    private boolean isVisionModelLoaded;
    private InferenceModeManager modeManager;
    private ThermalMonitor thermalMonitor;
    private Context context;
    private ExecutorService executorService;

    private SimulatedVisionEngine simulatedEngine;
    private boolean useSimulationMode;

    public interface LoadCallback {
        void onLoaded(AIModel model);
        void onError(String error);
    }

    public interface VisionCallback {
        void onProgress(String partialText);
        void onSuccess(String result);
        void onError(String error);
    }

    public interface OcrCallback {
        void onProgress(int percent, String message);
        void onSuccess(String text);
        void onError(String error);
    }

    private VisionInferenceEngine() {
        this.bridge = LlamaBridge.getInstance();
        this.visionModelHandle = 0L;
        this.isVisionModelLoaded = false;
        this.modeManager = InferenceModeManager.getInstance();
        this.thermalMonitor = ThermalMonitor.getInstance();
        this.executorService = Executors.newSingleThreadExecutor();
        this.context = OmniAIApplication.getInstance();
        this.simulatedEngine = SimulatedVisionEngine.getInstance(context);
        loadSimulationModePreference();
    }

    public static VisionInferenceEngine getInstance() {
        if (instance == null) {
            synchronized (VisionInferenceEngine.class) {
                if (instance == null) {
                    instance = new VisionInferenceEngine();
                }
            }
        }
        return instance;
    }

    public static synchronized void init(Context context) {
        VisionInferenceEngine engine = getInstance();
        engine.context = context.getApplicationContext();
    }

    private void loadSimulationModePreference() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.useSimulationMode = prefs.getBoolean(Constants.PREF_KEY_USE_SIMULATION, true);
    }

    public void setUseSimulationMode(boolean enabled) {
        this.useSimulationMode = enabled;
        SharedPreferences.Editor editor = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putBoolean(Constants.PREF_KEY_USE_SIMULATION, enabled);
        editor.apply();
        unloadVisionModel();
    }

    public boolean isUsingSimulationMode() {
        return useSimulationMode;
    }

    public void loadVisionModel(AIModel model, LoadCallback callback) {
        if (useSimulationMode) {
            loadVisionModelSimulation(model, callback);
        } else {
            loadVisionModelNative(model, callback);
        }
    }

    private void loadVisionModelNative(AIModel model, LoadCallback callback) {
        executorService.execute(() -> {
            try {
                if (visionModelHandle != 0L) {
                    unloadVisionModel();
                }

                int cpuCores = Runtime.getRuntime().availableProcessors();
                int threads = Math.max(1, cpuCores - 1);

                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                boolean hasLowMemory = false;
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    hasLowMemory = mi.availMem < 512L * 1024 * 1024;
                }
                if (hasLowMemory) {
                    threads = Math.max(1, threads / 2);
                }

                int gpuLayers = 0;
                if (bridge.isGpuAvailable()) {
                    gpuLayers = hasLowMemory ? 10 : 20;
                }

                int ctxSize = 2048;

                visionModelHandle = bridge.initVisionModel(model.getFilePath(), ctxSize, threads, gpuLayers);
                if (visionModelHandle == 0L) {
                    if (callback != null) callback.onError("Failed to load vision model: " + model.getName());
                    return;
                }

                currentVisionModel = model;
                currentVisionModel.setLoaded(true);
                isVisionModelLoaded = true;

                thermalMonitor.startMonitoring();

                if (callback != null) callback.onLoaded(model);
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    private void loadVisionModelSimulation(AIModel model, LoadCallback callback) {
        simulatedEngine.loadModel(model, new SimulatedVisionEngine.LoadCallback() {
            @Override
            public void onProgress(int current, int total, String message) {}

            @Override
            public void onSuccess() {
                currentVisionModel = model;
                currentVisionModel.setLoaded(true);
                isVisionModelLoaded = true;
                if (callback != null) callback.onLoaded(model);
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    public void unloadVisionModel() {
        if (useSimulationMode) {
            simulatedEngine.unloadModel();
        } else {
            if (visionModelHandle != 0L) {
                bridge.releaseVisionModel(visionModelHandle);
                visionModelHandle = 0L;
            }
        }
        if (currentVisionModel != null) {
            currentVisionModel.setLoaded(false);
        }
        currentVisionModel = null;
        isVisionModelLoaded = false;
        thermalMonitor.stopMonitoring();
    }

    public void visionChat(String imagePath, String prompt, VisionCallback callback) {
        if (useSimulationMode) {
            visionChatSimulation(imagePath, prompt, callback);
        } else {
            visionChatNative(imagePath, prompt, callback);
        }
    }

    private void visionChatNative(String imagePath, String prompt, VisionCallback callback) {
        if (!isVisionModelLoaded || visionModelHandle == 0L) {
            if (callback != null) callback.onError("Vision model not loaded");
            return;
        }
        executorService.execute(() -> {
            try {
                InferenceParams params = getVisionModeParams();
                String result = bridge.visionChat(visionModelHandle, imagePath, prompt, params.getNPredict(), params.getTemperature());
                if (result == null) {
                    if (callback != null) callback.onError("Vision chat returned null");
                    return;
                }
                if (callback != null) callback.onSuccess(result);
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    private void visionChatSimulation(String imagePath, String prompt, VisionCallback callback) {
        if (!isVisionModelLoaded) {
            if (callback != null) callback.onError("Vision model not loaded");
            return;
        }
        InferenceParams params = getVisionModeParams();
        simulatedEngine.visionChat(imagePath, prompt, params.getNPredict(), params.getTemperature(),
                new SimulatedVisionEngine.VisionCallback() {
                    @Override
                    public void onPartialResult(String text) {
                        if (callback != null) callback.onProgress(text);
                    }

                    @Override
                    public void onComplete(String text) {
                        if (callback != null) callback.onSuccess(text);
                    }

                    @Override
                    public void onError(String error) {
                        if (callback != null) callback.onError(error);
                    }
                });
    }

    public void imageOcr(String imagePath, OcrCallback callback) {
        if (useSimulationMode) {
            imageOcrSimulation(imagePath, callback);
        } else {
            imageOcrNative(imagePath, callback);
        }
    }

    private void imageOcrNative(String imagePath, OcrCallback callback) {
        if (!isVisionModelLoaded || visionModelHandle == 0L) {
            if (callback != null) callback.onError("Vision model not loaded");
            return;
        }
        executorService.execute(() -> {
            try {
                String result = bridge.imageOcr(visionModelHandle, imagePath);
                if (result == null) {
                    if (callback != null) callback.onError("OCR returned null");
                    return;
                }
                if (callback != null) callback.onSuccess(result);
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    private void imageOcrSimulation(String imagePath, OcrCallback callback) {
        if (!isVisionModelLoaded) {
            if (callback != null) callback.onError("Vision model not loaded");
            return;
        }
        simulatedEngine.imageOcr(imagePath, new SimulatedVisionEngine.OcrCallback() {
            @Override
            public void onProgress(int percent, String message) {
                if (callback != null) callback.onProgress(percent, message);
            }

            @Override
            public void onComplete(String text) {
                if (callback != null) callback.onSuccess(text);
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    public void switchVisionModel(AIModel newModel, LoadCallback callback) {
        executorService.execute(() -> {
            try {
                unloadVisionModel();
                loadVisionModel(newModel, callback);
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    public boolean isVisionModelLoaded() {
        if (useSimulationMode) {
            return simulatedEngine.isModelLoaded();
        }
        return isVisionModelLoaded && visionModelHandle != 0L;
    }

    public AIModel getCurrentVisionModel() {
        return currentVisionModel;
    }

    public InferenceParams getVisionModeParams() {
        InferenceSpeedMode mode = modeManager.getMode();
        switch (mode) {
            case FAST:
                return new InferenceParams.Builder()
                        .temperature(0.3f)
                        .topP(0.8f)
                        .topK(40)
                        .nPredict(512)
                        .build();
            case BALANCED:
                return new InferenceParams.Builder()
                        .temperature(0.7f)
                        .topP(0.9f)
                        .topK(50)
                        .nPredict(1024)
                        .build();
            case PRECISION:
                return new InferenceParams.Builder()
                        .temperature(0.2f)
                        .topP(0.95f)
                        .topK(80)
                        .nPredict(2048)
                        .build();
            default:
                return new InferenceParams.Builder()
                        .temperature(0.7f)
                        .topP(0.9f)
                        .topK(50)
                        .nPredict(1024)
                        .build();
        }
    }

    public boolean isQwenVisionModel() {
        if (useSimulationMode) {
            return simulatedEngine.isQwenVisionModel();
        }
        if (!isVisionModelLoaded || visionModelHandle == 0L) {
            return false;
        }
        return bridge.isQwenVisionModel(visionModelHandle);
    }

    public List<AIModel> getAvailableVisionModels() {
        if (useSimulationMode) {
            return simulatedEngine.getAvailableModels();
        }
        List<AIModel> models = new ArrayList<>();

        AIModel qwen3vl2b = new AIModel();
        qwen3vl2b.setId("qwen3-vl-2b");
        qwen3vl2b.setName("Qwen3-VL-2B");
        qwen3vl2b.setModelType("VISION");
        qwen3vl2b.setVisionCapability("FULL");
        qwen3vl2b.setFileSize(1_500_000_000L);
        qwen3vl2b.setQuantType("Q4_K_M");
        models.add(qwen3vl2b);

        AIModel qwen25vl7b = new AIModel();
        qwen25vl7b.setId("qwen2.5-vl-7b");
        qwen25vl7b.setName("Qwen2.5-VL-7B");
        qwen25vl7b.setModelType("VISION");
        qwen25vl7b.setVisionCapability("FULL");
        qwen25vl7b.setFileSize(4_200_000_000L);
        qwen25vl7b.setQuantType("Q4_K_M");
        models.add(qwen25vl7b);

        AIModel smolvlm256m = new AIModel();
        smolvlm256m.setId("smolvlm2-256m");
        smolvlm256m.setName("SmolVLM2-256M");
        smolvlm256m.setModelType("VISION");
        smolvlm256m.setVisionCapability("IMAGE_UNDERSTANDING");
        smolvlm256m.setFileSize(500_000_000L);
        smolvlm256m.setQuantType("Q4_K_M");
        models.add(smolvlm256m);

        return models;
    }

    public AIModel getDefaultVisionModel() {
        if (useSimulationMode) {
            return simulatedEngine.getDefaultModel();
        }
        AIModel model = new AIModel();
        model.setId("qwen3-vl-2b");
        model.setName("Qwen3-VL-2B");
        model.setModelType("VISION");
        model.setVisionCapability("FULL");
        model.setFileSize(1_500_000_000L);
        model.setQuantType("Q4_K_M");
        return model;
    }

    public boolean checkHardwareCompatibility(AIModel model) {
        if (useSimulationMode) return true;
        if (context == null) return false;

        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);

        long requiredMemory = model.getFileSize() * 2;
        long availableMemory = mi.availMem;

        if (availableMemory < requiredMemory) {
            return false;
        }

        int cpuCores = Runtime.getRuntime().availableProcessors();
        if (cpuCores < 2) {
            return false;
        }

        return true;
    }
}
