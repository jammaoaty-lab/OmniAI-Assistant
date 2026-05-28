package com.omniai.assistant.credits;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.omniai.assistant.BuildConfig;
import com.omniai.assistant.common.NetworkClient;
import com.omniai.assistant.common.Result;
import com.omniai.assistant.user.AuthInterceptor;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CreditsApiService {

    private static final String TAG = "CreditsApiService";

    private final OkHttpClient client;
    private final String apiBaseUrl;
    private final Gson gson;
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    public CreditsApiService() {
        this(BuildConfig.API_BASE_URL + "credits/");
    }

    public CreditsApiService(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
        this.gson = new Gson();
        AuthInterceptor authInterceptor = new AuthInterceptor();
        try {
            com.omniai.assistant.user.UserManager userManager = com.omniai.assistant.user.UserManager.getInstance();
            authInterceptor.setUserManager(userManager);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set auth interceptor on CreditsApiService", e);
        }
        this.client = NetworkClient.getSecureBuilder()
                .addInterceptor(authInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public Result<Integer> getCreditsBalance(String userId) {
        try {
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "balance?userId=" + userId)
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    BalanceResponse balanceResponse = gson.fromJson(body, BalanceResponse.class);
                    return Result.success(balanceResponse.balance);
                }
                return Result.error("Get balance failed: " + response.code());
            }
        } catch (IOException e) {
            return Result.error("Network error: " + e.getMessage());
        }
    }

    public Result<CreditsManager.CreditsRecord> recharge(String userId, int planId, String paymentToken) {
        try {
            String json = gson.toJson(new RechargeRequest(userId, planId, paymentToken));
            RequestBody body = RequestBody.create(json, JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "recharge")
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    CreditsManager.CreditsRecord record = gson.fromJson(responseBody, CreditsManager.CreditsRecord.class);
                    return Result.success(record);
                }
                return Result.error("Recharge failed: " + response.code());
            }
        } catch (IOException e) {
            return Result.error("Network error: " + e.getMessage());
        }
    }

    public Result<Integer> processInvite(String userId, String inviteCode) {
        try {
            String json = gson.toJson(new InviteRequest(userId, inviteCode));
            RequestBody body = RequestBody.create(json, JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "invite")
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    InviteResponse inviteResponse = gson.fromJson(responseBody, InviteResponse.class);
                    return Result.success(inviteResponse.reward);
                }
                return Result.error("Process invite failed: " + response.code());
            }
        } catch (IOException e) {
            return Result.error("Network error: " + e.getMessage());
        }
    }

    public Result<String[]> getInviteInfo(String userId) {
        try {
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "invite/info?userId=" + userId)
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    InviteInfoResponse infoResponse = gson.fromJson(responseBody, InviteInfoResponse.class);
                    return Result.success(new String[]{infoResponse.inviteCode, String.valueOf(infoResponse.inviteCount)});
                }
                return Result.error("Get invite info failed: " + response.code());
            }
        } catch (IOException e) {
            return Result.error("Network error: " + e.getMessage());
        }
    }

    public Result<List<CreditsManager.CreditsRecord>> getCreditsHistory(String userId, int page) {
        try {
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "history?userId=" + userId + "&page=" + page)
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    Type listType = new TypeToken<List<CreditsManager.CreditsRecord>>(){}.getType();
                    List<CreditsManager.CreditsRecord> records = gson.fromJson(response.body().string(), listType);
                    return Result.success(records);
                }
                return Result.error("Get history failed: " + response.code());
            }
        } catch (IOException e) {
            return Result.error("Network error: " + e.getMessage());
        }
    }

    public Result<Boolean> validatePayment(String paymentToken) {
        try {
            String json = gson.toJson(new PaymentValidationRequest(paymentToken));
            RequestBody body = RequestBody.create(json, JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "validate-payment")
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return Result.success(response.isSuccessful());
            }
        } catch (IOException e) {
            return Result.error("Network error: " + e.getMessage());
        }
    }

    private static class BalanceResponse {
        int balance;
    }

    private static class RechargeRequest {
        final String userId;
        final int planId;
        final String paymentToken;
        RechargeRequest(String userId, int planId, String paymentToken) {
            this.userId = userId;
            this.planId = planId;
            this.paymentToken = paymentToken;
        }
    }

    private static class InviteRequest {
        final String userId;
        final String inviteCode;
        InviteRequest(String userId, String inviteCode) {
            this.userId = userId;
            this.inviteCode = inviteCode;
        }
    }

    private static class InviteResponse {
        int reward;
    }

    private static class InviteInfoResponse {
        String inviteCode;
        int inviteCount;
    }

    private static class PaymentValidationRequest {
        final String paymentToken;
        PaymentValidationRequest(String paymentToken) {
            this.paymentToken = paymentToken;
        }
    }
}
