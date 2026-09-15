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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class StatusService extends Service {

    private static final String CHANNEL_ID = "audio_splitter_card_channel";
    private static final String ACTION_TOGGLE_MODE = "com.audiosplitter.ACTION_TOGGLE_MODE";
    private static final String ACTION_EMERGENCY_RESET = "com.audiosplitter.ACTION_EMERGENCY_RESET";
    private static final int PORT = 8080;
    
    private HttpServer httpServer;
    private boolean isRootGranted = false;

    // 模式管理：0-白名单模式, 1-黑名单模式, 2-系统原生音频
    private int currentModeIndex = 0;
    private final String[] MODES = {"【白名单】", "【黑名单】", "【系统原生音频】"};

    // 监听广播：包含模式切换、紧急重置以及蓝牙断开事件
    private final BroadcastReceiver coreReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_TOGGLE_MODE.equals(action)) {
                // 1. 轮流切换模式：白名单 -> 黑名单 -> 系统原生音频
                currentModeIndex = (currentModeIndex + 1) % MODES.length;
                applyModeSelection(currentModeIndex);

            } else if (ACTION_EMERGENCY_RESET.equals(action)) {
                // 2. 紧急重置：回归系统原生音频并重置路由
                currentModeIndex = 2;
                AudioEngine.switchRoutingMode(2);
                updateNotificationCard("音频分流：【已紧急重置】", "已强行恢复系统原生音频状态");

            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                // 3. 核心新增：监听到蓝牙断开连接，立即暂停手机播放的声音
                pauseMediaPlayback();
            }
        }
    };

    // 暂停全系统媒体播放（通过 Root 执行媒体按键模拟，无需额外权限）
    private void pauseMediaPlayback() {
        new Thread(() -> {
            // 发送 KEYEVENT_MEDIA_PAUSE (127) 或 KEYEVENT_MEDIA_PLAY_PAUSE (85)
            AudioEngine.executeRootCommand("input keyevent 127");
        }).start();
    }

    private void applyModeSelection(int modeIndex) {
        String newMode = MODES[modeIndex];
        AudioEngine.switchRoutingMode(modeIndex);
        if (modeIndex == 2) {
            updateNotificationCard("音频分流：" + newMode, "已回归系统原生音频，使用手机默认路径");
        } else {
            updateNotificationCard("音频分流：" + newMode, "点击卡片切换模式 | 8080 控制台就绪");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // 注册控制与蓝牙断开监听广播
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOGGLE_MODE);
        filter.addAction(ACTION_EMERGENCY_RESET);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED); // 蓝牙断开事件

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(coreReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(coreReceiver, filter);
        }

        updateNotificationCard("音频分流：" + MODES[currentModeIndex], "点击卡片切换模式 | 8080 控制台启动中...");

        new Thread(() -> {
            isRootGranted = AudioEngine.requestRoot();
            if (isRootGranted) {
                updateNotificationCard("音频分流：" + MODES[currentModeIndex], "点击卡片切换模式 | Root 授权成功");
            } else {
                updateNotificationCard("音频分流：警告", "未获取到 Root 权限，点击重试");
            }
        }).start();

        startLocalHttpServer();
    }

    // 渲染带有【紧急恢复原生】按钮的悬浮卡片
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
                .setContentIntent(togglePendingIntent) // 点击卡片主体切换模式
                .addAction(android.R.drawable.ic_menu_call, "🚨 紧急重置原生", resetPendingIntent) // 状态栏紧急独立按钮
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1357, notification);
    }

    private void startLocalHttpServer() {
        new Thread(() -> {
            try {
                InetSocketAddress address = new InetSocketAddress("127.0.0.1", PORT);
                httpServer = HttpServer.create(address, 0);

                httpServer.createContext("/", new HttpHandler() {
                    @Override
                    public void handle(HttpExchange exchange) throws IOException {
                        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

                        String responseJson = "{"
                                + "\"status\":\"ok\","
                                + "\"modeIndex\":" + currentModeIndex + ","
                                + "\"modeName\":\"" + MODES[currentModeIndex] + "\","
                                + "\"root\":" + isRootGranted
                                + "}";

                        byte[] respBytes = responseJson.getBytes("UTF-8");
                        exchange.sendResponseHeaders(200, respBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(respBytes);
                        os.close();
                    }
                });

                httpServer.setExecutor(null);
                httpServer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
        if (httpServer != null) httpServer.stop(0);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
