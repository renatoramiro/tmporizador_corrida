package com.temporizadorciclico.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

/** Notificações: permanente (tela de bloqueio) + alerta de troca de etapa. */
public final class NotificationHelper {

    public static final String CHANNEL_STATUS = "timer-status-v2";
    public static final String CHANNEL_ALERT = "timer-alertas-v2";
    public static final int NOTIF_STATUS = 42;
    public static final int NOTIF_ALERT = 43;

    private NotificationHelper() {}

    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel status = new NotificationChannel(
            CHANNEL_STATUS,
            "Treino em andamento",
            NotificationManager.IMPORTANCE_LOW
        );
        status.setDescription("Mostra o tempo do treino na tela de bloqueio");
        status.setShowBadge(false);
        status.enableVibration(false);
        status.setSound(null, null);
        nm.createNotificationChannel(status);

        NotificationChannel alert = new NotificationChannel(
            CHANNEL_ALERT,
            "Alertas do treino",
            NotificationManager.IMPORTANCE_MAX
        );
        alert.setDescription("Vibra e toca ao trocar de etapa");
        alert.enableVibration(true);
        alert.setVibrationPattern(new long[]{0, 800, 200, 800, 200, 800, 200, 1000});
        alert.enableLights(true);
        alert.setLightColor(0xFF4F46E5);
        nm.createNotificationChannel(alert);
    }

    /** Notificação permanente com countdown — fica visível na tela de bloqueio. */
    public static Notification buildStatus(Context ctx, String title, String text, String big) {
        ensureChannels(ctx);
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (launch == null) launch = new Intent(ctx, MainActivity.class);
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent content = PendingIntent.getActivity(ctx, 0, launch, piFlags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4F46E5)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(big != null ? big : text))
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // VISIBILITY_PUBLIC = aparece na tela de bloqueio
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    public static void showAlert(Context ctx, String title, String body) {
        ensureChannels(ctx);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (launch == null) launch = new Intent(ctx, MainActivity.class);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent content = PendingIntent.getActivity(ctx, 0, launch, piFlags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4F46E5)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(content)
            .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE);
        nm.notify(NOTIF_ALERT, b.build());
    }
}
