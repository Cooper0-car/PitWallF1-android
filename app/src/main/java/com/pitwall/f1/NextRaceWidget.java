package com.pitwall.f1;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 홈 화면 위젯: 다음 레이스 · 결승까지 카운트다운 · 다음 세션.
 * 데이터는 Jolpica F1 API(current.json)에서 직접 받아오고, 마지막 결과를 저장해 두었다가
 * 인터넷이 없을 때도 보여준다. 위젯을 누르면 앱이 열린다.
 */
public class NextRaceWidget extends AppWidgetProvider {

    static final String API = "https://api.jolpi.ca/ergast/f1/current.json?limit=100";
    private static final String PREFS = "pitwall_widget";
    private static final String ACTION_TICK = "com.pitwall.f1.WIDGET_TICK";
    private static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;

    private static final Map<String, String> GP_KO = new HashMap<>();
    static {
        String[][] m = {
            {"Australian Grand Prix", "호주 그랑프리"}, {"Chinese Grand Prix", "중국 그랑프리"},
            {"Japanese Grand Prix", "일본 그랑프리"}, {"Bahrain Grand Prix", "바레인 그랑프리"},
            {"Saudi Arabian Grand Prix", "사우디아라비아 그랑프리"}, {"Miami Grand Prix", "마이애미 그랑프리"},
            {"Emilia Romagna Grand Prix", "에밀리아로마냐 그랑프리"}, {"Monaco Grand Prix", "모나코 그랑프리"},
            {"Canadian Grand Prix", "캐나다 그랑프리"}, {"Spanish Grand Prix", "스페인 그랑프리"},
            {"Barcelona Grand Prix", "바르셀로나 그랑프리"}, {"Austrian Grand Prix", "오스트리아 그랑프리"},
            {"British Grand Prix", "영국 그랑프리"}, {"Belgian Grand Prix", "벨기에 그랑프리"},
            {"Hungarian Grand Prix", "헝가리 그랑프리"}, {"Dutch Grand Prix", "네덜란드 그랑프리"},
            {"Italian Grand Prix", "이탈리아 그랑프리"}, {"Azerbaijan Grand Prix", "아제르바이잔 그랑프리"},
            {"Singapore Grand Prix", "싱가포르 그랑프리"}, {"United States Grand Prix", "미국 그랑프리"},
            {"Mexico City Grand Prix", "멕시코시티 그랑프리"}, {"São Paulo Grand Prix", "상파울루 그랑프리"},
            {"Brazilian Grand Prix", "브라질 그랑프리"}, {"Las Vegas Grand Prix", "라스베이거스 그랑프리"},
            {"Qatar Grand Prix", "카타르 그랑프리"}, {"Abu Dhabi Grand Prix", "아부다비 그랑프리"},
            {"Bahrain Grand Prix in Malaysia", "바레인 그랑프리 (말레이시아)"}, {"Madrid Grand Prix", "마드리드 그랑프리"},
        };
        for (String[] p : m) GP_KO.put(p[0], p[1]);
    }

    private static final class Session {
        final String name; final long start; final long durMs;
        Session(String name, long start, long durMs) { this.name = name; this.start = start; this.durMs = durMs; }
    }

    private static final class Race {
        int round; String name; long raceStart; final List<Session> sessions = new ArrayList<>();
    }

    // ------------------------------------------------------------------ lifecycle
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        final Context app = context.getApplicationContext();
        render(app, manager, ids, cached(app));               // 저장된 데이터로 즉시 표시
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                String json = fetch(app);
                if (!storeIfValid(app, json) && json != null) storeError(app, "응답 형식 오류");
                render(app, manager, ids, cached(app));
            } catch (Exception ignored) {
            } finally {
                result.finish();
            }
        }).start();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TICK.equals(intent.getAction())) {
            AppWidgetManager m = AppWidgetManager.getInstance(context);
            int[] ids = m.getAppWidgetIds(new ComponentName(context, NextRaceWidget.class));
            if (ids.length > 0) onUpdate(context, m, ids);
        }
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickIntent(context));
    }

    /** 앱에서 호출: 위젯이 있으면 새로고침. */
    static void refreshAll(Context context) {
        AppWidgetManager m = AppWidgetManager.getInstance(context);
        int[] ids = m.getAppWidgetIds(new ComponentName(context, NextRaceWidget.class));
        if (ids.length == 0) return;
        Intent i = new Intent(context, NextRaceWidget.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(i);
    }

    // ------------------------------------------------------------------ data
    private static String cached(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", null);
    }

    /** 올바른 일정 데이터면 저장하고 true. 앱 화면(웹뷰)에서 받아온 데이터도 여기로 들어온다. */
    static boolean storeIfValid(Context ctx, String json) {
        if (json == null || parse(json).isEmpty()) return false;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("json", json).putString("err", "").apply();
        return true;
    }

    static boolean isSameAsCached(Context ctx, String json) {
        return json != null && json.equals(cached(ctx));
    }

    private static void storeError(Context ctx, String err) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("err", err).apply();
    }

    private static String fetch(Context ctx) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(API).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ") PitwallF1Widget/1.1");
            int code = c.getResponseCode();
            if (code != 200) { storeError(ctx, "HTTP " + code); return null; }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            storeError(ctx, e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static List<Race> parse(String json) {
        List<Race> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray races = new JSONObject(json).getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
            for (int i = 0; i < races.length(); i++) {
                JSONObject r = races.getJSONObject(i);
                long raceStart = toMillis(r.optString("date"), r.optString("time"));
                if (raceStart <= 0) continue;
                Race race = new Race();
                race.round = r.optInt("round");
                race.name = r.optString("raceName");
                race.raceStart = raceStart;
                add(race, "1차 연습", r.optJSONObject("FirstPractice"), 60);
                add(race, "2차 연습", r.optJSONObject("SecondPractice"), 60);
                add(race, "3차 연습", r.optJSONObject("ThirdPractice"), 60);
                JSONObject sq = r.optJSONObject("SprintQualifying");
                add(race, "스프린트 예선", sq != null ? sq : r.optJSONObject("SprintShootout"), 45);
                add(race, "스프린트", r.optJSONObject("Sprint"), 60);
                add(race, "예선", r.optJSONObject("Qualifying"), 60);
                race.sessions.add(new Session("결승", raceStart, 120 * MIN));
                race.sessions.sort((a, b) -> Long.compare(a.start, b.start));
                out.add(race);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static void add(Race race, String name, JSONObject o, int durMin) {
        if (o == null) return;
        long t = toMillis(o.optString("date"), o.optString("time"));
        if (t > 0) race.sessions.add(new Session(name, t, durMin * MIN));
    }

    private static long toMillis(String date, String time) {
        if (date == null || date.isEmpty()) return -1;
        String t = (time == null || time.isEmpty()) ? "00:00:00Z" : time;
        if (!t.endsWith("Z") && !t.contains("+")) t = t + "Z";
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(date + "T" + t);
            return d == null ? -1 : d.getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ view
    private static void render(Context ctx, AppWidgetManager manager, int[] ids, String json) {
        long now = System.currentTimeMillis();
        List<Race> races = parse(json);
        Race next = null;
        for (Race r : races) {
            if (r.raceStart + 2 * HOUR > now) { next = r; break; }
        }

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_next_race);
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        long wake = now + 30 * MIN;
        if (next == null) {
            v.setTextViewText(R.id.kicker, races.isEmpty() ? "PITWALL F1" : "시즌 종료");
            String err = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("err", "");
            v.setTextViewText(R.id.gp, races.isEmpty() ? "데이터를 불러오는 중" : "다음 시즌을 기다리는 중");
            v.setTextViewText(R.id.race, races.isEmpty() ? "탭해서 앱을 한 번 열면 채워져요" : "탭해서 앱 열기");
            v.setViewVisibility(R.id.chrono, View.GONE);
            v.setViewVisibility(R.id.days, View.GONE);
            v.setViewVisibility(R.id.cdlabel, View.GONE);
            v.setTextViewText(R.id.next, races.isEmpty() && err != null && !err.isEmpty() ? "위젯 직접 연결 실패 · " + err : "");
        } else {
            boolean weekend = now >= next.sessions.get(0).start;
            v.setTextViewText(R.id.kicker, "ROUND " + next.round + " · " + (weekend ? "이번 주말" : "다음 레이스"));
            v.setTextViewText(R.id.gp, GP_KO.containsKey(next.name) ? GP_KO.get(next.name) : next.name);
            v.setTextViewText(R.id.race, "결승 " + when(next.raceStart, now));

            long left = next.raceStart - now;
            if (left <= 0) {
                v.setViewVisibility(R.id.chrono, View.GONE);
                v.setViewVisibility(R.id.days, View.VISIBLE);
                v.setViewVisibility(R.id.cdlabel, View.GONE);
                v.setTextViewText(R.id.days, "결승 진행 중");
                wake = Math.min(wake, next.raceStart + 2 * HOUR);
            } else if (left > 2 * DAY) {
                v.setViewVisibility(R.id.chrono, View.GONE);
                v.setViewVisibility(R.id.days, View.VISIBLE);
                v.setViewVisibility(R.id.cdlabel, View.VISIBLE);
                v.setTextViewText(R.id.days, (left / DAY) + "일 " + ((left % DAY) / HOUR) + "시간");
                wake = Math.min(wake, next.raceStart - 2 * DAY);
            } else {
                // 48시간 이내: 초 단위로 줄어드는 실시간 카운트다운
                v.setViewVisibility(R.id.days, View.GONE);
                v.setViewVisibility(R.id.chrono, View.VISIBLE);
                v.setViewVisibility(R.id.cdlabel, View.VISIBLE);
                v.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + left, null, true);
                v.setChronometerCountDown(R.id.chrono, true);
                wake = Math.min(wake, next.raceStart);
            }

            Session live = null, upcoming = null;
            for (Session s : next.sessions) {
                if (now >= s.start && now < s.start + s.durMs) live = s;
                if (s.start > now && upcoming == null) upcoming = s;
            }
            if (live != null) {
                v.setTextViewText(R.id.next, "지금 · " + live.name + " 진행 중");
                wake = Math.min(wake, live.start + live.durMs);
            } else if (upcoming != null) {
                v.setTextViewText(R.id.next, "다음 세션 · " + upcoming.name + " · " + when(upcoming.start, now));
                wake = Math.min(wake, upcoming.start);
            } else {
                v.setTextViewText(R.id.next, "");
            }
        }

        manager.updateAppWidget(ids, v);
        scheduleTick(ctx, Math.max(wake, now + MIN));
    }

    private static String when(long ts, long now) {
        String hm = new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date(ts));
        long d = dayIndex(ts) - dayIndex(now);
        if (d == 0) return "오늘 " + hm;
        if (d == 1) return "내일 " + hm;
        return new SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREAN).format(new Date(ts));
    }

    private static long dayIndex(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return c.get(Calendar.YEAR) * 400L + c.get(Calendar.DAY_OF_YEAR);
    }

    // ------------------------------------------------------------------ scheduling
    private static PendingIntent tickIntent(Context ctx) {
        Intent i = new Intent(ctx, NextRaceWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void scheduleTick(Context ctx, long at) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        // 정확한 알람 권한 없이 쓸 수 있는 방식 (최대 5분 오차)
        am.setWindow(AlarmManager.RTC, at, 5 * MIN, tickIntent(ctx));
    }
}
