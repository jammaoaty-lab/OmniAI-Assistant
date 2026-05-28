package com.omniai.assistant.modelmgmt;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.omniai.assistant.common.Constants;
import com.omniai.assistant.model.AIModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PreinstalledModelManager {

    private static final String TAG = "PreinstalledModel";
    private static final String PREF_KEY_MODELS_EXTRACTED = "preinstalled_models_extracted";
    private static final String PREF_KEY_EXTRACTION_VERSION = "preinstalled_models_version";

    private static final int EXTRACTION_VERSION = 3;

    private static final String ASSET_TEXT_MODEL = "models/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf";
    private static final String ASSET_VISION_MODEL = "models/Qwen3-VL-2B-Q4_K_M.gguf";

    private static volatile PreinstalledModelManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final DynamicFeatureManager dynamicFeatureManager;

    public interface ExtractionCallback {
        void onProgress(String modelName, float progress);
        void onModelReady(AIModel model);
        void onAllModelsReady();
        void onError(String message);
        void onRequiresFeatureInstall();
    }

    private PreinstalledModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.dynamicFeatureManager = DynamicFeatureManager.getInstance(context);
    }

    public static synchronized PreinstalledModelManager getInstance(Context context) {
        if (instance == null) {
            instance = new PreinstalledModelManager(context);
        }
        return instance;
    }

    public void ensureModelsExtracted(ExtractionCallback callback) {
        int extractedVersion = prefs.getInt(PREF_KEY_EXTRACTION_VERSION, 0);
        if (extractedVersion >= EXTRACTION_VERSION) {
            verifyAndRegisterModels(callback);
            return;
        }

        if (!dynamicFeatureManager.isFeatureInstalled()) {
            if (callback != null) {
                mainHandler.post(() -> callback.onRequiresFeatureInstall());
            }
            return;
        }

        extractAllModels(callback);
    }

    public boolean areModelsExtracted() {
        return prefs.getInt(PREF_KEY_EXTRACTION_VERSION, 0) >= EXTRACTION_VERSION;
    }

    public boolean isDynamicFeatureInstalled() {
        return dynamicFeatureManager.isFeatureInstalled();
    }

    private void extractAllModels(ExtractionCallback callback) {
        executorService.execute(() -> {
            try {
                File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
                if (!modelsDir.exists()) {
                    modelsDir.mkdirs();
                }

                boolean textOk = extractModel(ASSET_TEXT_MODEL, "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf", callback);
                boolean visionOk = extractModel(ASSET_VISION_MODEL, "Qwen3-VL-2B-Q4_K_M.gguf", callback);

                if (textOk && visionOk) {
                    prefs.edit()
                            .putInt(PREF_KEY_EXTRACTION_VERSION, EXTRACTION_VERSION)
                            .putBoolean(PREF_KEY_MODELS_EXTRACTED, true)
                            .apply();

                    registerPreinstalledModels(callback);

                    mainHandler.post(() -> {
                        if (callback != null) callback.onAllModelsReady();
                    });
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Model extraction failed");
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Extraction error: " + e.getMessage());
                });
            }
        });
    }

    private boolean extractModel(String assetPath, String outputName, ExtractionCallback callback) {
        File outputFile = new File(new File(context.getFilesDir(), Constants.MODEL_DIR), outputName);

        if (outputFile.exists() && outputFile.length() > 0) {
            ModelVerifier verifier = new ModelVerifier();
            if (verifier.verifyGguf(outputFile.getAbsolutePath())) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onProgress(outputName, 1.0f);
                });
                return true;
            }
            outputFile.delete();
        }

        try {
            InputStream is = null;
            try {
                Context featureContext = dynamicFeatureManager.getFeatureContext();
                is = featureContext.getAssets().open(assetPath);
            } catch (IOException e) {
                try {
                    is = context.getAssets().open(assetPath);
                } catch (IOException e2) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("模型文件不存在，请重新安装应用: " + assetPath);
                    });
                    return false;
                }
            }

            long assetSize = is.available();
            File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
            if (!hasEnoughDiskSpace(modelsDir, assetSize)) {
                is.close();
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("磁盘空间不足，无法提取模型: " + outputName);
                });
                return false;
            }

            FileOutputStream fos = new FileOutputStream(outputFile);

            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int bytesRead;
            int lastProgressPercent = -1;

            while ((bytesRead = is.read(buffer)) != -1) {
                try {
                    fos.write(buffer, 0, bytesRead);
                } catch (IOException e) {
                    is.close();
                    fos.close();
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("文件写入失败: " + outputFile.getAbsolutePath());
                    });
                    return false;
                }
                totalRead += bytesRead;

                if (assetSize > 0) {
                    int percent = (int) ((totalRead * 100) / assetSize);
                    if (percent != lastProgressPercent && percent % 5 == 0) {
                        lastProgressPercent = percent;
                        float progress = (float) totalRead / assetSize;
                        String name = outputName;
                        mainHandler.post(() -> {
                            if (callback != null) callback.onProgress(name, progress);
                        });
                    }
                }
            }

            fos.flush();
            fos.close();
            is.close();

            ModelVerifier verifier = new ModelVerifier();
            boolean verified = verifier.verifyGguf(outputFile.getAbsolutePath());
            if (!verified) {
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("GGUF验证失败: " + outputName);
                });
                return false;
            }
            return true;

        } catch (Exception e) {
            if (outputFile.exists()) {
                outputFile.delete();
            }
            mainHandler.post(() -> {
                if (callback != null) callback.onError("模型提取异常: " + outputName + " - " + e.getMessage());
            });
            return false;
        }
    }

    private boolean hasEnoughDiskSpace(File dir, long requiredBytes) {
        if (dir != null) {
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return dir.getFreeSpace() >= requiredBytes;
        }
        return true;
    }

    private void verifyAndRegisterModels(ExtractionCallback callback) {
        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);

        File textModel = new File(modelsDir, "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf");
        File visionModel = new File(modelsDir, "Qwen3-VL-2B-Q4_K_M.gguf");

        ModelVerifier verifier = new ModelVerifier();

        boolean textOk = textModel.exists() && verifier.verifyGguf(textModel.getAbsolutePath());
        boolean visionOk = visionModel.exists() && verifier.verifyGguf(visionModel.getAbsolutePath());

        if (!textOk || !visionOk) {
            prefs.edit().putInt(PREF_KEY_EXTRACTION_VERSION, 0).apply();
            ensureModelsExtracted(callback);
            return;
        }

        registerPreinstalledModels(callback);
        mainHandler.post(() -> {
            if (callback != null) callback.onAllModelsReady();
        });
    }

    private void registerPreinstalledModels(ExtractionCallback callback) {
        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);

        AIModel textModel = new AIModel();
        textModel.setId("qwen2.5-0.5b-instruct");
        textModel.setName("Qwen2.5-0.5B-Instruct");
        textModel.setFilePath(new File(modelsDir, "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf").getAbsolutePath());
        textModel.setFileSize(new File(modelsDir, "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf").length());
        textModel.setQuantType("Q4_K_M");
        textModel.setModelType("TEXT");
        textModel.setPreinstalled(true);
        textModel.setCategory("文本");
        textModel.setGpuAccelerated(false);

        AIModel visionModel = new AIModel();
        visionModel.setId("qwen3-vl-2b");
        visionModel.setName("Qwen3-VL-2B");
        visionModel.setFilePath(new File(modelsDir, "Qwen3-VL-2B-Q4_K_M.gguf").getAbsolutePath());
        visionModel.setFileSize(new File(modelsDir, "Qwen3-VL-2B-Q4_K_M.gguf").length());
        visionModel.setQuantType("Q4_K_M");
        visionModel.setModelType("VISION");
        visionModel.setVisionCapability("FULL");
        visionModel.setPreinstalled(true);
        visionModel.setCategory("视觉");

        mainHandler.post(() -> {
            if (callback != null) callback.onModelReady(textModel);
        });
        mainHandler.post(() -> {
            if (callback != null) callback.onModelReady(visionModel);
        });
    }

    public AIModel getPreinstalledTextModel() {
        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
        File modelFile = new File(modelsDir, "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf");

        AIModel model = new AIModel();
        model.setId("qwen2.5-0.5b-instruct");
        model.setName("Qwen2.5-0.5B-Instruct");
        model.setFilePath(modelFile.getAbsolutePath());
        model.setFileSize(modelFile.length());
        model.setQuantType("Q4_K_M");
        model.setModelType("TEXT");
        model.setPreinstalled(true);
        model.setCategory("文本");
        return model;
    }

    public AIModel getPreinstalledVisionModel() {
        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
        File modelFile = new File(modelsDir, "Qwen3-VL-2B-Q4_K_M.gguf");

        AIModel model = new AIModel();
        model.setId("qwen3-vl-2b");
        model.setName("Qwen3-VL-2B");
        model.setFilePath(modelFile.getAbsolutePath());
        model.setFileSize(modelFile.length());
        model.setQuantType("Q4_K_M");
        model.setModelType("VISION");
        model.setVisionCapability("FULL");
        model.setPreinstalled(true);
        model.setCategory("视觉");
        return model;
    }

    public boolean isTextModelReady() {
        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
        File modelFile = new File(modelsDir, "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf");
        if (!modelFile.exists()) return false;
        ModelVerifier verifier = new ModelVerifier();
        return verifier.verifyGguf(modelFile.getAbsolutePath());
    }

    public boolean isVisionModelReady() {
        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
        File modelFile = new File(modelsDir, "Qwen3-VL-2B-Q4_K_M.gguf");
        if (!modelFile.exists()) return false;
        ModelVerifier verifier = new ModelVerifier();
        return verifier.verifyGguf(modelFile.getAbsolutePath());
    }

    public long getTotalPreinstalledSize() {
        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
        long total = 0;
        File textModel = new File(modelsDir, "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf");
        File visionModel = new File(modelsDir, "Qwen3-VL-2B-Q4_K_M.gguf");
        if (textModel.exists()) total += textModel.length();
        if (visionModel.exists()) total += visionModel.length();
        return total;
    }

    public DynamicFeatureManager getDynamicFeatureManager() {
        return dynamicFeatureManager;
    }
}
