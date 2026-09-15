package com.audiosplitter.status;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.webkit.JavascriptInterface;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatusService extends Service {

    private static final String CHANNEL_ID = "status_service_channel";
    private static final int NOTIF_ID = 1001;
    private static final int MAX_LOG_LINES = 500;
    
    private final Queue<String> logBuffer = new LinkedList<>();
    private ExecutorService executorService;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("音频分流控制台")
                .setContentText("后台日志监控及服务运行中...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        
        startForeground(NOTIF_ID, notification);
        executorService = Executors.newSingleThreadExecutor();
        startLogMonitoring();
    }

    private void startLogMonitoring() {
        isRunning = true;
        executorService.execute(() -> {
            Process process = null;
            BufferedReader reader = null;
            try {
                process = Runtime.getRuntime().exec("logcat -v time");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    synchronized (logBuffer) {
                        if (logBuffer.size() >= MAX_LOG_LINES) {
                            logBuffer.poll();
                        }
                        logBuffer.add(line);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (process != null) process.destroy();
                } catch (Exception ignored) {}
            }
        });
    }

    @JavascriptInterface
    public String getRecentLogs() {
        synchronized (logBuffer) {
            StringBuilder sb = new StringBuilder();
            for (String log : logBuffer) {
                sb.append(log).append("\n");
            }
            return sb.toString();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "后台状态服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        super.onDestroy();
    }
}
