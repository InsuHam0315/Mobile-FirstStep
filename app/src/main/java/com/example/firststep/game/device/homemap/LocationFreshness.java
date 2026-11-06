package com.example.firststep.game.device.homemap;

/** Monotonic freshness policy for received current-location indicators, never arrival evidence. */
public final class LocationFreshness {
  public static final long MAX_AGE_NANOS = 30_000_000_000L;
  private LocationFreshness() {}

  public static boolean valid(double latitude, double longitude, float accuracy,
      long observedNanos, long nowNanos) {
    return Double.isFinite(latitude) && Math.abs(latitude) <= 90
        && Double.isFinite(longitude) && Math.abs(longitude) <= 180
        && Float.isFinite(accuracy) && accuracy > 0
        && observedNanos > 0 && nowNanos >= observedNanos
        && nowNanos - observedNanos < MAX_AGE_NANOS;
  }
}
