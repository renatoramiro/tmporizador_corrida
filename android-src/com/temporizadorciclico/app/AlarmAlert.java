package com.temporizadorciclico.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Alerta de troca de etapa que funciona com a tela bloqueada.
 *
 * - Som em USAGE_ALARM (STREAM_ALARM): o sistema deixa tocar com o aparelho bloqueado.
 * - Vibração com VibrationAttributes.USAGE_ALARM (Android 13+): exigido para
 *   vibrar em segundo plano; sem esse atributo o Android suprime a vibração.
 */
public final class AlarmAlert {

    private static MediaPlayer mediaPlayer;
    private static long lastFireAt = 0;

    private AlarmAlert() {}

    /** Evita dispare duplo (service + receiver + JS) na mesma troca. */
    public static void fire(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastFireAt < 2500) return;
        lastFireAt = now;
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

            // ~5s: 3 pulsos curtos + 1 longo
            long[] pattern = {0, 800, 200, 800, 200, 800, 200, 1000};
            VibrationEffect effect = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? VibrationEffect.createWaveform(pattern, -1)
                : null;

            if (Build.VERSION.SDK_INT >= 33) {
                // Android 13+: sem USAGE_ALARM a vibração em background é suprimida
                VibrationAttributes attrs = new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build();
                vibrator.vibrate(effect, attrs);
            } else if (effect != null) {
                vibrator.vibrate(effect);
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
