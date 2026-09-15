package com.audiosplitter.status;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建布局
        Button button = new Button(this);
        button.setText("打开音频分流 WebUI 控制台");
        button.setTextSize(18);
        
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8080"));
                startActivity(browserIntent);
            }
        });

        setContentView(button);

        // 启动后台监测服务
        Intent serviceIntent = new Intent(this, StatusService.class);
        startService(serviceIntent);
    }
}
