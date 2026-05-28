package com.omniai.assistant.cache;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import com.omniai.assistant.cache.CacheManager;

public class PerformanceOptimizer {

    private ThermalMonitor thermalMonitor;
    private boolean isCpuThrottled;
    private int cpuLevel;
    private boolean isBackgroundOptimized;
    private Context context;

    public PerformanceOptimizer(Context context) {
        this.context = context.getApplicationContext();
        this.thermalMonitor = new ThermalMonitor(context);
        this.isCpuThrottled = false;
        this.cpuLevel = 100;
        this.isBackgroundOptimized = false;
    }

    public void optimizeForInference() {
        isBackgroundOptimized = false;
        if (isThermalCritical()) {
            cpuLevel = 30;
            isCpuThrottled = true;
        } else if (isThermalHigh()) {
            cpuLevel = 60;
            isCpuThrottled = true;
        } else {
            cpuLevel = 100;
            isCpuThrottled = false;
        }
    }

    public void optimizeForTraining() {
        isBackgroundOptimized = false;
        if (isThermalCritical()) {
            cpuLevel = 20;
            isCpuThrottled = true;
        } else if (isThermalHigh()) {
            cpuLevel = 50;
            isCpuThrottled = true;
        } else {
            cpuLevel = 90;
            isCpuThrottled = false;
        }
    }

    public void optimizeForBackground() {
        isBackgroundOptimized = true;
        cpuLevel = 25;
        isCpuThrottled = true;
    }

    public void restorePerformance() {
        isCpuThrottled = false;
        isBackgroundOptimized = false;
        cpuLevel = 100;
    }

    public int getCpuLevel() {
        return cpuLevel;
    }

    public void setCpuThrottling(boolean throttled) {
        this.isCpuThrottled = throttled;
        if (throttled) {
            cpuLevel = Math.min(cpuLevel, 50);
        }
    }

    public int adjustThreadCount(int baseCount) {
        if (isBackgroundOptimized) {
            return Math.max(1, baseCount / 4);
        }
        if (isCpuThrottled) {
            float factor = cpuLevel / 100.0f;
            return Math.max(1, Math.round(baseCount * factor));
        }
        return baseCount;
    }

    public boolean shouldReduceQuality() {
        return cpuLevel < 50 || isMemoryLow();
    }

    public OptimizationLevel getOptimizationLevel() {
        if (isBackgroundOptimized) {
            return OptimizationLevel.MINIMAL;
        }
        if (cpuLevel <= 25 || isThermalCritical()) {
            return OptimizationLevel.CRITICAL;
        }
        if (cpuLevel <= 50 || isThermalHigh()) {
            return OptimizationLevel.MINIMAL;
        }
        if (cpuLevel <= 75 || isCpuThrottled) {
            return OptimizationLevel.REDUCED;
        }
        return OptimizationLevel.FULL;
    }

    private boolean isThermalHigh() {
        return thermalMonitor.getThermalStatus() >= ThermalMonitor.THERMAL_STATUS_HIGH;
    }

    private boolean isThermalCritical() {
        return thermalMonitor.getThermalStatus() >= ThermalMonitor.THERMAL_STATUS_CRITICAL;
    }

    private boolean isMemoryLow() {
        CacheManager.MemoryInfo info = CacheManager.getInstance(context).getMemoryInfo();
        return info.usagePercent > 85;
    }

    public enum OptimizationLevel {
        FULL,
        REDUCED,
        MINIMAL,
        CRITICAL
    }

    private static class ThermalMonitor {

        static final int THERMAL_STATUS_NONE = 0;
        static final int THERMAL_STATUS_HIGH = 2;
        static final int THERMAL_STATUS_CRITICAL = 3;

        private Context context;

        ThermalMonitor(Context context) {
            this.context = context.getApplicationContext();
        }

        int getThermalStatus() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    Object powerManager = context.getSystemService(Context.POWER_SERVICE);
                    if (powerManager instanceof android.os.PowerManager) {
                        return ((android.os.PowerManager) powerManager).getCurrentThermalStatus();
                    }
                } catch (Exception e) {
                    return THERMAL_STATUS_NONE;
                }
            }
            return THERMAL_STATUS_NONE;
        }
    }
}
