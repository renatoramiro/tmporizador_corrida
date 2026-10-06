package com.temporizadorciclico.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

    /**
     * Grava o GPX em Downloads/ (MediaStore no Android 10+; arquivo público antes).
     * Retorna JSON: { ok, path } ou { ok:false, error }.
     */
    @JavascriptInterface
    public String saveGpx(String filename, String content) {
        try {
            String name = (filename == null || filename.trim().isEmpty())
                ? "atividade.gpx" : filename.trim();
            if (!name.toLowerCase().endsWith(".gpx")) name += ".gpx";
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);

            String path;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = activity.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("MediaStore recusou o arquivo");
                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os == null) throw new IllegalStateException("Não abriu o stream");
                    os.write(bytes);
                    os.flush();
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, done, null, null);
                path = Environment.DIRECTORY_DOWNLOADS + "/" + name;
            } else {
                if (!hasWritePermission()) {
                    JSONObject denied = new JSONObject();
                    denied.put("ok", false);
                    denied.put("error", "Sem permissão de armazenamento");
                    return denied.toString();
                }
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("Não criou a pasta Downloads");
                }
                File out = new File(dir, name);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(bytes);
                    fos.flush();
                }
                path = out.getAbsolutePath();
            }
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("path", path);
            return o.toString();
        } catch (Exception e) {
            try {
                JSONObject o = new JSONObject();
                o.put("ok", false);
                o.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                return o.toString();
            } catch (Exception ignored) {
                return "{\"ok\":false,\"error\":\"falha ao salvar\"}";
            }
        }
    }

    private boolean hasWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;
    }
}
