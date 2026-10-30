package online.qqhtml.mafia;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Принимает push от сервера (FCM data-сообщения) и показывает уведомление. */
public class PushService extends FirebaseMessagingService {

    @Override
    public void onNewToken(String token) {
        getSharedPreferences("qq", MODE_PRIVATE).edit().putString("fcm", token).apply();
    }

    @Override
    public void onMessageReceived(RemoteMessage m) {
        Map<String, String> d = m.getData();
        String title = d.get("title"), body = d.get("body");
        if (title == null && m.getNotification() != null) {
            title = m.getNotification().getTitle();
            body = m.getNotification().getBody();
        }
        if (title == null) title = "QQ Mafia";
        String ch = Notif.SOCIAL.equals(d.get("channel")) ? Notif.SOCIAL : Notif.GAME;
        Notif.channels(this);

        Intent i = new Intent(this, MainActivity.class)
                .putExtra("url", d.get("url"))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, ch)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFFF1428)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(d.get("tag"), 1, n);          // одинаковый tag заменяет прежнее уведомление
    }
}
