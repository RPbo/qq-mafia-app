package online.qqhtml.mafia;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

/** Оболочка над сайтом игры: WebView, push-уведомления и автообновление через GitHub Releases. */
public class MainActivity extends Activity {
    private WebView web;
    private SharedPreferences sp;
    private UpdateManager upd;
    private String host;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("qq", MODE_PRIVATE);
        host = Uri.parse(BuildConfig.SITE_URL).getHost();
        Notif.channels(this);

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);                  // window.open (вход через бота) обрабатываем сами
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(s.getUserAgentString() + " QQMafiaApp/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        web.addJavascriptInterface(new Bridge(), "QQApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("https".equals(u.getScheme()) && host.equals(u.getHost())) return false;
                openExternal(u);
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, Message msg) {
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                        Uri u = r.getUrl();
                        if ("about".equals(u.getScheme())) return false;
                        openExternal(u);                    // например, t.me/<бот>?start=... откроется в Telegram
                        w.post(w::destroy);
                        return true;
                    }
                });
                tmp.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView w) { w.destroy(); }
                });
                ((WebView.WebViewTransport) msg.obj).setWebView(tmp);
                msg.sendToTarget();
                return true;
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        fetchToken();

        web.loadUrl(target(getIntent()));
        upd = new UpdateManager(this);
        upd.check(false);
    }

    private String target(Intent i) {
        String u = i == null ? null : i.getStringExtra("url");
        if (u == null && i != null && i.getData() != null) u = i.getData().toString();
        if (u == null || u.isEmpty()) return BuildConfig.SITE_URL;
        if (u.startsWith("/")) return BuildConfig.SITE_URL.replaceAll("/$", "") + u;
        Uri p = Uri.parse(u);
        return host.equals(p.getHost()) ? u : BuildConfig.SITE_URL;
    }

    private void fetchToken() {
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> {
                if (t == null) return;
                sp.edit().putString("fcm", t).apply();
                web.post(() -> web.evaluateJavascript("window.dispatchEvent(new Event('fcm'))", null));
            });
        } catch (Exception ignored) {
            // Firebase не настроен (нет google-services.json): приложение работает без push
        }
    }

    void openExternal(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    /** То, что сайт видит как window.QQApp. */
    class Bridge {
        @JavascriptInterface
        public String token() { return sp.getString("fcm", ""); }

        @JavascriptInterface
        public String version() { return BuildConfig.VERSION_NAME; }

        @JavascriptInterface
        public void openUrl(String u) { runOnUiThread(() -> openExternal(Uri.parse(u))); }

        @JavascriptInterface
        public void checkUpdate() { runOnUiThread(() -> upd.check(true)); }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (i.getStringExtra("url") != null) web.loadUrl(target(i));
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (upd != null) upd.resumePending();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    /** «Назад» не закрывает игру: внутри сайта шагает назад, на первом экране сворачивает приложение. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else moveTaskToBack(true);
    }
}
