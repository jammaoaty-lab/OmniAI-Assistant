package com.omniai.assistant.util;

import android.content.Context;

import com.omniai.assistant.common.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;

public final class FileUtil {

    private static final String[] MODEL_EXTENSIONS = {".gguf", ".bin", ".ggml", ".safetensors"};

    private FileUtil() {
        throw new UnsupportedOperationException("FileUtil cannot be instantiated");
    }

    public static String copyFile(File src, File dst) {
        if (src == null || dst == null) {
            return "源文件或目标文件为空";
        }
        if (!src.exists()) {
            return "源文件不存在: " + src.getAbsolutePath();
        }
        File dstParent = dst.getParentFile();
        if (dstParent != null && !dstParent.exists()) {
            if (!dstParent.mkdirs()) {
                return "目标目录创建失败: " + dstParent.getAbsolutePath();
            }
        }
        if (!hasEnoughSpace(dstParent != null ? dstParent : dst, src.length())) {
            return "磁盘空间不足，需要 " + formatFileSize(src.length()) + "，可用 " + formatFileSize(getAvailableSpace(dstParent != null ? dstParent : dst));
        }
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
            return null;
        } catch (IOException e) {
            if (dst.exists()) {
                dst.delete();
            }
            String msg = e.getMessage();
            if (msg != null && (msg.toLowerCase().contains("enospc") || msg.toLowerCase().contains("space") || msg.toLowerCase().contains("full"))) {
                return "磁盘空间不足: " + msg;
            }
            return "文件拷贝IO异常: " + msg;
        }
    }

    public interface CopyCallback {
        void onProgress(long copied, long total);
        void onSuccess();
        void onError(String error);
    }

    public static void copyFileWithCallback(File src, File dst, CopyCallback callback) {
        if (src == null || dst == null) {
            if (callback != null) callback.onError("源文件或目标文件为空");
            return;
        }
        if (!src.exists()) {
            if (callback != null) callback.onError("源文件不存在: " + src.getAbsolutePath());
            return;
        }
        File dstParent = dst.getParentFile();
        if (dstParent != null && !dstParent.exists()) {
            if (!dstParent.mkdirs()) {
                if (callback != null) callback.onError("目标目录创建失败: " + dstParent.getAbsolutePath());
                return;
            }
        }
        long srcSize = src.length();
        if (!hasEnoughSpace(dstParent != null ? dstParent : dst, srcSize)) {
            if (callback != null) callback.onError("磁盘空间不足，需要 " + formatFileSize(srcSize) + "，可用 " + formatFileSize(getAvailableSpace(dstParent != null ? dstParent : dst)));
            return;
        }
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            long totalCopied = 0;
            int len;
            long lastCallbackBytes = 0;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                totalCopied += len;
                if (srcSize > 0 && totalCopied - lastCallbackBytes >= srcSize / 100) {
                    lastCallbackBytes = totalCopied;
                    if (callback != null) callback.onProgress(totalCopied, srcSize);
                }
            }
            out.flush();
            if (callback != null) callback.onProgress(srcSize, srcSize);
            if (callback != null) callback.onSuccess();
        } catch (IOException e) {
            if (dst.exists()) {
                dst.delete();
            }
            String msg = e.getMessage();
            String errorMsg;
            if (msg != null && (msg.toLowerCase().contains("enospc") || msg.toLowerCase().contains("space") || msg.toLowerCase().contains("full"))) {
                errorMsg = "磁盘空间不足: " + msg;
            } else {
                errorMsg = "文件拷贝IO异常: " + msg;
            }
            if (callback != null) callback.onError(errorMsg);
        }
    }

    public static long getAvailableSpace(File path) {
        return path.getFreeSpace();
    }

    public static boolean hasEnoughSpace(File path, long requiredBytes) {
        return getAvailableSpace(path) >= requiredBytes;
    }

    public static boolean deleteFile(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFile(child);
                }
            }
        }
        return file.delete();
    }

    public static long getFileSize(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        if (file.isDirectory()) {
            long size = 0;
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    size += getFileSize(child);
                }
            }
            return size;
        }
        return file.length();
    }

    public static String formatFileSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        DecimalFormat df = new DecimalFormat("#,##0.#");
        return df.format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static boolean isModelFile(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        for (String ext : MODEL_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public static File getModelsDir(Context context) {
        return new File(context.getFilesDir(), Constants.MODEL_DIR);
    }

    public static File getLoraDir(Context context) {
        return new File(context.getFilesDir(), Constants.LORA_DIR);
    }

    public static File getKnowledgeDir(Context context) {
        return new File(context.getFilesDir(), Constants.KNOWLEDGE_DIR);
    }

    public static void ensureDirs(Context context) {
        File modelsDir = getModelsDir(context);
        File loraDir = getLoraDir(context);
        File knowledgeDir = getKnowledgeDir(context);
        File cacheDir = new File(context.getFilesDir(), Constants.CACHE_DIR);

        modelsDir.mkdirs();
        loraDir.mkdirs();
        knowledgeDir.mkdirs();
        cacheDir.mkdirs();
    }
}
