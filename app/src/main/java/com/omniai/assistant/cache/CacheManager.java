package com.omniai.assistant.cache;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class CacheManager {

    private static CacheManager instance;

    private static final long DEFAULT_MAX_CACHE_SIZE = 500 * 1024 * 1024;
    private static final long CLEANUP_THRESHOLD = 7 * 24 * 60 * 60 * 1000L;

    private long maxCacheSize;
    private long currentCacheSize;
    private Context context;
    private Handler cleanupHandler;
    private Runnable cleanupRunnable;
    private boolean autoCleanupRunning;

    private CacheManager(Context context) {
        this.context = context.getApplicationContext();
        this.maxCacheSize = DEFAULT_MAX_CACHE_SIZE;
        this.currentCacheSize = 0;
        this.cleanupHandler = new Handler(Looper.getMainLooper());
        this.autoCleanupRunning = false;
        this.cleanupRunnable = null;
        recalculateCacheSize();
    }

    public static synchronized CacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new CacheManager(context);
        }
        return instance;
    }

    public void cleanCache() {
        File cacheDir = context.getCacheDir();
        if (cacheDir.exists()) {
            deleteDirectory(cacheDir, false);
        }
        recalculateCacheSize();
    }

    public void cleanLogs() {
        File logDir = new File(context.getFilesDir(), "logs");
        if (logDir.exists()) {
            deleteDirectory(logDir, true);
        }
        recalculateCacheSize();
    }

    public void cleanTempFiles() {
        File tempDir = new File(context.getCacheDir(), "temp");
        if (tempDir.exists()) {
            deleteDirectory(tempDir, true);
        }
        File[] files = context.getCacheDir().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".tmp") || file.getName().endsWith(".temp")) {
                    file.delete();
                }
            }
        }
        recalculateCacheSize();
    }

    public long cleanVisionCache() {
        long cleaned = 0;
        File visionCacheDir = new File(context.getCacheDir(), "vision");
        if (visionCacheDir.exists()) {
            cleaned += calculateDirectorySize(visionCacheDir);
            deleteDirectory(visionCacheDir, true);
        }
        File visionInferenceDir = new File(context.getCacheDir(), "vision_inference");
        if (visionInferenceDir.exists()) {
            cleaned += calculateDirectorySize(visionInferenceDir);
            deleteDirectory(visionInferenceDir, true);
        }
        recalculateCacheSize();
        return cleaned;
    }

    public long cleanTempImages() {
        long cleaned = 0;
        File tempImageDir = new File(context.getCacheDir(), "temp_images");
        if (tempImageDir.exists()) {
            cleaned += calculateDirectorySize(tempImageDir);
            deleteDirectory(tempImageDir, true);
        }
        File[] files = context.getCacheDir().listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if ((name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                        || name.endsWith(".webp")) && name.contains("temp")) {
                    cleaned += file.length();
                    file.delete();
                }
            }
        }
        recalculateCacheSize();
        return cleaned;
    }

    public long getVisionCacheSize() {
        long size = 0;
        File visionCacheDir = new File(context.getCacheDir(), "vision");
        if (visionCacheDir.exists()) {
            size += calculateDirectorySize(visionCacheDir);
        }
        File visionInferenceDir = new File(context.getCacheDir(), "vision_inference");
        if (visionInferenceDir.exists()) {
            size += calculateDirectorySize(visionInferenceDir);
        }
        return size;
    }

    public long getTempImagesSize() {
        long size = 0;
        File tempImageDir = new File(context.getCacheDir(), "temp_images");
        if (tempImageDir.exists()) {
            size += calculateDirectorySize(tempImageDir);
        }
        File[] files = context.getCacheDir().listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if ((name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                        || name.endsWith(".webp")) && name.contains("temp")) {
                    size += file.length();
                }
            }
        }
        return size;
    }

    public long getCacheSize() {
        recalculateCacheSize();
        return currentCacheSize;
    }

    public String formatCacheSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    public void setMaxCacheSize(long maxSize) {
        this.maxCacheSize = maxSize;
        if (currentCacheSize > maxCacheSize) {
            trimCache();
        }
    }

    public void trimCache() {
        recalculateCacheSize();
        if (currentCacheSize <= maxCacheSize) {
            return;
        }

        File cacheDir = context.getCacheDir();
        List<File> files = getFilesSortedByAge(cacheDir);

        Iterator<File> iterator = files.iterator();
        while (iterator.hasNext() && currentCacheSize > maxCacheSize * 0.8) {
            File file = iterator.next();
            long fileSize = file.length();
            if (file.delete()) {
                currentCacheSize -= fileSize;
            }
        }
        recalculateCacheSize();
    }

    public void reclaimMemory() {
        trimCache();
        cleanTempFiles();
        cleanVisionCache();
        cleanTempImages();
        forceGc();
    }

    public void startAutoCleanup(long intervalMs) {
        if (autoCleanupRunning) {
            stopAutoCleanup();
        }
        autoCleanupRunning = true;
        cleanupRunnable = new Runnable() {
            @Override
            public void run() {
                if (!autoCleanupRunning) {
                    return;
                }
                removeOldFiles();
                cleanVisionCache();
                cleanTempImages();
                cleanupHandler.postDelayed(this, intervalMs);
            }
        };
        cleanupHandler.postDelayed(cleanupRunnable, intervalMs);
    }

    public void stopAutoCleanup() {
        autoCleanupRunning = false;
        if (cleanupRunnable != null) {
            cleanupHandler.removeCallbacks(cleanupRunnable);
            cleanupRunnable = null;
        }
    }

    public MemoryInfo getMemoryInfo() {
        MemoryInfo info = new MemoryInfo();
        Runtime runtime = Runtime.getRuntime();
        info.totalMemory = runtime.totalMemory();
        info.freeMemory = runtime.freeMemory();
        info.usedMemory = info.totalMemory - info.freeMemory;
        if (info.totalMemory > 0) {
            info.usagePercent = (float) info.usedMemory / info.totalMemory * 100;
        } else {
            info.usagePercent = 0;
        }
        return info;
    }

    public boolean isMemoryLow() {
        MemoryInfo info = getMemoryInfo();
        return info.usagePercent > 85;
    }

    public void forceGc() {
        System.gc();
        System.runFinalization();
    }

    private void recalculateCacheSize() {
        currentCacheSize = calculateDirectorySize(context.getCacheDir());
    }

    private long calculateDirectorySize(File directory) {
        if (directory == null || !directory.exists()) {
            return 0;
        }
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    private void deleteDirectory(File directory, boolean deleteRoot) {
        if (directory == null || !directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file, true);
                } else {
                    file.delete();
                }
            }
        }
        if (deleteRoot) {
            directory.delete();
        }
    }

    private void removeOldFiles() {
        long cutoffTime = System.currentTimeMillis() - CLEANUP_THRESHOLD;
        removeOldFilesRecursive(context.getCacheDir(), cutoffTime);
        recalculateCacheSize();
    }

    private void removeOldFilesRecursive(File directory, long cutoffTime) {
        if (directory == null || !directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    removeOldFilesRecursive(file, cutoffTime);
                } else {
                    if (file.lastModified() < cutoffTime) {
                        file.delete();
                    }
                }
            }
        }
    }

    private List<File> getFilesSortedByAge(File directory) {
        List<File> files = new LinkedList<>();
        collectFiles(directory, files);
        files.sort((f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
        return files;
    }

    private void collectFiles(File directory, List<File> fileList) {
        if (directory == null || !directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    collectFiles(file, fileList);
                } else {
                    fileList.add(file);
                }
            }
        }
    }

    public static class MemoryInfo {
        public long totalMemory;
        public long freeMemory;
        public long usedMemory;
        public float usagePercent;
    }
}
