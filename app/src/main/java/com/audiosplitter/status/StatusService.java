package com.audiosplitter.status;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class StatusService extends Service {
    private static final String CHANNEL_ID = "AudioSplitterChannel";
    private static final int NOTIF_ID = 1001;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("正在读取音频状态..."));

        runnable = new Runnable() {
            @Override
            public void run() {
                String logData = readStatusLog();
                updateNotification(logData);
                handler.postDelayed(this, 10000);
            }
        };
        handler.post(runnable);
    }

    private String readStatusLog() {
        File file = new File("/tmp/audio_splitter.log");
        if (!file.exists()) return "分流服务未运行";
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            return line != null ? line : "状态未知";
        } catch (Exception e) {
            return "状态读取失败";
        }
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIF_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("音频分流控制台")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "音频分流常驻状态",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(runnable);
        super.onDestroy();
    }
}
