package com.example.firststep.game.integration;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.integration.NavigationPolicy;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class IntegrationBoundaryTest {
  private static String uuid() {
    return UUID.randomUUID().toString();
  }

  private static IntegrationContext context(
      String run, Progress.Mode mode, IntegrationContext.Clock clock) {
    return IntegrationContext.create(
        IntegrationContext.DATASET,
        run,
        IntegrationContext.TARGET,
        mode,
        clock,
        clock == IntegrationContext.Clock.DEBUG_FAKE ? 100L : null);
  }

  private static IntegrationResult result(IntegrationContext c, String nonce) {
    return new IntegrationResult(
        c,
        nonce,
        new DiscoveryResult(
            42,
            c.targetId,
            DiscoveryResult.Method.SHAKE,
            DiscoveryResult.Source.SIMULATED,
            true,
            false));
  }

  private static Intent encoded(IntegrationContext c) {
    Intent i = new Intent();
    IntegrationIntentCodec.writeContext(i, c);
    return i;
  }

  private static Intent encodedResult(IntegrationContext c, String nonce) {
    Intent i = new Intent();
    IntegrationIntentCodec.writeResult(i, result(c, nonce));
    return i;
  }

  private static IsolatedContext isolated() {
    return new IsolatedContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
  }

  @Test
  public void intentContextBoundary() {
    IntegrationContext c =
        context(uuid(), Progress.Mode.NORMAL, IntegrationContext.Clock.DEBUG_FAKE);
    assertFalse(IntegrationIntentCodec.hasIntegrationContext(null));
    assertFalse(
        IntegrationIntentCodec.hasIntegrationContext(
            new Intent().putExtra(IntegrationIntentCodec.RUN, c.runId)));
    assertNull(IntegrationIntentCodec.readContext(null));
    assertNotNull(IntegrationIntentCodec.readContext(encoded(c)));
    Intent i = encoded(c);
    i.removeExtra(IntegrationIntentCodec.MARKER);
    assertTrue(IntegrationIntentCodec.hasIntegrationContext(i));
    assertNull(IntegrationIntentCodec.readContext(i));
    i = encoded(c);
    i.putExtra(IntegrationIntentCodec.MARKER, false);
    assertNull(IntegrationIntentCodec.readContext(i));
    i = encoded(c);
    i.putExtra(IntegrationIntentCodec.MARKER, "true");
    assertNull(IntegrationIntentCodec.readContext(i));
    i = encoded(c);
    i.removeExtra(IntegrationIntentCodec.DATASET);
    assertNull(IntegrationIntentCodec.readContext(i));
    i = encoded(c);
    i.putExtra(IntegrationIntentCodec.RUN, "not-a-uuid");
    assertNull(IntegrationIntentCodec.readContext(i));
    i = encoded(c);
    i.putExtra(IntegrationIntentCodec.FAKE_NOW, 100);
    assertNull(IntegrationIntentCodec.readContext(i));
    i = encoded(c);
    i.putExtra(IntegrationIntentCodec.TARGET, "synthetic_cover_demo");
    assertNull(IntegrationIntentCodec.readContext(i));
    i = encoded(c);
    i.putExtra("game_intro_mode", "DEMO");
    assertNull(IntegrationIntentCodec.readContext(i));
    i = encoded(c);
    i.putExtra("game_intro_building_id", "other");
    assertNull(IntegrationIntentCodec.readContext(i));
    i = encoded(c);
    i.putExtra("game_intro_instant_demo", true);
    assertNull(IntegrationIntentCodec.readContext(i));
    i = encoded(c);
    i.putExtra(IntegrationIntentCodec.DATASET, (String) null);
    assertNull(IntegrationIntentCodec.readContext(i));
  }

  @Test
  public void intentResultBoundary() {
    IntegrationContext c = context(uuid(), Progress.Mode.NORMAL, IntegrationContext.Clock.REAL);
    String n = uuid();
    assertNotNull(IntegrationIntentCodec.readResult(encodedResult(c, n)));
    Intent i = encodedResult(c, n);
    i.putExtra(IntegrationIntentCodec.NONCE, "bad");
    assertNull(IntegrationIntentCodec.readResult(i));
    i = encodedResult(c, n);
    i.putExtra("game_integration_result_target", "synthetic_cover_demo");
    assertNull(IntegrationIntentCodec.readResult(i));
    i = encodedResult(c, n);
    i.putExtra("game_integration_method", "LIGHT");
    assertNull(IntegrationIntentCodec.readResult(i));
    i = encodedResult(c, n);
    i.putExtra("game_integration_source", "LONG_PRESS");
    assertNull(IntegrationIntentCodec.readResult(i));
    i = encodedResult(c, n);
    i.putExtra("game_integration_session", 0L);
    assertNull(IntegrationIntentCodec.readResult(i));
    i = encodedResult(c, n);
    i.putExtra("game_integration_session", 42);
    assertNull(IntegrationIntentCodec.readResult(i));
    i = encodedResult(c, n);
    i.putExtra("game_integration_arrival_simulated", false);
    assertNull(IntegrationIntentCodec.readResult(i));
    i = encodedResult(c, n);
    i.removeExtra("game_integration_camera_fallback");
    assertNull(IntegrationIntentCodec.readResult(i));
  }

  @Test
  public void navigationPersistenceAndStaleness() {
    IsolatedContext app = isolated();
    NavigationStore first = new NavigationStore(app);
    String run = uuid(), nonce = uuid();
    IntegrationContext c = context(run, Progress.Mode.NORMAL, IntegrationContext.Clock.DEBUG_FAKE);
    IntegrationContext otherRun =
        context(uuid(), Progress.Mode.NORMAL, IntegrationContext.Clock.DEBUG_FAKE);
    IntegrationContext otherMode =
        context(run, Progress.Mode.DEMO, IntegrationContext.Clock.DEBUG_FAKE);
    assertTrue(first.bindClock(c));
    assertTrue(first.bindClock(otherRun));
    assertTrue(first.bindClock(otherMode));
    assertTrue(first.begin(c, nonce));
    assertFalse(first.cancel(c, uuid()));
    assertFalse(first.accept(result(otherRun, nonce)));
    assertFalse(first.accept(result(otherMode, nonce)));
    assertFalse(first.accept(result(c, uuid())));
    assertFalse(
        first.accept(
            new IntegrationResult(
                c,
                nonce,
                new DiscoveryResult(
                    7,
                    "synthetic_cover_demo",
                    DiscoveryResult.Method.SHAKE,
                    DiscoveryResult.Source.SIMULATED,
                    false,
                    false))));
    assertTrue(first.saveClock(c.withFakeNow(900)));
    assertTrue(first.accept(result(c, nonce)));
    assertFalse(first.accept(result(c, nonce)));
    NavigationStore reopened = new NavigationStore(app);
    NavigationPolicy.State accepted = reopened.load(c);
    assertEquals(NavigationPolicy.Phase.ACCEPTED, accepted.phase);
    assertEquals(Long.valueOf(900), accepted.context.fakeNow);
    assertEquals(42, accepted.result.discovery.sessionId);
    assertTrue(accepted.result.discovery.cameraFallback);
    assertFalse(accepted.result.discovery.headingFallback);
    assertFalse(reopened.consume(c, uuid()));
    assertTrue(reopened.consume(c, nonce));
    assertFalse(first.consume(c, nonce));
    NavigationPolicy.State consumed = new NavigationStore(app).load(c);
    assertEquals(NavigationPolicy.Phase.CONSUMED, consumed.phase);
    assertEquals(DiscoveryResult.Source.SIMULATED, consumed.result.discovery.source);
    assertEquals(Long.valueOf(900), consumed.context.fakeNow);
    String cancel = uuid();
    assertTrue(first.begin(otherRun, cancel));
    assertTrue(first.cancel(otherRun, cancel));
    assertFalse(first.accept(result(otherRun, cancel)));
    assertEquals(NavigationPolicy.Phase.EMPTY, first.load(otherRun).phase);
  }

  @Test
  public void clockBindingAndIsolation() throws Exception {
    IsolatedContext app = isolated();
    NavigationStore store = new NavigationStore(app);
    String run = uuid();
    IntegrationContext fake =
        context(run, Progress.Mode.NORMAL, IntegrationContext.Clock.DEBUG_FAKE);
    IntegrationContext real = context(run, Progress.Mode.NORMAL, IntegrationContext.Clock.REAL);
    IntegrationContext demoReal = context(run, Progress.Mode.DEMO, IntegrationContext.Clock.REAL);
    assertTrue(store.bindClock(fake));
    assertFalse(store.bindClock(real));
    assertNull(store.load(real));
    assertTrue(store.saveClock(fake.withFakeNow(500)));
    assertFalse(store.saveClock(fake.withFakeNow(499)));
    assertEquals(Long.valueOf(500), new NavigationStore(app).load(fake).context.fakeNow);
    assertTrue(store.bindClock(demoReal));
    assertEquals(NavigationPolicy.Phase.EMPTY, store.load(demoReal).phase);
    FakePrefs prefs = app.prefs("game_integration_navigation");
    String missing = uuid();
    prefs.edit().putString(missing + "_NORMAL_clock", "DEBUG_FAKE").commit();
    IntegrationContext bad =
        context(missing, Progress.Mode.NORMAL, IntegrationContext.Clock.DEBUG_FAKE);
    assertFalse(store.bindClock(bad));
    assertNull(store.load(bad));
    assertFalse(store.saveClock(bad));
    String stray = uuid();
    prefs
        .edit()
        .putString(stray + "_NORMAL_clock", "REAL")
        .putLong(stray + "_NORMAL_now", 1)
        .commit();
    assertFalse(
        store.bindClock(context(stray, Progress.Mode.NORMAL, IntegrationContext.Clock.REAL)));
    String corruptRun = uuid();
    IntegrationContext corrupt =
        context(corruptRun, Progress.Mode.NORMAL, IntegrationContext.Clock.REAL);
    assertTrue(store.bindClock(corrupt));
    assertTrue(store.begin(corrupt, uuid()));
    String stateKey = corruptRun + "_NORMAL_state";
    org.json.JSONObject state = new org.json.JSONObject(prefs.getString(stateKey, null));
    state.put("camera", true);
    prefs.edit().putString(stateKey, state.toString()).commit();
    assertNull(store.load(corrupt));
    assertFalse(store.begin(corrupt, uuid()));
    state.remove("camera");
    state.put("schema", "1");
    prefs.edit().putString(stateKey, state.toString()).commit();
    assertNull(store.load(corrupt));
  }

  @Test
  public void stableEntryRunIsolation() {
    IsolatedContext app = isolated();
    StableRunStore a = new StableRunStore(app);
    String stable = a.resolve(null), explicit = uuid();
    assertTrue(IntegrationContext.canonicalUuid(stable));
    assertEquals(explicit, a.resolve(explicit));
    assertEquals(stable, new StableRunStore(app).resolve(null));
    assertNull(a.resolve("bad"));
  }

  @Test
  public void commitFailurePoisonsOnlyIdentity() {
    IsolatedContext app = isolated();
    FakePrefs prefs = app.prefs("game_integration_navigation");
    for (Failure failure : new Failure[] {Failure.FALSE, Failure.THROW}) {
      IntegrationContext c = context(uuid(), Progress.Mode.NORMAL, IntegrationContext.Clock.REAL);
      String nonce = uuid();
      NavigationStore a = new NavigationStore(app);
      assertTrue(a.bindClock(c));
      assertTrue(a.begin(c, nonce));
      prefs.nextFailure = failure;
      assertFalse(a.accept(result(c, nonce)));
      NavigationStore b = new NavigationStore(app);
      assertNull(b.load(c));
      assertFalse(b.consume(c, nonce));
      assertFalse(b.bindClock(c));
      IntegrationContext fresh =
          context(uuid(), Progress.Mode.NORMAL, IntegrationContext.Clock.REAL);
      assertTrue(b.bindClock(fresh));
      assertTrue(b.begin(fresh, uuid()));
    }
  }

  private enum Failure {
    NONE,
    FALSE,
    THROW
  }

  private static final class IsolatedContext extends ContextWrapper {
    private final Map<String, FakePrefs> stores = new HashMap<>();

    IsolatedContext(Context base) {
      super(base);
    }

    @Override
    public Context getApplicationContext() {
      return this;
    }

    @Override
    public synchronized SharedPreferences getSharedPreferences(String name, int mode) {
      return prefs(name);
    }

    synchronized FakePrefs prefs(String name) {
      FakePrefs p = stores.get(name);
      if (p == null) {
        p = new FakePrefs();
        stores.put(name, p);
      }
      return p;
    }
  }

  private static final class FakePrefs implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();
    volatile Failure nextFailure = Failure.NONE;

    @Override
    public synchronized Map<String, ?> getAll() {
      return new HashMap<>(values);
    }

    @Override
    public synchronized String getString(String k, String d) {
      return values.containsKey(k) ? (String) values.get(k) : d;
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized Set<String> getStringSet(String k, Set<String> d) {
      return values.containsKey(k) ? new HashSet<>((Set<String>) values.get(k)) : d;
    }

    @Override
    public synchronized int getInt(String k, int d) {
      return values.containsKey(k) ? (Integer) values.get(k) : d;
    }

    @Override
    public synchronized long getLong(String k, long d) {
      return values.containsKey(k) ? (Long) values.get(k) : d;
    }

    @Override
    public synchronized float getFloat(String k, float d) {
      return values.containsKey(k) ? (Float) values.get(k) : d;
    }

    @Override
    public synchronized boolean getBoolean(String k, boolean d) {
      return values.containsKey(k) ? (Boolean) values.get(k) : d;
    }

    @Override
    public synchronized boolean contains(String k) {
      return values.containsKey(k);
    }

    @Override
    public Editor edit() {
      return new Edit();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

    private final class Edit implements Editor {
      private final Map<String, Object> changes = new HashMap<>();
      private final Set<String> removed = new HashSet<>();
      private boolean clear;

      @Override
      public Editor putString(String k, String v) {
        if (v == null) return remove(k);
        changes.put(k, v);
        removed.remove(k);
        return this;
      }

      @Override
      public Editor putStringSet(String k, Set<String> v) {
        if (v == null) return remove(k);
        changes.put(k, new HashSet<>(v));
        removed.remove(k);
        return this;
      }

      @Override
      public Editor putInt(String k, int v) {
        changes.put(k, v);
        removed.remove(k);
        return this;
      }

      @Override
      public Editor putLong(String k, long v) {
        changes.put(k, v);
        removed.remove(k);
        return this;
      }

      @Override
      public Editor putFloat(String k, float v) {
        changes.put(k, v);
        removed.remove(k);
        return this;
      }

      @Override
      public Editor putBoolean(String k, boolean v) {
        changes.put(k, v);
        removed.remove(k);
        return this;
      }

      @Override
      public Editor remove(String k) {
        changes.remove(k);
        removed.add(k);
        return this;
      }

      @Override
      public Editor clear() {
        clear = true;
        return this;
      }

      @Override
      public boolean commit() {
        synchronized (FakePrefs.this) {
          if (clear) values.clear();
          for (String k : removed) values.remove(k);
          values.putAll(changes);
          Failure failure = nextFailure;
          nextFailure = Failure.NONE;
          if (failure == Failure.THROW)
            throw new IllegalStateException("injected failure after mutation");
          return failure != Failure.FALSE;
        }
      }

      @Override
      public void apply() {
        commit();
      }
    }
  }
}
