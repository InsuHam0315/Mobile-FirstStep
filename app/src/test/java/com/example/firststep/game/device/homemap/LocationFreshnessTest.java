package com.example.firststep.game.device.homemap;

import static org.junit.Assert.*;
import org.junit.Test;

public class LocationFreshnessTest {
  @Test public void monotonicDeadlineRejectsStaleFutureAndMissingFixes() {
    long observed = 1_000_000L;
    assertTrue(LocationFreshness.valid(35.94, 126.68, 15, observed, observed));
    assertTrue(LocationFreshness.valid(35.94, 126.68, 15, observed,
        observed + LocationFreshness.MAX_AGE_NANOS - 1));
    assertFalse(LocationFreshness.valid(35.94, 126.68, 15, observed,
        observed + LocationFreshness.MAX_AGE_NANOS));
    assertFalse(LocationFreshness.valid(35.94, 126.68, 15, observed, observed - 1));
    assertFalse(LocationFreshness.valid(35.94, 126.68, 15, 0, observed));
  }

  @Test public void malformedCoordinatesOrAccuracyNeverDrawIndicators() {
    for (double latitude : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 91, -91})
      assertFalse(LocationFreshness.valid(latitude, 126, 10, 1, 1));
    for (double longitude : new double[] {Double.NaN, 181, -181})
      assertFalse(LocationFreshness.valid(35, longitude, 10, 1, 1));
    for (float accuracy : new float[] {Float.NaN, Float.POSITIVE_INFINITY, 0, -1})
      assertFalse(LocationFreshness.valid(35, 126, accuracy, 1, 1));
  }
}
