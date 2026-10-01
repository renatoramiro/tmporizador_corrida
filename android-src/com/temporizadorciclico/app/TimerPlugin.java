package com.temporizadorciclico.app;

import android.content.Intent;
import android.os.Build;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "TimerNative")
public class TimerPlugin extends Plugin {

    @PluginMethod
    public void start(PluginCall call) {
        try {
            Intent i = new Intent(getContext(), TimerService.class);
            i.setAction(TimerService.ACTION_START);
            i.putExtra(TimerService.EXTRA_STAGES, call.getString("stages", "[]"));
            i.putExtra(TimerService.EXTRA_INDEX, call.getInt("index", 0));
            i.putExtra(TimerService.EXTRA_DEADLINE, call.getLong("deadline", 0L));
            i.putExtra(TimerService.EXTRA_IN_TRANSITION, Boolean.TRUE.equals(call.getBoolean("inTransition", false)));
            i.putExtra(TimerService.EXTRA_TRANSITION_MS, call.getLong("transitionMs", 5000L));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(i);
            } else {
                getContext().startService(i);
            }
            call.resolve();
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
