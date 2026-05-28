package com.omniai.assistant.multimodal;

import android.app.ActivityManager;

import com.omniai.assistant.inference.VisionInferenceEngine;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ImageAnalyzer {

    private VisionInferenceEngine visionEngine;
    private boolean isInitialized;
    private android.content.Context context;

    private static final long MAX_IMAGE_SIZE = 20 * 1024 * 1024;
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("jpg", "jpeg", "png", "webp");
    private static final int VISION_TIMEOUT_SECONDS = 60;

    public interface ImageCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public ImageAnalyzer() {
        this.isInitialized = false;
        this.visionEngine = null;
    }

    public void initialize() {
        visionEngine = VisionInferenceEngine.getInstance();
        isInitialized = true;
    }

    public void setContext(android.content.Context context) {
        this.context = context.getApplicationContext();
    }

    public String analyze(String imagePath, String question) {
        if (!isInitialized || visionEngine == null) {
            throw new IllegalStateException("Image analyzer not initialized");
        }
        if (!validateImageFile(imagePath)) {
            throw new IllegalArgumentException("Invalid image file");
        }
        checkMemoryBeforeInference();
        return callVisionSync(imagePath, question);
    }

    public String describe(String imagePath) {
        if (!isInitialized || visionEngine == null) {
            throw new IllegalStateException("Image analyzer not initialized");
        }
        if (!validateImageFile(imagePath)) {
            throw new IllegalArgumentException("Invalid image file");
        }
        checkMemoryBeforeInference();
        return callVisionSync(imagePath, "Describe this image in detail.");
    }

    private String callVisionSync(String imagePath, String prompt) {
        try {
            final String[] resultHolder = new String[1];
            final boolean[] errorHolder = new boolean[1];
            CountDownLatch latch = new CountDownLatch(1);

            visionEngine.visionChat(imagePath, prompt, new VisionInferenceEngine.VisionCallback() {
                @Override
                public void onProgress(String partialText) {
                }

                @Override
                public void onSuccess(String result) {
                    resultHolder[0] = result;
                    latch.countDown();
                }

                @Override
                public void onError(String error) {
                    errorHolder[0] = true;
                    resultHolder[0] = error;
                    latch.countDown();
                }
            });

            boolean completed = latch.await(VISION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                throw new RuntimeException("Image analysis timed out");
            }
            if (errorHolder[0]) {
                throw new RuntimeException(resultHolder[0]);
            }
            return resultHolder[0] != null ? resultHolder[0] : "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Analysis interrupted", e);
        }
    }

    public void analyzeWithQuestion(String imagePath, String question, ImageCallback callback) {
        if (callback == null) return;
        if (!isInitialized || visionEngine == null) {
            callback.onError("Image analyzer not initialized");
            return;
        }
        if (!validateImageFile(imagePath)) {
            callback.onError("Invalid image file: does not exist, unsupported format, or exceeds 20MB");
            return;
        }
        if (!checkMemoryForCallback()) {
            callback.onError("Insufficient memory for image analysis");
            return;
        }
        visionEngine.visionChat(imagePath, question, new VisionInferenceEngine.VisionCallback() {
            @Override
            public void onProgress(String partialText) {
            }

            @Override
            public void onSuccess(String result) {
                callback.onSuccess(result);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    private boolean validateImageFile(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) return false;
        File file = new File(imagePath);
        if (!file.exists()) return false;
        if (file.length() > MAX_IMAGE_SIZE) return false;
        String extension = getImageExtension(imagePath);
        return !extension.isEmpty() && SUPPORTED_FORMATS.contains(extension.toLowerCase());
    }

    private String getImageExtension(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < path.length() - 1) {
            return path.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    private void checkMemoryBeforeInference() {
        if (context == null) return;
        ActivityManager am = (ActivityManager) context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (mi.availMem < 512L * 1024 * 1024) {
                throw new RuntimeException("Insufficient memory for inference");
            }
        }
    }

    private boolean checkMemoryForCallback() {
        if (context == null) return true;
        ActivityManager am = (ActivityManager) context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.availMem >= 512L * 1024 * 1024;
        }
        return true;
    }
}
