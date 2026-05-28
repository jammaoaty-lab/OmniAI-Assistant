package com.omniai.assistant.modelmgmt;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.SplitInstallSessionState;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;

public class DynamicFeatureManager {

    private static final String TAG = "DynamicFeatureManager";
    private static final String MODEL_FEATURE = "model_feature";

    private static volatile DynamicFeatureManager instance;
    private final Context context;
    private final SplitInstallManager splitInstallManager;
    private final Handler mainHandler;
    private FeatureInstallCallback installCallback;
    private int currentSessionId = -1;

    public interface FeatureInstallCallback {
        void onDownloadProgress(int progress);
        void onInstalling();
        void onInstalled();
        void onFailed(String error);
        void onRequiresUserConfirmation();
    }

    private final SplitInstallStateUpdatedListener stateListener =
        state -> {
            if (state.sessionId() != currentSessionId) return;

            int status = state.status();
            switch (status) {
                case SplitInstallSessionStatus.PENDING:
                    break;
                case SplitInstallSessionStatus.DOWNLOADING:
                    int progress = (int) (state.bytesDownloaded() * 100 / state.totalBytesToDownload());
                    if (installCallback != null) {
                        installCallback.onDownloadProgress(progress);
                    }
                    break;
                case SplitInstallSessionStatus.INSTALLING:
                    if (installCallback != null) {
                        installCallback.onInstalling();
                    }
                    break;
                case SplitInstallSessionStatus.INSTALLED:
                    if (installCallback != null) {
                        installCallback.onInstalled();
                    }
                    unregisterListener();
                    break;
                case SplitInstallSessionStatus.FAILED:
                    String error = "安装失败，错误码: " + state.errorCode();
                    if (installCallback != null) {
                        installCallback.onFailed(error);
                    }
                    unregisterListener();
                    break;
                case SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION:
                    if (installCallback != null) {
                        installCallback.onRequiresUserConfirmation();
                    }
                    break;
            }
        };

    private DynamicFeatureManager(Context context) {
        this.context = context.getApplicationContext();
        this.splitInstallManager = SplitInstallManagerFactory.create(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized DynamicFeatureManager getInstance(Context context) {
        if (instance == null) {
            instance = new DynamicFeatureManager(context);
        }
        return instance;
    }

    public void setInstallCallback(FeatureInstallCallback callback) {
        this.installCallback = callback;
    }

    public boolean isFeatureInstalled() {
        return splitInstallManager.getInstalledModules().contains(MODEL_FEATURE);
    }

    public void installFeature() {
        if (isFeatureInstalled()) {
            if (installCallback != null) {
                installCallback.onInstalled();
            }
            return;
        }

        SplitInstallRequest request = SplitInstallRequest.newBuilder()
                .addModule(MODEL_FEATURE)
                .build();

        splitInstallManager.registerListener(stateListener);

        splitInstallManager.startInstall(request)
                .addOnSuccessListener(sessionId -> {
                    currentSessionId = sessionId;
                })
                .addOnFailureListener(e -> {
                    String error = "启动安装失败: " + e.getMessage();
                    if (installCallback != null) {
                        mainHandler.post(() -> installCallback.onFailed(error));
                    }
                    unregisterListener();
                });
    }

    public void cancelInstall() {
        if (currentSessionId != -1) {
            splitInstallManager.cancelInstall(currentSessionId);
            unregisterListener();
            currentSessionId = -1;
        }
    }

    public Context getFeatureContext() {
        try {
            return context.createPackageContext(
                    context.getPackageName(),
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY
            );
        } catch (PackageManager.NameNotFoundException e) {
            return context;
        }
    }

    private void unregisterListener() {
        try {
            splitInstallManager.unregisterListener(stateListener);
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister split install listener", e);
        }
    }

    public void destroy() {
        unregisterListener();
        installCallback = null;
    }
}
