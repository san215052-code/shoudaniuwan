package com.audiosplitter.status;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private StatusService statusService;

    private static final String TARGET_URL = "http://127.0.0.1:8080";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RelativeLayout rootLayout = new RelativeLayout(this);
        swipeRefreshLayout = new SwipeRefreshLayout(this);
        webView = new WebView(this);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);

        RelativeLayout.LayoutParams webViewParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        RelativeLayout.LayoutParams progressParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 12);

        swipeRefreshLayout.addView(webView, webViewParams);
        rootLayout.addView(swipeRefreshLayout, webViewParams);
        rootLayout.addView(progressBar, progressParams);
        setContentView(rootLayout);

        // 关键优化：关闭 WebView 的硬件加速，降低 GPU 耗电与发热
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        initWebViewSettings();

        Intent serviceIntent = new Intent(this, StatusService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());
        webView.loadUrl(TARGET_URL);
    }

    private void initWebViewSettings() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        
        // 低功耗 WebView 设置
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setGeolocationEnabled(false); // 关闭定位开销
        settings.setMediaPlaybackRequiresUserGesture(true); // 禁止媒体自动播放
        
        statusService = new StatusService();
        webView.addJavascriptInterface(statusService, "AndroidLog");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    String errorHtml = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                            + "<style>"
                            + "body { font-family: -apple-system, sans-serif; text-align: center; background-color: #f7f9fc; padding: 40px 20px; color: #333; }"
                            + "h2 { color: #e53935; margin-bottom: 10px; font-size: 20px; }"
                            + "p { color: #666; font-size: 14px; line-height: 1.5; margin-bottom: 25px; }"
                            + ".btn { background-color: #1e88e5; color: white; border: none; padding: 12px 28px; font-size: 15px; border-radius: 6px; font-weight: bold; cursor: pointer; }"
                            + "</style></head><body>"
                            + "<h2>控制台未启动</h2>"
                            + "<p>无法连接到后台服务 (127.0.0.1:8080)</p>"
                            + "<button class='btn' onclick=\"location.href='" + TARGET_URL + "'\">重新连接</button>"
                            + "</body></html>";
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

    // 关键优化：切到后台时暂停 JS 定时器和网页渲染，彻底释放 CPU/GPU 资源
    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers(); // 停止 JS setTimeout/setInterval
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers(); // 恢复运行
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
