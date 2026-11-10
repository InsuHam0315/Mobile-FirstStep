package com.example.firststep.game.device.authored;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Objects;

/** Foreground GPS acquisition. Call start/retry/stop on the main thread and stop on pause. */
public final class ForegroundArrival {
  public enum State {
    PERMISSION_REQUIRED, APPROXIMATE_ONLY, UNAVAILABLE, LOCATING, ADMITTED,
    NOT_ARRIVED, STALE, LOW_ACCURACY, MOCK, INVALID
  }

  public interface Listener { void onState(State state); }

  private static final long ACQUISITION_TIMEOUT_MS = 30000;
  private final Context context;
  private final LocationManager manager;
  private final AuthoredTargetConfig config;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ArrivalRequestGuard guard = new ArrivalRequestGuard();
  private LocationListener activeListener;
  private Runnable timeout;
  private volatile Observation observation;

  private static final class Observation {
    final long token;
    final ArrivalPolicy.Fix fix;
    Observation(long token, ArrivalPolicy.Fix fix) { this.token = token; this.fix = fix; }
  }

  public ForegroundArrival(Context context, AuthoredTargetConfig config, Listener listener) {
    this.context = context.getApplicationContext();
    this.manager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    this.config = Objects.requireNonNull(config);
    this.listener = Objects.requireNonNull(listener);
  }

  /** Called only after the user chooses to request location. Handle the result by calling retry. */
  public static void requestPermissions(Activity activity, int requestCode) {
    ActivityCompat.requestPermissions(activity,
        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
        requestCode);
  }

  public void start() {
    stop();
    long token = guard.start();
    boolean fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED;
    boolean coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        == PackageManager.PERMISSION_GRANTED;
    if (!fine) {
      emit(token, coarse ? State.APPROXIMATE_ONLY : State.PERMISSION_REQUIRED);
      return;
    }
    if (manager == null) {
      emit(token, State.UNAVAILABLE);
      return;
    }
    try {
      if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        emit(token, State.UNAVAILABLE);
        return;
      }
      LocationListener request = new LocationListener() {
        @Override public void onLocationChanged(Location location) { receive(token, location); }
        @Override public void onProviderDisabled(String provider) {
          if (guard.current(token)) observation = null;
          emit(token, State.UNAVAILABLE);
        }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
      };
      activeListener = request;
      requestUpdates(request);
      timeout = () -> {
        if (guard.current(token) && observation == null) emit(token, State.UNAVAILABLE);
      };
      main.postDelayed(timeout, ACQUISITION_TIMEOUT_MS);
      emit(token, State.LOCATING);
    } catch (SecurityException | IllegalArgumentException failure) {
      removeUpdates();
      emit(token, State.UNAVAILABLE);
    }
  }

  @SuppressWarnings("MissingPermission")
  private void requestUpdates(LocationListener request) {
    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, request, Looper.getMainLooper());
  }

  public void retry() { start(); }

  public void stop() {
    guard.stop();
    observation = null;
    removeUpdates();
  }

  private void removeUpdates() {
    if (timeout != null) main.removeCallbacks(timeout);
    timeout = null;
    if (manager != null && activeListener != null) {
      try { manager.removeUpdates(activeListener); } catch (SecurityException ignored) { }
    }
    activeListener = null;
  }

  /** Rechecks monotonic freshness on every use. A stopped request never supplies evidence. */
  public ArrivalPolicy.Verdict currentVerdict() {
    Observation current = observation;
    if (current == null || !guard.current(current.token) || manager == null
        || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return ArrivalPolicy.Verdict.INVALID;
    try {
      if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return ArrivalPolicy.Verdict.INVALID;
    } catch (SecurityException | IllegalArgumentException unavailable) {
      return ArrivalPolicy.Verdict.INVALID;
    }
    ArrivalPolicy.Verdict verdict = ArrivalPolicy.evaluate(config, current.fix,
        SystemClock.elapsedRealtimeNanos());
    return current == observation && guard.current(current.token)
        ? verdict : ArrivalPolicy.Verdict.INVALID;
  }

  private void receive(long token, Location location) {
    if (!guard.current(token)) return;
    if (location == null || !location.hasAccuracy()) {
      observation = null;
      emit(token, State.INVALID);
      return;
    }
    ArrivalPolicy.Fix fix = new ArrivalPolicy.Fix(location.getLatitude(), location.getLongitude(),
        location.getAccuracy(), location.getElapsedRealtimeNanos(), location.isFromMockProvider());
    observation = new Observation(token, fix);
    emit(token, state(ArrivalPolicy.evaluate(config, fix, SystemClock.elapsedRealtimeNanos())));
  }

  private static State state(ArrivalPolicy.Verdict verdict) {
    return State.valueOf(verdict.name());
  }

  private void emit(long token, State state) {
    if (guard.current(token)) listener.onState(state);
  }
}
