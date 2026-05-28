package com.omniai.assistant.settings;

import android.content.SharedPreferences;
import com.omniai.assistant.BuildConfig;
import com.google.gson.Gson;

public class SettingsManager {

    private static volatile SettingsManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;

    public static class AiSettings {
        public String defaultModel;
        public String defaultVisionModel;
        public String systemPrompt;
        public float temperature;
        public float topP;
        public int topK;
        public float repeatPenalty;

        public AiSettings() {
            this.defaultModel = "llama-3-8b";
            this.defaultVisionModel = "qwen3-vl-2b";
            this.systemPrompt = "";
            this.temperature = 0.7f;
            this.topP = 0.9f;
            this.topK = 40;
            this.repeatPenalty = 1.1f;
        }

        public AiSettings copy() {
            AiSettings copy = new AiSettings();
            copy.defaultModel = this.defaultModel;
            copy.defaultVisionModel = this.defaultVisionModel;
            copy.systemPrompt = this.systemPrompt;
            copy.temperature = this.temperature;
            copy.topP = this.topP;
            copy.topK = this.topK;
            copy.repeatPenalty = this.repeatPenalty;
            return copy;
        }
    }

    public static class InferenceSettings {
        public int nThreads;
        public int nCtx;
        public boolean useMmap;
        public boolean useGpu;
        public InferenceSpeedMode speedMode;

        public InferenceSettings() {
            this.nThreads = 4;
            this.nCtx = 4096;
            this.useMmap = true;
            this.useGpu = false;
            this.speedMode = InferenceSpeedMode.BALANCED;
        }

        public InferenceSettings copy() {
            InferenceSettings copy = new InferenceSettings();
            copy.nThreads = this.nThreads;
            copy.nCtx = this.nCtx;
            copy.useMmap = this.useMmap;
            copy.useGpu = this.useGpu;
            copy.speedMode = this.speedMode;
            return copy;
        }
    }

    public static class PrivacySettings {
        public boolean appLockEnabled;
        public boolean fingerprintEnabled;
        public boolean incognitoMode;
        public boolean encryptData;
        public boolean filterSensitive;
        public boolean visionGpuAcceleration;
        public boolean autoOcr;
        public boolean autoCleanImageCache;

        public PrivacySettings() {
            this.appLockEnabled = false;
            this.fingerprintEnabled = false;
            this.incognitoMode = false;
            this.encryptData = false;
            this.filterSensitive = true;
            this.visionGpuAcceleration = false;
            this.autoOcr = false;
            this.autoCleanImageCache = false;
        }

        public PrivacySettings copy() {
            PrivacySettings copy = new PrivacySettings();
            copy.appLockEnabled = this.appLockEnabled;
            copy.fingerprintEnabled = this.fingerprintEnabled;
            copy.incognitoMode = this.incognitoMode;
            copy.encryptData = this.encryptData;
            copy.filterSensitive = this.filterSensitive;
            copy.visionGpuAcceleration = this.visionGpuAcceleration;
            copy.autoOcr = this.autoOcr;
            copy.autoCleanImageCache = this.autoCleanImageCache;
            return copy;
        }
    }

    public static class NetworkSettings {
        public String cloudApiUrl;
        public String cloudApiKey;
        public int timeout;
        public boolean autoFallback;

        public NetworkSettings() {
            this.cloudApiUrl = BuildConfig.API_BASE_URL;
            this.cloudApiKey = "";
            this.timeout = 30;
            this.autoFallback = true;
        }

        public NetworkSettings copy() {
            NetworkSettings copy = new NetworkSettings();
            copy.cloudApiUrl = this.cloudApiUrl;
            copy.cloudApiKey = this.cloudApiKey;
            copy.timeout = this.timeout;
            copy.autoFallback = this.autoFallback;
            return copy;
        }
    }

    private SettingsManager(SharedPreferences prefs) {
        this.prefs = prefs;
        this.gson = new Gson();
    }

    public static SettingsManager getInstance(SharedPreferences prefs) {
        if (instance == null) {
            synchronized (SettingsManager.class) {
                if (instance == null) {
                    instance = new SettingsManager(prefs);
                }
            }
        }
        return instance;
    }

    public AiSettings getAiSettings() {
        String json = prefs.getString("ai_settings", "");
        if (json.isEmpty()) return new AiSettings();
        try {
            return gson.fromJson(json, AiSettings.class);
        } catch (Exception e) {
            return new AiSettings();
        }
    }

    public void updateAiSettings(AiSettings settings) {
        prefs.edit().putString("ai_settings", gson.toJson(settings)).apply();
    }

    public InferenceSettings getInferenceSettings() {
        String json = prefs.getString("inference_settings", "");
        if (json.isEmpty()) return new InferenceSettings();
        try {
            return gson.fromJson(json, InferenceSettings.class);
        } catch (Exception e) {
            return new InferenceSettings();
        }
    }

    public void updateInferenceSettings(InferenceSettings settings) {
        prefs.edit().putString("inference_settings", gson.toJson(settings)).apply();
    }

    public boolean isGpuAccelerationEnabled() {
        return prefs.getBoolean("gpu_acceleration", false);
    }

    public void setGpuAccelerationEnabled(boolean enabled) {
        prefs.edit().putBoolean("gpu_acceleration", enabled).apply();
    }

    public PrivacySettings getPrivacySettings() {
        String json = prefs.getString("privacy_settings", "");
        if (json.isEmpty()) return new PrivacySettings();
        try {
            return gson.fromJson(json, PrivacySettings.class);
        } catch (Exception e) {
            return new PrivacySettings();
        }
    }

    public void updatePrivacySettings(PrivacySettings settings) {
        prefs.edit().putString("privacy_settings", gson.toJson(settings)).apply();
    }

    public NetworkSettings getNetworkSettings() {
        String json = prefs.getString("network_settings", "");
        if (json.isEmpty()) return new NetworkSettings();
        try {
            return gson.fromJson(json, NetworkSettings.class);
        } catch (Exception e) {
            return new NetworkSettings();
        }
    }

    public void updateNetworkSettings(NetworkSettings settings) {
        prefs.edit().putString("network_settings", gson.toJson(settings)).apply();
    }

    public boolean isDeveloperMode() {
        return prefs.getBoolean("developer_mode", false);
    }

    public void setDeveloperMode(boolean enabled) {
        prefs.edit().putBoolean("developer_mode", enabled).apply();
    }

    public boolean isAutoCleanupEnabled() {
        return prefs.getBoolean("auto_cleanup", true);
    }

    public void setAutoCleanupEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_cleanup", enabled).apply();
    }

    public boolean isIncognitoMode() {
        return prefs.getBoolean("incognito_mode", false);
    }

    public void setIncognitoMode(boolean enabled) {
        prefs.edit().putBoolean("incognito_mode", enabled).apply();
    }

    public boolean isCloudFallbackEnabled() {
        return prefs.getBoolean("cloud_fallback", true);
    }

    public void setCloudFallbackEnabled(boolean enabled) {
        prefs.edit().putBoolean("cloud_fallback", enabled).apply();
    }

    public boolean isVisionGpuEnabled() {
        PrivacySettings settings = getPrivacySettings();
        return settings.visionGpuAcceleration;
    }

    public void setVisionGpuEnabled(boolean enabled) {
        PrivacySettings settings = getPrivacySettings();
        settings.visionGpuAcceleration = enabled;
        updatePrivacySettings(settings);
    }

    public boolean isAutoOcr() {
        PrivacySettings settings = getPrivacySettings();
        return settings.autoOcr;
    }

    public void setAutoOcr(boolean enabled) {
        PrivacySettings settings = getPrivacySettings();
        settings.autoOcr = enabled;
        updatePrivacySettings(settings);
    }

    public String getDefaultVisionModel() {
        AiSettings settings = getAiSettings();
        return settings.defaultVisionModel;
    }

    public void setDefaultVisionModel(String modelId) {
        AiSettings settings = getAiSettings();
        settings.defaultVisionModel = modelId;
        updateAiSettings(settings);
    }

    public void resetToDefaults() {
        prefs.edit()
                .remove("ai_settings")
                .remove("inference_settings")
                .remove("gpu_acceleration")
                .remove("privacy_settings")
                .remove("network_settings")
                .remove("developer_mode")
                .remove("auto_cleanup")
                .remove("incognito_mode")
                .remove("cloud_fallback")
                .apply();
    }
}
