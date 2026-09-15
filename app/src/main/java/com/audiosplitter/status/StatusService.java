package com.audiosplitter.status;

import android.app.*;
import android.content.Intent;
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
    private static final int PORT = 8080;
    private HttpServer httpServer;
    private boolean isRootGranted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        updateNotificationCard("音频分流服务启动中", "正在初始化 8080 本地控制台...");

        // 1. 异步请求 Root 权限
        new Thread(() -> {
            isRootGranted = AudioEngine.requestRoot();
            if (isRootGranted) {
                updateNotificationCard("音频分流运行中", "8080 端口已监听 - 零功耗监控");
            } else {
                updateNotificationCard("音频分流警告", "未获取到 Root 权限");
            }
        }).start();

        // 2. 启动 127.0.0.1:8080 本地 HTTP 服务
        startLocalHttpServer();
    }

    private void startLocalHttpServer() {
        new Thread(() -> {
            try {
                // 绑定到 127.0.0.1:8080
                InetSocketAddress address = new InetSocketAddress("127.0.0.1", PORT);
                httpServer = HttpServer.create(address, 0);

                // 设置 API 路由处理逻辑 (App 访问 127.0.0.1:8080 时返回响应)
                httpServer.createContext("/", new HttpHandler() {
                    @Override
                    public void handle(HttpExchange exchange) throws IOException {
                        // 解决跨域问题（CORS）
                        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

                        String path = exchange.getRequestURI().getPath();
                        String responseJson = "{\"status\":\"ok\", \"message\":\"Server Running\"}";

                        // 简单路由匹配示例
                        if ("/status".equals(path)) {
                            boolean btOn = AudioEngine.isBluetoothConnected();
                            responseJson = "{\"bluetooth\":" + btOn + ", \"root\":" + isRootGranted + "}";
                        }

                        byte[] respBytes = responseJson.getBytes("UTF-8");
                        exchange.sendResponseHeaders(200, respBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(respBytes);
                        os.close();
                    }
                });

                httpServer.setExecutor(null); // 使用默认单线程执行器，确保低 CPU 占用
                httpServer.start();
            } catch (Exception e) {
                e.printStackTrace();
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

    @Override
    public void onDestroy() {
        // 服务销毁时释放 8080 端口
        if (httpServer != null) {
            httpServer.stop(0);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
