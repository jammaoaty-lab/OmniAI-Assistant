package com.omniai.assistant.user;

import android.util.Base64;

import com.omniai.assistant.BuildConfig;
import com.omniai.assistant.common.NetworkClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UserApiService {

    private static final String BASE_URL = BuildConfig.API_BASE_URL;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;

    public UserApiService(OkHttpClient baseClient, AuthInterceptor authInterceptor) {
        this.client = NetworkClient.getSecureBuilder()
                .addInterceptor(authInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public Result<UserProfile> login(String account, String password) {
        try {
            JSONObject body = new JSONObject();
            body.put("account", account);
            body.put("password", password);
            Request request = new Request.Builder()
                    .url(BASE_URL + "auth/login")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return parseUserProfile(response);
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public Result<UserProfile> loginWithCode(String phone, String code) {
        try {
            JSONObject body = new JSONObject();
            body.put("phone", phone);
            body.put("code", code);
            Request request = new Request.Builder()
                    .url(BASE_URL + "auth/login/code")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return parseUserProfile(response);
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public Result<UserProfile> register(String account, String password, String code) {
        try {
            JSONObject body = new JSONObject();
            body.put("account", account);
            body.put("password", password);
            body.put("code", code);
            Request request = new Request.Builder()
                    .url(BASE_URL + "auth/register")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return parseUserProfile(response);
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public Result<String[]> refreshToken(String refreshToken) {
        try {
            JSONObject body = new JSONObject();
            body.put("refreshToken", refreshToken);
            Request request = new Request.Builder()
                    .url(BASE_URL + "auth/refresh")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return Result.error("Refresh failed: " + response.code());
                }
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject json = new JSONObject(responseBody);
                JSONObject data = json.getJSONObject("data");
                String newAccessToken = data.getString("accessToken");
                String newRefreshToken = data.getString("refreshToken");
                long expiry = data.optLong("expiry", 0);
                return Result.success(new String[]{newAccessToken, newRefreshToken, String.valueOf(expiry)});
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public Result<UserProfile> getProfile() {
        try {
            Request request = new Request.Builder()
                    .url(BASE_URL + "user/profile")
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return parseUserProfile(response);
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public Result<UserProfile> updateProfile(UserProfile profile) {
        try {
            JSONObject body = new JSONObject();
            body.put("nickname", profile.getNickname());
            body.put("avatar", profile.getAvatar());
            body.put("email", profile.getEmail());
            body.put("phone", profile.getPhone());
            Request request = new Request.Builder()
                    .url(BASE_URL + "user/profile")
                    .put(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return parseUserProfile(response);
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public Result<UserProfile> socialLogin(String provider, String token) {
        try {
            JSONObject body = new JSONObject();
            body.put("provider", provider);
            body.put("token", token);
            Request request = new Request.Builder()
                    .url(BASE_URL + "auth/social")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return parseUserProfile(response);
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public Result<Void> sendVerificationCode(String phone) {
        try {
            JSONObject body = new JSONObject();
            body.put("phone", phone);
            Request request = new Request.Builder()
                    .url(BASE_URL + "auth/send-code")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return Result.error("发送验证码失败: " + response.code());
                }
                return Result.success(null);
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public Result<Void> resetPassword(String email) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            Request request = new Request.Builder()
                    .url(BASE_URL + "auth/reset-password")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return Result.error("重置密码失败: " + response.code());
                }
                return Result.success(null);
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public Result<Boolean> checkDevice(String deviceId) {
        try {
            JSONObject body = new JSONObject();
            body.put("deviceId", deviceId);
            Request request = new Request.Builder()
                    .url(BASE_URL + "device/check")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return Result.error("Check device failed: " + response.code());
                }
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject json = new JSONObject(responseBody);
                boolean isTrusted = json.optJSONObject("data") != null && json.getJSONObject("data").optBoolean("trusted", false);
                return Result.success(isTrusted);
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public Result<Void> reportRemoteLogin(String deviceId) {
        try {
            JSONObject body = new JSONObject();
            body.put("deviceId", deviceId);
            Request request = new Request.Builder()
                    .url(BASE_URL + "device/remote-login")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return Result.error("Report remote login failed: " + response.code());
                }
                return Result.success(null);
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private Result<UserProfile> parseUserProfile(Response response) throws IOException {
        if (!response.isSuccessful()) {
            String errorBody = response.body() != null ? response.body().string() : "";
            return Result.error("Request failed: " + response.code() + " " + errorBody);
        }
        String responseBody = response.body() != null ? response.body().string() : "";
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONObject data = json.getJSONObject("data");
            UserProfile profile = new UserProfile();
            profile.setUserId(data.optString("userId", ""));
            profile.setNickname(data.optString("nickname", ""));
            profile.setAvatar(data.optString("avatar", ""));
            profile.setEmail(data.optString("email", ""));
            profile.setPhone(data.optString("phone", ""));
            profile.setAccessToken(data.optString("accessToken", ""));
            profile.setRefreshToken(data.optString("refreshToken", ""));
            if (data.has("tokenExpiry")) {
                profile.setTokenExpiry(data.getLong("tokenExpiry"));
            }
            return Result.success(profile);
        } catch (Exception e) {
            return Result.error("Parse error: " + e.getMessage());
        }
    }

    public static class Result<T> {
        private final T data;
        private final String error;
        private final boolean success;

        private Result(T data, String error, boolean success) {
            this.data = data;
            this.error = error;
            this.success = success;
        }

        public static <T> Result<T> success(T data) {
            return new Result<>(data, null, true);
        }

        public static <T> Result<T> error(String error) {
            return new Result<>(null, error, false);
        }

        public T getData() {
            return data;
        }

        public String getError() {
            return error;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
