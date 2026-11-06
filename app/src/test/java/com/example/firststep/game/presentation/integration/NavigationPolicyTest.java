package com.example.firststep.game.presentation.integration;

import static org.junit.Assert.*;

import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import org.junit.Test;

public class NavigationPolicyTest {
  private static final String RUN = "1f1413cd-06d2-4199-9978-5623496d021c",
      OTHER = "2f1413cd-06d2-4199-9978-5623496d021c",
      NONCE = "3f1413cd-06d2-4199-9978-5623496d021c";

  private IntegrationContext context(String run, Progress.Mode mode) {
    return IntegrationContext.create(
        IntegrationContext.DATASET,
        run,
        IntegrationContext.TARGET,
        mode,
        IntegrationContext.Clock.DEBUG_FAKE,
        100L);
  }

  private IntegrationResult result(
      IntegrationContext c,
      String n,
      String target,
      DiscoveryResult.Method m,
      DiscoveryResult.Source source,
      long session) {
    return new IntegrationResult(
        c, n, new DiscoveryResult(session, target, m, source, true, false));
  }

  @Test
  public void rejectsInvalidContext() {
    try {
      IntegrationContext.create(
          "other",
          RUN,
          IntegrationContext.TARGET,
          Progress.Mode.NORMAL,
          IntegrationContext.Clock.REAL,
          null);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      IntegrationContext.create(
          IntegrationContext.DATASET,
          "bad",
          IntegrationContext.TARGET,
          Progress.Mode.NORMAL,
          IntegrationContext.Clock.REAL,
          null);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      IntegrationContext.create(
          IntegrationContext.DATASET,
          RUN,
          "synthetic_cover_demo",
          Progress.Mode.NORMAL,
          IntegrationContext.Clock.REAL,
          null);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      IntegrationContext.create(
          IntegrationContext.DATASET,
          RUN,
          IntegrationContext.TARGET,
          Progress.Mode.NORMAL,
          IntegrationContext.Clock.DEBUG_FAKE,
          null);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void acceptanceIsBoundAndOnceOnly() {
    IntegrationContext c = context(RUN, Progress.Mode.NORMAL);
    NavigationPolicy.State p = NavigationPolicy.begin(NavigationPolicy.State.empty(), c, NONCE);
    assertNull(
        NavigationPolicy.accept(
            p,
            result(
                context(OTHER, Progress.Mode.NORMAL),
                NONCE,
                IntegrationContext.TARGET,
                DiscoveryResult.Method.SHAKE,
                DiscoveryResult.Source.SIMULATED,
                1)));
    assertNull(
        NavigationPolicy.accept(
            p,
            result(
                context(RUN, Progress.Mode.DEMO),
                NONCE,
                IntegrationContext.TARGET,
                DiscoveryResult.Method.SHAKE,
                DiscoveryResult.Source.SIMULATED,
                1)));
    assertNull(
        NavigationPolicy.accept(
            p,
            result(
                c,
                OTHER,
                IntegrationContext.TARGET,
                DiscoveryResult.Method.SHAKE,
                DiscoveryResult.Source.SIMULATED,
                1)));
    assertNull(
        NavigationPolicy.accept(
            p,
            result(
                c,
                NONCE,
                "synthetic_cover_demo",
                DiscoveryResult.Method.SHAKE,
                DiscoveryResult.Source.SIMULATED,
                1)));
    assertNull(
        NavigationPolicy.accept(
            p,
            result(
                c,
                NONCE,
                IntegrationContext.TARGET,
                DiscoveryResult.Method.LIGHT,
                DiscoveryResult.Source.SIMULATED,
                1)));
    assertNull(
        NavigationPolicy.accept(
            p,
            result(
                c,
                NONCE,
                IntegrationContext.TARGET,
                DiscoveryResult.Method.SHAKE,
                DiscoveryResult.Source.LONG_PRESS,
                1)));
    assertNull(
        NavigationPolicy.accept(
            p,
            result(
                c,
                NONCE,
                IntegrationContext.TARGET,
                DiscoveryResult.Method.SHAKE,
                DiscoveryResult.Source.SIMULATED,
                0)));
    IntegrationResult r =
        result(
            c.withFakeNow(200),
            NONCE,
            IntegrationContext.TARGET,
            DiscoveryResult.Method.LONG_PRESS,
            DiscoveryResult.Source.LONG_PRESS,
            1);
    NavigationPolicy.State accepted = NavigationPolicy.accept(p, r);
    assertNotNull(accepted);
    assertNull(NavigationPolicy.accept(accepted, r));
    NavigationPolicy.State consumed = NavigationPolicy.consume(accepted, c, NONCE);
    assertNotNull(consumed);
    assertNull(NavigationPolicy.consume(consumed, c, NONCE));
    assertNull(NavigationPolicy.accept(consumed, r));
  }

  @Test
  public void cancelRejectsLateResult() {
    IntegrationContext c = context(RUN, Progress.Mode.DEMO);
    NavigationPolicy.State p = NavigationPolicy.begin(NavigationPolicy.State.empty(), c, NONCE);
    NavigationPolicy.State empty = NavigationPolicy.cancel(p, c, NONCE);
    assertNotNull(empty);
    assertNull(
        NavigationPolicy.accept(
            empty,
            result(
                c,
                NONCE,
                IntegrationContext.TARGET,
                DiscoveryResult.Method.SHAKE,
                DiscoveryResult.Source.REAL_SENSOR,
                1)));
  }

  @Test
  public void clockIdentityAndValidation() {
    IntegrationContext fake = context(RUN, Progress.Mode.NORMAL);
    IntegrationContext real =
        IntegrationContext.create(
            IntegrationContext.DATASET,
            RUN,
            IntegrationContext.TARGET,
            Progress.Mode.NORMAL,
            IntegrationContext.Clock.REAL,
            null);
    assertTrue(fake.equalsIdentity(fake.withFakeNow(500)));
    assertFalse(fake.equalsIdentity(real));
    try {
      real.withFakeNow(5);
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      IntegrationContext.create(
          IntegrationContext.DATASET,
          RUN,
          IntegrationContext.TARGET,
          Progress.Mode.NORMAL,
          IntegrationContext.Clock.REAL,
          5L);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void incorrectCancelConsumeAndRestartAreRejected() {
    IntegrationContext c = context(RUN, Progress.Mode.NORMAL);
    NavigationPolicy.State pending =
        NavigationPolicy.begin(NavigationPolicy.State.empty(), c, NONCE);
    assertNull(NavigationPolicy.cancel(pending, c, OTHER));
    assertNull(NavigationPolicy.cancel(pending, context(OTHER, Progress.Mode.NORMAL), NONCE));
    assertNull(NavigationPolicy.consume(pending, c, NONCE));
    NavigationPolicy.State accepted =
        NavigationPolicy.accept(
            pending,
            result(
                c,
                NONCE,
                IntegrationContext.TARGET,
                DiscoveryResult.Method.SHAKE,
                DiscoveryResult.Source.REAL_SENSOR,
                1));
    assertNull(NavigationPolicy.consume(accepted, c, OTHER));
    assertNull(NavigationPolicy.begin(accepted, c, OTHER));
    NavigationPolicy.State consumed = NavigationPolicy.consume(accepted, c, NONCE);
    assertNull(NavigationPolicy.begin(consumed, c, OTHER));
  }
}
