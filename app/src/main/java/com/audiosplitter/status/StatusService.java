package com.audiosplitter.status;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;

public class StatusService extends Service {

    private static final String CHANNEL_ID = "audio_splitter_card_channel";
    private static final String ACTION_TOGGLE_MODE = "com.audiosplitter.ACTION_TOGGLE_MODE";
    private static final String ACTION_EMERGENCY_RESET = "com.audiosplitter.ACTION_EMERGENCY_RESET";
    private static final int PORT = 8080;

    private LocalHttpServer httpServer;
    private boolean isRootGranted = false;

    // 0: 白名单模式, 1: 黑名单模式, 2: 系统原生音频
    private int currentModeIndex = 0;
    private final String[] MODES = {"【白名单】", "【黑名单】", "【系统原生音频】"};

    // 接收通知栏卡片点击及蓝牙广播
    private final BroadcastReceiver coreReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_TOGGLE_MODE.equals(action)) {
                // 轮询切换模式
                currentModeIndex = (currentModeIndex + 1) % MODES.length;
                applyModeSelection(currentModeIndex);

            } else if (ACTION_EMERGENCY_RESET.equals(action)) {
                // 点击通知栏“紧急重置”
                currentModeIndex = 2;
                AudioEngine.switchRoutingMode(2);
                updateNotificationCard("音频分流：【已紧急重置】", "已强行恢复系统原生音频状态");

            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                // 蓝牙断开连接，自动发送媒体暂停指令
                pauseMediaPlayback();
            }
        }
    };

    private void pauseMediaPlayback() {
        new Thread(() -> {
            AudioEngine.executeRootCommand("input keyevent 127"); // KEYCODE_MEDIA_PAUSE
        }).start();
    }

    private void applyModeSelection(int modeIndex) {
        String newMode = MODES[modeIndex];
        AudioEngine.switchRoutingMode(modeIndex);
        if (modeIndex == 2) {
            updateNotificationCard("音频分流：" + newMode, "已回归系统原生音频，使用手机默认路径");
        } else {
            updateNotificationCard("音频分流：" + newMode, "点击卡片切换模式 | 127.0.0.1:8080 就绪");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOGGLE_MODE);
        filter.addAction(ACTION_EMERGENCY_RESET);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(coreReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(coreReceiver, filter);
        }

        updateNotificationCard("音频分流：" + MODES[currentModeIndex], "点击卡片切换模式 | 8080 控制台启动中...");

        // 异步检查 Root 状态
        new Thread(() -> {
            isRootGranted = AudioEngine.requestRoot();
            if (isRootGranted) {
                updateNotificationCard("音频分流：" + MODES[currentModeIndex], "点击卡片切换模式 | Root 授权成功");
            } else {
                updateNotificationCard("音频分流：警告", "未获取到 Root 权限，点击重试");
            }
        }).start();

        // 启动轻量级 NanoHTTPD 服务器
        startLocalHttpServer();
    }

    private void startLocalHttpServer() {
        try {
            httpServer = new LocalHttpServer(PORT);
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // NanoHTTPD 实现类 (绑定 127.0.0.1)
    private class LocalHttpServer extends NanoHTTPD {
        public LocalHttpServer(int port) {
            super("127.0.0.1", port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String json = "{"
                    + "\"status\":\"ok\","
                    + "\"modeIndex\":" + currentModeIndex + ","
                    + "\"modeName\":\"" + MODES[currentModeIndex] + "\","
                    + "\"root\":" + isRootGranted
                    + "}";

            Response res = newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", json);
            res.addHeader("Access-Control-Allow-Origin", "*");
            return res;
        }
    }

    private void updateNotificationCard(String title, String content) {
        Intent toggleIntent = new Intent(ACTION_TOGGLE_MODE);
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(
                this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent resetIntent = new Intent(ACTION_EMERGENCY_RESET);
        PendingIntent resetPendingIntent = PendingIntent.getBroadcast(
                this, 1, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(togglePendingIntent)
                .addAction(android.R.drawable.ic_menu_call, "🚨 紧急重置原生", resetPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1357, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "音频分流模式控制", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(coreReceiver); } catch (Exception ignored) {}
        if (httpServer != null) httpServer.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
