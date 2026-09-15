package com.audiosplitter.status;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        Button btnStartService = findViewById(R.id.btnStartService);
        Button btnToggleMode = findViewById(R.id.btnToggleMode);
        Button btnReset = findViewById(R.id.btnReset);

        requestRequiredPermissions();

        btnStartService.setOnClickListener(v -> {
            startAudioService();
            tvStatus.setText("后台服务及 8080 控制台已成功启动！");
        });

        btnToggleMode.setOnClickListener(v -> {
            Intent intent = new Intent(StatusService.ACTION_TOGGLE_MODE);
            sendBroadcast(intent);
            Toast.makeText(this, "已发送切换模式广播", Toast.LENGTH_SHORT).show();
        });

        btnReset.setOnClickListener(v -> {
            Intent intent = new Intent(StatusService.ACTION_EMERGENCY_RESET);
            sendBroadcast(intent);
            Toast.makeText(this, "已强行恢复原生音频状态", Toast.LENGTH_SHORT).show();
        });

        startAudioService();
    }

    private void startAudioService() {
        Intent intent = new Intent(this, StatusService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.POST_NOTIFICATIONS
                        }, 100);
            }
        }
    }
}
