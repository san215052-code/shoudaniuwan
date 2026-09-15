package com.audiosplitter.status;

import android.app.*;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.webkit.JavascriptInterface;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;

public class StatusService extends Service {

    private static final String CHANNEL_ID = "status_service_channel";
    private final Queue<String> logBuffer = new LinkedList<>();
    private ScheduledExecutorService scheduler;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("音频分流控制台")
                .setContentText("后台低功耗运行中...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();

        startForeground(1001, notification);

        // 每 2 秒批量提取 Logcat 50 条快照，极大减少 IO 与 CPU 开销
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(this::fetchLogcat, 0, 2, TimeUnit.SECONDS);
    }

    private void fetchLogcat() {
        try {
            Process p = Runtime.getRuntime().exec("logcat -d -t 50 -v time");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                synchronized (logBuffer) {
                    logBuffer.clear();
                    while ((line = reader.readLine()) != null) {
                        logBuffer.add(line);
                    }
                }
            }
            p.destroy();
        } catch (Exception ignored) {}
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

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, "状态服务", NotificationManager.IMPORTANCE_LOW));
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
        if (scheduler != null) scheduler.shutdownNow();
        super.onDestroy();
    }
}
