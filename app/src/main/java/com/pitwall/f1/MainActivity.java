package com.pitwall.f1;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** 깃허브 사이트(우리가 만든 대시보드)를 그대로 띄우는 화면. */
public class MainActivity extends Activity {

    static final String SITE = "https://cooper0-car.github.io/PitwallF1/";
    private static final String SITE_HOST = "cooper0-car.github.io";

    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0A0A0C);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // 앱 캐시(localStorage) 유지
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new WidgetBridge(), "PitwallNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // 화면이 뜨면 웹뷰(브라우저)로 일정 데이터를 받아 위젯에 넘겨줌 — 위젯의 직접 연결이 막혀도 동작
                view.evaluateJavascript("fetch('" + NextRaceWidget.API + "').then(function(r){return r.ok?r.text():null})"
                        + ".then(function(t){if(t&&window.PitwallNative)PitwallNative.saveSchedule(t)}).catch(function(){})", null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (SITE_HOST.equals(uri.getHost())) return false;   // 앱 안에서 이동
                try {                                                 // 외부 링크는 브라우저로
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) { }
                return true;
            }
        });

        setContentView(web);
        if (state != null) web.restoreState(state);
        else web.loadUrl(SITE);

        NextRaceWidget.refreshAll(this);   // 앱을 열 때마다 위젯도 새로고침
    }

    /** 웹 페이지 → 앱: 받아온 일정 JSON을 위젯용으로 저장. */
    private final class WidgetBridge {
        @JavascriptInterface
        public void saveSchedule(String json) {
            if (NextRaceWidget.isSameAsCached(getApplicationContext(), json)) return;
            if (NextRaceWidget.storeIfValid(getApplicationContext(), json)) NextRaceWidget.refreshAll(getApplicationContext());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
