package com.example.firststep.game.device.authored;

import static org.junit.Assert.*;

import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.discovery.DiscoveryController;
import com.example.firststep.game.presentation.discovery.DiscoveryState;
import com.example.firststep.game.sync.SyncScope;
import org.junit.Test;

public class NormalDiscoverySessionTest {
  private static final String NONCE = "0f536bc8-2f20-4f75-94dd-b775fa9c3572";
  private static final String OTHER = "1f536bc8-2f20-4f75-94dd-b775fa9c3572";
  private long now;
  private NormalDiscoverySession.Receipt receipt;
  private int completions;

  private SyncScope scope(String user, long generation, String target, Progress.Mode mode) {
    return new SyncScope("project", user, generation, "software_authored_v1", mode, target);
  }
  private SyncScope normal() {
    return scope("alice", 1, "digital_information_software", Progress.Mode.NORMAL);
  }
  private NormalDiscoverySession make(SyncScope scope) {
    return new NormalDiscoverySession(scope, NONCE, 90, () -> now,
        new NormalDiscoverySession.Listener() {
          @Override public void onState(DiscoveryState state) { }
          @Override public void onCompleted(NormalDiscoverySession.Receipt result) {
            receipt = result;
            completions++;
          }
        });
  }
  private void ready(NormalDiscoverySession session) {
    session.start();
    session.camera(DiscoveryState.CameraStatus.READY);
    session.availability(true, true);
    session.heading(90, true);
  }
  private void shakes(NormalDiscoverySession session) {
    for (int i = 0; i < 5; i++) {
      now += 200;
      session.heading(90, true);
      session.acceleration(23f);
      session.acceleration(9.8f);
    }
  }

  @Test public void directionThenRealShakeOnlyOnceAndBoundToSession() {
    NormalDiscoverySession session = make(normal());
    ready(session);
    session.heading(140, true);
    shakesWithoutHeading(session);
    assertNull(receipt);
    shakes(session);
    assertEquals(1, completions);
    assertEquals(DiscoveryResult.Method.SHAKE, receipt.method);
    assertEquals(DiscoveryResult.Source.REAL_SENSOR, receipt.source);
    assertFalse(session.consume(receipt, normal(), OTHER));
    assertFalse(session.consume(receipt, scope("bob", 1, "digital_information_software", Progress.Mode.NORMAL), NONCE));
    assertFalse(session.consume(receipt, scope("alice", 2, "digital_information_software", Progress.Mode.NORMAL), NONCE));
    assertFalse(session.consume(receipt, scope("alice", 1, "another_target", Progress.Mode.NORMAL), NONCE));
    assertTrue(session.consume(receipt, normal(), NONCE));
    assertFalse(session.consume(receipt, normal(), NONCE));
  }

  private void shakesWithoutHeading(NormalDiscoverySession session) {
    for (int i = 0; i < 5; i++) {
      now += 200;
      session.acceleration(23f);
      session.acceleration(9.8f);
    }
  }

  @Test public void fallbackIsExplicitAndCancellationRejectsEvidence() {
    NormalDiscoverySession session = make(normal());
    session.start();
    session.cameraFallback();
    session.headingFallback();
    session.holdStart();
    now += 1600;
    session.tick();
    assertNotNull(receipt);
    assertEquals(DiscoveryResult.Method.LONG_PRESS, receipt.method);
    assertEquals(DiscoveryResult.Source.LONG_PRESS, receipt.source);
    assertTrue(receipt.cameraFallback);
    assertTrue(receipt.headingFallback);
    session.cancel();
    assertFalse(session.consume(receipt, normal(), NONCE));
    try { session.start(); fail("cancelled session must not restart"); }
    catch (IllegalStateException expected) { }
  }

  @Test public void aCompletedReceiptCannotAuthorizeAfterItsMonotonicWindow() {
    NormalDiscoverySession session = make(normal());
    ready(session);
    shakes(session);
    assertNotNull(receipt);
    now += NormalDiscoverySession.RECEIPT_MAX_AGE_MS + 1;
    assertFalse(session.consume(receipt, normal(), NONCE));
  }

  @Test public void legacySimulatedArrivalRemainsSimulatedAndRejectsTrustedSeam() {
    DiscoveryController simulated = new DiscoveryController(() -> now,
        new DiscoveryController.Listener() {
          @Override public void onState(DiscoveryState state) { }
          @Override public void onCompletion(DiscoveryResult result) { }
        }, true);
    long generation = simulated.resume();
    try { simulated.trustedArrival(); fail("Simulated controller cannot claim trusted arrival"); }
    catch (IllegalStateException expected) { }
    assertFalse(simulated.snapshot().arrived);
    simulated.arrival();
    simulated.setCamera(generation, DiscoveryState.CameraStatus.READY);
    simulated.headingFallback();
    simulated.holdStart();
    now += 1600;
    simulated.tick();
    assertTrue(simulated.snapshot().result.arrivalSimulated);
  }

  @Test public void staleHeadingAndDemoCannotAuthorize() {
    try {
      make(scope("alice", 1, "digital_information_software", Progress.Mode.DEMO));
      fail("DEMO must not enter NORMAL discovery");
    } catch (IllegalArgumentException expected) { }
    NormalDiscoverySession session = make(normal());
    ready(session);
    now += 1001;
    shakesWithoutHeading(session);
    assertNull(receipt);
    session.cancel();
    session.cameraFallback();
    session.headingFallback();
    now += 2000;
    session.tick();
    assertNull(receipt);
  }
}
