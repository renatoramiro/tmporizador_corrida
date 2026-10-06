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
    private static final int DB_VERSION = 1;

    public ActivityDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE activities (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  title TEXT NOT NULL," +
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
        db.execSQL("DROP TABLE IF EXISTS points");
        db.execSQL("DROP TABLE IF EXISTS activities");
        onCreate(db);
    }

    /** Persiste a atividade a partir do snapshot do LocationTracker. */
    public long saveFromSnapshot(JSONObject snap, String title, JSONArray stageLog) {
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

    public JSONObject listActivities() {
        JSONObject out = new JSONObject();
        JSONArray arr = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                "SELECT id, title, started_at, ended_at, duration_sec, distance_m," +
                "       avg_speed, max_speed, avg_pace_min_km" +
                " FROM activities ORDER BY started_at DESC",
                null
            );
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getLong(0));
                o.put("title", c.getString(1));
                o.put("startedAt", c.getLong(2));
                o.put("endedAt", c.getLong(3));
                o.put("durationSec", c.getDouble(4));
                o.put("distance", c.getDouble(5));
                o.put("avgSpeed", c.getDouble(6));
                o.put("maxSpeed", c.getDouble(7));
                o.put("avgPaceMinKm", c.getDouble(8));
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
                "SELECT id, title, started_at, ended_at, duration_sec, distance_m," +
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
            out.put("startedAt", c.getLong(2));
            out.put("endedAt", c.getLong(3));
            out.put("durationSec", c.getDouble(4));
            out.put("distance", c.getDouble(5));
            out.put("avgSpeed", c.getDouble(6));
            out.put("maxSpeed", c.getDouble(7));
            out.put("avgPaceMinKm", c.getDouble(8));
            try {
                out.put("steps", new JSONArray(c.getString(9)));
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
}
