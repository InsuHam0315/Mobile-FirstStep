package com.example.firststep.game.device;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.Surface;
import android.view.WindowManager;
import com.example.firststep.game.presentation.discovery.DiscoveryController;

public final class DiscoverySensors implements SensorEventListener {
  private final SensorManager manager;
  private final WindowManager window;
  private final DiscoveryController controller;
  private final long generation;
  private final Sensor rotation, accel, light;
  private int accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
  private boolean closed, headingRegistered;
  private final SensorRegistration registration;

  public DiscoverySensors(Context context, DiscoveryController controller, long generation) {
    this.controller = controller;
    this.generation = generation;
    manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    window = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    rotation = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    accel = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    light = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_LIGHT);
    registration =
        new SensorRegistration(
            new SensorRegistration.Port() {
              @Override
              public boolean register(SensorRegistration.Kind kind) {
                if (manager == null) return false;
                Sensor sensor;
                int delay;
                switch (kind) {
                  case HEADING:
                    sensor = window == null ? null : rotation;
                    delay = SensorManager.SENSOR_DELAY_UI;
                    break;
                  case MOTION:
                    sensor = accel;
                    delay = SensorManager.SENSOR_DELAY_GAME;
                    break;
                  default:
                    sensor = light;
                    delay = SensorManager.SENSOR_DELAY_NORMAL;
                }
                if (sensor == null) return false;
                try {
                  return manager.registerListener(DiscoverySensors.this, sensor, delay);
                } catch (RuntimeException e) {
                  return false;
                }
              }

              @Override
              public void unregister() {
                if (manager != null) manager.unregisterListener(DiscoverySensors.this);
              }
            });
  }

  public void start() {
    SensorRegistration.Availability available = registration.start();
    headingRegistered = available.heading;
    controller.sensorAvailability(generation, available.motion, available.light);
    if (!available.heading) controller.headingUnavailable(generation, false);
  }

  public void close() {
    closed = true;
    registration.close();
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int value) {
    if (!closed && sensor == rotation) {
      accuracy = value;
      if (value == SensorManager.SENSOR_STATUS_UNRELIABLE)
        controller.headingUnavailable(generation, true);
    }
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (closed
        || event.timestamp <= 0
        || SystemClock.elapsedRealtimeNanos() - event.timestamp > 1000000000L
        || event.timestamp > SystemClock.elapsedRealtimeNanos()) return;
    if (event.sensor == rotation) {
      if (!headingRegistered || window == null || window.getDefaultDisplay() == null) {
        controller.headingUnavailable(generation, false);
        return;
      }
      float[] matrix = new float[9], remapped = new float[9], orient = new float[3];
      SensorManager.getRotationMatrixFromVector(matrix, event.values);
      int x = SensorManager.AXIS_X, y = SensorManager.AXIS_Y;
      int r = window.getDefaultDisplay().getRotation();
      if (r == Surface.ROTATION_90) {
        x = SensorManager.AXIS_Y;
        y = SensorManager.AXIS_MINUS_X;
      } else if (r == Surface.ROTATION_180) {
        x = SensorManager.AXIS_MINUS_X;
        y = SensorManager.AXIS_MINUS_Y;
      } else if (r == Surface.ROTATION_270) {
        x = SensorManager.AXIS_MINUS_Y;
        y = SensorManager.AXIS_X;
      }
      SensorManager.remapCoordinateSystem(matrix, x, y, remapped);
      SensorManager.getOrientation(remapped, orient);
      controller.heading(
          generation,
          (float) Math.toDegrees(orient[0]),
          accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
              && event.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE);
    } else if (event.sensor == accel) {
      float[] v = event.values;
      controller.acceleration(
          generation, (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
    } else if (event.sensor == light) controller.light(generation, event.values[0]);
  }
}
