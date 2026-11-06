package com.example.firststep.game.device.homemap;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.example.firststep.BuildConfig;
import com.example.firststep.location.LocationController;

/** Foreground current-location adapter. Call on main thread; stop on pause. No arrival authority. */
public final class H05LocationAdapter implements AutoCloseable {
  public interface Listener { void onLocation(Location location, String message); }
  private final Context context;
  private final Listener listener;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private LocationController controller;
  private Runnable expiry;
  private long generation;
  private boolean active, closed;

  public H05LocationAdapter(Context context, Listener listener) {
    this.context = context.getApplicationContext();
    this.listener = listener;
  }

  public void start() {
    stop();
    if (closed) return;
    active = true;
    long token = generation;
    controller = new LocationController(context, (location, message) -> {
      if (active && token == generation) accept(location, message, false);
    });
    controller.start(); // Reused adapter explicitly explains denied/approximate-only/no-provider states.
  }

  @SuppressWarnings("deprecation")
  private void accept(Location location, String message, boolean injected) {
    if (expiry != null) handler.removeCallbacks(expiry);
    expiry = null;
    long now = SystemClock.elapsedRealtimeNanos();
    if (location == null) {
      listener.onLocation(null, message);
      return;
    }
    if (!LocationFreshness.valid(location.getLatitude(), location.getLongitude(),
        location.hasAccuracy() ? location.getAccuracy() : Float.NaN,
        location.getElapsedRealtimeNanos(), now)) {
      listener.onLocation(null, "오래되었거나 유효하지 않은 위치입니다. 현재 위치를 다시 요청해 주세요.");
      return;
    }
    Location copy = new Location(location);
    boolean simulated = injected || copy.isFromMockProvider();
    listener.onLocation(copy, simulated
        ? "시뮬레이션 위치입니다. 실제 캠퍼스 수신·도착을 확인한 결과가 아닙니다."
        : "현재 위치를 수신했습니다. 시설 위치나 탐방 도착의 검증 근거는 아닙니다.");
    long token = generation;
    expiry = () -> {
      if (active && token == generation)
        listener.onLocation(null, "위치 수신 후 30초가 지나 표시를 지웠습니다. 다시 요청해 주세요.");
    };
    long remaining = LocationFreshness.MAX_AGE_NANOS - (now - copy.getElapsedRealtimeNanos());
    handler.postDelayed(expiry, Math.max(1, (remaining + 999_999L) / 1_000_000L));
  }

  /** Explicit test input; does not change providers, permissions or stored progress. */
  public void debugInject(Location location) {
    if (!BuildConfig.DEBUG || closed) return;
    stop();
    active = true;
    accept(location, "시뮬레이션 위치 없음", true);
  }

  public void stop() {
    active = false;
    generation++;
    if (controller != null) controller.stop();
    controller = null;
    if (expiry != null) handler.removeCallbacks(expiry);
    expiry = null;
    listener.onLocation(null, "현재 위치 표시를 지웠습니다. 필요하면 다시 요청해 주세요.");
  }

  @Override public void close() {
    stop();
    closed = true;
  }
}
