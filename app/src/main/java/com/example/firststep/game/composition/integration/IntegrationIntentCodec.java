package com.example.firststep.game.composition.integration;

import android.content.Intent;
import android.os.Bundle;
import com.example.firststep.BuildConfig;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.integration.NavigationPolicy;

/** Strict primitive Intent boundary. */
public final class IntegrationIntentCodec {
  public static final String MARKER = "game_integration_marker",
      DATASET = "game_integration_dataset",
      RUN = "game_intro_debug_run",
      TARGET = "game_integration_target",
      MODE = "game_integration_mode",
      CLOCK = "game_integration_clock",
      FAKE_NOW = "game_intro_debug_clock",
      NONCE = "game_integration_nonce";
  private static final String SESSION = "game_integration_session",
      RESULT_TARGET = "game_integration_result_target",
      METHOD = "game_integration_method",
      SOURCE = "game_integration_source",
      ARRIVAL = "game_integration_arrival_simulated",
      CAMERA = "game_integration_camera_fallback",
      HEADING = "game_integration_heading_fallback";

  public static final class Request {
    public final IntegrationContext context;
    public final String nonce;

    Request(IntegrationContext c, String n) {
      context = c;
      nonce = n;
    }
  }

  public static boolean hasIntegrationContext(Intent i) {
    try {
      if (i == null || i.getExtras() == null) return false;
      for (String key : i.getExtras().keySet())
        if (key.startsWith("game_integration_")) return true;
      return false;
    } catch (RuntimeException invalid) {
      return true;
    }
  }

  private static Object value(Bundle b, String key, Class<?> type) {
    Object v = b.get(key);
    if (!type.isInstance(v)) throw new IllegalArgumentException(key);
    return v;
  }

  public static void writeContext(Intent i, IntegrationContext c) {
    i.putExtra(MARKER, true)
        .putExtra(DATASET, c.datasetId)
        .putExtra(RUN, c.runId)
        .putExtra(TARGET, c.targetId)
        .putExtra(MODE, c.mode.name())
        .putExtra(CLOCK, c.clock.name())
        .putExtra("game_intro_mode", c.mode.name())
        .putExtra("game_intro_building_id", c.targetId)
        .putExtra("game_intro_instant_demo", c.mode == Progress.Mode.DEMO);
    if (c.fakeNow != null) i.putExtra(FAKE_NOW, c.fakeNow.longValue());
    else i.removeExtra(FAKE_NOW);
  }

  public static IntegrationContext readContext(Intent i) {
    return readContext(i, BuildConfig.DEBUG);
  }

  static IntegrationContext readContext(Intent i, boolean debugBuild) {
    try {
      Bundle b = i.getExtras();
      if (!Boolean.TRUE.equals(value(b, MARKER, Boolean.class))) return null;
      String d = (String) value(b, DATASET, String.class),
          r = (String) value(b, RUN, String.class),
          t = (String) value(b, TARGET, String.class),
          m = (String) value(b, MODE, String.class),
          c = (String) value(b, CLOCK, String.class);
      if (b.containsKey("game_intro_mode") && !m.equals(value(b, "game_intro_mode", String.class)))
        return null;
      if (b.containsKey("game_intro_building_id")
          && !t.equals(value(b, "game_intro_building_id", String.class))) return null;
      if (b.containsKey("game_intro_instant_demo")
          && !Boolean.valueOf("DEMO".equals(m))
              .equals(value(b, "game_intro_instant_demo", Boolean.class))) return null;
      IntegrationContext.Clock clock = IntegrationContext.Clock.valueOf(c);
      Long n = b.containsKey(FAKE_NOW) ? (Long) value(b, FAKE_NOW, Long.class) : null;
      IntegrationContext context =
          IntegrationContext.create(d, r, t, Progress.Mode.valueOf(m), clock, n);
      return IntegrationComposition.allowedContext(context, debugBuild) ? context : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  public static void writeRequest(Intent i, IntegrationContext c, String n) {
    if (!IntegrationContext.canonicalUuid(n)) throw new IllegalArgumentException("nonce");
    writeContext(i, c);
    i.putExtra(NONCE, n);
  }

  public static Request readRequest(Intent i) {
    try {
      IntegrationContext c = readContext(i);
      String n = (String) value(i.getExtras(), NONCE, String.class);
      return c != null && IntegrationContext.canonicalUuid(n) ? new Request(c, n) : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  public static void writeResult(Intent i, IntegrationResult r) {
    writeRequest(i, r.context, r.nonce);
    DiscoveryResult d = r.discovery;
    i.putExtra(SESSION, d.sessionId)
        .putExtra(RESULT_TARGET, d.targetId)
        .putExtra(METHOD, d.method.name())
        .putExtra(SOURCE, d.source.name())
        .putExtra(ARRIVAL, d.arrivalSimulated)
        .putExtra(CAMERA, d.cameraFallback)
        .putExtra(HEADING, d.headingFallback);
  }

  public static IntegrationResult readResult(Intent i) {
    try {
      Request r = readRequest(i);
      Bundle b = i.getExtras();
      if (r == null || !Boolean.TRUE.equals(value(b, ARRIVAL, Boolean.class))) return null;
      DiscoveryResult d =
          new DiscoveryResult(
              (Long) value(b, SESSION, Long.class),
              (String) value(b, RESULT_TARGET, String.class),
              DiscoveryResult.Method.valueOf((String) value(b, METHOD, String.class)),
              DiscoveryResult.Source.valueOf((String) value(b, SOURCE, String.class)),
              (Boolean) value(b, CAMERA, Boolean.class),
              (Boolean) value(b, HEADING, Boolean.class));
      IntegrationResult result = new IntegrationResult(r.context, r.nonce, d);
      NavigationPolicy.State pending =
          NavigationPolicy.begin(NavigationPolicy.State.empty(), r.context, r.nonce);
      return NavigationPolicy.accept(pending, result) != null ? result : null;
    } catch (RuntimeException e) {
      return null;
    }
  }
}
