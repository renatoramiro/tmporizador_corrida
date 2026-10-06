package com.temporizadorciclico.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Recebe o AlarmManager.setAlarmClock e dispara o alerta (som STREAM_ALARM + vibração).
 * Roda mesmo com a tela bloqueada — setAlarmClock é isento de Doze, como um despertador.
 */
public class AlertReceiver extends BroadcastReceiver {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        // Treino encerrado (prefs limpas): ignora alarme órfão em vez de reanimar o service.
        try {
            android.content.SharedPreferences p =
                context.getSharedPreferences("timer_native", android.content.Context.MODE_PRIVATE);
            if (!p.contains("stages")) return;
        } catch (Exception ignored) {
        }

        String title = intent.getStringExtra(EXTRA_TITLE);
        String body = intent.getStringExtra(EXTRA_BODY);
        if (title == null) title = "Trocando de etapa";
        if (body == null) body = "";

        // Alerta imediato — não depende do service estar vivo
        AlarmAlert.fire(context);
        NotificationHelper.showAlert(context, title, body);

        // Acorda/atualiza o service para continuar o countdown na lock screen
        Intent svc = new Intent(context, TimerService.class);
        svc.setAction(TimerService.ACTION_ALERT);
        svc.putExtra(EXTRA_TITLE, title);
        svc.putExtra(EXTRA_BODY, body);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception ignored) {
        }
    }
}
