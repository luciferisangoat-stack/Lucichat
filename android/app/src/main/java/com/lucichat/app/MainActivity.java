package com.lucichat.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String AURA_URL = "https://aura-ai-tvzpe5.v2.appdeploy.ai/";
    private static final int AUDIO_PERMISSION_REQUEST = 42;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 43;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    String host = request.getOrigin().getHost();
                    boolean isAuraOrigin = "aura-ai-tvzpe5.v2.appdeploy.ai".equals(host);
                    String[] requested = request.getResources();
                    boolean audioOnly = requested.length > 0;
                    for (String resource : requested) {
                        if (!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) audioOnly = false;
                    }
                    boolean granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                    if (isAuraOrigin && audioOnly && granted) request.grant(requested);
                    else request.deny();
                });
            }
        });

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            webView.loadUrl(AURA_URL);
            startWakeWordServiceIfAllowed();
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            webView.loadUrl(AURA_URL);
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startWakeWordServiceIfAllowed();
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            // The service still shows its required foreground notification on supported Android versions.
            if (Build.VERSION.SDK_INT < 33 || grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchWakeWordService();
            }
        }
    }

    private void startWakeWordServiceIfAllowed() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        launchWakeWordService();
    }

    private void launchWakeWordService() {
        Intent service = new Intent(this, WakeWordService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}