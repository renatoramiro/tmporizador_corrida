package com.temporizadorciclico.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Rastreia o percurso com LocationManager (GPS_PROVIDER).
 * Sem dependência do Play Services. Os pontos ficam em memória e
 * são gravados no SQLite quando a atividade é finalizada.
 */
public class LocationTracker {

    public static class Point {
        public final double lat;
        public final double lng;
        public final double alt;
        public final float speed;      // m/s
        public final float accuracy;   // metros
        public final long ts;

        Point(double lat, double lng, double alt, float speed, float accuracy, long ts) {
            this.lat = lat;
            this.lng = lng;
            this.alt = alt;
            this.speed = speed;
            this.accuracy = accuracy;
            this.ts = ts;
        }
    }

    /** Distância de Haversine em metros. */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static final float MIN_ACCURACY_M = 50f;
    private static final float MIN_DELTA_M = 2f;
    private static final long MIN_DELTA_MS = 1000L;

    private static LocationTracker instance;

    /** Singleton compartilhado entre Activity, bridge JS e TimerService. */
    public static synchronized LocationTracker get(Context context) {
        if (instance == null) {
            instance = new LocationTracker(context);
        }
        return instance;
    }

    private final Context context;
    private final CopyOnWriteArrayList<Point> points = new CopyOnWriteArrayList<>();
    private LocationManager locationManager;
    private LocationListener listener;
    private volatile boolean tracking = false;
    private volatile double distanceM = 0;
    private volatile float lastSpeed = 0;
    private volatile float maxSpeed = 0;
    private volatile float accuracy = 999f;
    private volatile double lat = 0;
    private volatile double lng = 0;
    private volatile boolean hasFix = false;
    private volatile long startedAt = 0;

    public LocationTracker(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized boolean start() {
        if (tracking) return true;
        if (!hasPermission()) return false;

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return false;

        points.clear();
        distanceM = 0;
        lastSpeed = 0;
        maxSpeed = 0;
        accuracy = 999f;
        hasFix = false;
        startedAt = System.currentTimeMillis();

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                handleLocation(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        try {
            // GPS precisa mais bateria, mas dá precisão de corrida.
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper());
            // Rede como fallback rápido do primeiro fix
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 3000L, 0f, listener, Looper.getMainLooper());
            } catch (Exception ignored) {
            }
            Location last = null;
            try {
                last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (Exception ignored) {
            }
            if (last == null) {
                try {
                    last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                } catch (Exception ignored) {
                }
            }
            if (last != null) handleLocation(last);
            tracking = true;
            return true;
        } catch (Exception e) {
            tracking = false;
            return false;
        }
    }

    public synchronized void stop() {
        tracking = false;
        if (locationManager != null && listener != null) {
            try {
                locationManager.removeUpdates(listener);
            } catch (Exception ignored) {
            }
        }
        listener = null;
    }

    private void handleLocation(Location location) {
        if (!tracking || location == null) return;

        float acc = location.getAccuracy();
        if (acc > MIN_ACCURACY_M) return;

        long ts = location.getTime() > 0 ? location.getTime() : System.currentTimeMillis();
        double la = location.getLatitude();
        double lo = location.getLongitude();
        double al = location.hasAltitude() ? location.getAltitude() : 0;
        float sp = location.hasSpeed() ? location.getSpeed() : 0f;

        Point prev = points.isEmpty() ? null : points.get(points.size() - 1);
        if (prev != null) {
            long dt = ts - prev.ts;
            double d = haversine(prev.lat, prev.lng, la, lo);
            // filtra ruído GPS parado / pico impossível
            if (d < MIN_DELTA_M && dt < MIN_DELTA_MS) {
                // atualiza precisão/posição mesmo sem mover
                accuracy = acc;
                this.lat = la;
                this.lng = lo;
                hasFix = true;
                return;
            }
            if (d > 200 && dt < 10000) {
                // salto absurdo: descarta
                return;
            }
            distanceM += d;
            // velocidade derivada é mais estável que a do chip quando parado
            if (dt > 500 && sp <= 0 && d > 1) {
                sp = (float) (d / (dt / 1000.0));
            }
        }

        if (sp > maxSpeed) maxSpeed = sp;
        lastSpeed = sp;
        accuracy = acc;
        this.lat = la;
        this.lng = lo;
        hasFix = true;
        points.add(new Point(la, lo, al, sp, acc, ts));
    }

    public boolean isTracking() {
        return tracking;
    }

    public boolean hasPermission() {
        try {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /** Snapshot ao vivo para o JS (poll). */
    public JSONObject liveStats() {
        JSONObject o = new JSONObject();
        try {
            o.put("tracking", tracking);
            o.put("hasFix", hasFix);
            o.put("distance", distanceM);
            o.put("speed", lastSpeed);
            o.put("maxSpeed", maxSpeed);
            o.put("accuracy", accuracy);
            o.put("lat", lat);
            o.put("lng", lng);
            o.put("points", points.size());
            o.put("startedAt", startedAt);
            if (points.size() >= 2) {
                Point a = points.get(0);
                Point b = points.get(points.size() - 1);
                double elapsed = (b.ts - a.ts) / 1000.0;
                o.put("elapsedSec", elapsed);
                if (elapsed > 1 && distanceM > 5) {
                    o.put("avgSpeed", distanceM / elapsed);
                    // pace em min/km
                    o.put("paceMinKm", (elapsed / 60.0) / (distanceM / 1000.0));
                }
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    /** Serializa pontos e métricas para salvar a atividade. */
    public JSONObject snapshot() {
        JSONObject o = new JSONObject();
        try {
            JSONArray arr = new JSONArray();
            for (Point p : points) {
                JSONObject j = new JSONObject();
                j.put("lat", p.lat);
                j.put("lng", p.lng);
                j.put("alt", p.alt);
                j.put("speed", p.speed);
                j.put("acc", p.accuracy);
                j.put("ts", p.ts);
                arr.put(j);
            }
            o.put("points", arr);
            o.put("distance", distanceM);
            o.put("maxSpeed", maxSpeed);
            o.put("startedAt", startedAt);
            o.put("endedAt", System.currentTimeMillis());
            if (points.size() >= 2) {
                Point a = points.get(0);
                Point b = points.get(points.size() - 1);
                double elapsed = (b.ts - a.ts) / 1000.0;
                o.put("elapsedSec", elapsed);
                if (elapsed > 0) {
                    o.put("avgSpeed", distanceM / elapsed);
                    o.put("paceMinKm", distanceM > 5 ? (elapsed / 60.0) / (distanceM / 1000.0) : 0);
                }
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    public void reset() {
        points.clear();
        distanceM = 0;
        lastSpeed = 0;
        maxSpeed = 0;
        hasFix = false;
    }
}
