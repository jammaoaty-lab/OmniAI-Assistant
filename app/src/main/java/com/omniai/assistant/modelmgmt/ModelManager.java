package com.omniai.assistant.modelmgmt;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.omniai.assistant.inference.VisionInferenceEngine;
import com.omniai.assistant.model.AIModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModelManager {

    private static final String TAG = "ModelManager";

    private static volatile ModelManager instance;
    private List<AIModel> models;
    private List<AIModel> visionModels;
    private AIModel activeModel;
    private SharedPreferences prefs;
    private final Gson gson;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile float downloadProgress;
    private Map<String, Float> downloadProgressMap;
    private Map<String, Boolean> downloadCancelledMap;
    private OkHttpClient downloadClient;

    public interface DownloadCallback {
        void onProgress(float progress);
        void onComplete(AIModel model);
        void onError(String message);
    }

    private ModelManager(SharedPreferences prefs) {
        this.prefs = prefs;
        this.gson = new Gson();
        this.models = new ArrayList<>();
        this.visionModels = new ArrayList<>();
        this.downloadProgress = 0f;
        this.downloadProgressMap = new HashMap<>();
        this.downloadCancelledMap = new HashMap<>();
        this.downloadClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        loadModels();
    }

    public static ModelManager getInstance(SharedPreferences prefs) {
        if (instance == null) {
            synchronized (ModelManager.class) {
                if (instance == null) {
                    instance = new ModelManager(prefs);
                }
            }
        }
        return instance;
    }

    public void importModel(String filePath, String name) {
        File file = new File(filePath);
        if (!file.exists()) return;
        AIModel model = new AIModel();
        model.setId(generateModelId());
        model.setName(name);
        model.setFilePath(filePath);
        model.setEncrypted(false);
        models.add(model);
        saveModels();
    }

    public void deleteModel(String modelId) {
        AIModel target = null;
        for (AIModel model : models) {
            if (model.getId().equals(modelId)) {
                target = model;
                break;
            }
        }
        if (target != null) {
            File file = new File(target.getFilePath());
            if (file.exists()) {
                file.delete();
            }
            models.remove(target);
            if (activeModel != null && activeModel.getId().equals(modelId)) {
                activeModel = models.isEmpty() ? null : models.get(0);
                saveActiveModel();
            }
            saveModels();
        }
    }

    public void renameModel(String modelId, String newName) {
        for (AIModel model : models) {
            if (model.getId().equals(modelId)) {
                model.setName(newName);
                saveModels();
                return;
            }
        }
    }

    public void categorizeModel(String modelId, String category) {
        for (AIModel model : models) {
            if (model.getId().equals(modelId)) {
                model.setCategory(category);
                saveModels();
                return;
            }
        }
    }

    public boolean verifyModelIntegrity(String modelId) {
        AIModel model = getModel(modelId);
        if (model == null) return false;
        ModelVerifier verifier = new ModelVerifier();
        return verifier.verifyGguf(model.getFilePath());
    }

    public void quantizeModel(String modelId, GgufQuantizer.QuantizationType quantType, 
                            GgufQuantizer.QuantizationCallback callback) {
        AIModel model = getModel(modelId);
        if (model == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Model not found"));
            }
            return;
        }

        GgufQuantizer quantizer = GgufQuantizer.getInstance();
        String outputPath = quantizer.generateOutputPath(model.getFilePath(), quantType);
        
        quantizer.quantizeModel(null, model.getFilePath(), outputPath, quantType, 
            new GgufQuantizer.QuantizationCallback() {
                @Override
                public void onProgress(float progress) {
                    if (callback != null) {
                        callback.onProgress(progress);
                    }
                }

                @Override
                public void onComplete(String path) {
                    AIModel quantizedModel = new AIModel();
                    quantizedModel.setId(generateModelId());
                    quantizedModel.setName(model.getName() + " (" + quantType.getType().toUpperCase() + ")");
                    quantizedModel.setFilePath(path);
                    quantizedModel.setQuantType(quantType.getType().toUpperCase());
                    quantizedModel.setFileSize(new java.io.File(path).length());
                    models.add(quantizedModel);
                    saveModels();
                    
                    if (callback != null) {
                        callback.onComplete(path);
                    }
                }

                @Override
                public void onError(String message) {
                    if (callback != null) {
                        callback.onError(message);
                    }
                }
            });
    }

    public boolean repairModel(String modelId) {
        AIModel model = getModel(modelId);
        if (model == null) return false;
        ModelVerifier verifier = new ModelVerifier();
        if (verifier.verifyGguf(model.getFilePath())) {
            return true;
        }
        File file = new File(model.getFilePath());
        return file.exists() && file.length() > 0;
    }

    public boolean encryptModel(String modelId) {
        AIModel model = getModel(modelId);
        if (model == null || model.isEncrypted()) return false;
        model.setEncrypted(true);
        saveModels();
        return true;
    }

    public boolean decryptModel(String modelId) {
        AIModel model = getModel(modelId);
        if (model == null || !model.isEncrypted()) return false;
        model.setEncrypted(false);
        saveModels();
        return true;
    }

    public List<AIModel> getModels() {
        return new ArrayList<>(models);
    }

    public AIModel getModel(String id) {
        for (AIModel model : models) {
            if (model.getId().equals(id)) {
                return model;
            }
        }
        return null;
    }

    public AIModel getActiveModel() {
        return activeModel;
    }

    public void setActiveModel(String modelId) {
        AIModel model = getModel(modelId);
        if (model != null) {
            this.activeModel = model;
            saveActiveModel();
        }
    }

    public List<AIModel> getModelsByCategory(String category) {
        List<AIModel> result = new ArrayList<>();
        for (AIModel model : models) {
            if (category.equals(model.getCategory())) {
                result.add(model);
            }
        }
        return result;
    }

    public List<AIModel> getVisionModels() {
        List<AIModel> result = new ArrayList<>();
        for (AIModel model : models) {
            if ("VISION".equals(model.getModelType())) {
                result.add(model);
            }
        }
        return result;
    }

    public List<AIModel> getTextModels() {
        List<AIModel> result = new ArrayList<>();
        for (AIModel model : models) {
            if ("TEXT".equals(model.getModelType())) {
                result.add(model);
            }
        }
        return result;
    }

    public List<AIModel> getPreinstalledModels() {
        List<AIModel> result = new ArrayList<>();
        for (AIModel model : models) {
            if (model.isPreinstalled()) {
                result.add(model);
            }
        }
        return result;
    }

    public List<AIModel> getDownloadableModels() {
        List<AIModel> result = new ArrayList<>();
        for (AIModel model : models) {
            if (!model.isPreinstalled() && !isModelDownloaded(model)) {
                result.add(model);
            }
        }
        return result;
    }

    private boolean isModelDownloaded(AIModel model) {
        if (model.getFilePath() == null || model.getFilePath().isEmpty()) return false;
        File file = new File(model.getFilePath());
        return file.exists() && file.length() > 0;
    }

    public void downloadModel(String url, String name, long expectedSize, String expectedHash, DownloadCallback callback) {
        if (callback == null) return;

        String modelId = generateModelId();

        if (!checkStorageSpace(expectedSize)) {
            callback.onError("Insufficient storage space for download");
            return;
        }

        downloadProgressMap.put(modelId, 0f);
        downloadCancelledMap.put(modelId, false);

        executorService.execute(() -> {
            File tempFile = null;
            FileOutputStream fos = null;
            InputStream is = null;
            try {
                Request request = new Request.Builder().url(url).build();
                Response response = downloadClient.newCall(request).execute();

                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Download failed: HTTP " + response.code()));
                    return;
                }

                long contentLength = response.body().contentLength();
                tempFile = new File(java.io.File.createTempFile("model_download_", ".tmp").getAbsolutePath());
                fos = new FileOutputStream(tempFile);
                is = response.body().byteStream();

                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;

                while ((bytesRead = is.read(buffer)) != -1) {
                    if (downloadCancelledMap.containsKey(modelId) && downloadCancelledMap.get(modelId)) {
                        mainHandler.post(() -> callback.onError("Download cancelled"));
                        cleanupTempFile(tempFile);
                        downloadProgressMap.remove(modelId);
                        downloadCancelledMap.remove(modelId);
                        return;
                    }
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    if (contentLength > 0) {
                        float progress = (float) totalRead / contentLength;
                        downloadProgressMap.put(modelId, progress);
                        mainHandler.post(() -> callback.onProgress(progress));
                    }
                }

                fos.flush();
                fos.close();
                is.close();

                if (expectedHash != null && !expectedHash.isEmpty()) {
                    String actualHash = computeSHA256(tempFile.getAbsolutePath());
                    if (!actualHash.equalsIgnoreCase(expectedHash)) {
                        cleanupTempFile(tempFile);
                        mainHandler.post(() -> callback.onError("Hash verification failed"));
                        downloadProgressMap.remove(modelId);
                        downloadCancelledMap.remove(modelId);
                        return;
                    }
                }

                AIModel model = new AIModel();
                model.setId(modelId);
                model.setName(name);
                model.setFilePath(tempFile.getAbsolutePath());
                model.setFileSize(tempFile.length());
                model.setExpectedHash(expectedHash);
                model.setDownloadUrl(url);
                models.add(model);
                saveModels();

                downloadProgressMap.put(modelId, 1.0f);
                mainHandler.post(() -> callback.onComplete(model));

            } catch (IOException e) {
                cleanupTempFile(tempFile);
                mainHandler.post(() -> callback.onError("Download failed: " + e.getMessage()));
            } finally {
                try { if (fos != null) fos.close(); } catch (IOException e) { Log.w(TAG, "Failed to close file output stream", e); }
                try { if (is != null) is.close(); } catch (IOException e) { Log.w(TAG, "Failed to close input stream", e); }
                downloadProgressMap.remove(modelId);
                downloadCancelledMap.remove(modelId);
            }
        });
    }

    public void cancelDownload(String modelId) {
        downloadCancelledMap.put(modelId, true);
    }

    public void pauseDownload(String modelId) {
        downloadCancelledMap.put(modelId, true);
    }

    public void resumeDownload(String modelId) {
        downloadCancelledMap.put(modelId, false);
    }

    public boolean verifyModelHash(String modelId) {
        AIModel model = getModel(modelId);
        if (model == null || model.getExpectedHash() == null || model.getExpectedHash().isEmpty()) return false;
        String actualHash = computeSHA256(model.getFilePath());
        return actualHash.equalsIgnoreCase(model.getExpectedHash());
    }

    public boolean switchVisionModel(String modelId) {
        AIModel newModel = getModel(modelId);
        if (newModel == null) return false;
        if (!"VISION".equals(newModel.getModelType())) return false;
        VisionInferenceEngine visionEngine = VisionInferenceEngine.getInstance();
        final boolean[] success = {false};
        Thread thread = new Thread(() -> {
            visionEngine.switchVisionModel(newModel, new VisionInferenceEngine.LoadCallback() {
                @Override
                public void onLoaded(AIModel model) {
                    success[0] = true;
                }

                @Override
                public void onError(String error) {
                    success[0] = false;
                }
            });
        });
        thread.start();
        try {
            thread.join(30000);
        } catch (InterruptedException e) {
            return false;
        }
        return success[0];
    }

    public boolean restoreDefaultVisionModel() {
        AIModel defaultModel = null;
        for (AIModel model : models) {
            if ("qwen3-vl-2b".equals(model.getId())) {
                defaultModel = model;
                break;
            }
        }
        if (defaultModel == null) {
            defaultModel = new AIModel();
            defaultModel.setId("qwen3-vl-2b");
            defaultModel.setName("Qwen3-VL-2B");
            defaultModel.setModelType("VISION");
            defaultModel.setVisionCapability("FULL");
            defaultModel.setFileSize(1_500_000_000L);
            defaultModel.setQuantType("Q4_K_M");
        }
        VisionInferenceEngine visionEngine = VisionInferenceEngine.getInstance();
        final boolean[] success = {false};
        Thread thread = new Thread(() -> {
            visionEngine.switchVisionModel(defaultModel, new VisionInferenceEngine.LoadCallback() {
                @Override
                public void onLoaded(AIModel model) {
                    success[0] = true;
                }

                @Override
                public void onError(String error) {
                    success[0] = false;
                }
            });
        });
        thread.start();
        try {
            thread.join(30000);
        } catch (InterruptedException e) {
            return false;
        }
        return success[0];
    }

    public float getDownloadProgress(String modelId) {
        if (downloadProgressMap.containsKey(modelId)) {
            return downloadProgressMap.get(modelId);
        }
        return 0f;
    }

    public float getDownloadProgress() {
        return downloadProgress;
    }

    private boolean checkStorageSpace(long requiredSize) {
        File downloadDir = new File(System.getProperty("java.io.tmpdir", "/tmp"));
        if (!downloadDir.exists()) downloadDir = new File("/data/local/tmp");
        StatFs stat = new StatFs(downloadDir.getPath());
        long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        return availableBytes > requiredSize * 2;
    }

    private void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    private String computeSHA256(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String generateModelId() {
        return "model_" + System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    private void loadModels() {
        String json = prefs.getString("models_list", "");
        if (!json.isEmpty()) {
            try {
                Type listType = new TypeToken<List<AIModel>>(){}.getType();
                List<AIModel> loaded = gson.fromJson(json, listType);
                if (loaded != null) {
                    models = loaded;
                }
            } catch (Exception e) {
                models = new ArrayList<>();
            }
        }
        String activeId = prefs.getString("active_model_id", "");
        if (!activeId.isEmpty()) {
            for (AIModel model : models) {
                if (model.getId().equals(activeId)) {
                    activeModel = model;
                    break;
                }
            }
        }
        visionModels = new ArrayList<>();
        for (AIModel model : models) {
            if ("VISION".equals(model.getModelType())) {
                visionModels.add(model);
            }
        }
    }

    private void saveModels() {
        prefs.edit().putString("models_list", gson.toJson(models)).apply();
        visionModels.clear();
        for (AIModel model : models) {
            if ("VISION".equals(model.getModelType())) {
                visionModels.add(model);
            }
        }
    }

    private void saveActiveModel() {
        String id = activeModel != null ? activeModel.getId() : "";
        prefs.edit().putString("active_model_id", id).apply();
    }
}
