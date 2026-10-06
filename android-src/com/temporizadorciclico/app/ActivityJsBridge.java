package com.temporizadorciclico.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Bridge JS → nativo para GPS + banco de atividades.
 * Métodos primitivos (tipos simples) para o JavascriptInterface do WebView.
 */
public class ActivityJsBridge {

    public static final int REQ_LOCATION = 4242;

    private final Activity activity;
    private final LocationTracker tracker;
    private final ActivityDb db;
    private JSONArray stageLog = new JSONArray();

    public ActivityJsBridge(Activity activity, LocationTracker tracker, ActivityDb db) {
        this.activity = activity;
        this.tracker = tracker;
        this.db = db;
    }

    public LocationTracker getTracker() {
        return tracker;
    }

    @JavascriptInterface
    public boolean hasLocationPermission() {
        return tracker.hasPermission();
    }

    @JavascriptInterface
    public void requestLocationPermission() {
        try {
            if (tracker.hasPermission()) return;
            ActivityCompat.requestPermissions(
                activity,
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                },
                REQ_LOCATION
            );
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public boolean startGps() {
        return tracker.start();
    }

    @JavascriptInterface
    public void stopGps() {
        tracker.stop();
    }

    @JavascriptInterface
    public String getLiveGps() {
        return tracker.liveStats().toString();
    }

    @JavascriptInterface
    public void resetGps() {
        tracker.reset();
    }

    /** Registra o log de etapas (JSON) para mostrar no resumo. */
    @JavascriptInterface
    public void setStageLog(String json) {
        try {
            stageLog = new JSONArray(json != null ? json : "[]");
        } catch (Exception e) {
            stageLog = new JSONArray();
        }
    }

    /**
     * Finaliza e salva a atividade. Se o GPS não tiver pontos, ainda assim
     * salva com as métricas do timer (distância 0) para não perder o treino.
     */
    @JavascriptInterface
    public String saveActivity(String title) {
        try {
            tracker.stop();
            JSONObject snap = tracker.snapshot();
            long id = db.saveFromSnapshot(snap, title, stageLog);
            tracker.reset();
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("ok", id > 0);
            return o.toString();
        } catch (Exception e) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", -1);
                o.put("ok", false);
                o.put("error", e.getMessage());
                return o.toString();
            } catch (Exception ignored) {
                return "{\"ok\":false,\"id\":-1}";
            }
        }
    }

    @JavascriptInterface
    public String listActivities() {
        return db.listActivities().toString();
    }

    @JavascriptInterface
    public String getActivity(double id) {
        return db.getActivity((long) id).toString();
    }

    @JavascriptInterface
    public boolean deleteActivity(double id) {
        return db.deleteActivity((long) id);
    }
}
