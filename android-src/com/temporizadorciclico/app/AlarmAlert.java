package com.temporizadorciclico.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Alerta de troca de etapa que funciona com a tela bloqueada.
 *
 * Vibração sozinha é suprimida pelo Android com a tela apagada em apps comuns.
 * O caminho confiável é som em STREAM_ALARM (canal de despertador), que o sistema
 * deixa tocar com o aparelho bloqueado. A vibração entra como reforço.
 */
public final class AlarmAlert {

    private static MediaPlayer mediaPlayer;

    private AlarmAlert() {}

    public static void fire(Context ctx) {
        vibrate(ctx);
        playAlarmSound(ctx);
    }

    public static void stop() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {
        }
    }

    private static void vibrate(Context ctx) {
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = {0, 800, 200, 800, 200, 800, 200, 1000};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {
        }
    }

    private static void playAlarmSound(Context ctx) {
        try {
            stop();
            MediaPlayer mp = MediaPlayer.create(ctx, R.raw.alarm_beep);
            if (mp == null) {
                // fallback: tone generator em loop por 5s
                playToneFallback();
                return;
            }
            mediaPlayer = mp;
            mp.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            );
            mp.setLooping(false);
            // força volume no canal de alarme (o sistema pode deixar em 0 por padrão)
            try {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                    int cur = am.getStreamVolume(AudioManager.STREAM_ALARM);
                    if (cur < Math.max(1, max / 2)) {
                        am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
                    }
                }
            } catch (Exception ignored) {
            }
            mp.setOnCompletionListener(m -> {
                try {
                    m.release();
                } catch (Exception ignored) {
                }
                if (mediaPlayer == m) mediaPlayer = null;
            });
            mp.start();
        } catch (Exception e) {
            playToneFallback();
        }
    }

    private static void playToneFallback() {
        try {
            android.media.ToneGenerator tone =
                new android.media.ToneGenerator(AudioManager.STREAM_ALARM, 100);
            Handler h = new Handler(Looper.getMainLooper());
            // 5 bipes (~5s)
            for (int i = 0; i < 5; i++) {
                final int delay = i * 1000;
                h.postDelayed(() -> {
                    try {
                        tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 300);
                    } catch (Exception ignored) {
                    }
                }, delay);
            }
            h.postDelayed(() -> {
                try {
                    tone.release();
                } catch (Exception ignored) {
                }
            }, 5500);
        } catch (Exception ignored) {
        }
    }
}
