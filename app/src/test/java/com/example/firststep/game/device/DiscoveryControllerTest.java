package com.example.firststep.game.device;

import static org.junit.Assert.*;

import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.discovery.*;
import org.junit.Before;
import org.junit.Test;

public class DiscoveryControllerTest {
  private long now, g;
  private int effects;
  private DiscoveryController c;

  @Before
  public void setup() {
    c =
        new DiscoveryController(
            () -> now,
            new DiscoveryController.Listener() {
              public void onState(DiscoveryState s) {}

              public void onCompletion(DiscoveryResult r) {
                effects++;
              }
            },
            true);
    g = c.resume();
    c.setCamera(g, DiscoveryState.CameraStatus.READY);
    c.arrival();
    c.headingFallback();
  }

  private void pulse() {
    c.acceleration(g, 22);
    c.acceleration(g, 9.8f);
    now += 150;
  }

  @Test
  public void distinctExcursionsAndExactlyOnce() {
    for (int i = 0; i < 20; i++) c.acceleration(g, 22);
    assertEquals(1, c.snapshot().shakeCount);
    c.acceleration(g, 9.8f);
    now = 150;
    for (int i = 0; i < 4; i++) pulse();
    assertTrue(c.snapshot().completed);
    assertEquals("digital_information_software", c.snapshot().result.targetId);
    assertEquals(1, effects);
    c.acceleration(g, 22);
    c.tick();
    assertEquals(1, effects);
  }

  @Test
  public void suppressedDebouncedExcursionRequiresNewRearm() {
    c.acceleration(g, 22);
    now = 10;
    c.acceleration(g, 9.8f);
    now = 50;
    c.acceleration(g, 22);
    now = 150;
    c.acceleration(g, 22);
    assertEquals(1, c.snapshot().shakeCount);
    c.acceleration(g, 9.8f);
    c.acceleration(g, 22);
    assertEquals(2, c.snapshot().shakeCount);
  }

  @Test
  public void shakeWindowAndDebounce() {
    pulse();
    now = 50;
    c.acceleration(g, 22);
    assertEquals(1, c.snapshot().shakeCount);
    now = 3101;
    c.acceleration(g, 9);
    c.acceleration(g, 22);
    assertEquals(1, c.snapshot().shakeCount);
    assertFalse(c.snapshot().completed);
  }

  @Test
  public void headingWrapHysteresisAndStaleAdmission() {
    c.pause();
    g = c.resume();
    c.setCamera(g, DiscoveryState.CameraStatus.READY);
    c.arrival();
    c.heading(g, 359, true);
    assertEquals(DiscoveryState.HeadingStatus.ALIGNED, c.snapshot().heading);
    c.heading(g, 19, true);
    assertEquals(DiscoveryState.HeadingStatus.ALIGNED, c.snapshot().heading);
    c.heading(g, 21, true);
    assertEquals(DiscoveryState.HeadingStatus.MISALIGNED, c.snapshot().heading);
    c.heading(g, 0, true);
    for (int i = 0; i < 4; i++) pulse();
    now += 1001;
    c.acceleration(g, 22);
    assertFalse(c.snapshot().completed);
    assertEquals(DiscoveryState.HeadingStatus.STALE, c.snapshot().heading);
  }

  @Test
  public void stableOnChangeDarkAndContinuousSamples() {
    c.select(DiscoveryResult.Method.LIGHT);
    c.light(g, 100);
    c.light(g, 20);
    now = 1499;
    c.tick();
    assertFalse(c.snapshot().completed);
    now = 1500;
    c.tick();
    assertTrue(c.snapshot().completed);
    assertEquals(DiscoveryResult.Method.LIGHT, c.snapshot().result.method);
    assertEquals("synthetic_cover_demo", c.snapshot().result.targetId);
  }

  @Test
  public void darkGapDoesNotCompleteOnNewSample() {
    c.select(DiscoveryResult.Method.LIGHT);
    c.light(g, 100);
    c.light(g, 10);
    now = 3000;
    c.light(g, 10);
    assertFalse(c.snapshot().completed);
    now = 4499;
    c.light(g, 10);
    assertFalse(c.snapshot().completed);
    now = 4500;
    c.light(g, 10);
    assertTrue(c.snapshot().completed);
  }

  @Test
  public void missingDarkAndStaleCannotComplete() {
    c.select(DiscoveryResult.Method.LIGHT);
    c.light(g, 0);
    now = 2000;
    c.tick();
    assertFalse(c.snapshot().completed);
    assertEquals("too_dark", c.snapshot().lightStatus);
    c.light(g, 100);
    c.light(g, 10);
    c.sensorAvailability(g, true, false);
    now = 4000;
    c.tick();
    assertFalse(c.snapshot().completed);
    c.light(g, 0);
    assertFalse(c.snapshot().completed);
  }

  @Test
  public void holdAndLifecycleCancellation() {
    c.holdStart();
    now = 1000;
    c.holdCancel();
    now = 2000;
    c.tick();
    assertFalse(c.snapshot().completed);
    c.holdStart();
    long old = g;
    c.pause();
    g = c.resume();
    c.setCamera(g, DiscoveryState.CameraStatus.READY);
    c.arrival();
    c.headingFallback();
    c.acceleration(old, 25);
    now = 4000;
    c.tick();
    assertFalse(c.snapshot().completed);
    c.holdStart();
    now = 5500;
    c.tick();
    assertEquals(DiscoveryResult.Source.LONG_PRESS, c.snapshot().result.source);
    assertEquals(1, effects);
  }

  @Test
  public void cameraInterruptionCancelsHoldAndCover() {
    c.holdStart();
    now = 1000;
    c.setCamera(g, DiscoveryState.CameraStatus.ERROR);
    now = 2000;
    c.setCamera(g, DiscoveryState.CameraStatus.READY);
    c.tick();
    assertFalse(c.snapshot().completed);
    c.select(DiscoveryResult.Method.LIGHT);
    c.light(g, 100);
    c.light(g, 10);
    now = 2500;
    c.setCamera(g, DiscoveryState.CameraStatus.ERROR);
    now = 4000;
    c.setCamera(g, DiscoveryState.CameraStatus.READY);
    c.tick();
    assertFalse(c.snapshot().completed);
    c.light(g, 10);
    now = 5500;
    c.tick();
    assertTrue(c.snapshot().completed);
  }

  @Test
  public void holdCompletionUsesSelectedScenarioTarget() {
    c.holdStart();
    now = 1500;
    c.tick();
    assertEquals("digital_information_software", c.snapshot().result.targetId);
    assertEquals(DiscoveryResult.Method.LONG_PRESS, c.snapshot().result.method);
    c.pause();
    g = c.resume();
    c.setCamera(g, DiscoveryState.CameraStatus.READY);
    c.arrival();
    c.headingFallback();
    c.select(DiscoveryResult.Method.LIGHT);
    c.holdStart();
    now = 3000;
    c.tick();
    assertEquals("synthetic_cover_demo", c.snapshot().result.targetId);
    assertEquals(DiscoveryResult.Method.LONG_PRESS, c.snapshot().result.method);
  }

  @Test
  public void cachedFreshEntryBaseline() {
    c.pause();
    g = c.resume();
    c.light(g, 100);
    c.setCamera(g, DiscoveryState.CameraStatus.READY);
    c.arrival();
    c.headingFallback();
    c.select(DiscoveryResult.Method.LIGHT);
    c.light(g, 10);
    now = 1500;
    c.tick();
    assertTrue(c.snapshot().completed);
  }
}
