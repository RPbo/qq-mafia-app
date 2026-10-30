package online.qqhtml.mafia;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Автообновление: смотрит последний релиз на GitHub, скачивает APK и запускает установку. */
final class UpdateManager {
    private static final long EVERY = 3 * 3600 * 1000L;       // не чаще раза в 3 часа
    private final Activity a;
    private final SharedPreferences sp;

    UpdateManager(Activity a) {
        this.a = a;
        this.sp = a.getSharedPreferences("qq", Context.MODE_PRIVATE);
    }

    void check(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - sp.getLong("upd_t", 0) < EVERY) return;
        sp.edit().putLong("upd_t", now).apply();
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(BuildConfig.UPDATE_API).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                }
                JSONObject j = new JSONObject(sb.toString());
                int latest = Integer.parseInt(j.getString("tag_name").replaceAll("\\D", ""));   // тег вида build-12
                if (latest <= BuildConfig.VERSION_CODE) return;
                String url = null;
                JSONArray as = j.getJSONArray("assets");
                for (int i = 0; i < as.length(); i++) {
                    JSONObject x = as.getJSONObject(i);
                    if (x.getString("name").endsWith(".apk")) url = x.getString("browser_download_url");
                }
                if (url == null) return;
                final String u = url, name = j.optString("name", "новая версия");
                a.runOnUiThread(() -> ask(name, u));
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void ask(String name, String url) {
        if (a.isFinishing()) return;
        new AlertDialog.Builder(a)
                .setTitle("Доступно обновление")
                .setMessage(name + "\nОбновить приложение сейчас?")
                .setPositiveButton("Обновить", (d, w) -> download(url))
                .setNegativeButton("Позже", null)
                .show();
    }

    private void download(String url) {
        File f = new File(a.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "qq-mafia.apk");
        if (f.exists()) //noinspection ResultOfMethodCallIgnored
            f.delete();
        DownloadManager dm = (DownloadManager) a.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request rq = new DownloadManager.Request(Uri.parse(url))
                .setTitle("QQ Mafia")
                .setDescription("Загрузка обновления")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(a, Environment.DIRECTORY_DOWNLOADS, "qq-mafia.apk");
        final long id = dm.enqueue(rq);
        Toast.makeText(a, "Загружаем обновление…", Toast.LENGTH_SHORT).show();
        BroadcastReceiver rc = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                try { a.unregisterReceiver(this); } catch (Exception ignored) { }
                install(f);
            }
        };
        ContextCompat.registerReceiver(a, rc, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED);
    }

    /** Если установка из этого источника ещё не разрешена, открываем настройку и продолжаем после возврата. */
    void install(File f) {
        if (!f.exists()) return;
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            sp.edit().putString("pending_apk", f.getAbsolutePath()).apply();
            Toast.makeText(a, "Разрешите установку обновлений из QQ Mafia", Toast.LENGTH_LONG).show();
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
            return;
        }
        sp.edit().remove("pending_apk").apply();
        Uri u = FileProvider.getUriForFile(a, a.getPackageName() + ".files", f);
        a.startActivity(new Intent(Intent.ACTION_VIEW)
                .setDataAndType(u, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    void resumePending() {
        String p = sp.getString("pending_apk", null);
        if (p != null && a.getPackageManager().canRequestPackageInstalls()) install(new File(p));
    }
}
