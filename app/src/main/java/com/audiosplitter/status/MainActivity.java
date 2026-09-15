package com.audiosplitter.status;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private static final String TARGET_URL = "http://127.0.0.1:8080";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 极简代码构建 View 树
        RelativeLayout root = new RelativeLayout(this);
        swipeRefresh = new SwipeRefreshLayout(this);
        webView = new WebView(this);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);

        swipeRefresh.addView(webView, -1, -1);
        root.addView(swipeRefresh, -1, -1);
        root.addView(progressBar, -1, 12);
        setContentView(root);

        // 2. 关闭硬件加速降功耗，初始化配置
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        initWebView();

        // 3. 启动后台服务
        Intent serviceIntent = new Intent(this, StatusService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        swipeRefresh.setOnRefreshListener(() -> webView.reload());
        webView.loadUrl(TARGET_URL);
    }

    private void initWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setGeolocationEnabled(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        // 注册日志 JS 接口
        webView.addJavascriptInterface(new StatusService(), "AndroidLog");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    String errorHtml = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                            + "<style>body{font-family:sans-serif;text-align:center;background:#f7f9fc;padding:40px 20px;color:#333;}"
                            + "h2{color:#e53935;} .btn{background:#1e88e5;color:#fff;border:none;padding:12px 28px;font-size:15px;border-radius:6px;}</style>"
                            + "</head><body><h2>控制台未启动</h2><p>无法连接到后台服务 (127.0.0.1:8080)</p>"
                            + "<button class='btn' onclick=\"location.href='" + TARGET_URL + "'\">重新连接</button></body></html>";
                    view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers(); // 挂起后台 JS 计算，省电
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
