package com.bogdan_paykan.lab9_;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class WebActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlEditText;
    private Button loadUrlButton;
    private Button profileButtonWeb;
    private Button refreshButtonWeb;
    private Button settingsButtonWeb;
    private SwipeRefreshLayout swipeRefreshLayout;

    private String defaultUrl = "https://github.com/Bogdan-paykan/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);
        webView = findViewById(R.id.webView);
        urlEditText = findViewById(R.id.urlEditText);
        loadUrlButton = findViewById(R.id.loadUrlButton);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        profileButtonWeb = findViewById(R.id.profile_button_web);
        refreshButtonWeb = findViewById(R.id.refresh_button_web);
        settingsButtonWeb = findViewById(R.id.settings_button_web);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new MyWebViewClient());
        urlEditText.setText(defaultUrl);
        loadUrl(defaultUrl);

        loadUrlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = urlEditText.getText().toString().trim();
                if (!url.isEmpty()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://" + url;
                    }
                    loadUrl(url);
                } else {
                    Toast.makeText(WebActivity.this, "Будь ласка, введіть URL", Toast.LENGTH_SHORT).show();
                }
            }
        });

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
            }
        });

        profileButtonWeb.setOnClickListener(v -> {
            Intent intent = new Intent(WebActivity.this, MainActivity.class);
            intent.putExtra("navigateTo", "profile");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        refreshButtonWeb.setOnClickListener(v -> {
            webView.reload();
        });

        settingsButtonWeb.setOnClickListener(v -> {
            finish();
        });
    }

    private void loadUrl(String url) {
        webView.loadUrl(url);
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
                // swipeRefreshLayout.setRefreshing(true); // Розкоментуйте за потреби
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            urlEditText.setText(url);
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
            Toast.makeText(WebActivity.this, "Помилка завантаження: " + description, Toast.LENGTH_SHORT).show();
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && urlEditText.hasFocus()) {
            loadUrlButton.performClick();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}