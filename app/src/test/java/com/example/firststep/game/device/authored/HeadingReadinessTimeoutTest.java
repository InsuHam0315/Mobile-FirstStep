package com.example.firststep.game.device.authored;

import static org.junit.Assert.*;

import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.discovery.DiscoveryState;
import com.example.firststep.game.sync.SyncScope;
import org.junit.Test;

public class HeadingReadinessTimeoutTest {
  @Test public void silenceDeadlineIsOneShotAndCloseSuppressesLateCallback() {
    HeadingReadinessTimeout timeout = new HeadingReadinessTimeout(100);
    assertEquals(HeadingReadinessTimeout.Outcome.PENDING, timeout.poll(3099));
    assertEquals(HeadingReadinessTimeout.Outcome.UNAVAILABLE, timeout.poll(3100));
    assertEquals(HeadingReadinessTimeout.Outcome.PENDING, timeout.poll(3200));
    HeadingReadinessTimeout cancelled = new HeadingReadinessTimeout(100);
    cancelled.close();
    cancelled.invalidHeading();
    assertEquals(HeadingReadinessTimeout.Outcome.PENDING, cancelled.poll(10000));
  }

  @Test public void invalidDoesNotSatisfyDeadlineAndValidCancelsIt() {
    HeadingReadinessTimeout invalid = new HeadingReadinessTimeout(0);
    invalid.invalidHeading();
    assertEquals(HeadingReadinessTimeout.Outcome.UNRELIABLE, invalid.poll(3000));
    HeadingReadinessTimeout recovered = new HeadingReadinessTimeout(0);
    recovered.invalidHeading();
    recovered.validHeading();
    assertEquals(HeadingReadinessTimeout.Outcome.PENDING, recovered.poll(3000));
    HeadingReadinessTimeout late = new HeadingReadinessTimeout(0);
    assertEquals(HeadingReadinessTimeout.Outcome.UNAVAILABLE, late.poll(3000));
    late.validHeading();
    assertEquals(HeadingReadinessTimeout.Outcome.PENDING, late.poll(4000));
  }

  @Test public void silentTimeoutEnablesExplicitFallbackAndReportsLongPress() {
    long[] now = { 0 };
    DiscoveryState[] latest = { null };
    NormalDiscoverySession.Receipt[] result = { null };
    SyncScope scope = new SyncScope("project", "alice", 1, "software_authored_v1",
        Progress.Mode.NORMAL, "digital_information_software");
    NormalDiscoverySession session = new NormalDiscoverySession(scope,
        "0f536bc8-2f20-4f75-94dd-b775fa9c3572", 90, () -> now[0],
        new NormalDiscoverySession.Listener() {
          @Override public void onState(DiscoveryState state) { latest[0] = state; }
          @Override public void onCompleted(NormalDiscoverySession.Receipt receipt) { result[0] = receipt; }
        });
    session.start();
    session.cameraFallback();
    session.availability(true, false);
    assertEquals(DiscoveryState.HeadingStatus.WAITING, latest[0].heading);
    HeadingReadinessTimeout timeout = new HeadingReadinessTimeout(now[0]);
    now[0] = HeadingReadinessTimeout.FIRST_FIX_TIMEOUT_MS;
    if (timeout.poll(now[0]) == HeadingReadinessTimeout.Outcome.UNAVAILABLE) {
      session.headingUnavailable(false);
    }
    assertEquals(DiscoveryState.HeadingStatus.UNAVAILABLE, latest[0].heading);
    session.heading(90, true);
    assertEquals(DiscoveryState.HeadingStatus.ALIGNED, latest[0].heading);
    session.headingUnavailable(false);
    session.headingFallback();
    session.holdStart();
    now[0] += 1600;
    session.tick();
    assertNotNull(result[0]);
    assertEquals(DiscoveryResult.Source.LONG_PRESS, result[0].source);
    assertTrue(result[0].headingFallback);
    assertTrue(result[0].cameraFallback);
  }
}
