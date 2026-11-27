package com.pitwall.f1;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 세션 시작 1분 전에 소리 없이 화면 위에 뜨는 알림. 알람이 울리면 다음 세션을 다시 예약한다. */
public class SessionAlert extends BroadcastReceiver {

    private static final String ACTION = "com.pitwall.f1.SESSION_ALERT";
    private static final long MIN = 60_000L;

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("pitwall_alert", Context.MODE_PRIVATE);
    }

    /** 세션 이름 → 설정 키(연습/스프린트/예선/결승). */
    private static String kind(String name) {
        return name.contains("연습") ? "k_prac" : name.contains("스프린트") ? "k_sprint" : name.contains("예선") ? "k_quali" : "k_race";
    }

    @Override
    public void onReceive(Context ctx, Intent i) {
        if (ACTION.equals(i.getAction())) show(ctx, i.getStringExtra("gp"), i.getStringExtra("name"), i.getLongExtra("start", 0), i.getIntExtra("lead", 1));
        schedule(ctx);   // 다음 세션 예약 (부팅·앱 업데이트 후에도 여기로 들어옴)
    }

    /** 저장된 일정에서 1분 전 시각이 아직 안 지난 가장 빠른 세션 하나를 알람으로 예약. */
    static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(ctx);
        int lead = p.getInt("lead", 1);
        NextRaceWidget.Race gp = null;
        NextRaceWidget.Session next = null;
        if (p.getBoolean("on", true))
            for (NextRaceWidget.Race r : NextRaceWidget.parse(NextRaceWidget.cached(ctx)))
                for (NextRaceWidget.Session s : r.sessions)
                    if (s.start - lead * MIN > now && p.getBoolean(kind(s.name), true) && (next == null || s.start < next.start)) { gp = r; next = s; }

        Intent i = new Intent(ctx, SessionAlert.class).setAction(ACTION);
        if (next != null) {
            i.putExtra("gp", NextRaceWidget.GP_KO.containsKey(gp.name) ? NextRaceWidget.GP_KO.get(gp.name) : gp.name)
                    .putExtra("name", next.name).putExtra("start", next.start).putExtra("lead", lead);
        }
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 2, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (next == null) { am.cancel(pi); return; }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.start - lead * MIN, pi);
        } catch (SecurityException e) {   // 정확한 알람 권한이 없으면 오차를 감수
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.start - lead * MIN, pi);
        }
    }

    static void show(Context ctx, String gp, String name, long start, int lead) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || gp == null || System.currentTimeMillis() > start) return;

        // 화면 위에 뜨고(헤드업) 알림 목록에도 남는 채널. 소리·진동 설정은 채널에 고정되므로 조합마다 따로 만든다.
        boolean sound = prefs(ctx).getBoolean("sound", false), vib = prefs(ctx).getBoolean("vib", false);
        String channel = "session_alert_" + (sound ? "s" : "") + (vib ? "v" : "");
        NotificationChannel ch = new NotificationChannel(channel, "세션 시작 알림", NotificationManager.IMPORTANCE_HIGH);
        if (!sound) ch.setSound(null, null);
        ch.enableVibration(vib);
        nm.createNotificationChannel(ch);

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.notification_session);
        v.setTextViewText(R.id.n_gp, gp);
        v.setTextViewText(R.id.n_sess, name + " 시작 " + lead + "분 전");
        v.setTextViewText(R.id.n_time, new SimpleDateFormat("a h:mm", Locale.KOREAN).format(new Date(start)));

        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify((int) (start / MIN), new Notification.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_notify)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(v)
                .setCustomHeadsUpContentView(v)
                .setCategory(Notification.CATEGORY_EVENT)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)   // 잠금화면에도 표시
                .build());
    }
}
