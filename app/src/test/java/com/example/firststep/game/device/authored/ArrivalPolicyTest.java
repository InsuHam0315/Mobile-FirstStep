package com.example.firststep.game.device.authored;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ArrivalPolicyTest {
  private static final long SECOND = 1_000_000_000L;
  private final AuthoredTargetConfig config = new AuthoredTargetConfig(
      "synthetic_target", "synthetic survey for test", 37.0, 127.0, 100, 90, 20, 30 * SECOND);

  private ArrivalPolicy.Fix fix(double latitude, double longitude, float accuracy, long elapsed, boolean mock) {
    return new ArrivalPolicy.Fix(latitude, longitude, accuracy, elapsed, mock);
  }

  @Test public void productionIsUnavailableAndConfigurationRejectsInvalidSurveyData() {
    assertFalse(AuthoredTargetConfig.production().get("digital_information_software").isPresent());
    assertThrows(IllegalArgumentException.class,
        () -> new AuthoredTargetConfig("test", " ", 37, 127, 100, 90, 20, SECOND));
    assertThrows(IllegalArgumentException.class,
        () -> new AuthoredTargetConfig("test", "survey", Double.NaN, 127, 100, 90, 20, SECOND));
    assertThrows(IllegalArgumentException.class,
        () -> new AuthoredTargetConfig("test", "survey", 37, 127, 100, 360, 20, SECOND));
    assertThrows(IllegalArgumentException.class,
        () -> new AuthoredTargetConfig("test", "survey", 37, 127, 0, 90, 20, SECOND));
  }

  @Test public void accuracyMustFitInsideSurveyRadius() {
    assertEquals(ArrivalPolicy.Verdict.ADMITTED,
        ArrivalPolicy.evaluate(config, fix(37, 127, 5, SECOND, false), SECOND));
    assertEquals(ArrivalPolicy.Verdict.NOT_ARRIVED,
        ArrivalPolicy.evaluate(config, fix(37.0009, 127, 5, SECOND, false), SECOND));
    assertEquals(ArrivalPolicy.Verdict.NOT_ARRIVED,
        ArrivalPolicy.evaluate(config, fix(37.0008, 127, 15, SECOND, false), SECOND));
    assertEquals(ArrivalPolicy.Verdict.LOW_ACCURACY,
        ArrivalPolicy.evaluate(config, fix(37, 127, 21, SECOND, false), SECOND));
  }

  @Test public void rejectsStaleFutureMockAndInvalidFixes() {
    assertEquals(ArrivalPolicy.Verdict.STALE,
        ArrivalPolicy.evaluate(config, fix(37, 127, 1, SECOND, false), 32 * SECOND));
    assertEquals(ArrivalPolicy.Verdict.STALE,
        ArrivalPolicy.evaluate(config, fix(37, 127, 1, 2 * SECOND, false), SECOND));
    assertEquals(ArrivalPolicy.Verdict.MOCK,
        ArrivalPolicy.evaluate(config, fix(37, 127, 1, SECOND, true), SECOND));
    assertEquals(ArrivalPolicy.Verdict.INVALID,
        ArrivalPolicy.evaluate(config, fix(Double.NaN, 127, 1, SECOND, false), SECOND));
    assertEquals(ArrivalPolicy.Verdict.INVALID,
        ArrivalPolicy.evaluate(config, fix(37, 127, Float.POSITIVE_INFINITY, SECOND, false), SECOND));
    assertEquals(ArrivalPolicy.Verdict.INVALID,
        ArrivalPolicy.evaluate(config, fix(37, 127, -1, SECOND, false), SECOND));
  }

  @Test public void retryAndStopInvalidateLateCallbacks() {
    ArrivalRequestGuard guard = new ArrivalRequestGuard();
    long first = guard.start();
    assertTrue(guard.current(first));
    long next = guard.start();
    assertFalse(guard.current(first));
    assertTrue(guard.current(next));
    guard.stop();
    assertFalse(guard.current(next));
  }
}
