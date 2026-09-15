package com.audiosplitter.status;

import android.app.*;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.webkit.JavascriptInterface;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;

public class StatusService extends Service {

    private static final String CHANNEL_ID = "bt_audio_service_channel";
    private final Queue<String> btLogBuffer = new LinkedList<>();
    private ScheduledExecutorService scheduler;
    private boolean isRootGranted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("蓝牙音频分流控制")
                .setContentText(" Root 模式已启用，模块监控中...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();

        startForeground(1001, notification);

        // 初始化时异步请求 Root 权限
        Executors.newSingleThreadExecutor().execute(this::checkAndRequestRoot);

        // 每 2 秒拉取一次模块状态或 Logcat
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(this::fetchAudioBtLogs, 0, 2, TimeUnit.SECONDS);
    }

    /**
     * 检查并请求 Root 权限
     */
    private void checkAndRequestRoot() {
        isRootGranted = executeRootCommand("echo root_ok").contains("root_ok");
    }

    /**
     * 执行 Root Shell 命令的通用工具函数
     */
    private String executeRootCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            os.writeBytes(command + "\nexit\n");
            os.flush();

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            p.waitFor();
            p.destroy();
        } catch (Exception e) {
            output.append("Root Exec Error: ").append(e.getMessage());
        }
        return output.toString().trim();
    }

    private void fetchAudioBtLogs() {
        try {
            // 使用 su 执行 Logcat 命令，获取包含底层模块的完整日志
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "logcat -d -t 40 *:S Bluetooth:V AudioTrack:V AudioService:V"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                synchronized (btLogBuffer) {
                    btLogBuffer.clear();
                    while ((line = reader.readLine()) != null) {
                        btLogBuffer.add(line);
                    }
                }
            }
            p.destroy();
        } catch (Exception ignored) {}
    }

    // ------------------ JS Bridge 供网页前端调用的接口 ------------------

    // 1. 检查是否成功获取 Root
    @JavascriptInterface
    public boolean hasRootPermission() {
        return isRootGranted;
    }

    // 2. 供 Web 控制台直接向音频模块发送 Root 指令（如调用模块控制脚本）
    @JavascriptInterface
    public String runModuleCommand(String cmd) {
        if (!isRootGranted) {
            checkAndRequestRoot();
        }
        return executeRootCommand(cmd);
    }

    // 3. 获取音频与蓝牙日志
    @JavascriptInterface
    public String getAudioBtLogs() {
        synchronized (btLogBuffer) {
            StringBuilder sb = new StringBuilder();
            for (String log : btLogBuffer) {
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
                        CHANNEL_ID, "蓝牙音频控制服务", NotificationManager.IMPORTANCE_LOW));
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
