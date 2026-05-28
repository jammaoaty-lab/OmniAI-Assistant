package com.omniai.assistant.modelmgmt;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.omniai.assistant.common.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ModelDownloadManager {

    private static final String TAG = "ModelDownloadManager";
    private static final String PREFS_DOWNLOAD = "model_download_prefs";
    private static final String KEY_DOWNLOADED_BYTES = "downloaded_bytes_";
    private static final String KEY_DOWNLOAD_MD5 = "download_md5_";
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT = 30;
    private static final int READ_TIMEOUT = 60;
    private static final int MAX_RETRY_COUNT = 3;

    private static volatile ModelDownloadManager instance;
    private final Context context;
    private final SharedPreferences downloadPrefs;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final OkHttpClient httpClient;
    private final Map<String, Boolean> downloadingMap = new HashMap<>();

    public interface DownloadCallback {
        void onProgress(String modelId, int percent, long downloadedBytes, long totalBytes, float speedMBps);
        void onPaused(String modelId, long downloadedBytes);
        void onCompleted(String modelId, String filePath);
        void onMd5Verified(String modelId, boolean passed);
        void onFailed(String modelId, String error);
    }

    private DownloadCallback callback;

    private ModelDownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.downloadPrefs = context.getSharedPreferences(PREFS_DOWNLOAD, Context.MODE_PRIVATE);
        this.executorService = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized ModelDownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new ModelDownloadManager(context);
        }
        return instance;
    }

    public void setCallback(DownloadCallback callback) {
        this.callback = callback;
    }

    public void startDownload(ModelDownloadInfo modelInfo) {
        if (modelInfo == null) return;

        String modelId = modelInfo.getModelId();
        if (Boolean.TRUE.equals(downloadingMap.get(modelId))) {
            notifyFailed(modelId, "该模型正在下载中");
            return;
        }

        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
        }

        File targetFile = new File(modelsDir, modelInfo.getFileName());
        if (targetFile.exists() && targetFile.length() > 0) {
            ModelVerifier verifier = new ModelVerifier();
            if (verifier.verifyGguf(targetFile.getAbsolutePath())) {
                notifyCompleted(modelId, targetFile.getAbsolutePath());
                return;
            }
            targetFile.delete();
        }

        File tempFile = new File(modelsDir, modelInfo.getFileName() + ".tmp");

        downloadingMap.put(modelId, true);
        executorService.execute(() -> downloadWithRetry(modelInfo, tempFile, targetFile, 0));
    }

    private void downloadWithRetry(ModelDownloadInfo modelInfo, File tempFile, File targetFile, int retryCount) {
        try {
            downloadInternal(modelInfo, tempFile, targetFile);
        } catch (Exception e) {
            String modelId = modelInfo.getModelId();
            downloadingMap.put(modelId, false);

            if (retryCount < MAX_RETRY_COUNT) {
                notifyProgress(modelId, -1, getDownloadedBytes(modelId), modelInfo.getFileSize(), 0);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                    return;
                }
                downloadingMap.put(modelId, true);
                downloadWithRetry(modelInfo, tempFile, targetFile, retryCount + 1);
            } else {
                String error = classifyDownloadError(e);
                notifyFailed(modelId, error + "（已重试" + MAX_RETRY_COUNT + "次）");
            }
        }
    }

    private void downloadInternal(ModelDownloadInfo modelInfo, File tempFile, File targetFile) throws Exception {
        String modelId = modelInfo.getModelId();
        long existingBytes = 0;

        if (tempFile.exists()) {
            existingBytes = tempFile.length();
            saveDownloadedBytes(modelId, existingBytes);
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(modelInfo.getDownloadUrl());

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=" + existingBytes + "-");
        }

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 206) {
                if (response.code() == 416) {
                    tempFile.delete();
                    saveDownloadedBytes(modelId, 0);
                    throw new IOException("Range请求无效，将重新下载");
                }
                throw new IOException(classifyHttpError(response.code()));
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("服务器返回空响应");
            }

            long totalSize = modelInfo.getFileSize();
            String contentRange = response.header("Content-Range");
            if (contentRange != null && contentRange.contains("/")) {
                try {
                    String totalStr = contentRange.substring(contentRange.indexOf("/") + 1);
                    totalSize = Long.parseLong(totalStr);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Failed to parse content range total size", e);
                }
            }

            long downloadedBytes = existingBytes;
            boolean isResume = response.code() == 206;

            RandomAccessFile raf = new RandomAccessFile(tempFile, "rw");
            if (isResume) {
                raf.seek(existingBytes);
            } else {
                raf.setLength(0);
                downloadedBytes = 0;
            }

            InputStream is = body.byteStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long lastNotifyTime = System.currentTimeMillis();
            long lastNotifyBytes = downloadedBytes;

            while ((bytesRead = is.read(buffer)) != -1) {
                if (!Boolean.TRUE.equals(downloadingMap.get(modelId))) {
                    raf.close();
                    is.close();
                    saveDownloadedBytes(modelId, downloadedBytes);
                    notifyPaused(modelId, downloadedBytes);
                    return;
                }

                raf.write(buffer, 0, bytesRead);
                downloadedBytes += bytesRead;

                long now = System.currentTimeMillis();
                if (now - lastNotifyTime >= 500) {
                    float speed = 0;
                    if (now > lastNotifyTime) {
                        speed = (downloadedBytes - lastNotifyBytes) / ((now - lastNotifyTime) / 1000.0f) / (1024 * 1024);
                    }
                    int percent = totalSize > 0 ? (int) (downloadedBytes * 100 / totalSize) : 0;
                    notifyProgress(modelId, percent, downloadedBytes, totalSize, speed);
                    lastNotifyTime = now;
                    lastNotifyBytes = downloadedBytes;
                }
            }

            raf.close();
            is.close();
        }

        saveDownloadedBytes(modelId, 0);

        if (modelInfo.getExpectedMd5() != null && !modelInfo.getExpectedMd5().isEmpty()) {
            notifyProgress(modelId, -2, 0, 0, 0);
            String actualMd5 = calculateMd5(tempFile);
            boolean md5Passed = actualMd5.equalsIgnoreCase(modelInfo.getExpectedMd5());
            notifyMd5Verified(modelId, md5Passed);
            if (!md5Passed) {
                tempFile.delete();
                throw new IOException("MD5校验失败，文件可能已损坏，请重新下载");
            }
        }

        ModelVerifier verifier = new ModelVerifier();
        if (!verifier.verifyGguf(tempFile.getAbsolutePath())) {
            tempFile.delete();
            throw new IOException("GGUF格式验证失败，文件可能已损坏");
        }

        if (targetFile.exists()) {
            targetFile.delete();
        }
        if (!tempFile.renameTo(targetFile)) {
            copyFile(tempFile, targetFile);
            tempFile.delete();
        }

        downloadingMap.put(modelId, false);
        notifyCompleted(modelId, targetFile.getAbsolutePath());
    }

    public void pauseDownload(String modelId) {
        downloadingMap.put(modelId, false);
    }

    public void cancelDownload(String modelId, String fileName) {
        downloadingMap.put(modelId, false);
        saveDownloadedBytes(modelId, 0);

        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
        File tempFile = new File(modelsDir, fileName + ".tmp");
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    public boolean isDownloading(String modelId) {
        return Boolean.TRUE.equals(downloadingMap.get(modelId));
    }

    public boolean hasPartialDownload(String modelId, String fileName) {
        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
        File tempFile = new File(modelsDir, fileName + ".tmp");
        return tempFile.exists() && tempFile.length() > 0;
    }

    public long getPartialDownloadSize(String modelId, String fileName) {
        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
        File tempFile = new File(modelsDir, fileName + ".tmp");
        if (tempFile.exists()) return tempFile.length();
        return getDownloadedBytes(modelId);
    }

    public boolean isModelDownloaded(String fileName) {
        File modelsDir = new File(context.getFilesDir(), Constants.MODEL_DIR);
        File modelFile = new File(modelsDir, fileName);
        if (!modelFile.exists()) return false;
        ModelVerifier verifier = new ModelVerifier();
        return verifier.verifyGguf(modelFile.getAbsolutePath());
    }

    private long getDownloadedBytes(String modelId) {
        return downloadPrefs.getLong(KEY_DOWNLOADED_BYTES + modelId, 0);
    }

    private void saveDownloadedBytes(String modelId, long bytes) {
        downloadPrefs.edit().putLong(KEY_DOWNLOADED_BYTES + modelId, bytes).apply();
    }

    private String calculateMd5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    private String classifyDownloadError(Exception e) {
        if (e instanceof SocketTimeoutException) return "下载超时，请检查网络连接";
        if (e instanceof UnknownHostException) return "无法连接服务器，请检查网络";
        if (e instanceof javax.net.ssl.SSLException) return "安全连接失败";
        if (e instanceof java.net.ConnectException) return "服务器连接失败";
        if (e instanceof IOException) {
            String msg = e.getMessage();
            if (msg != null) {
                if (msg.contains("ENOSPC") || msg.contains("No space")) return "磁盘空间不足，请清理存储空间";
                if (msg.contains("EACCES") || msg.contains("Permission")) return "存储权限被拒绝，请检查权限设置";
            }
            return "网络错误: " + e.getMessage();
        }
        return "下载失败: " + e.getMessage();
    }

    private String classifyHttpError(int code) {
        switch (code) {
            case 401: return "下载链接无效（401）";
            case 403: return "下载被拒绝（403）";
            case 404: return "模型文件不存在（404）";
            case 429: return "请求过于频繁，请稍后重试（429）";
            case 500: case 502: case 503: return "服务器错误，请稍后重试（" + code + "）";
            default: return "下载失败: HTTP " + code;
        }
    }

    private void notifyProgress(String modelId, int percent, long downloaded, long total, float speed) {
        mainHandler.post(() -> {
            if (callback != null) callback.onProgress(modelId, percent, downloaded, total, speed);
        });
    }

    private void notifyPaused(String modelId, long downloadedBytes) {
        mainHandler.post(() -> {
            if (callback != null) callback.onPaused(modelId, downloadedBytes);
        });
    }

    private void notifyCompleted(String modelId, String filePath) {
        mainHandler.post(() -> {
            if (callback != null) callback.onCompleted(modelId, filePath);
        });
    }

    private void notifyMd5Verified(String modelId, boolean passed) {
        mainHandler.post(() -> {
            if (callback != null) callback.onMd5Verified(modelId, passed);
        });
    }

    private void notifyFailed(String modelId, String error) {
        mainHandler.post(() -> {
            if (callback != null) callback.onFailed(modelId, error);
        });
    }

    public void destroy() {
        executorService.shutdown();
        callback = null;
    }
}
