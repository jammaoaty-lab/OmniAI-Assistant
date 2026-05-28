package com.omniai.assistant.common;

public final class Constants {

    public static final String PREFS_NAME = "omniai_prefs";
    public static final String PREF_KEY_USE_SIMULATION = "use_simulation_mode";
    public static final String KEY_TOKEN = "key_token";
    public static final String KEY_REFRESH_TOKEN = "key_refresh_token";
    public static final String KEY_USER_ID = "key_user_id";
    public static final String KEY_IS_LOGGED_IN = "key_is_logged_in";

    public static final String MODEL_DIR = "models";
    public static final String LORA_DIR = "lora";
    public static final String KNOWLEDGE_DIR = "knowledge";
    public static final String CACHE_DIR = "cache";

    public static final String CLOUD_API_BASE = "https://api.omniai.cloud/v1";
    public static final int CLOUD_API_TIMEOUT = 30000;

    public static final int MAX_CONTEXT_LENGTH = 4096;
    public static final float DEFAULT_TEMPERATURE = 0.7f;
    public static final float DEFAULT_TOP_P = 0.9f;
    public static final int DEFAULT_TOP_K = 40;
    public static final float DEFAULT_REPEAT_PENALTY = 1.1f;

    public static final float THERMAL_THRESHOLD_HIGH = 45.0f;
    public static final float THERMAL_THRESHOLD_CRITICAL = 50.0f;

    public static final int MEMORY_THRESHOLD_LOW_MB = 512;

    public static final String INFERENCE_MODE_FAST = "fast";
    public static final String INFERENCE_MODE_BALANCED = "balanced";
    public static final String INFERENCE_MODE_PRECISION = "precision";

    private Constants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
