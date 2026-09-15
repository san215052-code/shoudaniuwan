package com.audiosplitter.status;

import android.app.*;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.webkit.JavascriptInterface;
import androidx.core.app.NotificationCompat;

public class StatusService extends Service {

    private static final String CHANNEL_ID = "audio_splitter_card_channel";
    private boolean isRootGranted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        updateNotificationCard("音频分流服务启动中", "正在请求 Root 授权...");

        new Thread(() -> {
            isRootGranted = AudioEngine.requestRoot();
            if (isRootGranted) {
                updateNotificationCard("音频分流运行中", "Root 授权成功 - 零功耗监控");
            } else {
                updateNotificationCard("音频分流警告", "未获取到 Root 权限，请检查授权");
            }
        }).start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "音频分流常驻卡片", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void updateNotificationCard(String title, String content) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1357, notification);
    }

    // ------------------ JS Bridge 桥接给前端 7 大功能 ------------------
    @JavascriptInterface
    public boolean hasRootPermission() {
        if (!isRootGranted) isRootGranted = AudioEngine.requestRoot();
        return isRootGranted;
    }

    @JavascriptInterface
    public String executeCommand(String cmd) {
        if (!isRootGranted) return "Error: Root permission denied.";
        return AudioEngine.exec(cmd);
    }

    @JavascriptInterface
    public boolean getBluetoothStatus() {
        return AudioEngine.isBluetoothConnected();
    }

    @JavascriptInterface
    public void toggleAudioRoute(boolean enable) {
        if (enable) AudioEngine.applyRoute();
        else AudioEngine.resetRoute();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
