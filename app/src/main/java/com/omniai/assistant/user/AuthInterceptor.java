package com.omniai.assistant.user;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private static final long TOKEN_REFRESH_TIMEOUT_SECONDS = 10L;

    private UserManager userManager;
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int HTTP_UNAUTHORIZED = 401;

    public AuthInterceptor() {
    }

    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();

        Request.Builder builder = originalRequest.newBuilder();
        String token = userManager != null ? userManager.getAuthToken() : null;
        if (token != null && !token.isEmpty()) {
            builder.header(HEADER_AUTHORIZATION, BEARER_PREFIX + token);
        }

        Request authenticatedRequest = builder.build();
        Response response = chain.proceed(authenticatedRequest);

        if (response.code() == HTTP_UNAUTHORIZED && userManager != null) {
            synchronized (this) {
                String currentToken = userManager.getAuthToken();
                if (currentToken != null && currentToken.equals(token)) {
                    boolean refreshed = refreshTokenInternal();
                    if (refreshed) {
                        response.close();
                        Request newRequest = originalRequest.newBuilder()
                                .header(HEADER_AUTHORIZATION, BEARER_PREFIX + userManager.getAuthToken())
                                .build();
                        return chain.proceed(newRequest);
                    }
                }
            }
        }

        return response;
    }

    private boolean refreshTokenInternal() {
        if (userManager == null) {
            return false;
        }
        final boolean[] result = {false};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        userManager.refreshToken(new UserManager.RefreshCallback() {
            @Override
            public void onSuccess(String accessToken, String newRefreshToken, long expiry) {
                result[0] = true;
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                result[0] = false;
                latch.countDown();
            }
        });
        try {
            latch.await(TOKEN_REFRESH_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return result[0];
    }
}
