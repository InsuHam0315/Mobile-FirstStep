package com.example.firststep.data;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class CompletionPolicyTest {
  private Mission typedMission(String type) {
    return new Mission(
        "M01",
        "title",
        "candidate",
        type,
        "story",
        "question",
        "answer",
        Collections.singletonList("answer"),
        Collections.singletonList("answer"),
        "info",
        1,
        false,
        null,
        null);
  }

  private boolean allowed(String type, String method) {
    Mission mission = typedMission(type);
    return CompletionPolicy.canComplete(
        AppMode.DEMO,
        mission,
        Collections.emptySet(),
        "CARD",
        method,
        Collections.singletonList(mission));
  }

  @Test
  public void recordsOnlyActionsSupportedByThatMission() {
    assertTrue(allowed("DIRECTION", "COMPASS"));
    assertTrue(allowed("DIRECTION", "HOLD"));
    assertFalse(allowed("DIRECTION", "LIGHT"));
    assertTrue(allowed("PHOTO_QUIZ", "PHOTO"));
    assertFalse(allowed("PHOTO_QUIZ", "HOLD"));
    assertTrue(allowed("VOICE_QUIZ", "TYPED"));
    assertTrue(allowed("VOICE_QUIZ", "SPEECH"));
    assertFalse(allowed("VOICE_QUIZ", "COMPASS"));
    assertTrue(allowed("ACCELERATION_QUIZ", "ACCELEROMETER"));
    assertTrue(allowed("ACCELERATION_QUIZ", "HOLD"));
    assertFalse(allowed("UNKNOWN", "HOLD"));
  }

  @Test
  public void legacyMetadataCanPreserveExistingAwardsButCannotCreateNewOnes() {
    Mission mission = typedMission("LIGHT_QUIZ");
    assertFalse(allowed("LIGHT_QUIZ", "LEGACY"));
    assertTrue(
        CompletionPolicy.canComplete(
            AppMode.DEMO,
            mission,
            Collections.singleton("M01"),
            "CARD",
            "LEGACY",
            Collections.singletonList(mission)));
  }

  private Mission mission(String id, int order, boolean verified) {
    return new Mission(
        id,
        id,
        "candidate",
        "LIGHT_QUIZ",
        "story",
        "question",
        "answer",
        Collections.singletonList("answer"),
        Collections.emptyList(),
        "info",
        order,
        verified,
        verified ? 1.0 : null,
        verified ? 2.0 : null);
  }

  @Test
  public void enforcesSequence() {
    Mission first = mission("M01", 1, false), second = mission("M02", 2, false);
    assertTrue(
        CompletionPolicy.canComplete(
            AppMode.DEMO,
            first,
            Collections.emptySet(),
            "CARD",
            "LIGHT",
            Arrays.asList(first, second)));
    assertFalse(
        CompletionPolicy.canComplete(
            AppMode.DEMO,
            second,
            Collections.emptySet(),
            "CARD",
            "LIGHT",
            Arrays.asList(first, second)));
    assertTrue(
        CompletionPolicy.canComplete(
            AppMode.DEMO,
            second,
            Collections.singleton("M01"),
            "AR",
            "LIGHT",
            Arrays.asList(first, second)));
  }

  @Test
  public void fieldRequiresVerifiedCoordinatesAndKnownDiscoveryMethod() {
    Mission unverified = mission("M01", 1, false);
    assertFalse(
        CompletionPolicy.canComplete(
            AppMode.FIELD,
            unverified,
            Collections.emptySet(),
            "AR",
            "PHOTO",
            Collections.singletonList(unverified)));
    assertFalse(
        CompletionPolicy.canComplete(
            AppMode.DEMO,
            unverified,
            Collections.emptySet(),
            "CAMERA",
            "TYPED",
            Collections.singletonList(unverified)));
    assertTrue(
        CompletionPolicy.canComplete(
            AppMode.DEMO,
            unverified,
            Collections.emptySet(),
            "AR",
            "HOLD",
            Collections.singletonList(unverified)));
    assertFalse(
        CompletionPolicy.canComplete(
            AppMode.DEMO,
            unverified,
            Collections.emptySet(),
            "AR",
            "ACCELEROMETER",
            Collections.singletonList(unverified)));
    assertFalse(
        CompletionPolicy.canComplete(
            AppMode.DEMO,
            unverified,
            Collections.emptySet(),
            "AR",
            "UNKNOWN",
            Collections.singletonList(unverified)));
  }
}
