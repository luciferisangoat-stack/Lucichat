package com.lucichat.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String AURA_URL = "https://aura-ai-tvzpe5.v2.appdeploy.ai/";
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
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    String host = request.getOrigin().getHost();
                    boolean isAuraOrigin = "aura-ai-tvzpe5.v2.appdeploy.ai".equals(host);
                    String[] requested = request.getResources();
                    boolean audioOnly = requested.length > 0;
                    for (String resource : requested) {
                        if (!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                            audioOnly = false;
                            break;
                        }
                    }
                    if (isAuraOrigin && audioOnly) {
                        request.grant(requested);
                    } else {
                        request.deny();
                    }
                });
            }
        });

        webView.loadUrl(AURA_URL);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
