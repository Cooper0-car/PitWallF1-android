package com.pitwall.f1;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.AdapterView;

/** 알림 설정 화면. 바뀔 때마다 알람을 다시 예약한다. */
public class SettingsActivity extends Activity {

    private static final int[] LEADS = {1, 3, 5, 10, 15, 30, 60};

    private SharedPreferences p;
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        p = SessionAlert.prefs(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad, pad, pad);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0A0A0C);
        sv.setFillViewport(true);
        sv.addView(box);
        setContentView(sv);

        text("알림 설정", 22, Color.WHITE).setPadding(0, 0, 0, dp(8));
        sw("세션 시작 알림", "on", true);

        text("몇 분 전에 알릴까요", 13, 0xFFA0A2A9).setPadding(0, dp(18), 0, 0);
        String[] labels = new String[LEADS.length];
        for (int i = 0; i < LEADS.length; i++) labels[i] = LEADS[i] + "분 전";
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        int cur = p.getInt("lead", 1);
        for (int i = 0; i < LEADS.length; i++) if (LEADS[i] == cur) sp.setSelection(i);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                if (p.getInt("lead", 1) == LEADS[pos]) return;
                p.edit().putInt("lead", LEADS[pos]).apply();
                SessionAlert.schedule(SettingsActivity.this);
            }
            @Override public void onNothingSelected(AdapterView<?> a) { }
        });
        box.addView(sp);

        text("알림 받을 세션", 13, 0xFFA0A2A9).setPadding(0, dp(18), 0, 0);
        sw("연습 (FP1·FP2·FP3)", "k_prac", true);
        sw("스프린트 (스프린트 예선·스프린트)", "k_sprint", true);
        sw("예선", "k_quali", true);
        sw("결승", "k_race", true);

        text("알림 방식", 13, 0xFFA0A2A9).setPadding(0, dp(18), 0, 0);
        sw("소리", "sound", false);
        sw("진동", "vib", false);

        Button test = new Button(this);
        test.setText("테스트 알림 보내기");
        test.setOnClickListener(v -> SessionAlert.show(this, "테스트 그랑프리", "예선", System.currentTimeMillis() + p.getInt("lead", 1) * 60_000L, p.getInt("lead", 1)));
        box.addView(test);
        ((LinearLayout.LayoutParams) test.getLayoutParams()).topMargin = dp(24);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        box.addView(t);
        return t;
    }

    private void sw(String label, String key, boolean def) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(0xFFF1F1F2);
        s.setTextSize(15);
        s.setPadding(0, dp(10), 0, dp(10));
        s.setChecked(p.getBoolean(key, def));
        s.setOnCheckedChangeListener((b, on) -> {
            p.edit().putBoolean(key, on).apply();
            SessionAlert.schedule(this);
        });
        box.addView(s);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
