package com.omniai.assistant.cloud;

import android.util.Base64;

import com.omniai.assistant.scheduler.InferenceParams;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class CloudInferenceClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private OkHttpClient client;
    private String apiBaseUrl;
    private String apiKey;
    private boolean isAvailable;
    private boolean isVisionAvailable;

    public interface CloudCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public interface StreamCallback {
        void onToken(String token);
        void onComplete(String fullResult);
        void onError(String error);
    }

    public CloudInferenceClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.apiBaseUrl = "";
        this.apiKey = "";
        this.isAvailable = false;
        this.isVisionAvailable = false;
    }

    private String classifyNetworkError(IOException e) {
        if (e instanceof SocketTimeoutException) return "网络请求超时，请检查网络连接";
        if (e instanceof UnknownHostException) return "无法连接服务器，请检查网络";
        if (e instanceof ConnectException) return "服务器连接失败";
        if (e instanceof SSLException) return "安全连接失败";
        return "网络错误: " + e.getMessage();
    }

    private String classifyHttpError(int code) {
        if (code == 401) return "API密钥无效，请检查配置";
        if (code == 403) return "访问被拒绝，请检查权限";
        if (code == 404) return "API接口不存在";
        if (code == 429) return "请求过于频繁，请稍后重试";
        if (code == 500 || code == 502 || code == 503) return "服务器错误，请稍后重试";
        return "请求失败: HTTP " + code;
    }

    public void complete(String prompt, InferenceParams params, CloudCallback callback) {
        try {
            JSONObject requestBody = buildRequestJson(prompt, params);
            RequestBody body = RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "/v1/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    isAvailable = false;
                    if (callback != null) callback.onError(classifyNetworkError(e));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        if (!response.isSuccessful()) {
                            if (callback != null) callback.onError(classifyHttpError(response.code()));
                            return;
                        }
                        if (response.body() == null) {
                            if (callback != null) callback.onError("Empty response body");
                            return;
                        }
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray choices = json.getJSONArray("choices");
                        String text = choices.getJSONObject(0).getString("text");
                        isAvailable = true;
                        if (callback != null) callback.onSuccess(text);
                    } catch (Exception e) {
                        if (callback != null) callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            if (callback != null) callback.onError(e.getMessage());
        }
    }

    public void streamComplete(String prompt, InferenceParams params, StreamCallback callback) {
        try {
            JSONObject requestBody = buildRequestJson(prompt, params);
            requestBody.put("stream", true);
            RequestBody body = RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "/v1/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    isAvailable = false;
                    if (callback != null) callback.onError(classifyNetworkError(e));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        if (!response.isSuccessful()) {
                            if (callback != null) callback.onError(classifyHttpError(response.code()));
                            return;
                        }
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
                        StringBuilder fullResult = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();
                                if (data.equals("[DONE]")) break;
                                JSONObject chunk = new JSONObject(data);
                                JSONArray choices = chunk.getJSONArray("choices");
                                String token = choices.getJSONObject(0).getString("text");
                                fullResult.append(token);
                                if (callback != null) callback.onToken(token);
                            }
                        }
                        reader.close();
                        isAvailable = true;
                        if (callback != null) callback.onComplete(fullResult.toString());
                    } catch (Exception e) {
                        if (callback != null) callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            if (callback != null) callback.onError(e.getMessage());
        }
    }

    public void visionChat(String imagePath, String prompt, CloudCallback callback) {
        if (callback == null) return;
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                callback.onError("Image file not found");
                return;
            }
            String base64Image = encodeImageToBase64(imagePath);
            if (base64Image == null || base64Image.isEmpty()) {
                callback.onError("Failed to encode image");
                return;
            }

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "qwen-vl");
            requestBody.put("prompt", prompt);
            requestBody.put("image", base64Image);

            RequestBody body = RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "/v1/vision/chat")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    isVisionAvailable = false;
                    callback.onError(classifyNetworkError(e));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        if (!response.isSuccessful()) {
                            callback.onError(classifyHttpError(response.code()));
                            return;
                        }
                        if (response.body() == null) {
                            callback.onError("Empty response body");
                            return;
                        }
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray choices = json.getJSONArray("choices");
                        String text = choices.getJSONObject(0).getString("text");
                        isVisionAvailable = true;
                        callback.onSuccess(text);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public void imageOcr(String imagePath, CloudCallback callback) {
        if (callback == null) return;
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                callback.onError("Image file not found");
                return;
            }
            String base64Image = encodeImageToBase64(imagePath);
            if (base64Image == null || base64Image.isEmpty()) {
                callback.onError("Failed to encode image");
                return;
            }

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "qwen-vl");
            requestBody.put("image", base64Image);
            requestBody.put("task", "ocr");

            RequestBody body = RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "/v1/vision/ocr")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    isVisionAvailable = false;
                    callback.onError(classifyNetworkError(e));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        if (!response.isSuccessful()) {
                            callback.onError(classifyHttpError(response.code()));
                            return;
                        }
                        if (response.body() == null) {
                            callback.onError("Empty response body");
                            return;
                        }
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        String text = json.getString("text");
                        isVisionAvailable = true;
                        callback.onSuccess(text);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public boolean checkVisionAvailability() {
        if (apiBaseUrl.isEmpty() || apiKey.isEmpty()) {
            isVisionAvailable = false;
            return false;
        }
        try {
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "/v1/vision/models")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .get()
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    isVisionAvailable = false;
                }

                @Override
                public void onResponse(Call call, Response response) {
                    isVisionAvailable = response.isSuccessful();
                }
            });
        } catch (Exception e) {
            isVisionAvailable = false;
        }
        return isVisionAvailable;
    }

    public void checkAvailability() {
        if (apiBaseUrl.isEmpty() || apiKey.isEmpty()) {
            isAvailable = false;
            return;
        }
        try {
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "/v1/models")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .get()
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    isAvailable = false;
                }

                @Override
                public void onResponse(Call call, Response response) {
                    isAvailable = response.isSuccessful();
                }
            });
        } catch (Exception e) {
            isAvailable = false;
        }
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    private String encodeImageToBase64(String imagePath) {
        try {
            File file = new File(imagePath);
            if (!file.exists()) return null;
            if (file.length() > 20 * 1024 * 1024) return null;
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()];
                fis.read(bytes);
                return Base64.encodeToString(bytes, Base64.NO_WRAP);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject buildRequestJson(String prompt, InferenceParams params) throws Exception {
        JSONObject json = new JSONObject();
        json.put("prompt", prompt);
        json.put("max_tokens", params.getNPredict());
        json.put("temperature", params.getTemperature());
        json.put("top_p", params.getTopP());
        json.put("n", 1);
        if (params.getSystemPrompt() != null && !params.getSystemPrompt().isEmpty()) {
            json.put("system", params.getSystemPrompt());
        }
        if (!params.getStopTokens().isEmpty()) {
            JSONArray stops = new JSONArray();
            for (String stop : params.getStopTokens()) {
                stops.put(stop);
            }
            json.put("stop", stops);
        }
        return json;
    }
}
