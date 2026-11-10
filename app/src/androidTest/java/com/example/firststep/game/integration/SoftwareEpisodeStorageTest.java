package com.example.firststep.game.integration;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.data.local.GameJson;
import com.example.firststep.game.domain.GameContent;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.integration.NavigationPolicy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Android contract, with generated test-only files and fail-closed database and preference names. */
@RunWith(AndroidJUnit4.class)
public class SoftwareEpisodeStorageTest {
  private static final Progress.Mode DEMO = Progress.Mode.DEMO;
  private static final String TARGET = IntegrationContext.TARGET;

  @Test
  public void sameRunSeparatesPhysicalProgressAndNavigation() throws Exception {
    String run = UUID.randomUUID().toString();
    IntegrationContext fixture = IntegrationContext.create(
        IntegrationContext.DATASET, run, TARGET, DEMO, IntegrationContext.Clock.DEBUG_FAKE, 100L);
    IntegrationContext software = IntegrationContext.create(
        IntegrationContext.SOFTWARE_DATASET, run, TARGET, DEMO, IntegrationContext.Clock.REAL, null);
    String fixtureDb = "h04-debug-" + run + ".db";
    String softwareDb = "software-episode-demo-" + run + ".db";
    Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    try (IsolatedContext app = new IsolatedContext(target, run, fixtureDb, softwareDb)) {
      Map<String, Object> storageSentinels = app.seedStorageSentinels();
      StableRunStore runs = new StableRunStore(app);
      String fixtureRun = runs.resolve(null);
      String softwareRun = runs.resolve(software.datasetId, null);
      assertNotEquals(fixtureRun, softwareRun);
      assertEquals(run, runs.resolve(software.datasetId, run));
      assertEquals(fixtureRun, new StableRunStore(app).resolve(null));
      assertEquals(softwareRun, new StableRunStore(app).resolve(software.datasetId, null));

      NavigationStore navigation = new NavigationStore(app);
      assertTrue(navigation.bindClock(fixture));
      assertTrue(navigation.bindClock(software));
      String nonce = UUID.randomUUID().toString();
      assertTrue(navigation.begin(fixture, nonce));
      assertEquals(NavigationPolicy.Phase.EMPTY, navigation.load(software).phase);
      assertTrue(navigation.begin(software, nonce));
      try {
        IntegrationRouteAccess.requireIntroduction(app, software);
        fail("unconfirmed discovery allowed introduction");
      } catch (IllegalStateException expected) {
        assertEquals("discovery continuation required", expected.getMessage());
      }
      DiscoveryResult discovery = new DiscoveryResult(
          1, TARGET, DiscoveryResult.Method.LONG_PRESS, DiscoveryResult.Source.LONG_PRESS,
          true, true);
      assertTrue(navigation.accept(new IntegrationResult(software, nonce, discovery)));
      assertTrue(navigation.consume(software, nonce));
      assertFalse(navigation.consume(software, nonce));
      assertEquals(NavigationPolicy.Phase.PENDING, navigation.load(fixture).phase);

      String fixturePayload;
      try (IntegrationComposition opened = IntegrationComposition.open(app, fixture)) {
        service(opened, 100L).intro(DEMO, TARGET, true);
        fixturePayload = GameJson.encode(opened.repository.read(DEMO, TARGET));
      }
      assertNull(IntegrationRouteAccess.readProgress(app, software).introDoneAt);
      assertTrue(software.equalsIdentity(IntegrationRouteAccess.requireIntroduction(app, software)));

      try (IntegrationComposition opened = IntegrationComposition.open(app, software)) {
        assertEquals(software.datasetId, opened.content.version);
        assertEquals(7, opened.narration.sentences().size());
        assertEquals("2026-11-08", opened.guide.getSources().get(0).getCheckedDate());
        List<GameContent.Question> questions = opened.content.buildings.get(TARGET).questions;
        assertEquals(Arrays.asList("location", "values", "notice"),
            Arrays.asList(questions.get(0).id, questions.get(1).id, questions.get(2).id));
        assertEquals(Arrays.asList("digital_first", "communicate", "official"),
            Arrays.asList(questions.get(0).correctOptionId, questions.get(1).correctOptionId,
                questions.get(2).correctOptionId));
        ProgressService service = service(opened, 900L);
        assertTrue(service.eligible(service.intro(DEMO, TARGET, true)));
        Progress.Attempt attempt = service.start(DEMO, TARGET).active;
        Map<String, String> answers = new LinkedHashMap<>();
        for (GameContent.Question question : attempt.questions)
          answers.put(question.id, question.correctOptionId);
        assertTrue(service.submit(DEMO, TARGET, attempt.id, attempt.contentVersion, answers).passed);
        assertTrue(service.submit(DEMO, TARGET, attempt.id, attempt.contentVersion, answers).passed);
      }
      assertTrue(app.getDatabasePath(fixtureDb).isFile());
      assertTrue(app.getDatabasePath(softwareDb).isFile());
      Progress resumed = IntegrationRouteAccess.readProgress(app, software);
      assertTrue(resumed.stamp);
      assertEquals(1, resumed.history.size());
      assertEquals(fixturePayload, GameJson.encode(IntegrationRouteAccess.readProgress(app, fixture)));
      assertEquals(storageSentinels, app.storageSentinels());
    }
  }

  private static ProgressService service(IntegrationComposition opened, long now) {
    return new ProgressService(opened.content, opened.repository, () -> now,
        new ProgressService.AttemptFactory() {
          public String nextId() { return UUID.randomUUID().toString(); }
          public void shuffle(List<?> values) { Collections.reverse(values); }
        });
  }

  private static final class IsolatedContext extends ContextWrapper implements AutoCloseable {
    private static final List<String> PREFS =
        Arrays.asList("game_integration_entry", "game_integration_navigation");
    private static final List<String> SENTINEL_DATABASES =
        Arrays.asList(
            "firststep-progress.db",
            "firststep-account-0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef.db");
    private static final List<String> SENTINEL_PREFS =
        Arrays.asList("firststep-settings", "firststep-auth");
    private final String prefix;
    private final List<String> databases;

    IsolatedContext(Context base, String run, String... databases) {
      super(base);
      prefix = "h10c-contract-" + run + "-";
      this.databases = Arrays.asList(databases);
    }

    @Override public Context getApplicationContext() { return this; }

    @Override public SharedPreferences getSharedPreferences(String name, int mode) {
      if (!PREFS.contains(name)) throw new AssertionError("Unexpected preferences " + name);
      return super.getSharedPreferences(prefix + name, mode);
    }

    private void assertTestDatabase(String name) {
      if (!databases.contains(name)) throw new AssertionError("Unexpected database " + name);
    }

    @Override public File getDatabasePath(String name) {
      assertTestDatabase(name);
      return super.getDatabasePath(prefix + name);
    }

    @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
        SQLiteDatabase.CursorFactory factory) {
      assertTestDatabase(name);
      return super.openOrCreateDatabase(prefix + name, mode, factory);
    }

    @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
        SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) {
      assertTestDatabase(name);
      return super.openOrCreateDatabase(prefix + name, mode, factory, errorHandler);
    }

    Map<String, Object> seedStorageSentinels() throws IOException, NoSuchAlgorithmException {
      for (String name : SENTINEL_DATABASES) {
        try (SQLiteDatabase db = super.openOrCreateDatabase(prefix + name, MODE_PRIVATE, null)) {
          db.execSQL("CREATE TABLE sentinel (value TEXT NOT NULL)");
          db.execSQL("INSERT INTO sentinel (value) VALUES (?)", new Object[] {"synthetic " + name});
        }
      }
      for (String name : SENTINEL_PREFS) {
        assertTrue(super.getSharedPreferences(prefix + name, MODE_PRIVATE)
            .edit().putString("sentinel", "synthetic " + name).commit());
      }
      return storageSentinels();
    }

    Map<String, Object> storageSentinels() throws IOException, NoSuchAlgorithmException {
      Map<String, Object> snapshot = new LinkedHashMap<>();
      for (String name : SENTINEL_DATABASES) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream stream = new FileInputStream(super.getDatabasePath(prefix + name))) {
          byte[] buffer = new byte[4096];
          for (int size; (size = stream.read(buffer)) != -1; ) digest.update(buffer, 0, size);
        }
        snapshot.put(name, Arrays.toString(digest.digest()));
      }
      for (String name : SENTINEL_PREFS) {
        snapshot.put(name, new LinkedHashMap<>(
            super.getSharedPreferences(prefix + name, MODE_PRIVATE).getAll()));
      }
      return snapshot;
    }

    @Override public void close() {
      for (String name : databases) super.deleteDatabase(prefix + name);
      for (String name : SENTINEL_DATABASES) super.deleteDatabase(prefix + name);
      for (String name : PREFS) super.deleteSharedPreferences(prefix + name);
      for (String name : SENTINEL_PREFS) super.deleteSharedPreferences(prefix + name);
    }
  }
}
