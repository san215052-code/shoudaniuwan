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

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class StatusService extends Service {

    private static final String CHANNEL_ID = "audio_splitter_card_channel";
    private static final String ACTION_TOGGLE_MODE = "com.audiosplitter.ACTION_TOGGLE_MODE";
    private static final String ACTION_EMERGENCY_RESET = "com.audiosplitter.ACTION_EMERGENCY_RESET";
    private static final int PORT = 8080;

    private ServerSocket serverSocket;
    private boolean isServerRunning = false;
    private boolean isRootGranted = false;

    // 0: 白名单模式, 1: 黑名单模式, 2: 系统原生音频
    private int currentModeIndex = 0;
    private final String[] MODES = {"【白名单】", "【黑名单】", "【系统原生音频】"};

    // 广播接收器：处理通知栏点击与蓝牙断开事件
    private final BroadcastReceiver coreReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();

            if (ACTION_TOGGLE_MODE.equals(action)) {
                currentModeIndex = (currentModeIndex + 1) % MODES.length;
                applyModeSelection(currentModeIndex);

            } else if (ACTION_EMERGENCY_RESET.equals(action)) {
                currentModeIndex = 2;
                switchRoutingMode(2);
                updateNotificationCard("音频分流：【已紧急重置】", "已强行恢复系统原生音频状态");

            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                pauseMediaPlayback();
            }
        }
    };

    // 蓝牙断开自动暂停播放
    private void pauseMediaPlayback() {
        execRoot("input keyevent 127");
    }

    // 内置音频路由切换逻辑
    private void switchRoutingMode(int modeIndex) {
        switch (modeIndex) {
            case 0:
                execRoot("cmd audio set-force-use 1 1");
                break;
            case 1:
                execRoot("cmd audio set-force-use 1 0");
                break;
            case 2:
                execRoot("cmd audio set-force-use 0 0\ncmd audio set-force-use 1 0\ncmd audio set-force-use 2 0");
                break;
        }
    }

    // 内置低延迟 Root 指令执行器
    private void execRoot(String command) {
        new Thread(() -> {
            Process process = null;
            DataOutputStream os = null;
            try {
                process = Runtime.getRuntime().exec("su");
                os = new DataOutputStream(process.getOutputStream());
                os.writeBytes(command + "\n");
                os.writeBytes("exit\n");
                os.flush();
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (os != null) os.close();
                    if (process != null) process.destroy();
                } catch (Exception ignored) {}
            }
        }).start();
    }

    // 内置 Root 检查
    private boolean checkRoot() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {}
        }
    }

    private void applyModeSelection(int modeIndex) {
        String newMode = MODES[modeIndex];
        switchRoutingMode(modeIndex);
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

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOGGLE_MODE);
        filter.addAction(ACTION_EMERGENCY_RESET);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        // 使用通用最兼容广播注册 API（兼容所有 SDK 版本）
        registerReceiver(coreReceiver, filter);

        updateNotificationCard("音频分流：" + MODES[currentModeIndex], "点击卡片切换模式 | 8080 控制台启动中...");

        new Thread(() -> {
            isRootGranted = checkRoot();
            if (isRootGranted) {
                updateNotificationCard("音频分流：" + MODES[currentModeIndex], "点击卡片切换模式 | Root 授权成功");
            } else {
                updateNotificationCard("音频分流：警告", "未获取到 Root 权限，点击重试");
            }
        }).start();

        startNativeHttpServer();
    }

    // 内置轻量级 127.0.0.1:8080 Socket 服务器
    private void startNativeHttpServer() {
        isServerRunning = true;
        new Thread(() -> {
            try {
                InetAddress localAddr = InetAddress.getByName("127.0.0.1");
                serverSocket = new ServerSocket(PORT, 50, localAddr);

                while (isServerRunning && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    handleClientRequest(clientSocket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleClientRequest(Socket client) {
        new Thread(() -> {
            try {
                String json = "{"
                        + "\"status\":\"ok\","
                        + "\"modeIndex\":" + currentModeIndex + ","
                        + "\"modeName\":\"" + MODES[currentModeIndex] + "\","
                        + "\"root\":" + isRootGranted
                        + "}";

                byte[] body = json.getBytes("UTF-8");

                OutputStream out = client.getOutputStream();
                PrintWriter pw = new PrintWriter(out);

                pw.println("HTTP/1.1 200 OK");
                pw.println("Content-Type: application/json; charset=UTF-8");
                pw.println("Content-Length: " + body.length);
                pw.println("Access-Control-Allow-Origin: *");
                pw.println("Connection: close");
                pw.println();
                pw.flush();

                out.write(body);
                out.flush();

                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateNotificationCard(String title, String content) {
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) { // 23 = Android 6.0
            pendingFlags |= 0x04000000; // 对应 PendingIntent.FLAG_IMMUTABLE
        }

        Intent toggleIntent = new Intent(ACTION_TOGGLE_MODE);
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(this, 0, toggleIntent, pendingFlags);

        Intent resetIntent = new Intent(ACTION_EMERGENCY_RESET);
        PendingIntent resetPendingIntent = PendingIntent.getBroadcast(this, 1, resetIntent, pendingFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) { // 26 = Android 8.0
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        // 使用基类最原生的 addAction 方法
        Notification notification = builder
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(togglePendingIntent)
                .addAction(android.R.drawable.ic_menu_call, "🚨 紧急重置原生", resetPendingIntent)
                .setOngoing(true)
                .build();

        startForeground(1357, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) { // 26 = Android 8.0
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "音频分流模式控制", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        isServerRunning = false;
        try { unregisterReceiver(coreReceiver); } catch (Exception ignored) {}
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
                }
