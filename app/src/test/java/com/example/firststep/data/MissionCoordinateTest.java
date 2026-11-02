package com.example.firststep.data;

import static org.junit.Assert.*;

import java.util.Collections;
import org.junit.Test;

public class MissionCoordinateTest {
  private Mission mission(boolean verified, Double latitude, Double longitude) {
    return new Mission(
        "M01",
        "title",
        "place",
        "PHOTO_QUIZ",
        "story",
        "question",
        "answer",
        Collections.singletonList("answer"),
        Collections.singletonList("answer"),
        "info",
        1,
        verified,
        latitude,
        longitude);
  }

  @Test
  public void suppliedNumbersRemainHiddenUntilVerified() {
    Mission candidate = mission(false, 35.0, 126.0);
    assertFalse(candidate.coordinateVerified);
    assertNull(candidate.latitude);
    assertNull(candidate.longitude);
  }

  @Test
  public void invalidNumbersCannotBecomeOperationalCoordinates() {
    assertFalse(mission(true, Double.NaN, 126.0).coordinateVerified);
    assertFalse(mission(true, 35.0, Double.POSITIVE_INFINITY).coordinateVerified);
    assertFalse(mission(true, 91.0, 126.0).coordinateVerified);
    assertFalse(mission(true, 35.0, -181.0).coordinateVerified);
    assertFalse(mission(true, null, 126.0).coordinateVerified);
    assertTrue(mission(true, 35.0, 126.0).coordinateVerified);
  }
}
