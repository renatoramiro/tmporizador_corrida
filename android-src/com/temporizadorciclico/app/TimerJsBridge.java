package com.temporizadorciclico.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.webkit.JavascriptInterface;

/**
 * Bridge estável via WebView.addJavascriptInterface.
 * Não depende do registro de plugin do Capacitor — se o proxy JS falhar,
 * este caminho continua funcionando.
 */
public class TimerJsBridge {

    private final Activity activity;

    public TimerJsBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void start(String stagesJson, int index, double deadline, boolean inTransition, double transitionMs) {
        try {
            Intent i = new Intent(activity, TimerService.class);
            i.setAction(TimerService.ACTION_START);
            i.putExtra(TimerService.EXTRA_STAGES, stagesJson != null ? stagesJson : "[]");
            i.putExtra(TimerService.EXTRA_INDEX, index);
            i.putExtra(TimerService.EXTRA_DEADLINE, (long) deadline);
            i.putExtra(TimerService.EXTRA_IN_TRANSITION, inTransition);
            i.putExtra(TimerService.EXTRA_TRANSITION_MS, (long) transitionMs);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(i);
            } else {
                activity.startService(i);
            }
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void stop() {
        try {
            Intent i = new Intent(activity, TimerService.class);
            i.setAction(TimerService.ACTION_STOP);
            activity.startService(i);
            AlarmScheduler.cancel(activity);
            AlarmAlert.stop();
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void alertNow() {
        // teste manual / reforço quando a página detecta troca em primeiro plano
        try {
            AlarmAlert.fire(activity);
        } catch (Exception ignored) {
        }
    }
}
