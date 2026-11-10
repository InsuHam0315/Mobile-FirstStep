package com.example.firststep.game.composition.integration;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.integration.NavigationPolicy;
import com.example.firststep.game.presentation.integration.NavigationPolicy.State;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

/** Blocking persistence; all operations must run off the UI thread. */
public final class NavigationStore {
  private static final Object LOCK = new Object();
  private static final Set<String> POISONED = new HashSet<>();
  private final SharedPreferences prefs;

  public NavigationStore(Context c) {
    prefs =
        c.getApplicationContext()
            .getSharedPreferences("game_integration_navigation", Context.MODE_PRIVATE);
  }

  private String key(IntegrationContext c) {
    return (IntegrationContext.DATASET.equals(c.datasetId) ? "" : c.datasetId + "_")
        + c.runId + "_" + c.mode.name();
  }

  private boolean commit(String k, SharedPreferences.Editor editor) {
    try {
      if (editor.commit()) return true;
    } catch (RuntimeException ignored) {
      // The in-memory preference may already have changed.
    }
    POISONED.add(k);
    return false;
  }

  /** Must succeed before opening H04 database. One clock kind per run/mode. */
  public boolean bindClock(IntegrationContext c) {
    synchronized (LOCK) {
      try {
        if (!IntegrationComposition.allowedContext(c, com.example.firststep.BuildConfig.DEBUG)) return false;
        String k = key(c);
        if (POISONED.contains(k)) return false;
        String old = prefs.getString(k + "_clock", null);
        if (old != null) return bound(c);
        if (prefs.contains(k + "_state") || prefs.contains(k + "_now")) return false;
        SharedPreferences.Editor editor = prefs.edit().putString(k + "_clock", c.clock.name());
        if (c.fakeNow != null) editor.putLong(k + "_now", c.fakeNow);
        return commit(k, editor);
      } catch (RuntimeException e) {
        return false;
      }
    }
  }

  private boolean bound(IntegrationContext c) {
    if (!IntegrationComposition.allowedContext(c, com.example.firststep.BuildConfig.DEBUG)) return false;
    String k = key(c);
    if (POISONED.contains(k) || !c.clock.name().equals(prefs.getString(k + "_clock", null)))
      return false;
    boolean hasNow = prefs.contains(k + "_now");
    return c.clock == IntegrationContext.Clock.DEBUG_FAKE
        ? hasNow && prefs.getAll().get(k + "_now") instanceof Long
        : !hasNow;
  }

  public boolean saveClock(IntegrationContext c) {
    synchronized (LOCK) {
      try {
        if (!bound(c)) return false;
        if (c.clock == IntegrationContext.Clock.REAL) return true;
        if (prefs.contains(key(c) + "_now") && c.fakeNow < prefs.getLong(key(c) + "_now", 0))
          return false;
        return commit(key(c), prefs.edit().putLong(key(c) + "_now", c.fakeNow));
      } catch (RuntimeException e) {
        return false;
      }
    }
  }

  private IntegrationContext latest(IntegrationContext c) {
    if (c.clock == IntegrationContext.Clock.DEBUG_FAKE && prefs.contains(key(c) + "_now"))
      return c.withFakeNow(prefs.getLong(key(c) + "_now", 0));
    return c;
  }

  public State load(IntegrationContext c) {
    synchronized (LOCK) {
      try {
        return read(c);
      } catch (Exception e) {
        return null;
      }
    }
  }

  private static String string(JSONObject j, String key) throws Exception {
    Object value = j.get(key);
    if (!(value instanceof String)) throw new IllegalArgumentException(key);
    return (String) value;
  }

  private static boolean bool(JSONObject j, String key) throws Exception {
    Object value = j.get(key);
    if (!(value instanceof Boolean)) throw new IllegalArgumentException(key);
    return (Boolean) value;
  }

  private static long session(JSONObject j) throws Exception {
    String value = string(j, "session");
    long parsed = Long.parseLong(value);
    if (!Long.toString(parsed).equals(value)) throw new IllegalArgumentException("session");
    return parsed;
  }

  private State read(IntegrationContext c) throws Exception {
    if (!bound(c)) return null;
    c = latest(c);
    String raw = prefs.getString(key(c) + "_state", null);
    if (raw == null) return new State(NavigationPolicy.Phase.EMPTY, c, null, null);
    JSONObject j = new JSONObject(raw);
    if (!(j.get("schema") instanceof Integer) || !Integer.valueOf(1).equals(j.get("schema")))
      return null;
    if (!c.datasetId.equals(string(j, "dataset"))
        || !c.runId.equals(string(j, "run"))
        || !c.targetId.equals(string(j, "target"))
        || !c.mode.name().equals(string(j, "mode"))
        || !c.clock.name().equals(string(j, "clock"))) return null;
    NavigationPolicy.Phase phase = NavigationPolicy.Phase.valueOf(string(j, "phase"));
    if (phase == NavigationPolicy.Phase.EMPTY) return null;
    String nonce = string(j, "nonce");
    if (!IntegrationContext.canonicalUuid(nonce)) return null;
    State pending = NavigationPolicy.begin(State.empty(), c, nonce);
    if (phase == NavigationPolicy.Phase.PENDING) {
      for (String field :
          new String[] {
            "session", "resultTarget", "method", "source", "arrivalSimulated", "camera", "heading"
          }) if (j.has(field)) return null;
      return pending;
    }
    if (!bool(j, "arrivalSimulated")) return null;
    DiscoveryResult d =
        new DiscoveryResult(
            session(j),
            string(j, "resultTarget"),
            DiscoveryResult.Method.valueOf(string(j, "method")),
            DiscoveryResult.Source.valueOf(string(j, "source")),
            bool(j, "camera"),
            bool(j, "heading"));
    State accepted = NavigationPolicy.accept(pending, new IntegrationResult(c, nonce, d));
    if (accepted == null) return null;
    if (phase == NavigationPolicy.Phase.ACCEPTED) return accepted;
    if (phase == NavigationPolicy.Phase.CONSUMED)
      return NavigationPolicy.consume(accepted, c, nonce);
    return null;
  }

  private boolean write(IntegrationContext c, State s) {
    try {
      String k = key(c);
      if (s == null) return false;
      if (s.phase == NavigationPolicy.Phase.EMPTY)
        return commit(k, prefs.edit().remove(k + "_state"));
      JSONObject j = new JSONObject();
      j.put("schema", 1)
          .put("dataset", c.datasetId)
          .put("run", c.runId)
          .put("target", c.targetId)
          .put("mode", c.mode.name())
          .put("clock", c.clock.name())
          .put("phase", s.phase.name())
          .put("nonce", s.nonce);
      if (s.result != null) {
        DiscoveryResult d = s.result.discovery;
        j.put("session", Long.toString(d.sessionId))
            .put("arrivalSimulated", d.arrivalSimulated)
            .put("resultTarget", d.targetId)
            .put("method", d.method.name())
            .put("source", d.source.name())
            .put("camera", d.cameraFallback)
            .put("heading", d.headingFallback);
      }
      SharedPreferences.Editor e = prefs.edit().putString(k + "_state", j.toString());
      return commit(k, e);
    } catch (Exception e) {
      return false;
    }
  }

  public boolean begin(IntegrationContext c, String nonce) {
    synchronized (LOCK) {
      try {
        return write(c, NavigationPolicy.begin(read(c), c, nonce));
      } catch (Exception e) {
        return false;
      }
    }
  }

  public boolean cancel(IntegrationContext c, String nonce) {
    synchronized (LOCK) {
      try {
        return write(c, NavigationPolicy.cancel(read(c), c, nonce));
      } catch (Exception e) {
        return false;
      }
    }
  }

  public boolean accept(IntegrationResult r) {
    synchronized (LOCK) {
      try {
        return write(r.context, NavigationPolicy.accept(read(r.context), r));
      } catch (Exception e) {
        return false;
      }
    }
  }

  public boolean consume(IntegrationContext c, String nonce) {
    synchronized (LOCK) {
      try {
        return write(c, NavigationPolicy.consume(read(c), c, nonce));
      } catch (Exception e) {
        return false;
      }
    }
  }
}
