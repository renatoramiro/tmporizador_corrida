package com.temporizadorciclico.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Foreground service que:
 * 1. Mostra o countdown na tela de bloqueio (notificação permanente).
 * 2. Dispara vibração/som no fim de cada etapa pelo relógio de parede.
 * 3. Agenda AlarmManager.setAlarmClock como rede de segurança (Doze).
 */
public class TimerService extends Service {

    public static final String ACTION_START = "com.temporizadorciclico.app.START";
    public static final String ACTION_STOP = "com.temporizadorciclico.app.STOP";
    public static final String ACTION_ALERT = "com.temporizadorciclico.app.ALERT";

    public static final String EXTRA_STAGES = "stages";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_DEADLINE = "deadline";
    public static final String EXTRA_IN_TRANSITION = "inTransition";
    public static final String EXTRA_TRANSITION_MS = "transitionMs";

    private static final String PREFS = "timer_native";
    private static final long TICK_MS = 250;
    private static final long TRANS_DEFAULT = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private JSONArray stages = new JSONArray();
    private int index = 0;
    private long deadline = 0;
    private boolean inTransition = false;
    private long transitionMs = TRANS_DEFAULT;
    private boolean running = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                step();
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stopWork();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_ALERT.equals(action)) {
            // AlertReceiver notificou: se o service ainda não está com estado, carrega do prefs
            if (!running) {
                restore();
                running = true;
                handler.removeCallbacks(tick);
                handler.post(tick);
            }
            startAsForeground("Trocando de etapa...");
            return START_NOT_STICKY;
        }

        // START
        try {
            if (intent != null && intent.hasExtra(EXTRA_STAGES)) {
                stages = new JSONArray(intent.getStringExtra(EXTRA_STAGES));
                index = intent.getIntExtra(EXTRA_INDEX, 0);
                deadline = intent.getLongExtra(EXTRA_DEADLINE, 0);
                inTransition = intent.getBooleanExtra(EXTRA_IN_TRANSITION, false);
                transitionMs = intent.getLongExtra(EXTRA_TRANSITION_MS, TRANS_DEFAULT);
                persist();
            } else {
                restore();
            }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }

        running = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
        startAsForeground(statusTitle());
        scheduleNativeAlarms();
        // GPS continua em background com o service (tela bloqueada / app minimizado)
        try {
            LocationTracker.get(this).start();
        } catch (Exception ignored) {
        }
        // NOT_STICKY: o sistema não deve reanimar o service sozinho.
        // Continuidade do treino (tela bloqueada) fica por conta do AlarmManager + prefs.
        return START_NOT_STICKY;
    }

    private void startAsForeground(String text) {
        try {
            String title = inTransition ? "Trocando de etapa" : stageName();
            String time = format(remainingMs());
            NotificationHelper.ensureChannels(this);
            android.app.Notification notification = NotificationHelper.buildStatus(
                this,
                title + "  ·  " + time,
                text,
                title + "\n" + time + " restantes  ·  etapa " + (index + 1) + "/" + Math.max(1, stages.length())
            );
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.NOTIF_STATUS,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                );
            } else {
                startForeground(NotificationHelper.NOTIF_STATUS, notification);
            }
        } catch (Exception ignored) {
        }
    }

    private void step() {
        long rest = deadline - System.currentTimeMillis();
        if (rest <= 0) {
            if (inTransition) {
                // fim da transição -> próxima etapa
                index = (index + 1) % Math.max(1, stages.length());
                inTransition = false;
                deadline = System.currentTimeMillis() + stageMs(index);
            } else {
                // fim da etapa -> alerta + transição
                inTransition = true;
                deadline = System.currentTimeMillis() + transitionMs;
                fireAlert();
                scheduleNativeAlarms();
            }
            persist();
            startAsForeground(statusTitle());
            return;
        }
        // atualiza countdown na tela de bloqueio (a cada ~1s o texto muda)
        startAsForeground(statusTitle());
    }

    private void fireAlert() {
        // Som STREAM_ALARM + vibração (~5s) — funciona com a tela bloqueada
        AlarmAlert.fire(this);
        NotificationHelper.showAlert(this, nextStageName(), "Trocando de etapa: " + stageName() + " → " + nextStageName());
    }

    private void scheduleNativeAlarms() {
        try {
            JSONArray alerts = new JSONArray();
            int idx = index;
            boolean trans = inTransition;
            long faseFim = deadline;
            // pula se já estamos em transição (o alerta dela já saiu)
            if (trans) {
                idx = (idx + 1) % Math.max(1, stages.length());
                trans = false;
                faseFim = faseFim + stageMs(idx);
            }
            for (int i = 0; i < 30; i++) {
                JSONObject a = new JSONObject();
                a.put("id", 1000 + i);
                a.put("at", faseFim);
                a.put("title", nameAt((idx + 1) % Math.max(1, stages.length())));
                a.put("body", "Trocando de etapa: " + nameAt(idx) + " → " + nameAt((idx + 1) % Math.max(1, stages.length())));
                alerts.put(a);
                int next = (idx + 1) % Math.max(1, stages.length());
                faseFim = faseFim + transitionMs + stageMs(next);
                idx = next;
            }
            AlarmScheduler.schedule(this, alerts);
        } catch (Exception ignored) {
        }
    }

    private void stopWork() {
        running = false;
        handler.removeCallbacks(tick);
        AlarmScheduler.cancel(this);
        try {
            AlarmAlert.stop();
        } catch (Exception ignored) {
        }
        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }
        cancelAllNotifications();
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        ed.clear();
        ed.apply();
    }

    private void cancelAllNotifications() {
        try {
            android.app.NotificationManager nm =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            // IDs usados pelo service + pelo plugin LocalNotifications (fallback JS)
            for (int id = 1; id < 1100; id++) {
                nm.cancel(id);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Usuário fechou o app (swipe em Recentes). Encerra o treino por completo:
     * service, alarmes e notificações — nada fica rodando em background.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            stopWork();
        } catch (Exception ignored) {
        }
        try {
            // Fechou o app: encerra o GPS junto (sem salvar — use "Finalizar" para gravar)
            LocationTracker.get(this).stop();
        } catch (Exception ignored) {
        }
        try {
            stopSelf();
        } catch (Exception ignored) {
        }
        super.onTaskRemoved(rootIntent);
    }

    /** Para o service e tudo que ele agendou. Usado pela Activity ao fechar. */
    public static void stopAll(Context ctx) {
        try {
            Intent i = new Intent(ctx, TimerService.class);
            i.setAction(ACTION_STOP);
            ctx.startService(i);
        } catch (Exception ignored) {
        }
        try {
            AlarmScheduler.cancel(ctx);
        } catch (Exception ignored) {
        }
        try {
            AlarmAlert.stop();
        } catch (Exception ignored) {
        }
        try {
            LocationTracker.get(ctx).stop();
        } catch (Exception ignored) {
        }
        try {
            android.app.NotificationManager nm =
                (android.app.NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                for (int id = 1; id < 1100; id++) {
                    nm.cancel(id);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private long remainingMs() {
        return Math.max(0, deadline - System.currentTimeMillis());
    }

    private String statusTitle() {
        String t = format(remainingMs());
        return (inTransition ? "Trocando de etapa" : stageName()) + "  ·  " + t;
    }

    private String stageName() {
        return nameAt(index);
    }

    private String nextStageName() {
        return nameAt((index + 1) % Math.max(1, stages.length()));
    }

    private String nameAt(int i) {
        try {
            JSONObject s = stages.optJSONObject(i);
            return s != null ? s.optString("nome", "Etapa " + (i + 1)) : "Etapa " + (i + 1);
        } catch (Exception e) {
            return "Etapa " + (i + 1);
        }
    }

    private long stageMs(int i) {
        try {
            JSONObject s = stages.optJSONObject(i);
            if (s == null) return 60000;
            return (s.optLong("min", 0) * 60L + s.optLong("seg", 0)) * 1000L;
        } catch (Exception e) {
            return 60000;
        }
    }

    private static String format(long ms) {
        long total = (ms + 999) / 1000;
        long m = total / 60;
        long s = total % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    private void persist() {
        try {
            SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
            ed.putString("stages", stages.toString());
            ed.putInt("index", index);
            ed.putLong("deadline", deadline);
            ed.putBoolean("inTransition", inTransition);
            ed.putLong("transitionMs", transitionMs);
            ed.apply();
        } catch (Exception ignored) {
        }
    }

    private void restore() {
        try {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            String raw = p.getString("stages", null);
            if (raw != null) stages = new JSONArray(raw);
            index = p.getInt("index", 0);
            deadline = p.getLong("deadline", 0);
            inTransition = p.getBoolean("inTransition", false);
            transitionMs = p.getLong("transitionMs", TRANS_DEFAULT);
            if (deadline <= 0 && stages.length() > 0) {
                deadline = System.currentTimeMillis() + stageMs(0);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
