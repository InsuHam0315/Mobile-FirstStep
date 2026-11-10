package com.example.firststep.game.device.authored;

import java.util.Objects;
import java.util.Optional;

/** Surveyed destination configuration. The production provider currently contains no target. */
public final class AuthoredTargetConfig {
  public interface Provider {
    Optional<AuthoredTargetConfig> get(String targetId);
  }

  public final String targetId;
  public final String surveyReference;
  public final double latitude;
  public final double longitude;
  public final double radiusMeters;
  /** Magnetic azimuth in degrees, using the rotation-vector orientation reference. */
  public final double headingDegrees;
  public final float maxAccuracyMeters;
  public final long maxFixAgeNanos;

  public AuthoredTargetConfig(String targetId, String surveyReference, double latitude,
      double longitude, double radiusMeters, double headingDegrees, float maxAccuracyMeters,
      long maxFixAgeNanos) {
    this.targetId = required(targetId);
    this.surveyReference = required(surveyReference);
    if (!validCoordinate(latitude, longitude) || !Double.isFinite(radiusMeters)
        || radiusMeters <= 0 || !Double.isFinite(headingDegrees) || headingDegrees < 0
        || headingDegrees >= 360 || !Float.isFinite(maxAccuracyMeters) || maxAccuracyMeters <= 0
        || maxFixAgeNanos <= 0) {
      throw new IllegalArgumentException("Invalid surveyed target configuration");
    }
    this.latitude = latitude;
    this.longitude = longitude;
    this.radiusMeters = radiusMeters;
    this.headingDegrees = headingDegrees;
    this.maxAccuracyMeters = maxAccuracyMeters;
    this.maxFixAgeNanos = maxFixAgeNanos;
  }

  private static String required(String value) {
    Objects.requireNonNull(value);
    if (value.trim().isEmpty()) throw new IllegalArgumentException("Missing configuration provenance");
    return value;
  }

  static boolean validCoordinate(double latitude, double longitude) {
    return Double.isFinite(latitude) && latitude >= -90 && latitude <= 90
        && Double.isFinite(longitude) && longitude >= -180 && longitude <= 180;
  }

  /** Activation requires an independently surveyed, reviewed provider. */
  public static Provider production() {
    return targetId -> Optional.empty();
  }
}
