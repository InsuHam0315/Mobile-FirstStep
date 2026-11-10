package com.example.firststep.game.composition.integration;

import com.example.firststep.game.domain.Progress;
import java.util.UUID;

/** Validated fixture identity; fakeNow is mutable clock data, not storage identity. */
public final class IntegrationContext {
  public static final String DATASET = "h04_fixture_v1", TARGET = "digital_information_software";

  public static final String SOFTWARE_DATASET = "software_episode_demo_v1";

  public enum Clock {
    REAL,
    DEBUG_FAKE
  }

  public final String datasetId, runId, targetId;
  public final Progress.Mode mode;
  public final Clock clock;
  public final Long fakeNow;

  private IntegrationContext(String d, String r, String t, Progress.Mode m, Clock c, Long n) {
    datasetId = d;
    runId = r;
    targetId = t;
    mode = m;
    clock = c;
    fakeNow = n;
  }

  public static boolean canonicalUuid(String value) {
    try {
      return value != null && UUID.fromString(value).toString().equals(value);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public static IntegrationContext create(
      String d, String r, String t, Progress.Mode m, Clock c, Long n) {
    if (!(DATASET.equals(d) || SOFTWARE_DATASET.equals(d))
        || (SOFTWARE_DATASET.equals(d) && m != Progress.Mode.DEMO)
        || !canonicalUuid(r)
        || !TARGET.equals(t)
        || m == null
        || c == null
        || (c == Clock.REAL && n != null)
        || (c == Clock.DEBUG_FAKE && n == null))
      throw new IllegalArgumentException("invalid integration context");
    return new IntegrationContext(d, r, t, m, c, n);
  }

  public IntegrationContext withFakeNow(long value) {
    if (clock != Clock.DEBUG_FAKE) throw new IllegalStateException("real clock");
    return create(datasetId, runId, targetId, mode, clock, value);
  }

  public boolean equalsIdentity(IntegrationContext other) {
    return other != null
        && datasetId.equals(other.datasetId)
        && runId.equals(other.runId)
        && targetId.equals(other.targetId)
        && mode == other.mode
        && clock == other.clock;
  }
}
