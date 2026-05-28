package com.omniai.assistant.modelmgmt;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.omniai.assistant.common.Constants;
import com.omniai.assistant.nativebridge.LlamaBridge;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GgufQuantizer {

    private static final String TAG = "GgufQuantizer";

    public interface QuantizationCallback {
        void onProgress(float progress);
        void onComplete(String outputPath);
        void onError(String message);
    }

    public enum QuantizationType {
        Q4_0("q4_0", "4-bit Q4_0", 0.15f),
        Q4_1("q4_1", "4-bit Q4_1", 0.16f),
        Q5_0("q5_0", "5-bit Q5_0", 0.18f),
        Q5_1("q5_1", "5-bit Q5_1", 0.19f),
        Q8_0("q8_0", "8-bit Q8_0", 0.25f),
        Q2_K("q2_k", "2-bit Q2_K", 0.09f),
        Q3_K("q3_k", "3-bit Q3_K", 0.12f),
        Q4_K("q4_k", "4-bit Q4_K", 0.15f),
        Q5_K("q5_k", "5-bit Q5_K", 0.18f),
        Q6_K("q6_k", "6-bit Q6_K", 0.20f),
        Q8_K("q8_k", "8-bit Q8_K", 0.25f),
        IQ1_S("iq1_s", "1-bit IQ1_S", 0.06f),
        IQ2_S("iq2_s", "2-bit IQ2_S", 0.08f),
        IQ3_S("iq3_s", "3-bit IQ3_S", 0.11f),
        IQ4_S("iq4_s", "4-bit IQ4_S", 0.14f),
        F16("f16", "16-bit Float", 0.5f),
        F32("f32", "32-bit Float", 1.0f);

        private final String type;
        private final String description;
        private final float compressionRatio;

        QuantizationType(String type, String description, float compressionRatio) {
            this.type = type;
            this.description = description;
            this.compressionRatio = compressionRatio;
        }

        public String getType() { return type; }
        public String getDescription() { return description; }
        public float getCompressionRatio() { return compressionRatio; }
    }

    private static GgufQuantizer instance;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final LlamaBridge bridge;
    private volatile boolean isQuantizing = false;

    private GgufQuantizer() {
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.bridge = LlamaBridge.getInstance();
    }

    public static synchronized GgufQuantizer getInstance() {
        if (instance == null) {
            instance = new GgufQuantizer();
        }
        return instance;
    }

    public void quantizeModel(Context context, String inputPath, String outputPath,
                              QuantizationType quantType, QuantizationCallback callback) {
        if (isQuantizing) {
            notifyError(callback, "已有量化任务正在执行，请等待完成后再试");
            return;
        }

        executorService.execute(() -> {
            try {
                File inputFile = new File(inputPath);
                if (!inputFile.exists()) {
                    notifyError(callback, "源模型文件不存在: " + inputPath);
                    return;
                }
                if (inputFile.length() == 0) {
                    notifyError(callback, "源模型文件为空: " + inputPath);
                    return;
                }

                ModelVerifier verifier = new ModelVerifier();
                if (!verifier.verifyGguf(inputPath)) {
                    notifyError(callback, "源文件不是有效的GGUF格式");
                    return;
                }

                File outputDir = new File(outputPath).getParentFile();
                if (outputDir != null && !outputDir.exists()) {
                    if (!outputDir.mkdirs()) {
                        notifyError(callback, "无法创建输出目录: " + outputDir.getAbsolutePath());
                        return;
                    }
                }

                long estimatedSize = estimateQuantizedSize(inputPath, quantType);
                if (outputDir != null && outputDir.getFreeSpace() < estimatedSize) {
                    notifyError(callback, "磁盘空间不足，预计需要 " + formatSize(estimatedSize)
                            + "，可用 " + formatSize(outputDir.getFreeSpace()));
                    return;
                }

                File existingOutput = new File(outputPath);
                if (existingOutput.exists()) {
                    existingOutput.delete();
                }

                isQuantizing = true;
                notifyProgress(callback, 0.0f);

                SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
                boolean useSimulation = prefs.getBoolean(Constants.PREF_KEY_USE_SIMULATION, true);

                if (useSimulation) {
                    performSimulatedQuantization(inputPath, outputPath, quantType, callback);
                } else {
                    performNativeQuantization(inputPath, outputPath, quantType, callback);
                }

            } catch (Exception e) {
                isQuantizing = false;
                notifyError(callback, "量化异常: " + e.getMessage());
            }
        });
    }

    private void performNativeQuantization(String inputPath, String outputPath,
                                            QuantizationType quantType, QuantizationCallback callback) {
        int nThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        boolean allowRequantize = true;
        boolean quantizeOutputTensor = true;

        notifyProgress(callback, 0.05f);

        Thread progressThread = new Thread(() -> {
            while (isQuantizing) {
                float progress = bridge.getQuantizeProgress();
                if (progress >= 0) {
                    notifyProgress(callback, progress);
                }
                if (progress >= 1.0f || progress < 0) {
                    break;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        progressThread.setDaemon(true);
        progressThread.start();

        int result = bridge.quantizeModel(inputPath, outputPath, quantType.getType(),
                nThreads, allowRequantize, quantizeOutputTensor);

        isQuantizing = false;

        try {
            progressThread.join(1000);
        } catch (InterruptedException e) {
            Log.w(TAG, "Progress thread join interrupted", e);
        }

        if (result == 0) {
            File outputFile = new File(outputPath);
            if (outputFile.exists() && outputFile.length() > 0) {
                ModelVerifier v = new ModelVerifier();
                if (v.verifyGguf(outputPath)) {
                    notifyProgress(callback, 1.0f);
                    notifyComplete(callback, outputPath);
                } else {
                    outputFile.delete();
                    notifyError(callback, "量化后的文件GGUF格式验证失败，文件可能已损坏");
                }
            } else {
                notifyError(callback, "量化完成但输出文件不存在或为空");
            }
        } else if (result == -1) {
            notifyError(callback, "推理引擎未正确安装，无法执行量化。请重新安装应用");
        } else if (result == -2) {
            notifyError(callback, "量化调用异常，请检查模型文件是否完整");
        } else {
            notifyError(callback, "量化失败，错误码: " + result + "。请检查量化类型是否与模型兼容");
        }
    }

    private void performSimulatedQuantization(String inputPath, String outputPath,
                                               QuantizationType quantType, QuantizationCallback callback) {
        try {
            notifyProgress(callback, 0.1f);

            File inputFile = new File(inputPath);
            long totalBytes = inputFile.length();
            long copiedBytes = 0;

            java.io.FileInputStream fis = new java.io.FileInputStream(inputFile);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outputPath);
            byte[] buffer = new byte[65536];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                copiedBytes += bytesRead;
                float progress = 0.1f + 0.85f * ((float) copiedBytes / totalBytes);
                notifyProgress(callback, progress);
            }

            fis.close();
            fos.close();

            notifyProgress(callback, 0.98f);

            File outputFile = new File(outputPath);
            if (outputFile.exists() && outputFile.length() > 0) {
                notifyProgress(callback, 1.0f);
                notifyComplete(callback, outputPath);
            } else {
                notifyError(callback, "模拟量化完成但输出文件不存在");
            }
        } catch (java.io.IOException e) {
            new File(outputPath).delete();
            notifyError(callback, "模拟量化IO错误: " + e.getMessage());
        } finally {
            isQuantizing = false;
        }
    }

    public void abortQuantization() {
        if (isQuantizing) {
            bridge.abortQuantize();
            isQuantizing = false;
        }
    }

    public boolean isQuantizing() {
        return isQuantizing;
    }

    public String generateOutputPath(String inputPath, QuantizationType quantType) {
        File file = new File(inputPath);
        String parentDir = file.getParent();
        String fileName = file.getName();
        String baseName = fileName;
        if (fileName.contains(".")) {
            baseName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        if (baseName.contains("-Q")) {
            baseName = baseName.substring(0, baseName.indexOf("-Q"));
        }
        if (baseName.contains("_Q")) {
            baseName = baseName.substring(0, baseName.indexOf("_Q"));
        }
        return parentDir + File.separator + baseName + "-" + quantType.getType().toUpperCase() + ".gguf";
    }

    public boolean isQuantizedModel(String filePath) {
        String name = new File(filePath).getName().toLowerCase();
        return name.contains("q4") || name.contains("q5") || name.contains("q6") ||
               name.contains("q8") || name.contains("iq") || name.contains("f16");
    }

    public long estimateQuantizedSize(String inputPath, QuantizationType quantType) {
        long originalSize = new File(inputPath).length();
        return (long) (originalSize * quantType.getCompressionRatio());
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) return String.format("%.2f GB", gb);
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1.0) return String.format("%.1f MB", mb);
        return bytes / 1024 + " KB";
    }

    private void notifyProgress(QuantizationCallback callback, float progress) {
        if (callback != null) {
            mainHandler.post(() -> callback.onProgress(progress));
        }
    }

    private void notifyComplete(QuantizationCallback callback, String outputPath) {
        if (callback != null) {
            mainHandler.post(() -> callback.onComplete(outputPath));
        }
    }

    private void notifyError(QuantizationCallback callback, String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message));
        }
    }
}
