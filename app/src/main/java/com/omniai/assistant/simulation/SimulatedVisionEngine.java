package com.omniai.assistant.simulation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.omniai.assistant.model.AIModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SimulatedVisionEngine {

    private static final String TAG = "SimulatedVisionEngine";
    private static SimulatedVisionEngine instance;

    private final Context appContext;
    private final Random random = new Random();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isModelLoaded = false;
    private AIModel currentModel;
    private boolean isQwenModel = true;

    private Map<String, String> sampleOcrTexts = new HashMap<>();

    private SimulatedVisionEngine(Context context) {
        this.appContext = context.getApplicationContext();
        initSampleTexts();
    }

    public static synchronized SimulatedVisionEngine getInstance(Context context) {
        if (instance == null) {
            instance = new SimulatedVisionEngine(context);
        }
        return instance;
    }

    private void initSampleTexts() {
        sampleOcrTexts.put("default", """
                这是一张演示图片的 OCR 识别结果。
                
                标题：Senta AI 视觉识别
                
                正文：
                这是一个模拟的 OCR 文字提取演示。
                在真实环境中，我会准确提取图片中的所有文字。
                
                联系方式：
                邮箱：contact@omniai.com
                电话：400-123-4567
                
                日期：2026年5月27日
                """);
    }

    public boolean isModelLoaded() {
        return isModelLoaded;
    }

    public AIModel getCurrentModel() {
        return currentModel;
    }

    public boolean isQwenVisionModel() {
        return isQwenModel;
    }

    public void loadModel(AIModel model, final LoadCallback callback) {
        if (isModelLoaded && currentModel != null && currentModel.id.equals(model.id)) {
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess());
            }
            return;
        }

        isModelLoaded = false;
        currentModel = null;

        final int totalSteps = 150;
        new Thread(() -> {
            for (int i = 0; i <= totalSteps; i++) {
                final int progress = i;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onProgress(progress, totalSteps, "加载视觉模型...");
                    }
                });
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            isModelLoaded = true;
            currentModel = model;
            isQwenModel = model.id.contains("qwen") || model.id.contains("Qwen");

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onSuccess();
                }
            });
        }).start();
    }

    public void unloadModel() {
        isModelLoaded = false;
        currentModel = null;
    }

    public void visionChat(String imagePath, String prompt, int maxTokens, float temp, 
                           final VisionCallback callback) {
        if (!isModelLoaded) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("视觉模型未加载"));
            }
            return;
        }

        final String analysis = generateAnalysis(imagePath, prompt);

        new Thread(() -> {
            final StringBuilder sb = new StringBuilder();
            final int charsPerStep = Math.max(1, analysis.length() / 40);
            int idx = 0;

            while (idx < analysis.length()) {
                final int endIdx = Math.min(idx + charsPerStep, analysis.length());
                final String chunk = analysis.substring(idx, endIdx);
                sb.append(chunk);
                idx = endIdx;

                final String currentText = sb.toString();
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onPartialResult(currentText);
                    }
                });

                try {
                    Thread.sleep(60 + random.nextInt(80));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            final String fullAnalysis = sb.toString();
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onComplete(fullAnalysis);
                }
            });
        }).start();
    }

    public void imageOcr(String imagePath, final OcrCallback callback) {
        if (!isModelLoaded) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("视觉模型未加载"));
            }
            return;
        }

        final String ocrText = generateOcrText(imagePath);

        new Thread(() -> {
            for (int i = 0; i <= 100; i += 10) {
                final int progress = i;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onProgress(progress, "OCR 识别中...");
                    }
                });
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onComplete(ocrText);
                }
            });
        }).start();
    }

    private String generateAnalysis(String imagePath, String prompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 图片分析报告\n\n");

        File imgFile = new File(imagePath);
        if (imgFile.exists()) {
            try (FileInputStream fis = new FileInputStream(imagePath)) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(fis, null, opts);
                sb.append("📐 **图片信息**：\n");
                sb.append("- 分辨率：").append(opts.outWidth).append("×").append(opts.outHeight).append("\n");
                sb.append("- 格式：").append(opts.outMimeType).append("\n\n");
            } catch (IOException e) {
                Log.w(TAG, "Failed to read image info", e);
            }
        }

        sb.append("🔍 **视觉理解**：\n");
        sb.append("- 图片类型：彩色图片\n");
        sb.append("- 主要内容：").append(analyzeContent(prompt)).append("\n\n");

        sb.append("💬 **回答**：\n");
        if (prompt.contains("文字") || prompt.contains("OCR")) {
            sb.append(sampleOcrTexts.get("default"));
        } else if (prompt.contains("描述") || prompt.contains("介绍")) {
            sb.append("这张图片展示了 Senta AI 应用的界面。顶部是工具栏，中间是聊天区域，底部是输入框。整体设计采用了简洁的 Material Design 风格。");
        } else if (prompt.contains("颜色")) {
            sb.append("图片主要颜色：蓝色（#2563EB）、白色、浅灰色。整体配色简洁、专业。");
        } else {
            sb.append("我理解这张图片的内容。这是一个模拟的视觉分析响应，在真实环境中会使用 Qwen-VL 模型进行真实的图像理解。\n\n你的问题是：\"").append(prompt).append("\"\n");
            sb.append("\n答案：这是一个很好的问题。根据我的分析，图片内容符合预期。");
        }

        return sb.toString();
    }

    private String analyzeContent(String prompt) {
        if (prompt.contains("人像") || prompt.contains("人脸")) return "人像/人脸照片";
        if (prompt.contains("风景") || prompt.contains("自然")) return "自然风景照片";
        if (prompt.contains("文档") || prompt.contains("文字")) return "文档/文字图片";
        if (prompt.contains("截图") || prompt.contains("界面")) return "应用界面截图";
        return "普通彩色图片";
    }

    private String generateOcrText(String imagePath) {
        return sampleOcrTexts.get("default");
    }

    public List<AIModel> getAvailableModels() {
        List<AIModel> models = new ArrayList<>();
        AIModel qwen2VL = new AIModel();
        qwen2VL.id = "qwen3-vl-2b";
        qwen2VL.name = "Qwen3-VL-2B";
        qwen2VL.modelType = AIModel.MODEL_TYPE_VISION;
        qwen2VL.isPreinstalled = true;
        models.add(qwen2VL);

        AIModel qwen7VL = new AIModel();
        qwen7VL.id = "qwen2.5-vl-7b";
        qwen7VL.name = "Qwen2.5-VL-7B";
        qwen7VL.modelType = AIModel.MODEL_TYPE_VISION;
        models.add(qwen7VL);

        AIModel smol = new AIModel();
        smol.id = "smolvlm2-256m";
        smol.name = "SmolVLM2-256M";
        smol.modelType = AIModel.MODEL_TYPE_VISION;
        models.add(smol);

        return models;
    }

    public AIModel getDefaultModel() {
        for (AIModel model : getAvailableModels()) {
            if (model.isPreinstalled) return model;
        }
        return getAvailableModels().get(0);
    }

    public interface LoadCallback {
        void onProgress(int current, int total, String message);
        void onSuccess();
        void onError(String error);
    }

    public interface VisionCallback {
        void onPartialResult(String text);
        void onComplete(String text);
        void onError(String error);
    }

    public interface OcrCallback {
        void onProgress(int percent, String message);
        void onComplete(String text);
        void onError(String error);
    }
}
