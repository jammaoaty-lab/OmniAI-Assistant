package com.omniai.assistant.user;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Base64;

import com.omniai.assistant.security.DataEncryptor;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserManager {

    private static volatile UserManager instance;

    private UserProfile currentUser;
    private SharedPreferences prefs;
    private String authToken;
    private String refreshToken;
    private long tokenExpiry;

    private final UserApiService apiService;
    private final AuthInterceptor authInterceptor;
    private final DeviceManager deviceManager;
    private final ExecutorService executor;
    private final DataEncryptor dataEncryptor;

    private static final String PREFS_NAME = "omniai_user_prefs";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_TOKEN_EXPIRY = "token_expiry";
    private static final String KEY_USER_ID = "user_id";

    private UserManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dataEncryptor = new DataEncryptor(context);
        this.authInterceptor = new AuthInterceptor();
        this.authInterceptor.setUserManager(this);
        this.apiService = new UserApiService(new okhttp3.OkHttpClient(), authInterceptor);
        this.deviceManager = DeviceManager.getInstance();
        this.executor = Executors.newFixedThreadPool(4);
        loadToken();
    }

    public static UserManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("UserManager not initialized. Call init(context) first.");
        }
        return instance;
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new UserManager(context);
        }
    }

    public void login(String account, String password, LoginCallback callback) {
        executor.execute(() -> {
            checkTokenExpiry();
            UserApiService.Result<UserProfile> result = apiService.login(account, password);
            if (result.isSuccess()) {
                UserProfile profile = result.getData();
                handleLoginSuccess(profile);
                if (callback != null) {
                    callback.onSuccess(profile);
                }
            } else {
                if (callback != null) {
                    callback.onError(result.getError());
                }
            }
        });
    }

    public void loginWithCode(String phone, String code, LoginCallback callback) {
        executor.execute(() -> {
            checkTokenExpiry();
            UserApiService.Result<UserProfile> result = apiService.loginWithCode(phone, code);
            if (result.isSuccess()) {
                UserProfile profile = result.getData();
                handleLoginSuccess(profile);
                if (callback != null) {
                    callback.onSuccess(profile);
                }
            } else {
                if (callback != null) {
                    callback.onError(result.getError());
                }
            }
        });
    }

    public void loginWithGoogle(com.google.android.gms.auth.api.signin.GoogleSignInAccount account, LoginCallback callback) {
        executor.execute(() -> {
            checkTokenExpiry();
            String idToken = account.getIdToken();
            UserApiService.Result<UserProfile> result = apiService.socialLogin("google", idToken);
            if (result.isSuccess()) {
                UserProfile profile = result.getData();
                handleLoginSuccess(profile);
                if (callback != null) {
                    callback.onSuccess(profile);
                }
            } else {
                if (callback != null) {
                    callback.onError(result.getError());
                }
            }
        });
    }

    public void loginWithWechat(String code, LoginCallback callback) {
        executor.execute(() -> {
            checkTokenExpiry();
            UserApiService.Result<UserProfile> result = apiService.socialLogin("wechat", code);
            if (result.isSuccess()) {
                UserProfile profile = result.getData();
                handleLoginSuccess(profile);
                if (callback != null) {
                    callback.onSuccess(profile);
                }
            } else {
                if (callback != null) {
                    callback.onError(result.getError());
                }
            }
        });
    }

    public void loginWithQQ(String code, LoginCallback callback) {
        executor.execute(() -> {
            checkTokenExpiry();
            UserApiService.Result<UserProfile> result = apiService.socialLogin("qq", code);
            if (result.isSuccess()) {
                UserProfile profile = result.getData();
                handleLoginSuccess(profile);
                if (callback != null) {
                    callback.onSuccess(profile);
                }
            } else {
                if (callback != null) {
                    callback.onError(result.getError());
                }
            }
        });
    }

    public void loginWithApple(String token, LoginCallback callback) {
        executor.execute(() -> {
            checkTokenExpiry();
            UserApiService.Result<UserProfile> result = apiService.socialLogin("apple", token);
            if (result.isSuccess()) {
                UserProfile profile = result.getData();
                handleLoginSuccess(profile);
                if (callback != null) {
                    callback.onSuccess(profile);
                }
            } else {
                if (callback != null) {
                    callback.onError(result.getError());
                }
            }
        });
    }

    public void register(String account, String password, String code, RegisterCallback callback) {
        executor.execute(() -> {
            UserApiService.Result<UserProfile> result = apiService.register(account, password, code);
            if (result.isSuccess()) {
                UserProfile profile = result.getData();
                handleLoginSuccess(profile);
                if (callback != null) {
                    callback.onSuccess(profile);
                }
            } else {
                if (callback != null) {
                    callback.onError(result.getError());
                }
            }
        });
    }

    public void sendVerificationCode(String phone, CodeCallback callback) {
        executor.execute(() -> {
            UserApiService.Result<Void> result = apiService.sendVerificationCode(phone);
            if (result.isSuccess()) {
                if (callback != null) {
                    callback.onSuccess();
                }
            } else {
                if (callback != null) {
                    callback.onError(result.getError());
                }
            }
        });
    }

    public void loginWeChat(android.app.Activity activity, LoginCallback callback) {
        if (callback != null) {
            callback.onError("微信登录SDK尚未集成，请使用其他登录方式");
        }
    }

    public void loginQQ(android.app.Activity activity, LoginCallback callback) {
        if (callback != null) {
            callback.onError("QQ登录SDK尚未集成，请使用其他登录方式");
        }
    }

    public void loginApple(android.app.Activity activity, LoginCallback callback) {
        if (callback != null) {
            callback.onError("Apple登录SDK尚未集成，请使用其他登录方式");
        }
    }

    public void handleGoogleSignInResult(Intent data, LoginCallback callback) {
        try {
            com.google.android.gms.auth.api.signin.GoogleSignInAccount account =
                    com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
                            .getResult(com.google.android.gms.common.api.ApiException.class);
            loginWithGoogle(account, callback);
        } catch (com.google.android.gms.common.api.ApiException e) {
            if (callback != null) {
                callback.onError("Google登录失败: " + e.getStatusCode());
            }
        }
    }

    public void logout() {
        currentUser = null;
        clearToken();
        deviceManager.clearDevices();
    }

    public void refreshToken(RefreshCallback callback) {
        executor.execute(() -> {
            if (refreshToken == null || refreshToken.isEmpty()) {
                if (callback != null) {
                    callback.onError("No refresh token available");
                }
                return;
            }
            UserApiService.Result<String[]> result = apiService.refreshToken(refreshToken);
            if (result.isSuccess()) {
                String[] tokens = result.getData();
                String newAccessToken = tokens[0];
                String newRefreshToken = tokens[1];
                long expiry = tokens.length > 2 ? Long.parseLong(tokens[2]) : 0L;
                saveToken(newAccessToken, newRefreshToken, expiry);
                if (callback != null) {
                    callback.onSuccess(newAccessToken, newRefreshToken, expiry);
                }
            } else {
                clearToken();
                if (callback != null) {
                    callback.onError(result.getError());
                }
            }
        });
    }

    public boolean isLoggedIn() {
        return currentUser != null && authToken != null && !authToken.isEmpty();
    }

    public UserProfile getCurrentUser() {
        return currentUser;
    }

    public void updateProfile(UserProfile profile, UpdateCallback callback) {
        executor.execute(() -> {
            checkTokenExpiry();
            UserApiService.Result<UserProfile> result = apiService.updateProfile(profile);
            if (result.isSuccess()) {
                currentUser = result.getData();
                if (callback != null) {
                    callback.onSuccess(currentUser);
                }
            } else {
                if (callback != null) {
                    callback.onError(result.getError());
                }
            }
        });
    }

    public void checkTokenExpiry() {
        if (isTokenExpired() && refreshToken != null && !refreshToken.isEmpty()) {
            refreshToken(null);
        }
    }

    public boolean isTokenExpired() {
        if (tokenExpiry <= 0) {
            return true;
        }
        long currentTime = System.currentTimeMillis() / 1000;
        return currentTime >= tokenExpiry - 60;
    }

    public void saveToken(String accessToken, String newRefreshToken, long expiry) {
        this.authToken = accessToken;
        this.refreshToken = newRefreshToken;
        this.tokenExpiry = expiry;
        String encAccess = dataEncryptor.encryptString(accessToken);
        String encRefresh = dataEncryptor.encryptString(newRefreshToken);
        prefs.edit()
                .putString(KEY_AUTH_TOKEN, encAccess)
                .putString(KEY_REFRESH_TOKEN, encRefresh)
                .putLong(KEY_TOKEN_EXPIRY, expiry)
                .apply();
    }

    public void clearToken() {
        authToken = null;
        refreshToken = null;
        tokenExpiry = 0;
        prefs.edit()
                .remove(KEY_AUTH_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRY)
                .remove(KEY_USER_ID)
                .apply();
    }

    public void handleRemoteLogin() {
        String deviceId = getDeviceId();
        executor.execute(() -> {
            UserApiService.Result<Boolean> checkResult = apiService.checkDevice(deviceId);
            if (checkResult.isSuccess() && !checkResult.getData()) {
                apiService.reportRemoteLogin(deviceId);
            }
        });
    }

    public String getDeviceId() {
        String deviceId = deviceManager.getCurrentDeviceId();
        if (deviceId != null && !deviceId.isEmpty()) {
            return deviceId;
        }
        return Settings.Secure.getString(prefs.getContext().getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    String getAuthToken() {
        return authToken;
    }

    private void loadToken() {
        String encAccess = prefs.getString(KEY_AUTH_TOKEN, null);
        String encRefresh = prefs.getString(KEY_REFRESH_TOKEN, null);
        try {
            authToken = encAccess != null ? dataEncryptor.decryptString(encAccess) : null;
            refreshToken = encRefresh != null ? dataEncryptor.decryptString(encRefresh) : null;
        } catch (Exception e) {
            authToken = null;
            refreshToken = null;
        }
        tokenExpiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0);
    }

    private void handleLoginSuccess(UserProfile profile) {
        currentUser = profile;
        if (profile.getAccessToken() != null) {
            long expiry = profile.getTokenExpiry();
            if (expiry <= 0) {
                expiry = parseJwtExpiry(profile.getAccessToken());
            }
            saveToken(profile.getAccessToken(), profile.getRefreshToken(), expiry);
        }
        String deviceId = getDeviceId();
        DeviceManager.DeviceInfo deviceInfo = new DeviceManager.DeviceInfo(
                deviceId,
                android.os.Build.DEVICE,
                android.os.Build.MODEL,
                System.currentTimeMillis(),
                "",
                true
        );
        deviceManager.setCurrentDeviceId(deviceId);
        deviceManager.bindDevice(profile.getUserId(), deviceInfo);
    }

    private long parseJwtExpiry(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return 0;
            }
            byte[] payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            String payload = new String(payloadBytes, "UTF-8");
            JSONObject json = new JSONObject(payload);
            return json.optLong("exp", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public interface LoginCallback {
        void onSuccess(UserProfile profile);
        void onError(String message);
    }

    public interface CodeCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface RegisterCallback {
        void onSuccess(UserProfile profile);
        void onError(String message);
    }

    public interface RefreshCallback {
        void onSuccess(String accessToken, String refreshToken, long expiry);
        void onError(String message);
    }

    public interface UpdateCallback {
        void onSuccess(UserProfile profile);
        void onError(String message);
    }
}
