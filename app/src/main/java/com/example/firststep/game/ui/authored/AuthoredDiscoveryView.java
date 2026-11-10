package com.example.firststep.game.ui.authored;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.example.firststep.R;
import com.example.firststep.game.device.DiscoveryCamera;
import com.example.firststep.game.device.authored.NormalDiscoverySensors;
import com.example.firststep.game.device.authored.NormalDiscoverySession;
import com.example.firststep.game.presentation.discovery.DiscoveryState;
import com.example.firststep.game.ui.GameVisuals;

/** Foreground presentation and device lifetime for a single admitted discovery. */
final class AuthoredDiscoveryView {
  private final AppCompatActivity activity;
  private final NormalDiscoverySession session;
  private final AuthoredEpisodeActivity.DiscoveryInput injected;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final PreviewView preview;
  private final TextView cameraStatus, headingStatus, motionStatus;
  private final Button cameraFallback, headingFallback, hold;
  private DiscoveryCamera camera;
  private NormalDiscoverySensors sensors;
  private boolean closed;

  private final Runnable tick = new Runnable() {
    @Override public void run() {
      if (closed) return;
      session.tick();
      main.postDelayed(this, 100L);
    }
  };

  AuthoredDiscoveryView(AppCompatActivity activity, LinearLayout body,
      NormalDiscoverySession session, Runnable cancel, AuthoredEpisodeActivity.DiscoveryInput injected) {
    this.activity = activity;
    this.session = session;
    this.injected = injected;
    body.removeAllViews();
    TextView disclosure = text(body);
    disclosure.setText(R.string.game_authored_camera_disclosure);
    View overlay = activity.getLayoutInflater().inflate(
        R.layout.game_authored_discovery_preview, body, false);
    preview = overlay.findViewById(R.id.game_authored_preview);
    preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    body.addView(overlay);
    cameraStatus = text(body);
    headingStatus = text(body);
    motionStatus = text(body);
    cameraFallback = button(body, R.string.game_discovery_camera_fallback, () -> {
      closeCamera();
      session.cameraFallback();
    });
    headingFallback = button(body, R.string.game_discovery_align_fallback, session::headingFallback);
    hold = button(body, R.string.game_discovery_hold, () -> {});
    hold.setOnLongClickListener(view -> {
      session.holdStart();
      return true;
    });
    hold.setOnTouchListener((view, event) -> {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          session.holdStart();
          return true;
        case MotionEvent.ACTION_UP:
          session.holdCancel();
          view.performClick();
          return true;
        case MotionEvent.ACTION_CANCEL:
          session.holdCancel();
          return true;
        default: return true;
      }
    });
    button(body, R.string.game_authored_cancel_discovery, cancel);
  }

  void start() {
    session.start();
    if (injected == null) {
      sensors = new NormalDiscoverySensors(activity, session);
      sensors.start();
      if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
          == PackageManager.PERMISSION_GRANTED) {
        camera = new DiscoveryCamera(activity, activity, preview, session::camera);
        camera.start();
      } else {
        session.camera(DiscoveryState.CameraStatus.DENIED);
      }
    } else {
      injected.start();
    }
    main.post(tick);
  }

  void render(DiscoveryState state) {
    if (closed) return;
    cameraStatus.setText(cameraText(state.camera));
    headingStatus.setText(headingText(state.heading));
    motionStatus.setText(state.motionAvailable
        ? activity.getString(R.string.game_discovery_shake_count, state.shakeCount)
        : activity.getString(R.string.game_discovery_motion_missing));
    boolean cameraUnavailable = state.camera == DiscoveryState.CameraStatus.DENIED
        || state.camera == DiscoveryState.CameraStatus.UNAVAILABLE
        || state.camera == DiscoveryState.CameraStatus.ERROR;
    cameraFallback.setVisibility(cameraUnavailable ? View.VISIBLE : View.GONE);
    headingFallback.setVisibility(state.heading == DiscoveryState.HeadingStatus.UNAVAILABLE
        || state.heading == DiscoveryState.HeadingStatus.UNRELIABLE
        || state.heading == DiscoveryState.HeadingStatus.STALE ? View.VISIBLE : View.GONE);
    preview.setVisibility(state.cameraFallback || cameraUnavailable ? View.GONE : View.VISIBLE);
    hold.setEnabled(state.active && !state.completed
        && (state.camera == DiscoveryState.CameraStatus.READY || state.cameraFallback)
        && (state.heading == DiscoveryState.HeadingStatus.ALIGNED || state.headingFallback));
  }

  void close() {
    if (closed) return;
    closed = true;
    main.removeCallbacks(tick);
    closeCamera();
    if (sensors != null) sensors.close();
    if (injected != null) injected.close();
    session.cancel();
  }

  private void closeCamera() {
    if (camera != null) {
      camera.close();
      camera = null;
    }
  }

  private int cameraText(DiscoveryState.CameraStatus state) {
    switch (state) {
      case READY: return R.string.game_discovery_camera_ready;
      case DENIED:
      case PERMISSION_REQUIRED: return R.string.game_discovery_camera_denied;
      case UNAVAILABLE: return R.string.game_discovery_camera_unavailable;
      case ERROR: return R.string.game_discovery_camera_error;
      case FALLBACK: return R.string.game_discovery_camera_fallback_status;
      default: return R.string.game_discovery_camera_starting;
    }
  }

  private int headingText(DiscoveryState.HeadingStatus state) {
    switch (state) {
      case MISALIGNED: return R.string.game_authored_heading_misaligned;
      case ALIGNED: return R.string.game_authored_heading_aligned;
      case UNAVAILABLE: return R.string.game_discovery_heading_unavailable;
      case UNRELIABLE: return R.string.game_discovery_heading_unreliable;
      case STALE: return R.string.game_discovery_heading_stale;
      case FALLBACK: return R.string.game_discovery_heading_fallback_status;
      default: return R.string.game_discovery_heading_waiting;
    }
  }

  private TextView text(LinearLayout body) {
    TextView view = new TextView(activity);
    view.setTextColor(activity.getColor(R.color.game_color_text));
    body.addView(view);
    return view;
  }

  private Button button(LinearLayout body, int label, Runnable action) {
    Button view = new Button(activity);
    GameVisuals.action(view, false);
    view.setText(label);
    view.setOnClickListener(v -> action.run());
    body.addView(view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT));
    return view;
  }
}
