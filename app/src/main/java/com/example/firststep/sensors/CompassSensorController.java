package com.example.firststep.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

/** Reads a real magnetic heading, preferring rotation vector and falling back to accel+mag. */
public final class CompassSensorController implements SensorEventListener {
  public interface Listener {
    void onHeading(float magneticDegrees, int accuracy, String message);

    void onUnavailable(String message);
  }

  private final Context appContext;
  private final Listener listener;
  private final SensorManager sensorManager;
  private final Sensor rotationVector;
  private final Sensor accelerometer;
  private final Sensor magnetometer;
  private final float[] rotationMatrix = new float[9];
  private final float[] remappedMatrix = new float[9];
  private final float[] orientation = new float[3];
  private final float[] gravity = new float[3];
  private final float[] magnetic = new float[3];
  private boolean hasGravity;
  private boolean hasMagnetic;
  private boolean running;
  private long gravityAt = -1, magneticAt = -1;
  private int gravityAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
  private int magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;

  public CompassSensorController(Context context, Listener listener) {
    this.appContext = context.getApplicationContext();
    this.listener = listener;
    sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
    rotationVector =
        sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    accelerometer =
        sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    magnetometer =
        sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
  }

  public boolean start() {
    if (running) return true;
    if (sensorManager == null) {
      if (listener != null) listener.onUnavailable("방향 센서를 사용할 수 없어요.");
      return false;
    }
    running = true;
    hasGravity = false;
    hasMagnetic = false;
    gravityAt = -1;
    magneticAt = -1;
    gravityAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    boolean registered =
        rotationVector != null
            && sensorManager.registerListener(
                this, rotationVector, SensorManager.SENSOR_DELAY_GAME);
    if (!registered && accelerometer != null && magnetometer != null) {
      sensorManager.unregisterListener(this);
      boolean accelRegistered =
          sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
      boolean magneticRegistered =
          sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
      registered = accelRegistered && magneticRegistered;
    }
    if (!registered) {
      stop();
      if (listener != null) listener.onUnavailable("방향 센서를 시작할 수 없어요. 화면 조작으로 계속할 수 있어요.");
      return false;
    }
    return true;
  }

  public void stop() {
    if (sensorManager != null) sensorManager.unregisterListener(this);
    running = false;
    hasGravity = false;
    hasMagnetic = false;
    gravityAt = -1;
    magneticAt = -1;
    gravityAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (!running || event == null) return;
    if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
      invalidate(event.sensor);
      if (listener != null) listener.onUnavailable("방향 정확도가 낮아요. 천천히 움직이거나 화면 조작을 이용해 주세요.");
      return;
    }
    if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
      SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
      publishHeading(event.accuracy);
      return;
    }
    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      copy(event.values, gravity);
      hasGravity = true;
      gravityAccuracy = event.accuracy;
      gravityAt = event.timestamp / 1000000L;
    } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
      copy(event.values, magnetic);
      hasMagnetic = true;
      magneticAccuracy = event.accuracy;
      magneticAt = event.timestamp / 1000000L;
    }
    long now = android.os.SystemClock.elapsedRealtime();
    if (hasGravity
        && hasMagnetic
        && (now - gravityAt < 0
            || now - magneticAt < 0
            || now - gravityAt > 500
            || now - magneticAt > 500)) {
      if (listener != null) listener.onUnavailable("방향 센서 신호가 잠시 끊겼어요. 기기를 멈추고 다시 맞춰 주세요.");
      return;
    }
    if (hasGravity
        && hasMagnetic
        && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, magnetic)) {
      publishHeading(Math.min(gravityAccuracy, magneticAccuracy));
    }
  }

  private void publishHeading(int accuracy) {
    remapForDisplay(rotationMatrix, remappedMatrix, displayRotation());
    SensorManager.getOrientation(remappedMatrix, orientation);
    float magneticDegrees = (float) Math.toDegrees(orientation[0]);
    magneticDegrees = CompassMath.normalizeDegrees(magneticDegrees);
    if (Float.isNaN(magneticDegrees)) {
      if (listener != null) listener.onUnavailable("방향을 읽을 수 없어요. 화면 조작으로 계속할 수 있어요.");
      return;
    }
    if (listener != null) listener.onHeading(magneticDegrees, accuracy, accuracyMessage(accuracy));
  }

  private int displayRotation() {
    WindowManager manager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
    Display display = manager == null ? null : manager.getDefaultDisplay();
    return display == null ? Surface.ROTATION_0 : display.getRotation();
  }

  /** Remap the device axes so azimuth follows the current display's top edge. */
  static void remapForDisplay(float[] input, float[] output, int rotation) {
    int xAxis = SensorManager.AXIS_X, yAxis = SensorManager.AXIS_Y;
    if (rotation == Surface.ROTATION_90) {
      xAxis = SensorManager.AXIS_Y;
      yAxis = SensorManager.AXIS_MINUS_X;
    } else if (rotation == Surface.ROTATION_180) {
      xAxis = SensorManager.AXIS_MINUS_X;
      yAxis = SensorManager.AXIS_MINUS_Y;
    } else if (rotation == Surface.ROTATION_270) {
      xAxis = SensorManager.AXIS_MINUS_Y;
      yAxis = SensorManager.AXIS_X;
    }
    SensorManager.remapCoordinateSystem(input, xAxis, yAxis, output);
  }

  private static void copy(float[] source, float[] destination) {
    System.arraycopy(source, 0, destination, 0, Math.min(source.length, destination.length));
  }

  private static String accuracyMessage(int accuracy) {
    if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
      return "방향 정확도가 낮아요. 휴대전화를 천천히 움직여 센서를 보정해 주세요.";
    return "방향을 천천히 맞춰 주세요.";
  }

  private void invalidate(Sensor sensor) {
    if (sensor == null) return;
    if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      hasGravity = false;
      gravityAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
      gravityAt = -1;
    } else if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
      hasMagnetic = false;
      magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
      magneticAt = -1;
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) invalidate(sensor);
    if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW && listener != null)
      listener.onUnavailable("방향 정확도가 낮아요. 천천히 움직이거나 화면 조작을 이용해 주세요.");
  }
}
