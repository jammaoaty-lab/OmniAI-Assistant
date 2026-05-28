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
import androidx.core.app.NotificationCompat;

import com.omniai.assistant.R;
import com.omniai.assistant.ui.terminal.TerminalActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LlamaCppService extends Service {

    private static final String TAG = "LlamaCppService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "llama_service_channel";
    private static final String CHANNEL_NAME = "LlamaCpp Background Service";
    
    private static boolean isRunning = false;
    private List<Process> runningProcesses = new ArrayList<>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    public static void start(Context context) {
        Intent intent = new Intent(context, LlamaCppService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        isRunning = true;
    }
    
    public static void stop(Context context) {
        context.stopService(new Intent(context, LlamaCppService.class));
        isRunning = false;
    }
    
    public static boolean isServiceRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        stopAllProcesses();
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("LlamaCpp background inference service");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, TerminalActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_settings)
                .setContentTitle("Senta AI LlamaCpp")
                .setContentText("LlamaCpp is running in background")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
    
    private void stopAllProcesses() {
        for (Process process : runningProcesses) {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        runningProcesses.clear();
    }
    
    public void addProcess(Process process) {
        if (process != null) {
            runningProcesses.add(process);
        }
    }
    
    public void removeProcess(Process process) {
        if (process != null) {
            runningProcesses.remove(process);
        }
    }
}
