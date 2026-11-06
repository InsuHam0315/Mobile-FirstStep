package com.example.firststep.game.ui.discovery;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.game.composition.discovery.DiscoveryHost;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.discovery.DiscoveryController;
import com.example.firststep.game.presentation.discovery.DiscoveryState;

public final class DiscoveryActivity extends AppCompatActivity
    implements DiscoveryController.Listener {
  public static final String MODE_EXTRA = "com.example.firststep.discovery.MODE",
      SIMULATED = "SIMULATED";
  private static final int CAMERA_REQUEST = 403;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private DiscoveryController controller;
  private DiscoveryHost host;
  private boolean simulated, active;
  private long generation, permissionGeneration;
  private final Runnable ticker =
      new Runnable() {
        public void run() {
          if (active) {
            controller.tick();
            handler.postDelayed(this, 100);
          }
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.game_discovery);
    simulated = BuildConfig.DEBUG && SIMULATED.equals(getIntent().getStringExtra(MODE_EXTRA));
    host =
        new DiscoveryHost(
            this, this, (PreviewView) findViewById(R.id.game_discovery_preview), simulated, this);
    controller = host.controller();
    View root = findViewById(R.id.game_discovery_scroll);
    int left = root.getPaddingLeft(),
        top = root.getPaddingTop(),
        right = root.getPaddingRight(),
        bottom = root.getPaddingBottom();
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets i =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          v.setPadding(left + i.left, top + i.top, right + i.right, bottom + i.bottom);
          return insets;
        });
    findViewById(R.id.game_discovery_arrival).setOnClickListener(v -> controller.arrival());
    findViewById(R.id.game_discovery_align_fallback)
        .setOnClickListener(v -> controller.headingFallback());
    findViewById(R.id.game_discovery_method_shake)
        .setOnClickListener(v -> controller.select(DiscoveryResult.Method.SHAKE));
    findViewById(R.id.game_discovery_method_light)
        .setOnClickListener(v -> controller.select(DiscoveryResult.Method.LIGHT));
    findViewById(R.id.game_discovery_camera_fallback)
        .setOnClickListener(
            v -> {
              host.closeCamera();
              controller.cameraFallback();
            });
    findViewById(R.id.game_discovery_retry)
        .setOnClickListener(
            v -> {
              controller.retryCamera();
              startCamera(true);
            });
    findViewById(R.id.game_discovery_exit).setOnClickListener(v -> finish());
    findViewById(R.id.game_discovery_hold)
        .setOnTouchListener(
            (v, event) -> {
              switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                  controller.holdStart();
                  return true;
                case MotionEvent.ACTION_UP:
                  controller.holdCancel();
                  v.performClick();
                  return true;
                case MotionEvent.ACTION_CANCEL:
                  controller.holdCancel();
                  return true;
                default:
                  return true;
              }
            });
  }

  @Override
  protected void onResume() {
    super.onResume();
    setResult(RESULT_CANCELED);
    active = true;
    View art = findViewById(R.id.game_discovery_art);
    art.animate().cancel();
    art.setScaleX(1f);
    art.setScaleY(1f);
    generation = host.resume();
    if (!simulated) {
      startCamera(false);
      handler.post(ticker);
    }
  }

  @Override
  protected void onPause() {
    active = false;
    handler.removeCallbacks(ticker);
    findViewById(R.id.game_discovery_art).animate().cancel();
    host.pause();
    super.onPause();
  }

  private void startCamera(boolean requestPermission) {
    if (!active) return;
    if (simulated) {
      host.startCamera();
      return;
    }
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      permissionGeneration = generation;
      controller.setCamera(generation, DiscoveryState.CameraStatus.DENIED);
      if (requestPermission)
        ActivityCompat.requestPermissions(
            this, new String[] {Manifest.permission.CAMERA}, CAMERA_REQUEST);
      return;
    }
    host.startCamera();
  }

  @Override
  public void onRequestPermissionsResult(
      int code, @NonNull String[] permissions, @NonNull int[] grants) {
    super.onRequestPermissionsResult(code, permissions, grants);
    if (code != CAMERA_REQUEST || !active || permissionGeneration != generation) return;
    if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startCamera(false);
    else controller.setCamera(generation, DiscoveryState.CameraStatus.DENIED);
  }

  public DiscoveryState snapshot() {
    return controller.snapshot();
  }

  @Override
  public void onState(DiscoveryState s) {
    ((TextView) findViewById(R.id.game_discovery_disclosure))
        .setText(
            s.method == DiscoveryResult.Method.LIGHT
                ? R.string.game_discovery_disclosure_light
                : R.string.game_discovery_disclosure);
    TextView status = findViewById(R.id.game_discovery_status);
    if (status == null) return;
    int cameraText;
    switch (s.camera) {
      case READY:
        cameraText = R.string.game_discovery_camera_ready;
        break;
      case DENIED:
      case PERMISSION_REQUIRED:
        cameraText = R.string.game_discovery_camera_denied;
        break;
      case UNAVAILABLE:
        cameraText = R.string.game_discovery_camera_unavailable;
        break;
      case ERROR:
        cameraText = R.string.game_discovery_camera_error;
        break;
      case FALLBACK:
        cameraText = R.string.game_discovery_camera_fallback_status;
        break;
      default:
        cameraText = R.string.game_discovery_camera_starting;
    }
    String mode =
        getString(
            s.simulated
                ? R.string.game_discovery_mode_simulated
                : R.string.game_discovery_mode_actual);
    String selected =
        getString(
            s.method == DiscoveryResult.Method.LIGHT
                ? R.string.game_discovery_selected_light
                : R.string.game_discovery_selected_shake);
    String detail =
        s.method == DiscoveryResult.Method.LIGHT
            ? getString(lightResource(s.lightStatus))
            : getString(R.string.game_discovery_shake_count, s.shakeCount);
    if (!s.motionAvailable && s.method == DiscoveryResult.Method.SHAKE)
      detail = getString(R.string.game_discovery_motion_missing);
    status.setText(
        getString(
            R.string.game_discovery_status_format, mode, getString(cameraText), selected, detail));
    int h;
    switch (s.heading) {
      case UNAVAILABLE:
        h = R.string.game_discovery_heading_unavailable;
        break;
      case UNRELIABLE:
        h = R.string.game_discovery_heading_unreliable;
        break;
      case STALE:
        h = R.string.game_discovery_heading_stale;
        break;
      case MISALIGNED:
        h = R.string.game_discovery_heading_misaligned;
        break;
      case ALIGNED:
        h = R.string.game_discovery_heading_aligned;
        break;
      case FALLBACK:
        h = R.string.game_discovery_heading_fallback_status;
        break;
      default:
        h = R.string.game_discovery_heading_waiting;
    }
    ((TextView) findViewById(R.id.game_discovery_heading)).setText(h);
    TextView result = findViewById(R.id.game_discovery_result);
    if (s.result == null) result.setText("");
    else {
      int text =
          s.result.source == DiscoveryResult.Source.LONG_PRESS
              ? R.string.game_discovery_result_hold
              : s.result.source == DiscoveryResult.Source.SIMULATED
                  ? R.string.game_discovery_result_simulated
                  : R.string.game_discovery_result_real;
      result.setText(
          getString(
              R.string.game_discovery_result_format,
              getString(text),
              mode,
              s.cameraFallback ? getString(R.string.game_discovery_fallback_camera_suffix) : "",
              s.headingFallback ? getString(R.string.game_discovery_fallback_heading_suffix) : ""));
    }
  }

  private int lightResource(String status) {
    switch (status) {
      case "missing":
        return R.string.game_discovery_light_missing;
      case "too_dark":
        return R.string.game_discovery_light_too_dark;
      case "ready":
        return R.string.game_discovery_light_ready;
      case "dark":
        return R.string.game_discovery_light_dark;
      case "stale":
        return R.string.game_discovery_light_stale;
      default:
        return R.string.game_discovery_light_baseline;
    }
  }

  @Override
  public void onCompletion(DiscoveryResult result) {
    View art = findViewById(R.id.game_discovery_art);
    art.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    art.animate().scaleX(1.35f).scaleY(1.35f).setDuration(500).start();
    Intent data =
        new Intent()
            .putExtra("sessionId", result.sessionId)
            .putExtra("targetId", result.targetId)
            .putExtra("arrivalSimulated", result.arrivalSimulated)
            .putExtra("method", result.method.name())
            .putExtra("source", result.source.name())
            .putExtra("cameraFallback", result.cameraFallback)
            .putExtra("headingFallback", result.headingFallback);
    setResult(RESULT_OK, data);
  }
}
