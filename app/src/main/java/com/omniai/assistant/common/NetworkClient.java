package com.omniai.assistant.common;

import com.omniai.assistant.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;

public class NetworkClient {

    private static volatile OkHttpClient secureClient;

    private static final String[] PINNED_DOMAINS = {
            java.net.URI.create(BuildConfig.API_BASE_URL).getHost()
    };

    private static final String[] SHA256_PINS = {
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    };

    private NetworkClient() {}

    public static OkHttpClient getSecureClient() {
        if (secureClient == null) {
            synchronized (NetworkClient.class) {
                if (secureClient == null) {
                    CertificatePinner certificatePinner = buildCertificatePinner();
                    secureClient = new OkHttpClient.Builder()
                            .certificatePinner(certificatePinner)
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return secureClient;
    }

    public static OkHttpClient.Builder getSecureBuilder() {
        return getSecureClient().newBuilder();
    }

    private static CertificatePinner buildCertificatePinner() {
        CertificatePinner.Builder builder = new CertificatePinner.Builder();
        for (String domain : PINNED_DOMAINS) {
            for (String pin : SHA256_PINS) {
                builder.add(domain, pin);
            }
        }
        return builder.build();
    }

    public static void updatePins(String[] pins) {
        synchronized (NetworkClient.class) {
            CertificatePinner.Builder builder = new CertificatePinner.Builder();
            for (String domain : PINNED_DOMAINS) {
                for (String pin : pins) {
                    builder.add(domain, pin);
                }
            }
            secureClient = getSecureClient().newBuilder()
                    .certificatePinner(builder.build())
                    .build();
        }
    }
}
