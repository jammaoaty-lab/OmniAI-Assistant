package com.omniai.assistant.multimodal;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import com.omniai.assistant.inference.VisionInferenceEngine;
import com.omniai.assistant.model.AIModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MultimodalManager {

    private static MultimodalManager instance;

    private OcrEngine ocrEngine;
    private SpeechRecognizer speechRecognizer;
    private TtsEngine ttsEngine;
    private ImageAnalyzer imageAnalyzer;
    private VisionInferenceEngine visionEngine;
    private Context context;
    private Handler handler;

    private static final long MAX_IMAGE_SIZE = 20 * 1024 * 1024;
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("jpg", "jpeg", "png", "webp");

    private MultimodalManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        this.ocrEngine = new OcrEngine();
        this.speechRecognizer = new SpeechRecognizer(context);
        this.ttsEngine = new TtsEngine();
        this.imageAnalyzer = new ImageAnalyzer();
        this.visionEngine = VisionInferenceEngine.getInstance();
    }

    public static synchronized MultimodalManager getInstance(Context context) {
        if (instance == null) {
            instance = new MultimodalManager(context);
        }
        return instance;
    }

    public void recognizeImage(String imagePath, ImageCallback callback) {
        if (callback == null) return;
        if (!hasStoragePermission()) {
            callback.onError("Storage permission not granted");
            return;
        }
        if (!validateImageFile(imagePath, callback)) return;
        if (!checkHardwareReady(callback)) return;
        visionEngine.visionChat(imagePath, "Describe this image in detail.", new VisionInferenceEngine.VisionCallback() {
            @Override
            public void onSuccess(String result) {
                handler.post(() -> callback.onSuccess(result));
            }

            @Override
            public void onError(String error) {
                handler.post(() -> callback.onError(error));
            }
        });
    }

    public void extractOcr(String imagePath, OcrCallback callback) {
        if (callback == null) return;
        if (!hasStoragePermission()) {
            callback.onError("Storage permission not granted");
            return;
        }
        if (!validateImageFile(imagePath, callback)) return;
        if (!checkHardwareReady(callback)) return;
        visionEngine.imageOcr(imagePath, new VisionInferenceEngine.OcrCallback() {
            @Override
            public void onSuccess(String text) {
                handler.post(() -> callback.onSuccess(text));
            }

            @Override
            public void onError(String error) {
                handler.post(() -> callback.onError(error));
            }
        });
    }

    public void startVoiceRecognition(VoiceCallback callback) {
        if (speechRecognizer == null) {
            if (callback != null) {
                callback.onError("Speech recognizer not initialized");
            }
            return;
        }
        speechRecognizer.startListening();
    }

    public void stopVoiceRecognition() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    public void synthesizeSpeech(String text, String voiceId, TtsCallback callback) {
        if (ttsEngine == null || !ttsEngine.isReady()) {
            if (callback != null) {
                callback.onError("TTS engine not ready");
            }
            return;
        }
        ttsEngine.speak(text, voiceId, new TtsEngine.UtteranceCallback() {
            @Override
            public void onStart() {
                handler.post(() -> {
                    if (callback != null) {
                        callback.onStart();
                    }
                });
            }

            @Override
            public void onComplete() {
                handler.post(() -> {
                    if (callback != null) {
                        callback.onComplete();
                    }
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    if (callback != null) {
                        callback.onError(error);
                    }
                });
            }
        });
    }

    public void analyzeImage(String imagePath, String question, ImageCallback callback) {
        if (callback == null) return;
        if (!hasStoragePermission()) {
            callback.onError("Storage permission not granted");
            return;
        }
        if (!hasCameraPermission()) {
            callback.onError("Camera permission not granted");
            return;
        }
        if (!validateImageFile(imagePath, callback)) return;
        if (!checkHardwareReady(callback)) return;
        visionEngine.visionChat(imagePath, question, new VisionInferenceEngine.VisionCallback() {
            @Override
            public void onSuccess(String result) {
                handler.post(() -> callback.onSuccess(result));
            }

            @Override
            public void onError(String error) {
                handler.post(() -> callback.onError(error));
            }
        });
    }

    public void loadVisionModel(AIModel model, VisionInferenceEngine.LoadCallback callback) {
        visionEngine.loadVisionModel(model, callback);
    }

    public void switchVisionModel(AIModel newModel, VisionInferenceEngine.LoadCallback callback) {
        visionEngine.switchVisionModel(newModel, callback);
    }

    public boolean isVisionModelLoaded() {
        return visionEngine.isVisionModelLoaded();
    }

    public AIModel getCurrentVisionModel() {
        return visionEngine.getCurrentVisionModel();
    }

    public boolean checkHardwareCompatibility(AIModel model) {
        return visionEngine.checkHardwareCompatibility(model);
    }

    public List<String> getAvailableVoices() {
        if (ttsEngine != null && ttsEngine.isReady()) {
            return ttsEngine.getAvailableVoices();
        }
        return new ArrayList<>();
    }

    public OcrEngine getOcrEngine() {
        return ocrEngine;
    }

    public SpeechRecognizer getSpeechRecognizer() {
        return speechRecognizer;
    }

    public TtsEngine getTtsEngine() {
        return ttsEngine;
    }

    public ImageAnalyzer getImageAnalyzer() {
        return imageAnalyzer;
    }

    public void shutdown() {
        if (ocrEngine != null) {
            ocrEngine.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (ttsEngine != null) {
            ttsEngine.shutdown();
        }
        if (visionEngine != null) {
            visionEngine.unloadVisionModel();
        }
    }

    private boolean validateImageFile(String imagePath, ImageCallback callback) {
        if (imagePath == null || imagePath.isEmpty()) {
            callback.onError("Image path is empty");
            return false;
        }
        File file = new File(imagePath);
        if (!file.exists()) {
            callback.onError("Image file does not exist");
            return false;
        }
        if (file.length() > MAX_IMAGE_SIZE) {
            callback.onError("Image file exceeds 20MB limit");
            return false;
        }
        String extension = getImageExtension(imagePath);
        if (extension.isEmpty() || !SUPPORTED_FORMATS.contains(extension.toLowerCase())) {
            callback.onError("Unsupported image format. Supported: jpg, png, webp");
            return false;
        }
        return true;
    }

    private boolean validateImageFile(String imagePath, OcrCallback callback) {
        if (imagePath == null || imagePath.isEmpty()) {
            callback.onError("Image path is empty");
            return false;
        }
        File file = new File(imagePath);
        if (!file.exists()) {
            callback.onError("Image file does not exist");
            return false;
        }
        if (file.length() > MAX_IMAGE_SIZE) {
            callback.onError("Image file exceeds 20MB limit");
            return false;
        }
        String extension = getImageExtension(imagePath);
        if (extension.isEmpty() || !SUPPORTED_FORMATS.contains(extension.toLowerCase())) {
            callback.onError("Unsupported image format. Supported: jpg, png, webp");
            return false;
        }
        return true;
    }

    private boolean checkHardwareReady(ImageCallback callback) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (mi.availMem < 512L * 1024 * 1024) {
                callback.onError("Insufficient memory for vision operation");
                return false;
            }
        }
        float temperature = getDeviceTemperature();
        if (temperature > 45.0f) {
            callback.onError("Device temperature too high for vision operation");
            return false;
        }
        return true;
    }

    private boolean checkHardwareReady(OcrCallback callback) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (mi.availMem < 512L * 1024 * 1024) {
                callback.onError("Insufficient memory for OCR operation");
                return false;
            }
        }
        float temperature = getDeviceTemperature();
        if (temperature > 45.0f) {
            callback.onError("Device temperature too high for OCR operation");
            return false;
        }
        return true;
    }

    private String getImageExtension(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < path.length() - 1) {
            return path.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    private float getDeviceTemperature() {
        try {
            com.omniai.assistant.inference.ThermalMonitor thermalMonitor =
                    com.omniai.assistant.inference.ThermalMonitor.getInstance();
            com.omniai.assistant.inference.ThermalMonitor.ThermalStatus status =
                    thermalMonitor.checkThermalStatus();
            switch (status) {
                case CRITICAL: return 50.0f;
                case HIGH: return 46.0f;
                case MODERATE: return 40.0f;
                default: return 35.0f;
            }
        } catch (Exception e) {
            return 35.0f;
        }
    }

    private boolean hasStoragePermission() {
        if (context == null) return false;
        return context.checkCallingOrSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
                || context.checkCallingOrSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCameraPermission() {
        if (context == null) return false;
        return context.checkCallingOrSelfPermission(android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public interface ImageCallback {
        void onSuccess(String description);
        void onError(String error);
    }

    public interface OcrCallback {
        void onSuccess(String text);
        void onError(String error);
    }

    public interface VoiceCallback {
        void onResult(String text);
        void onPartial(String text);
        void onError(String error);
    }

    public interface TtsCallback {
        void onStart();
        void onComplete();
        void onError(String error);
    }
}
