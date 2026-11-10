package com.example.firststep.game.presentation.integration;

import static org.junit.Assert.*;
import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import org.junit.Test;

public class SoftwareEpisodeContractTest {
  private static final String RUN = "1f1413cd-06d2-4199-9978-5623496d021c";
  private static final String NONCE = "3f1413cd-06d2-4199-9978-5623496d021c";
  private static IntegrationContext context(String dataset) {
    return IntegrationContext.create(dataset, RUN, IntegrationContext.TARGET, Progress.Mode.DEMO,
        IntegrationContext.Clock.DEBUG_FAKE, 100L);
  }
  private static void rejected(String d, String r, String t, Progress.Mode m, IntegrationContext.Clock c, Long n) {
    try { IntegrationContext.create(d, r, t, m, c, n); fail("invalid context accepted"); }
    catch (IllegalArgumentException expected) { }
  }
  @Test public void registrationAndReleaseGuardAreClosed() {
    IntegrationContext demo = context(IntegrationContext.SOFTWARE_DATASET);
    assertTrue(IntegrationComposition.allowedDataset(IntegrationContext.SOFTWARE_DATASET, true));
    assertFalse(IntegrationComposition.allowedDataset(IntegrationContext.SOFTWARE_DATASET, false));
    assertFalse(IntegrationComposition.allowedDataset("unknown", true));
    assertTrue(IntegrationComposition.allowedDataset(IntegrationContext.DATASET, false));
    assertTrue(IntegrationComposition.allowedContext(demo, true));
    assertFalse(IntegrationComposition.allowedContext(demo, false));
    assertFalse(IntegrationComposition.allowedContext(null, true));
    try { IntegrationComposition.databaseName(demo, false); fail("release accepted demo"); }
    catch (IllegalArgumentException expected) { }
  }
  @Test public void invalidIdentityModeAndClockAreRejected() {
    String d = IntegrationContext.SOFTWARE_DATASET, t = IntegrationContext.TARGET;
    rejected("unknown", RUN, t, Progress.Mode.DEMO, IntegrationContext.Clock.REAL, null);
    rejected(d, "bad", t, Progress.Mode.DEMO, IntegrationContext.Clock.REAL, null);
    rejected(d, RUN.toUpperCase(), t, Progress.Mode.DEMO, IntegrationContext.Clock.REAL, null);
    rejected(d, RUN, "wrong", Progress.Mode.DEMO, IntegrationContext.Clock.REAL, null);
    rejected(d, RUN, t, Progress.Mode.NORMAL, IntegrationContext.Clock.REAL, null);
    rejected(d, RUN, t, null, IntegrationContext.Clock.REAL, null);
    rejected(d, RUN, t, Progress.Mode.DEMO, null, null);
    rejected(d, RUN, t, Progress.Mode.DEMO, IntegrationContext.Clock.REAL, 1L);
    rejected(d, RUN, t, Progress.Mode.DEMO, IntegrationContext.Clock.DEBUG_FAKE, null);
  }
  @Test public void sameUuidHasSeparateDatabaseAndContinuationIdentity() {
    IntegrationContext old = context(IntegrationContext.DATASET);
    IntegrationContext demo = context(IntegrationContext.SOFTWARE_DATASET);
    assertEquals("h04-debug-" + RUN + ".db", IntegrationComposition.databaseName(old, true));
    assertEquals("software-episode-demo-" + RUN + ".db", IntegrationComposition.databaseName(demo, true));
    assertFalse(old.equalsIdentity(demo));
    NavigationPolicy.State pending = NavigationPolicy.begin(NavigationPolicy.State.empty(), demo, NONCE);
    DiscoveryResult discovery = new DiscoveryResult(1, IntegrationContext.TARGET,
        DiscoveryResult.Method.SHAKE, DiscoveryResult.Source.SIMULATED, true, false);
    assertNull(NavigationPolicy.accept(pending, new IntegrationResult(old, NONCE, discovery)));
    NavigationPolicy.State accepted = NavigationPolicy.accept(pending, new IntegrationResult(demo, NONCE, discovery));
    assertNotNull(accepted);
    assertNull(NavigationPolicy.accept(accepted, new IntegrationResult(demo, NONCE, discovery)));
    assertNull(NavigationPolicy.consume(accepted, old, NONCE));
    assertNotNull(NavigationPolicy.consume(accepted, demo, NONCE));
    NavigationPolicy.State cancelled = NavigationPolicy.cancel(pending, demo, NONCE);
    assertNull(NavigationPolicy.accept(cancelled, new IntegrationResult(demo, NONCE, discovery)));
  }
}
