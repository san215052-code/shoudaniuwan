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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StatusService extends Service {

    private static final String CHANNEL_ID = "status_service_channel";
    private static final int NOTIF_ID = 1001;
    private static final int MAX_LOG_LINES = 200; // 降低内存和遍历开销
    
    private final Queue<String> logBuffer = new LinkedList<>();
    private ScheduledExecutorService scheduler;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("音频分流控制台")
                .setContentText("低功耗监控模式运行中...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN) // 最低优先级通知，减少系统打扰与渲染
                .build();
        
        startForeground(NOTIF_ID, notification);
        startLowPowerLogMonitoring();
    }

    private void startLowPowerLogMonitoring() {
        isRunning = true;
        // 使用单线程定时器代替无限死循环，每 2 秒批量提取一次日志，大幅降低 CPU 占用
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> {
            if (!isRunning) return;
            Process process = null;
            BufferedReader reader = null;
            try {
                // -t 100 仅抓取最近 100 条，避免全量日志导致 CPU/IO 飙升
                process = Runtime.getRuntime().exec("logcat -d -t 100 -v time *:E AudioSplitter:V");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                synchronized (logBuffer) {
                    logBuffer.clear(); // 保持最新快照即可
                    while ((line = reader.readLine()) != null) {
                        if (logBuffer.size() >= MAX_LOG_LINES) {
                            logBuffer.poll();
                        }
                        logBuffer.add(line);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (process != null) process.destroy();
                } catch (Exception ignored) {}
            }
        }, 0, 2, TimeUnit.SECONDS); // 2秒采集一次
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
                    "后台保活服务",
                    NotificationManager.IMPORTANCE_LOW // 低优先级无打扰
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
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        super.onDestroy();
    }
}
