package com.example.firststep.game.presentation.integration;

import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.composition.introquiz.DiscoveryIntroTarget;
import com.example.firststep.game.domain.discovery.DiscoveryResult;

/** Pure once-only transitions. Null means rejected. */
public final class NavigationPolicy {
  public enum Phase {
    EMPTY,
    PENDING,
    ACCEPTED,
    CONSUMED
  }

  public static final class State {
    public final Phase phase;
    public final IntegrationContext context;
    public final String nonce;
    public final IntegrationResult result;

    public State(Phase p, IntegrationContext c, String n, IntegrationResult r) {
      phase = p;
      context = c;
      nonce = n;
      result = r;
    }

    public static State empty() {
      return new State(Phase.EMPTY, null, null, null);
    }
  }

  private static boolean matches(State s, IntegrationContext c, String n) {
    return s != null
        && s.context != null
        && s.context.equalsIdentity(c)
        && s.nonce != null
        && s.nonce.equals(n);
  }

  public static State begin(State s, IntegrationContext c, String n) {
    if (s == null || c == null || !IntegrationContext.canonicalUuid(n) || s.phase != Phase.EMPTY)
      return null;
    return new State(Phase.PENDING, c, n, null);
  }

  public static State cancel(State s, IntegrationContext c, String n) {
    return s != null && s.phase == Phase.PENDING && matches(s, c, n) ? State.empty() : null;
  }

  public static State accept(State s, IntegrationResult r) {
    if (r == null
        || s == null
        || s.phase != Phase.PENDING
        || !matches(s, r.context, r.nonce)
        || r.discovery == null) return null;
    DiscoveryResult d = r.discovery;
    if (d.sessionId <= 0 || !d.arrivalSimulated || d.method == null || d.source == null)
      return null;
    if (!((d.method == DiscoveryResult.Method.SHAKE
            && (d.source == DiscoveryResult.Source.REAL_SENSOR
                || d.source == DiscoveryResult.Source.SIMULATED))
        || (d.method == DiscoveryResult.Method.LONG_PRESS
            && d.source == DiscoveryResult.Source.LONG_PRESS))) return null;
    try {
      DiscoveryIntroTarget.from(d);
    } catch (IllegalArgumentException e) {
      return null;
    }
    return new State(Phase.ACCEPTED, r.context, r.nonce, r);
  }

  public static State consume(State s, IntegrationContext c, String n) {
    return s != null && s.phase == Phase.ACCEPTED && matches(s, c, n)
        ? new State(Phase.CONSUMED, s.context, s.nonce, s.result)
        : null;
  }
}
