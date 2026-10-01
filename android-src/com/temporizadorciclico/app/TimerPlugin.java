package com.temporizadorciclico.app;

import android.content.Intent;
import android.os.Build;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONArray;

@CapacitorPlugin(name = "TimerNative")
public class TimerPlugin extends Plugin {

    private static long readLong(PluginCall call, String key, long fallback) {
        Object v = call.getData().opt(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return fallback;
    }

    private static int readInt(PluginCall call, String key, int fallback) {
        Object v = call.getData().opt(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return fallback;
    }

    private static boolean readBool(PluginCall call, String key, boolean fallback) {
        Object v = call.getData().opt(key);
        if (v instanceof Boolean) return (Boolean) v;
        return fallback;
    }

    @PluginMethod
    public void start(PluginCall call) {
        try {
            String stages = call.getString("stages", "[]");
            int index = readInt(call, "index", 0);
            long deadline = readLong(call, "deadline", 0);
            boolean inTransition = readBool(call, "inTransition", false);
            long transitionMs = readLong(call, "transitionMs", 5000);
            if (deadline <= 0) {
                call.reject("deadline inválido");
                return;
            }
            if (transitionMs < 500) transitionMs = 5000;

            // Android 12+: sem alarme exato o setAlarmClock ainda funciona, mas
            // o sistema pode mostrar o ícone de despertador — está ok para um timer.
            try {
                android.app.AlarmManager am =
                    (android.app.AlarmManager) getContext().getSystemService(android.content.Context.ALARM_SERVICE);
                if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canExact(am)) {
                    Intent settings = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(settings);
                }
            } catch (Exception ignored) {
            }

            Intent i = new Intent(getContext(), TimerService.class);
            i.setAction(TimerService.ACTION_START);
            i.putExtra(TimerService.EXTRA_STAGES, stages);
            i.putExtra(TimerService.EXTRA_INDEX, index);
            i.putExtra(TimerService.EXTRA_DEADLINE, deadline);
            i.putExtra(TimerService.EXTRA_IN_TRANSITION, inTransition);
            i.putExtra(TimerService.EXTRA_TRANSITION_MS, transitionMs);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(i);
            } else {
                getContext().startService(i);
            }
            JSObject r = new JSObject();
            r.put("ok", true);
            r.put("deadline", deadline);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Falha ao iniciar serviço: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        try {
            Intent i = new Intent(getContext(), TimerService.class);
            i.setAction(TimerService.ACTION_STOP);
            getContext().startService(i);
            AlarmScheduler.cancel(getContext());
            call.resolve();
        } catch (Exception e) {
            call.reject("Falha ao parar serviço: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void scheduleAlerts(PluginCall call) {
        try {
            JSArray alerts = call.getArray("alerts");
            if (alerts == null) {
                call.reject("alerts obrigatório");
                return;
            }
            AlarmScheduler.schedule(getContext(), alerts);
            call.resolve();
        } catch (Exception e) {
            call.reject("Falha ao agendar: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void cancelAlerts(PluginCall call) {
        try {
            AlarmScheduler.cancel(getContext());
            call.resolve();
        } catch (Exception e) {
            call.reject("Falha ao cancelar: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void canScheduleExactAlarms(PluginCall call) {
        try {
            android.app.AlarmManager am =
                (android.app.AlarmManager) getContext().getSystemService(android.content.Context.ALARM_SERVICE);
            boolean ok = am == null || AlarmScheduler.canExact(am);
            JSObject r = new JSObject();
            r.put("granted", ok);
            call.resolve(r);
        } catch (Exception e) {
            JSObject r = new JSObject();
            r.put("granted", false);
            call.resolve(r);
        }
    }
}
