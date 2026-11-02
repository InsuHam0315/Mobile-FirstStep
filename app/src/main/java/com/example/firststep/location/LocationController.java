package com.example.firststep.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import androidx.core.content.ContextCompat;

/** Foreground-only updates. Deliberately never accepts getLastKnownLocation as arrival evidence. */
public final class LocationController implements LocationListener {
    public interface Listener { void onLocation(Location location, String message); }
    private final Context context;
    private final LocationManager manager;
    private final Listener listener;
    public LocationController(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }
    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    @SuppressWarnings("MissingPermission")
    public boolean start() {
        stop();
        if (!hasPermission(context)) { listener.onLocation(null, "정확한 위치 권한이 필요합니다."); return false; }
        boolean requested = false;
        try {
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 1000, 0, this, Looper.getMainLooper());
                    requested = true;
                }
            }
            listener.onLocation(null, requested ? "현재 위치를 확인하고 있어요. 실외에서 잠시 기다려 주세요." : "기기의 위치 기능을 켜 주세요.");
        } catch (SecurityException | IllegalArgumentException error) {
            stop(); listener.onLocation(null, "위치를 사용할 수 없습니다. 권한과 위치 설정을 확인해 주세요."); return false;
        }
        return requested;
    }
    public void stop() { if (manager != null) manager.removeUpdates(this); }
    @Override public void onLocationChanged(Location location) { listener.onLocation(location, "위치를 수신했습니다."); }
    @Override public void onProviderDisabled(String provider) { listener.onLocation(null, "위치 수신이 중단됐습니다. 위치 설정을 확인해 주세요."); }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
}
