package com.omniai.assistant.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

public final class NetworkUtil {

    private NetworkUtil() {
        throw new UnsupportedOperationException("NetworkUtil cannot be instantiated");
    }

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) {
            return false;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            );
        } else {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }

    public static boolean isWifiConnected(Context context) {
        if (context == null) {
            return false;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected() && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        }
    }

    public static String getNetworkType(Context context) {
        if (context == null) {
            return "unknown";
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return "unknown";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return "none";
            }
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities == null) {
                return "none";
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return "wifi";
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return "ethernet";
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return "vpn";
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return "cellular";
            }
            return "unknown";
        } else {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo == null || !networkInfo.isConnected()) {
                return "none";
            }
            switch (networkInfo.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    return "wifi";
                case ConnectivityManager.TYPE_ETHERNET:
                    return "ethernet";
                case ConnectivityManager.TYPE_VPN:
                    return "vpn";
                case ConnectivityManager.TYPE_MOBILE:
                    return "cellular";
                default:
                    return "unknown";
            }
        }
    }
}
