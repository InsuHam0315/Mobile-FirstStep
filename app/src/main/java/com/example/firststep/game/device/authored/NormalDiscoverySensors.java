package com.example.firststep.game.device.authored;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.WindowManager;
import com.example.firststep.game.device.SensorRegistration;

/** Foreground sensor session. Late events and fixes older than one second are ignored. */
public final class NormalDiscoverySensors implements SensorEventListener, AutoCloseable {
  private final SensorManager manager;
  private final WindowManager window;
  private final Sensor rotation, acceleration;
  private final NormalDiscoverySession session;
  private final SensorRegistration registration;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private HeadingReadinessTimeout headingTimeout;
  private final Runnable checkHeading;
  private boolean closed, started, headingRegistered;
  private int accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;

  public NormalDiscoverySensors(Context context, NormalDiscoverySession session) {
    this.session = session;
    checkHeading = () -> {
      if (closed || headingTimeout == null) return;
      HeadingReadinessTimeout.Outcome outcome = headingTimeout.poll(SystemClock.elapsedRealtime());
      if (outcome == HeadingReadinessTimeout.Outcome.UNAVAILABLE) session.headingUnavailable(false);
      else if (outcome == HeadingReadinessTimeout.Outcome.UNRELIABLE) session.headingUnavailable(true);
    };
    manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    window = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    rotation = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    acceleration = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    registration = new SensorRegistration(new SensorRegistration.Port() {
      @Override public boolean register(SensorRegistration.Kind kind) {
        Sensor sensor = kind == SensorRegistration.Kind.HEADING ? rotation
            : kind == SensorRegistration.Kind.MOTION ? acceleration : null;
        if (manager == null || sensor == null || (kind == SensorRegistration.Kind.HEADING && window == null)) return false;
        try {
          return manager.registerListener(NormalDiscoverySensors.this, sensor,
              kind == SensorRegistration.Kind.MOTION ? SensorManager.SENSOR_DELAY_GAME : SensorManager.SENSOR_DELAY_UI);
        } catch (RuntimeException unavailable) { return false; }
      }
      @Override public void unregister() {
        if (manager != null) manager.unregisterListener(NormalDiscoverySensors.this);
      }
    });
  }

  public void start() {
    if (closed || started) return;
    started = true;
    SensorRegistration.Availability available = registration.start();
    headingRegistered = available.heading;
    if (headingRegistered) {
      headingTimeout = new HeadingReadinessTimeout(SystemClock.elapsedRealtime());
      handler.postDelayed(checkHeading, HeadingReadinessTimeout.FIRST_FIX_TIMEOUT_MS);
    }
    session.availability(available.heading, available.motion);
  }

  @Override public void close() {
    closed = true;
    handler.removeCallbacks(checkHeading);
    if (headingTimeout != null) headingTimeout.close();
    registration.close();
  }

  @Override public void onAccuracyChanged(Sensor sensor, int value) {
    if (!closed && sensor == rotation) {
      accuracy = value;
      if (value == SensorManager.SENSOR_STATUS_UNRELIABLE) {
        if (headingTimeout != null) headingTimeout.invalidHeading();
        session.headingUnavailable(true);
      }
    }
  }

  @Override public void onSensorChanged(SensorEvent event) {
    long now = SystemClock.elapsedRealtimeNanos();
    if (closed || event.timestamp <= 0 || event.timestamp > now
        || now - event.timestamp > 1_000_000_000L) return;
    if (event.sensor == rotation) {
      if (!headingRegistered || window == null || window.getDefaultDisplay() == null) {
        if (headingTimeout != null) headingTimeout.invalidHeading();
        session.headingUnavailable(false);
        return;
      }
      float[] matrix = new float[9], remapped = new float[9], orientation = new float[3];
      try {
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        int x = SensorManager.AXIS_X, y = SensorManager.AXIS_Y;
        int r = window.getDefaultDisplay().getRotation();
        if (r == Surface.ROTATION_90) { x = SensorManager.AXIS_Y; y = SensorManager.AXIS_MINUS_X; }
        else if (r == Surface.ROTATION_180) { x = SensorManager.AXIS_MINUS_X; y = SensorManager.AXIS_MINUS_Y; }
        else if (r == Surface.ROTATION_270) { x = SensorManager.AXIS_MINUS_Y; y = SensorManager.AXIS_X; }
        if (!SensorManager.remapCoordinateSystem(matrix, x, y, remapped)) {
          if (headingTimeout != null) headingTimeout.invalidHeading();
          session.headingUnavailable(true);
          return;
        }
        SensorManager.getOrientation(remapped, orientation);
        float degrees = (float) Math.toDegrees(orientation[0]);
        boolean reliable = Float.isFinite(degrees)
            && accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
            && event.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE;
        if (headingTimeout != null) {
          if (reliable) {
            headingTimeout.validHeading();
            handler.removeCallbacks(checkHeading);
          } else {
            headingTimeout.invalidHeading();
          }
        }
        session.heading(degrees, reliable);
      } catch (RuntimeException invalid) {
        if (headingTimeout != null) headingTimeout.invalidHeading();
        session.headingUnavailable(true);
      }
    } else if (event.sensor == acceleration && event.values.length >= 3) {
      float[] v = event.values;
      session.acceleration((float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
    }
  }
}
