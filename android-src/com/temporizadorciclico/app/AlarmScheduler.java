package com.temporizadorciclico.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;

/** Agenda alertas com AlarmManager.setAlarmClock — dispara mesmo com tela bloqueada e em Doze. */
public final class AlarmScheduler {

    private AlarmScheduler() {}

    public static void schedule(Context ctx, JSONArray alerts) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        cancel(ctx);
        long now = System.currentTimeMillis();
        for (int i = 0; i < alerts.length(); i++) {
            JSONObject a = alerts.optJSONObject(i);
            if (a == null) continue;
            long at = a.optLong("at", 0);
            if (at <= now + 150) continue;
            int id = a.optInt("id", 1000 + i);
            PendingIntent pi = pending(ctx, id);
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(at, showIntent(ctx));
            am.setAlarmClock(info, pi);
        }
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int id = 1000; id < 1100; id++) {
            am.cancel(pending(ctx, id));
        }
    }

    private static PendingIntent pending(Context ctx, int id) {
        Intent i = new Intent(ctx, AlertReceiver.class);
        i.putExtra(AlertReceiver.EXTRA_ID, id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(ctx, id, i, flags);
    }

    private static PendingIntent showIntent(Context ctx) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (launch == null) launch = new Intent(ctx, MainActivity.class);
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(ctx, 0, launch, flags);
    }

    public static boolean canExact(AlarmManager am) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return am.canScheduleExactAlarms();
    }

    /** Tempo monotônico útil para o service. */
    public static long nowWall() {
        return System.currentTimeMillis();
    }

    public static long uptime() {
        return SystemClock.elapsedRealtime();
    }
}
