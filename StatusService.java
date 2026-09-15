package com.audiosplitter.status;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class StatusService extends Service implements BluetoothManagerModule.BluetoothStateListener {

    private static final String CHANNEL_ID = "audio_splitter_card_channel";
    public static final String ACTION_TOGGLE_MODE = "com.audiosplitter.ACTION_TOGGLE_MODE";
    public static final String ACTION_EMERGENCY_RESET = "com.audiosplitter.ACTION_EMERGENCY_RESET";
    private static final int PORT = 8080;

    private ServerSocket serverSocket;
    private boolean isServerRunning = false;
    private boolean isRootGranted = false;

    private BluetoothManagerModule bluetoothModule;

    // 0: 白名单模式, 1: 黑名单模式, 2: 系统原生音频
    private int currentModeIndex = 0;
    private final String[] MODES = {"【白名单模式】", "【黑名单模式】", "【系统原生音频】"};

    private final BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();

            if (ACTION_TOGGLE_MODE.equals(action)) {
                currentModeIndex = (currentModeIndex + 1) % MODES.length;
                applyModeSelection(currentModeIndex);

            } else if (ACTION_EMERGENCY_RESET.equals(action)) {
                currentModeIndex = 2;
                AudioEngine.switchRoutingMode(2);
                updateNotificationCard("音频分流：【已紧急重置】", "已强行恢复系统原生音频状态");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // 1. 初始化并注册蓝牙监听模块
        bluetoothModule = new BluetoothManagerModule(this, this);
        bluetoothModule.register();

        // 2. 注册服务广播
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOGGLE_MODE);
        filter.addAction(ACTION_EMERGENCY_RESET);
        registerReceiver(serviceReceiver, filter);

        updateNotificationCard("音频分流：" + MODES[currentModeIndex], "点击卡片切换模式 | 8080 控制台就绪");

        // 3. 异步检测 Root 状态
        new Thread(() -> {
            isRootGranted = AudioEngine.checkRootPermission();
            refreshNotification();
        }).start();

        // 4. 启动原生 127.0.0.1:8080 HTTP 服务
        startNativeHttpServer();
    }

    private void applyModeSelection(int modeIndex) {
        String newMode = MODES[modeIndex];
        AudioEngine.switchRoutingMode(modeIndex);
        refreshNotification();
    }

    private void refreshNotification() {
        String statusText = "蓝牙: " + bluetoothModule.getLastConnectedDeviceName() 
                + " | Root: " + (isRootGranted ? "已授权" : "未授权");
        updateNotificationCard("音频分流：" + MODES[currentModeIndex], statusText);
    }

    // --- 蓝牙事件回调实现 ---
    @Override
    public void onBluetoothDeviceConnected(String deviceName) {
        refreshNotification();
    }

    @Override
    public void onBluetoothDeviceDisconnected(String deviceName) {
        refreshNotification();
    }

    // --- 纯原生 8080 Socket 服务器（零依赖、100% 编译通过） ---
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
                        + "\"root\":" + isRootGranted + ","
                        + "\"bluetoothConnected\":" + bluetoothModule.isConnected() + ","
                        + "\"bluetoothDevice\":\"" + bluetoothModule.getLastConnectedDeviceName() + "\""
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent toggleIntent = new Intent(ACTION_TOGGLE_MODE);
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(this, 0, toggleIntent, pendingFlags);

        Intent resetIntent = new Intent(ACTION_EMERGENCY_RESET);
        PendingIntent resetPendingIntent = PendingIntent.getBroadcast(this, 1, resetIntent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(togglePendingIntent)
                .addAction(android.R.drawable.ic_menu_call, "🚨 紧急重置原生", resetPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        startForeground(1357, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        if (bluetoothModule != null) bluetoothModule.unregister();
        try { unregisterReceiver(serviceReceiver); } catch (Exception ignored) {}
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
