package com.omniai.assistant.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.omniai.assistant.R;
import com.omniai.assistant.cloud.CloudFallbackManager;
import com.omniai.assistant.inference.InferenceEngine;
import com.omniai.assistant.inference.ThermalMonitor;
import com.omniai.assistant.scheduler.AIScheduler;
import com.omniai.assistant.scheduler.InferenceParams;

public class InferenceService extends Service {

    private static final String CHANNEL_ID = "omniai_inference_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static final String ACTION_START_INFERENCE = "com.omniai.assistant.ACTION_START_INFERENCE";
    private static final String ACTION_STOP_INFERENCE = "com.omniai.assistant.ACTION_STOP_INFERENCE";

    private AIScheduler scheduler;
    private InferenceEngine inferenceEngine;
    private ThermalMonitor thermalMonitor;
    private CloudFallbackManager fallbackManager;
    private boolean isInferenceRunning;

    public static Intent createStartIntent(Context context) {
        Intent intent = new Intent(context, InferenceService.class);
        intent.setAction(ACTION_START_INFERENCE);
        return intent;
    }

    public static Intent createStopIntent(Context context) {
        Intent intent = new Intent(context, InferenceService.class);
        intent.setAction(ACTION_STOP_INFERENCE);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        scheduler = AIScheduler.getInstance();
        inferenceEngine = InferenceEngine.getInstance();
        thermalMonitor = ThermalMonitor.getInstance();
        fallbackManager = CloudFallbackManager.getInstance();
        isInferenceRunning = false;
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_INFERENCE.equals(action)) {
                startInference();
            } else if (ACTION_STOP_INFERENCE.equals(action)) {
                stopInference();
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopInference();
        super.onDestroy();
    }

    public void startInference() {
        if (isInferenceRunning) return;
        isInferenceRunning = true;
        thermalMonitor.startMonitoring();
        Notification notification = buildNotification(getNotificationContent());
        startForeground(NOTIFICATION_ID, notification);
    }

    public void stopInference() {
        if (!isInferenceRunning) return;
        isInferenceRunning = false;
        thermalMonitor.stopMonitoring();
        inferenceEngine.abortCompletion();
        stopForeground(true);
        stopSelf();
    }

    private String getNotificationContent() {
        AIScheduler.InferenceMode mode = scheduler.getCurrentMode();
        switch (mode) {
            case LOCAL:
                if (inferenceEngine.isGpuAvailable()) {
                    return "GPU加速已启用";
                }
                return "本地模型运行中";
            case CLOUD:
                return "云端推理已接管";
            case HYBRID:
                if (fallbackManager.isCloudActive()) {
                    return "云端推理已接管";
                }
                if (inferenceEngine.isGpuAvailable()) {
                    return "GPU加速已启用";
                }
                return "本地模型运行中";
            default:
                return "本地模型运行中";
        }
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Notification notification = buildNotification(getNotificationContent());
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String contentText) {
        Intent stopIntent = createStopIntent(this);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Senta AI")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_inference_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AI推理服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Senta AI推理引擎后台运行状态");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
