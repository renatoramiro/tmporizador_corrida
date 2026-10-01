package com.temporizadorciclico.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Recebe o alarme nativo e dispara vibração + som + notificação, mesmo com a tela bloqueada. */
public class AlertReceiver extends BroadcastReceiver {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String title = intent.getStringExtra(EXTRA_TITLE);
        String body = intent.getStringExtra(EXTRA_BODY);
        if (title == null) title = "Trocando de etapa";
        if (body == null) body = "";

        vibrate(context);
        beep();
        NotificationHelper.showAlert(context, title, body);

        // Mantém o service informado para atualizar a UI / notificação permanente
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
            // se o service não puder subir, o alerta acima já foi entregue
        }
    }

    private void vibrate(Context context) {
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator == null || !vibrator.hasVibrator()) return;
            // ~5s: 3 pulsos curtos + 1 longo
            long[] pattern = {0, 800, 200, 800, 200, 800, 200, 1000};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {
        }
    }

    private void beep() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioAttributes.USAGE_ALARM, 100);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 180);
            tone.release();
        } catch (Exception ignored) {
        }
    }
}
