package com.temporizadorciclico.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * SQLite local: histórico de atividades + pontos GPS.
 * Sem rede, sem login — tudo fica no aparelho.
 */
public class ActivityDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "atividades.db";
    private static final int DB_VERSION = 2;

    public ActivityDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE activities (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  title TEXT NOT NULL," +
            "  type TEXT NOT NULL DEFAULT 'corrida'," +
            "  started_at INTEGER NOT NULL," +
            "  ended_at INTEGER NOT NULL," +
            "  duration_sec REAL NOT NULL," +
            "  distance_m REAL NOT NULL," +
            "  avg_speed REAL NOT NULL," +
            "  max_speed REAL NOT NULL," +
            "  avg_pace_min_km REAL NOT NULL," +
            "  steps_json TEXT" +
            ")"
        );
        db.execSQL(
            "CREATE TABLE points (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  activity_id INTEGER NOT NULL," +
            "  ts INTEGER NOT NULL," +
            "  lat REAL NOT NULL," +
            "  lng REAL NOT NULL," +
            "  alt REAL," +
            "  speed REAL," +
            "  acc REAL," +
            "  cum_dist REAL," +
            "  FOREIGN KEY(activity_id) REFERENCES activities(id) ON DELETE CASCADE" +
            ")"
        );
        db.execSQL("CREATE INDEX idx_points_activity ON points(activity_id, ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Preserva histórico: só adiciona colunas novas.
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE activities ADD COLUMN type TEXT NOT NULL DEFAULT 'corrida'");
            } catch (Exception ignored) {
            }
        }
    }

    /** Persiste a atividade a partir do snapshot do LocationTracker. */
    public long saveFromSnapshot(JSONObject snap, String title, String type, JSONArray stageLog) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long startedAt = snap.optLong("startedAt", System.currentTimeMillis());
            long endedAt = snap.optLong("endedAt", System.currentTimeMillis());
            double distance = snap.optDouble("distance", 0);
            double avgSpeed = snap.optDouble("avgSpeed", 0);
            double maxSpeed = snap.optDouble("maxSpeed", 0);
            double elapsed = snap.optDouble("elapsedSec", (endedAt - startedAt) / 1000.0);
            double pace = snap.optDouble("paceMinKm", 0);

            ContentValues cv = new ContentValues();
            cv.put("title", title != null && !title.isEmpty() ? title : "Treino");
            cv.put("type", normalizeType(type));
            cv.put("started_at", startedAt);
            cv.put("ended_at", endedAt);
            cv.put("duration_sec", elapsed);
            cv.put("distance_m", distance);
            cv.put("avg_speed", avgSpeed);
            cv.put("max_speed", maxSpeed);
            cv.put("avg_pace_min_km", pace);
            cv.put("steps_json", stageLog != null ? stageLog.toString() : "[]");
            long id = db.insert("activities", null, cv);

            JSONArray points = snap.optJSONArray("points");
            double cum = 0;
            if (points != null) {
                double prevLat = 0, prevLng = 0;
                boolean hasPrev = false;
                for (int i = 0; i < points.length(); i++) {
                    JSONObject p = points.optJSONObject(i);
                    if (p == null) continue;
                    double la = p.optDouble("lat");
                    double lo = p.optDouble("lng");
                    if (hasPrev) {
                        cum += LocationTracker.haversine(prevLat, prevLng, la, lo);
                    }
                    prevLat = la;
                    prevLng = lo;
                    hasPrev = true;

                    ContentValues pv = new ContentValues();
                    pv.put("activity_id", id);
                    pv.put("ts", p.optLong("ts"));
                    pv.put("lat", la);
                    pv.put("lng", lo);
                    pv.put("alt", p.optDouble("alt"));
                    pv.put("speed", p.optDouble("speed"));
                    pv.put("acc", p.optDouble("acc"));
                    pv.put("cum_dist", cum);
                    db.insert("points", null, pv);
                }
            }

            db.setTransactionSuccessful();
            return id;
        } catch (Exception e) {
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    public static String normalizeType(String type) {
        if (type == null) return "corrida";
        String t = type.trim().toLowerCase();
        if (t.equals("caminhada") || t.equals("walk") || t.equals("walking")) return "caminhada";
        if (t.equals("bicicleta") || t.equals("bike") || t.equals("cycling") || t.equals("ciclismo")) return "bicicleta";
        return "corrida";
    }

    /** Atualiza título e tipo (editáveis pelo usuário). */
    public boolean updateActivity(long id, String title, String type) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        if (title != null && !title.trim().isEmpty()) {
            cv.put("title", title.trim());
        }
        if (type != null && !type.trim().isEmpty()) {
            cv.put("type", normalizeType(type));
        }
        if (cv.size() == 0) return false;
        int n = db.update("activities", cv, "id = ?", new String[]{String.valueOf(id)});
        return n > 0;
    }

    public JSONObject listActivities() {
        JSONObject out = new JSONObject();
        JSONArray arr = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                "SELECT id, title, type, started_at, ended_at, duration_sec, distance_m," +
                "       avg_speed, max_speed, avg_pace_min_km" +
                " FROM activities ORDER BY started_at DESC",
                null
            );
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getLong(0));
                o.put("title", c.getString(1));
                o.put("type", c.getString(2) != null ? c.getString(2) : "corrida");
                o.put("startedAt", c.getLong(3));
                o.put("endedAt", c.getLong(4));
                o.put("durationSec", c.getDouble(5));
                o.put("distance", c.getDouble(6));
                o.put("avgSpeed", c.getDouble(7));
                o.put("maxSpeed", c.getDouble(8));
                o.put("avgPaceMinKm", c.getDouble(9));
                arr.put(o);
            }
            out.put("activities", arr);
        } catch (Exception e) {
            try {
                out.put("activities", new JSONArray());
                out.put("error", e.getMessage());
            } catch (Exception ignored) {
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public JSONObject getActivity(long id) {
        JSONObject out = new JSONObject();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                "SELECT id, title, type, started_at, ended_at, duration_sec, distance_m," +
                "       avg_speed, max_speed, avg_pace_min_km, steps_json" +
                " FROM activities WHERE id = ?",
                new String[]{String.valueOf(id)}
            );
            if (!c.moveToFirst()) {
                out.put("found", false);
                return out;
            }
            out.put("found", true);
            out.put("id", c.getLong(0));
            out.put("title", c.getString(1));
            out.put("type", c.getString(2) != null ? c.getString(2) : "corrida");
            out.put("startedAt", c.getLong(3));
            out.put("endedAt", c.getLong(4));
            out.put("durationSec", c.getDouble(5));
            out.put("distance", c.getDouble(6));
            out.put("avgSpeed", c.getDouble(7));
            out.put("maxSpeed", c.getDouble(8));
            out.put("avgPaceMinKm", c.getDouble(9));
            try {
                out.put("steps", new JSONArray(c.getString(10)));
            } catch (Exception e) {
                out.put("steps", new JSONArray());
            }
            c.close();
            c = null;

            JSONArray points = new JSONArray();
            c = db.rawQuery(
                "SELECT ts, lat, lng, alt, speed, acc, cum_dist" +
                " FROM points WHERE activity_id = ? ORDER BY ts ASC",
                new String[]{String.valueOf(id)}
            );
            while (c.moveToNext()) {
                JSONObject p = new JSONObject();
                p.put("ts", c.getLong(0));
                p.put("lat", c.getDouble(1));
                p.put("lng", c.getDouble(2));
                p.put("alt", c.isNull(3) ? 0 : c.getDouble(3));
                p.put("speed", c.isNull(4) ? 0 : c.getDouble(4));
                p.put("acc", c.isNull(5) ? 0 : c.getDouble(5));
                p.put("cumDist", c.isNull(6) ? 0 : c.getDouble(6));
                points.put(p);
            }
            out.put("points", points);
        } catch (Exception e) {
            try {
                out.put("found", false);
                out.put("error", e.getMessage());
            } catch (Exception ignored) {
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public boolean deleteActivity(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("points", "activity_id = ?", new String[]{String.valueOf(id)});
            int n = db.delete("activities", "id = ?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
            return n > 0;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Exporta todas as atividades (com pontos GPS e etapas) em JSON estruturado
     * para análise externa (IA, planilhas, scripts).
     */
    public JSONObject exportAll() {
        JSONObject out = new JSONObject();
        JSONArray activities = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            out.put("exportedAt", System.currentTimeMillis());
            out.put("app", "Ritmo");
            out.put("format", "ritmo.export/1");

            c = db.rawQuery(
                "SELECT id, title, type, started_at, ended_at, duration_sec, distance_m," +
                "       avg_speed, max_speed, avg_pace_min_km, steps_json" +
                " FROM activities ORDER BY started_at ASC",
                null
            );
            double totalDist = 0, totalDur = 0;
            int count = 0;
            while (c.moveToNext()) {
                JSONObject a = new JSONObject();
                long id = c.getLong(0);
                a.put("id", id);
                a.put("title", c.getString(1));
                a.put("type", c.getString(2) != null ? c.getString(2) : "corrida");
                a.put("startedAt", c.getLong(3));
                a.put("endedAt", c.getLong(4));
                a.put("durationSec", c.getDouble(5));
                a.put("distanceM", c.getDouble(6));
                a.put("avgSpeedMs", c.getDouble(7));
                a.put("maxSpeedMs", c.getDouble(8));
                a.put("avgPaceMinKm", c.getDouble(9));
                try {
                    a.put("stages", new JSONArray(c.getString(10)));
                } catch (Exception e) {
                    a.put("stages", new JSONArray());
                }
                totalDist += c.getDouble(6);
                totalDur += c.getDouble(5);
                count++;

                JSONArray points = new JSONArray();
                Cursor p = null;
                try {
                    p = db.rawQuery(
                        "SELECT ts, lat, lng, alt, speed, acc, cum_dist" +
                        " FROM points WHERE activity_id = ? ORDER BY ts ASC",
                        new String[]{String.valueOf(id)}
                    );
                    while (p.moveToNext()) {
                        JSONObject pt = new JSONObject();
                        pt.put("ts", p.getLong(0));
                        pt.put("lat", p.getDouble(1));
                        pt.put("lng", p.getDouble(2));
                        pt.put("alt", p.isNull(3) ? 0 : p.getDouble(3));
                        pt.put("speedMs", p.isNull(4) ? 0 : p.getDouble(4));
                        pt.put("accM", p.isNull(5) ? 0 : p.getDouble(5));
                        pt.put("cumDistM", p.isNull(6) ? 0 : p.getDouble(6));
                        points.put(pt);
                    }
                } finally {
                    if (p != null) p.close();
                }
                a.put("points", points);
                activities.put(a);
            }
            out.put("activities", activities);

            JSONObject summary = new JSONObject();
            summary.put("activityCount", count);
            summary.put("totalDistanceM", totalDist);
            summary.put("totalDurationSec", totalDur);
            out.put("summary", summary);
        } catch (Exception e) {
            try {
                out.put("error", e.getMessage());
            } catch (Exception ignored) {
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }
}
