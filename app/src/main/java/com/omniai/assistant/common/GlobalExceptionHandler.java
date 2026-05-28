package com.omniai.assistant.common;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.omniai.assistant.R;
import com.omniai.assistant.credits.CreditsManager;
import com.omniai.assistant.ui.credits.CreditsCenterActivity;

public class GlobalExceptionHandler {

    private static final String TAG = "GlobalExceptionHandler";

    private Context context;
    private CreditsManager creditsManager;
    private Handler mainHandler;

    private static volatile GlobalExceptionHandler instance;

    private Thread.UncaughtExceptionHandler defaultHandler;

    private GlobalExceptionHandler() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static GlobalExceptionHandler getInstance() {
        if (instance == null) {
            synchronized (GlobalExceptionHandler.class) {
                if (instance == null) {
                    instance = new GlobalExceptionHandler();
                }
            }
        }
        return instance;
    }

    public void init(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.creditsManager = CreditsManager.getInstance();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            handleUncaughtException(throwable);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    private void handleUncaughtException(Throwable throwable) {
        mainHandler.post(() -> {
            try {
                Toast.makeText(context, context.getString(R.string.error_app_crash), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.w(TAG, "Failed to show crash toast", e);
            }
        });
    }

    public void handleVisionException(Exception e, VisionErrorCallback callback) {
        String message = resolveVisionMessage(e);
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onVisionError(message);
            }
        });
    }

    public void handleCreditsException(Exception e, CreditsErrorCallback callback) {
        String message = resolveCreditsMessage(e);
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onCreditsError(message);
            }
        });
    }

    public void handleNetworkException(Exception e, NetworkErrorCallback callback) {
        String message = resolveNetworkMessage(e);
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onNetworkError(message);
            }
        });
    }

    public void handlePermissionException(String permission, PermissionErrorCallback callback) {
        String message = resolvePermissionMessage(permission);
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onPermissionError(message);
            }
        });
    }

    public void handleFileException(Exception e, FileErrorCallback callback) {
        String message = resolveFileMessage(e);
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onFileError(message);
            }
        });
    }

    public void handleModelException(Exception e, ModelErrorCallback callback) {
        String message = resolveModelMessage(e);
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onModelError(message);
            }
        });
    }

    public void showVisionErrorDialog(Context ctx, String message) {
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.error_vision_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    public void showCreditsErrorDialog(Context ctx, String message) {
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.error_credits_title)
                .setMessage(message)
                .setPositiveButton(R.string.credits_recharge, (dialog, which) -> {
                    Intent intent = new Intent(ctx, CreditsCenterActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void showNetworkErrorDialog(Context ctx, String message, Runnable retryAction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle(R.string.error_network_title)
                .setMessage(message);
        if (retryAction != null) {
            builder.setPositiveButton(R.string.retry, (dialog, which) -> retryAction.run());
        }
        builder.setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void showPermissionErrorDialog(Context ctx, String permission) {
        String message = resolvePermissionMessage(permission);
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.error_permission_title)
                .setMessage(message)
                .setPositiveButton(R.string.go_settings, (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(android.net.Uri.fromParts("package", ctx.getPackageName(), null));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String resolveVisionMessage(Exception e) {
        if (e == null || e.getMessage() == null) return context.getString(R.string.error_vision_default);
        String msg = e.getMessage().toLowerCase();
        if (msg.contains("format") || msg.contains("unsupported image")) {
            return context.getString(R.string.error_vision_format_unsupported);
        }
        if (msg.contains("corrupt") || msg.contains("damaged") || msg.contains("broken")) {
            return context.getString(R.string.error_vision_image_corrupted);
        }
        if (msg.contains("too large") || msg.contains("size") || msg.contains("oversize")) {
            return context.getString(R.string.error_vision_image_too_large);
        }
        if (msg.contains("load") && msg.contains("vision")) {
            return context.getString(R.string.error_vision_model_load_failed);
        }
        if (msg.contains("memory") || msg.contains("oom")) {
            return context.getString(R.string.error_vision_oom);
        }
        if (msg.contains("temperature") || msg.contains("thermal")) {
            return context.getString(R.string.error_vision_device_overheated);
        }
        if (msg.contains("camera") && msg.contains("permission")) {
            return context.getString(R.string.error_vision_camera_denied);
        }
        if (msg.contains("storage") && msg.contains("permission")) {
            return context.getString(R.string.error_vision_storage_denied);
        }
        if (msg.contains("download") && (msg.contains("interrupt") || msg.contains("cancel"))) {
            return context.getString(R.string.error_vision_download_interrupted);
        }
        if (msg.contains("hash") || msg.contains("checksum") || msg.contains("verify")) {
            return context.getString(R.string.error_vision_file_verify_failed);
        }
        return context.getString(R.string.error_vision_default);
    }

    private String resolveCreditsMessage(Exception e) {
        if (e == null || e.getMessage() == null) return context.getString(R.string.error_credits_default);
        String msg = e.getMessage().toLowerCase();
        if (msg.contains("invite") && (msg.contains("format") || msg.contains("invalid"))) {
            return context.getString(R.string.error_credits_invite_format);
        }
        if (msg.contains("invite") && (msg.contains("expired") || msg.contains("used"))) {
            return context.getString(R.string.error_credits_invite_expired);
        }
        if (msg.contains("recharge") && msg.contains("fail")) {
            return context.getString(R.string.error_credits_recharge_failed);
        }
        if (msg.contains("payment") && (msg.contains("interrupt") || msg.contains("cancel"))) {
            return context.getString(R.string.error_credits_payment_interrupted);
        }
        if (msg.contains("insufficient") || msg.contains("not enough")) {
            return context.getString(R.string.error_credits_insufficient);
        }
        if (msg.contains("load") && msg.contains("credits")) {
            return context.getString(R.string.error_credits_load_failed);
        }
        return context.getString(R.string.error_credits_default);
    }

    private String resolveNetworkMessage(Exception e) {
        if (e == null || e.getMessage() == null) return context.getString(R.string.error_network_connection);
        String msg = e.getMessage().toLowerCase();
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return context.getString(R.string.error_network_timeout);
        }
        if (msg.contains("500") || msg.contains("server") || msg.contains("502") || msg.contains("503")) {
            return context.getString(R.string.error_network_server);
        }
        return context.getString(R.string.error_network_connection);
    }

    private String resolvePermissionMessage(String permission) {
        if (permission == null) return context.getString(R.string.error_permission_default);
        String perm = permission.toLowerCase();
        if (perm.contains("camera")) {
            return context.getString(R.string.error_vision_camera_denied);
        }
        if (perm.contains("storage") || perm.contains("read_external") || perm.contains("write_external")) {
            return context.getString(R.string.error_vision_storage_denied);
        }
        if (perm.contains("record_audio") || perm.contains("microphone")) {
            return context.getString(R.string.error_permission_microphone);
        }
        return context.getString(R.string.error_permission_default);
    }

    private String resolveFileMessage(Exception e) {
        if (e == null || e.getMessage() == null) return context.getString(R.string.error_file_default);
        String msg = e.getMessage().toLowerCase();
        if (msg.contains("not found") || msg.contains("enoent")) {
            return context.getString(R.string.error_file_not_found);
        }
        if (msg.contains("permission") || msg.contains("eacces")) {
            return context.getString(R.string.error_vision_storage_denied);
        }
        if (msg.contains("space") || msg.contains("full")) {
            return context.getString(R.string.error_file_no_space);
        }
        return context.getString(R.string.error_file_default);
    }

    private String resolveModelMessage(Exception e) {
        if (e == null || e.getMessage() == null) return context.getString(R.string.error_model_load_failed);
        String msg = e.getMessage().toLowerCase();
        if (msg.contains("load") || msg.contains("initialize")) {
            return context.getString(R.string.error_model_load_failed);
        }
        if (msg.contains("corrupt") || msg.contains("damaged")) {
            return context.getString(R.string.error_model_corrupted);
        }
        if (msg.contains("memory") || msg.contains("oom")) {
            return context.getString(R.string.error_memory_low);
        }
        if (msg.contains("version") || msg.contains("incompatible")) {
            return context.getString(R.string.error_version_incompatible);
        }
        return context.getString(R.string.error_model_load_failed);
    }

    public interface VisionErrorCallback {
        void onVisionError(String message);
    }

    public interface CreditsErrorCallback {
        void onCreditsError(String message);
    }

    public interface NetworkErrorCallback {
        void onNetworkError(String message);
    }

    public interface PermissionErrorCallback {
        void onPermissionError(String message);
    }

    public interface FileErrorCallback {
        void onFileError(String message);
    }

    public interface ModelErrorCallback {
        void onModelError(String message);
    }
}
