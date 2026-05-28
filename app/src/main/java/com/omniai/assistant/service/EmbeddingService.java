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
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.omniai.assistant.R;
import com.omniai.assistant.knowledge.EmbeddingEngine;
import com.omniai.assistant.knowledge.KnowledgeBaseManager;

public class EmbeddingService extends Service {

    private static final String CHANNEL_ID = "omniai_embedding_channel";
    private static final int NOTIFICATION_ID = 3001;

    private static final String ACTION_START_EMBEDDING = "com.omniai.assistant.ACTION_START_EMBEDDING";
    private static final String ACTION_STOP_EMBEDDING = "com.omniai.assistant.ACTION_STOP_EMBEDDING";

    private static final String EXTRA_KB_ID = "extra_kb_id";

    private KnowledgeBaseManager kbManager;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean isRunning;

    public static Intent createStartIntent(Context context, String kbId) {
        Intent intent = new Intent(context, EmbeddingService.class);
        intent.setAction(ACTION_START_EMBEDDING);
        intent.putExtra(EXTRA_KB_ID, kbId);
        return intent;
    }

    public static Intent createStopIntent(Context context) {
        Intent intent = new Intent(context, EmbeddingService.class);
        intent.setAction(ACTION_STOP_EMBEDDING);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        kbManager = KnowledgeBaseManager.getInstance();
        isRunning = false;
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_EMBEDDING.equals(action)) {
                String kbId = intent.getStringExtra(EXTRA_KB_ID);
                startEmbedding(kbId);
            } else if (ACTION_STOP_EMBEDDING.equals(action)) {
                stopEmbedding();
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
        stopEmbedding();
        releaseWakeLock();
        super.onDestroy();
    }

    public void startEmbedding(String kbId) {
        if (isRunning) return;
        isRunning = true;
        Notification notification = buildNotification("Embedding生成中");
        startForeground(NOTIFICATION_ID, notification);
        new Thread(() -> {
            try {
                kbManager.buildIndex(kbId);
            } catch (Exception e) {
                stopEmbedding();
            }
            isRunning = false;
            stopForeground(true);
            stopSelf();
        }).start();
    }

    public void stopEmbedding() {
        isRunning = false;
        EmbeddingEngine engine = kbManager.getEmbeddingEngine();
        if (engine != null && engine.isInitialized()) {
            engine.shutdown();
        }
        stopForeground(true);
        stopSelf();
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
                    "Embedding生成服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Embedding向量生成后台运行状态");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OmniAI::EmbeddingWakeLock");
            wakeLock.acquire(2 * 60 * 60 * 1000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }
}
