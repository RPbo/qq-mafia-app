package online.qqhtml.mafia;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

/** Каналы уведомлений: игра (ход, фазы) и общение (друзья, сообщения, приглашения). */
final class Notif {
    static final String GAME = "game", SOCIAL = "social";

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel g = new NotificationChannel(GAME, "Игра", NotificationManager.IMPORTANCE_HIGH);
        g.setDescription("Начало партии, ночь, день, голосование");
        NotificationChannel s = new NotificationChannel(SOCIAL, "Друзья и сообщения", NotificationManager.IMPORTANCE_DEFAULT);
        s.setDescription("Заявки в друзья, личные сообщения, приглашения");
        nm.createNotificationChannel(g);
        nm.createNotificationChannel(s);
    }

    private Notif() {}
}
