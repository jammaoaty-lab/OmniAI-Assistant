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
import com.omniai.assistant.lora.LoraTrainManager;

public class LoraTrainService extends Service {

    private static final String CHANNEL_ID = "omniai_lora_train_channel";
    private static final int NOTIFICATION_ID = 2001;

    private static final String ACTION_START_TRAINING = "com.omniai.assistant.ACTION_START_TRAINING";
    private static final String ACTION_PAUSE_TRAINING = "com.omniai.assistant.ACTION_PAUSE_TRAINING";
    private static final String ACTION_RESUME_TRAINING = "com.omniai.assistant.ACTION_RESUME_TRAINING";
    private static final String ACTION_STOP_TRAINING = "com.omniai.assistant.ACTION_STOP_TRAINING";

    private static final String EXTRA_DATA_PATH = "extra_data_path";
    private static final String EXTRA_OUTPUT_PATH = "extra_output_path";
    private static final String EXTRA_LORA_RANK = "extra_lora_rank";
    private static final String EXTRA_LORA_ALPHA = "extra_lora_alpha";
    private static final String EXTRA_LEARNING_RATE = "extra_learning_rate";
    private static final String EXTRA_EPOCHS = "extra_epochs";
    private static final String EXTRA_BATCH_SIZE = "extra_batch_size";
    private static final String EXTRA_DROPOUT = "extra_dropout";
    private static final String EXTRA_CONTEXT_LENGTH = "extra_context_length";

    private LoraTrainManager trainManager;
    private PowerManager.WakeLock wakeLock;
    private Thread progressThread;
    private volatile boolean isRunning;

    public static Intent createStartIntent(Context context, LoraTrainManager.TrainConfig config) {
        Intent intent = new Intent(context, LoraTrainService.class);
        intent.setAction(ACTION_START_TRAINING);
        intent.putExtra(EXTRA_DATA_PATH, config.getDataPath());
        intent.putExtra(EXTRA_OUTPUT_PATH, config.getOutputPath());
        intent.putExtra(EXTRA_LORA_RANK, config.getLoraRank());
        intent.putExtra(EXTRA_LORA_ALPHA, config.getLoraAlpha());
        intent.putExtra(EXTRA_LEARNING_RATE, config.getLearningRate());
        intent.putExtra(EXTRA_EPOCHS, config.getEpochs());
        intent.putExtra(EXTRA_BATCH_SIZE, config.getBatchSize());
        intent.putExtra(EXTRA_DROPOUT, config.getDropout());
        intent.putExtra(EXTRA_CONTEXT_LENGTH, config.getContextLength());
        return intent;
    }

    public static Intent createPauseIntent(Context context) {
        Intent intent = new Intent(context, LoraTrainService.class);
        intent.setAction(ACTION_PAUSE_TRAINING);
        return intent;
    }

    public static Intent createResumeIntent(Context context) {
        Intent intent = new Intent(context, LoraTrainService.class);
        intent.setAction(ACTION_RESUME_TRAINING);
        return intent;
    }

    public static Intent createStopIntent(Context context) {
        Intent intent = new Intent(context, LoraTrainService.class);
        intent.setAction(ACTION_STOP_TRAINING);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        trainManager = LoraTrainManager.getInstance();
        isRunning = false;
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_TRAINING.equals(action)) {
                LoraTrainManager.TrainConfig config = extractConfig(intent);
                startTraining(config);
            } else if (ACTION_PAUSE_TRAINING.equals(action)) {
                pauseTraining();
            } else if (ACTION_RESUME_TRAINING.equals(action)) {
                resumeTraining();
            } else if (ACTION_STOP_TRAINING.equals(action)) {
                stopTraining();
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
        stopTraining();
        releaseWakeLock();
        super.onDestroy();
    }

    public void startTraining(LoraTrainManager.TrainConfig config) {
        if (isRunning) return;
        isRunning = true;
        Notification notification = buildNotification("LoRA训练中 0%");
        startForeground(NOTIFICATION_ID, notification);
        trainManager.startTraining(config, new LoraTrainManager.LoraTrainListener() {
            @Override
            public void onStateChanged(LoraTrainManager.TrainState state) {
            }

            @Override
            public void onProgress(float progress) {
                updateNotification(progress);
            }

            @Override
            public void onLog(LoraTrainManager.TrainLogEntry entry) {
            }

            @Override
            public void onError(String error) {
                isRunning = false;
                stopForeground(true);
                stopSelf();
            }

            @Override
            public void onCompleted(String outputPath) {
                isRunning = false;
                stopForeground(true);
                stopSelf();
            }
        });
        startProgressMonitor();
    }

    public void pauseTraining() {
        trainManager.pauseTraining();
        Notification notification = buildNotification("LoRA训练已暂停");
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    public void resumeTraining() {
        trainManager.resumeTraining();
        startProgressMonitor();
    }

    public void stopTraining() {
        trainManager.stopTraining();
        isRunning = false;
        stopProgressMonitor();
        stopForeground(true);
        stopSelf();
    }

    private void startProgressMonitor() {
        stopProgressMonitor();
        progressThread = new Thread(() -> {
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    float progress = trainManager.getProgress();
                    updateNotification(progress);
                    if (progress >= 1.0f) {
                        break;
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        progressThread.start();
    }

    private void stopProgressMonitor() {
        if (progressThread != null) {
            progressThread.interrupt();
            progressThread = null;
        }
    }

    private void updateNotification(float progress) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int percent = (int) (progress * 100);
            Notification notification = buildNotification("LoRA训练中 " + percent + "%");
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private LoraTrainManager.TrainConfig extractConfig(Intent intent) {
        LoraTrainManager.TrainConfig config = new LoraTrainManager.TrainConfig();
        config.setDataPath(intent.getStringExtra(EXTRA_DATA_PATH));
        config.setOutputPath(intent.getStringExtra(EXTRA_OUTPUT_PATH));
        config.setLoraRank(intent.getIntExtra(EXTRA_LORA_RANK, 8));
        config.setLoraAlpha(intent.getFloatExtra(EXTRA_LORA_ALPHA, 16.0f));
        config.setLearningRate(intent.getFloatExtra(EXTRA_LEARNING_RATE, 1e-4f));
        config.setEpochs(intent.getIntExtra(EXTRA_EPOCHS, 3));
        config.setBatchSize(intent.getIntExtra(EXTRA_BATCH_SIZE, 4));
        config.setDropout(intent.getFloatExtra(EXTRA_DROPOUT, 0.05f));
        config.setContextLength(intent.getIntExtra(EXTRA_CONTEXT_LENGTH, 512));
        return config;
    }

    private Notification buildNotification(String contentText) {
        Intent stopIntent = createStopIntent(this);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        float progress = trainManager.getProgress();
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Senta AI")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_inference_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setProgress(100, (int) (progress * 100), progress == 0)
                .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "LoRA训练服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("LoRA训练后台运行状态");
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
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OmniAI::LoraTrainWakeLock");
            wakeLock.acquire(4 * 60 * 60 * 1000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }
}
