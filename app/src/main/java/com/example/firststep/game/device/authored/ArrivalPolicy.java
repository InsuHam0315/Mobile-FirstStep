package com.example.firststep.game.device.authored;

/** Pure conservative arrival decision from a fresh, non-mock foreground location. */
public final class ArrivalPolicy {
  private static final double EARTH_RADIUS_METERS = 6371008.8;

  public enum Verdict { ADMITTED, NOT_ARRIVED, STALE, LOW_ACCURACY, MOCK, INVALID }

  public static final class Fix {
    public final double latitude;
    public final double longitude;
    public final float accuracyMeters;
    public final long elapsedRealtimeNanos;
    public final boolean mock;

    public Fix(double latitude, double longitude, float accuracyMeters,
        long elapsedRealtimeNanos, boolean mock) {
      this.latitude = latitude;
      this.longitude = longitude;
      this.accuracyMeters = accuracyMeters;
      this.elapsedRealtimeNanos = elapsedRealtimeNanos;
      this.mock = mock;
    }
  }

  private ArrivalPolicy() {}

  public static Verdict evaluate(AuthoredTargetConfig config, Fix fix, long nowElapsedNanos) {
    if (config == null || fix == null || !AuthoredTargetConfig.validCoordinate(fix.latitude, fix.longitude)
        || !Float.isFinite(fix.accuracyMeters) || fix.accuracyMeters < 0
        || fix.elapsedRealtimeNanos <= 0 || nowElapsedNanos <= 0) return Verdict.INVALID;
    if (fix.mock) return Verdict.MOCK;
    if (fix.elapsedRealtimeNanos > nowElapsedNanos
        || nowElapsedNanos - fix.elapsedRealtimeNanos > config.maxFixAgeNanos) return Verdict.STALE;
    if (fix.accuracyMeters > config.maxAccuracyMeters) return Verdict.LOW_ACCURACY;
    double distance = distanceMeters(config.latitude, config.longitude, fix.latitude, fix.longitude);
    return distance + fix.accuracyMeters <= config.radiusMeters ? Verdict.ADMITTED : Verdict.NOT_ARRIVED;
  }

  private static double distanceMeters(double latitude1, double longitude1, double latitude2, double longitude2) {
    double lat1 = Math.toRadians(latitude1);
    double lat2 = Math.toRadians(latitude2);
    double deltaLat = lat2 - lat1;
    double deltaLon = Math.toRadians(longitude2 - longitude1);
    double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
        + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
    return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1.0, Math.max(0.0, a))));
  }
}
