package com.omniai.assistant.security;

import android.content.Context;
import android.os.CancellationSignal;

import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.util.concurrent.Executor;

public class SecurityManager {

    private static SecurityManager instance;

    private boolean appLockEnabled;
    private boolean fingerprintEnabled;
    private boolean incognitoMode;
    private String appLockPin;
    private Context context;
    private BiometricPrompt biometricPrompt;
    private DataEncryptor dataEncryptor;
    private SensitiveFilter sensitiveFilter;

    private static final String IMAGE_ENCRYPT_SUFFIX = ".imgenc";

    private SecurityManager(Context context) {
        this.context = context.getApplicationContext();
        this.appLockEnabled = false;
        this.fingerprintEnabled = false;
        this.incognitoMode = false;
        this.appLockPin = null;
        this.dataEncryptor = new DataEncryptor(context);
        this.sensitiveFilter = new SensitiveFilter();
        this.sensitiveFilter.loadSensitiveWords();
    }

    public static synchronized SecurityManager getInstance(Context context) {
        if (instance == null) {
            instance = new SecurityManager(context);
        }
        return instance;
    }

    public void enableAppLock(String pin) {
        if (pin == null || pin.length() < 4) {
            throw new IllegalArgumentException("PIN must be at least 4 digits");
        }
        this.appLockPin = dataEncryptor.encryptString(pin);
        this.appLockEnabled = true;
    }

    public void disableAppLock() {
        this.appLockEnabled = false;
        this.appLockPin = null;
    }

    public boolean verifyPin(String pin) {
        if (!appLockEnabled || appLockPin == null) {
            return false;
        }
        String encryptedInput = dataEncryptor.encryptString(pin);
        return appLockPin.equals(encryptedInput);
    }

    public void enableFingerprint() {
        this.fingerprintEnabled = true;
    }

    public void disableFingerprint() {
        this.fingerprintEnabled = false;
    }

    public void authenticateFingerprint(AuthCallback callback) {
        if (!(context instanceof FragmentActivity)) {
            if (callback != null) {
                callback.onError("Context must be a FragmentActivity");
            }
            return;
        }
        FragmentActivity activity = (FragmentActivity) context;
        Executor executor = ContextCompat.getMainExecutor(activity);
        biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                if (callback != null) {
                    callback.onError("Authentication failed");
                }
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                if (callback != null) {
                    callback.onError(errString != null ? errString.toString() : "Authentication error");
                }
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Verify your identity to access Senta AI")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    public boolean isAppLockEnabled() {
        return appLockEnabled;
    }

    public boolean isFingerprintEnabled() {
        return fingerprintEnabled;
    }

    public void setIncognitoMode(boolean enabled) {
        this.incognitoMode = enabled;
    }

    public boolean isIncognitoMode() {
        return incognitoMode;
    }

    public String encryptData(String data) {
        return dataEncryptor.encryptString(data);
    }

    public String decryptData(String encrypted) {
        return dataEncryptor.decryptString(encrypted);
    }

    public boolean encryptModelFile(String filePath) {
        String outputPath = filePath + ".enc";
        return dataEncryptor.encryptFile(filePath, outputPath);
    }

    public boolean decryptModelFile(String filePath) {
        if (!filePath.endsWith(".enc")) {
            return false;
        }
        String outputPath = filePath.substring(0, filePath.length() - 4);
        return dataEncryptor.decryptFile(filePath, outputPath);
    }

    public boolean encryptImageFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        File file = new File(filePath);
        if (!file.exists()) return false;
        String outputPath = filePath + IMAGE_ENCRYPT_SUFFIX;
        boolean result = dataEncryptor.encryptFile(filePath, outputPath);
        if (result) {
            file.delete();
            new File(outputPath).renameTo(new File(filePath));
        }
        return result;
    }

    public boolean decryptImageFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        File file = new File(filePath);
        if (!file.exists()) return false;
        if (!isImageFileEncrypted(filePath)) return false;
        String tempOutput = filePath + ".dec";
        boolean result = dataEncryptor.decryptFile(filePath, tempOutput);
        if (result) {
            file.delete();
            new File(tempOutput).renameTo(new File(filePath));
        }
        return result;
    }

    public boolean isImageFileEncrypted(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        File file = new File(filePath);
        if (!file.exists()) return false;
        if (filePath.endsWith(IMAGE_ENCRYPT_SUFFIX)) return true;
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png") && !name.endsWith(".webp")) {
            return false;
        }
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] header = new byte[4];
            int read = fis.read(header);
            fis.close();
            if (read < 4) return false;
            int magic = ((header[0] & 0xFF) << 24) | ((header[1] & 0xFF) << 16) |
                    ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
            if (magic == 0xFFD8FF00 || magic == 0x89504E47 || magic == 0x52494646) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSensitiveContent(String text) {
        return sensitiveFilter.containsSensitive(text);
    }

    public String sanitizePrompt(String prompt) {
        if (prompt == null) return "";
        String sanitized = sensitiveFilter.filterText(prompt);
        sanitized = sanitizeImagePrompt(sanitized);
        return sanitized;
    }

    private String sanitizeImagePrompt(String prompt) {
        String result = prompt;
        String[] visionPatterns = {
                "ignore previous instructions",
                "ignore all previous",
                "disregard safety",
                "bypass filter",
                "jailbreak",
                "system prompt",
                "you are now",
                "new instructions"
        };
        String lower = result.toLowerCase();
        for (String pattern : visionPatterns) {
            if (lower.contains(pattern)) {
                result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(pattern), "[filtered]");
            }
        }
        return result;
    }

    public boolean checkPromptInjection(String prompt) {
        if (sensitiveFilter.detectInjection(prompt)) return true;
        String lower = prompt.toLowerCase();
        String[] visionInjectionPatterns = {
                "ignore image analysis",
                "skip image validation",
                "bypass vision",
                "override vision model"
        };
        for (String pattern : visionInjectionPatterns) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    public boolean cleanVisionCache() {
        boolean success = true;
        File cacheDir = context.getCacheDir();
        File visionCacheDir = new File(cacheDir, "vision");
        if (visionCacheDir.exists()) {
            success = deleteDirectory(visionCacheDir) && success;
        }
        File tempImageDir = new File(cacheDir, "temp_images");
        if (tempImageDir.exists()) {
            success = deleteDirectory(tempImageDir) && success;
        }
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().toLowerCase();
                    if ((name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                            || name.endsWith(".webp")) && name.contains("temp")) {
                        success = file.delete() && success;
                    }
                }
            }
        }
        return success;
    }

    private boolean deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) return true;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return directory.delete();
    }

    public interface AuthCallback {
        void onSuccess();
        void onError(String error);
    }
}
