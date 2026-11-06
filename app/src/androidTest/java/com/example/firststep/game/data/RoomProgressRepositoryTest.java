package com.example.firststep.game.data;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.firststep.game.data.local.*;
import com.example.firststep.game.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RoomProgressRepositoryTest {
  private GameProgressDatabase db(Context c, String name) {
    return Room.databaseBuilder(c, GameProgressDatabase.class, name).build();
  }

  private String asset(Context c) throws Exception {
    try (InputStream in = c.getAssets().open("game/synthetic_catalog.json");
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }

  private static class Factory implements ProgressService.AttemptFactory {
    public String nextId() {
      return UUID.randomUUID().toString();
    }

    public void shuffle(List<?> a) {
      Collections.reverse(a);
    }
  }

  private Map<String, String> answers(Progress.Attempt a, String value) {
    Map<String, String> m = new HashMap<>();
    for (GameContent.Question q : a.questions) m.put(q.id, value);
    return m;
  }

  @Test
  public void catalogReopenRollbackAndIsolation() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    String name = "h02-test-" + UUID.randomUUID() + ".db";
    String sentinel = "h02-legacy-sentinel-" + UUID.randomUUID() + ".db";
    assertNotEquals(GameProgressDatabase.FILE_NAME, "firststep-progress.db");
    try {
      try (android.database.sqlite.SQLiteDatabase legacy =
          c.openOrCreateDatabase(sentinel, 0, null)) {
        legacy.execSQL("CREATE TABLE sentinel (value TEXT)");
        legacy.execSQL("INSERT INTO sentinel VALUES ('kept')");
      }
      GameContent content = GameJson.catalog(asset(c));
      assertEquals(5, content.buildings.size());
      Progress.Attempt a;
      try (RoomProgressRepository r = new RoomProgressRepository(db(c, name))) {
        ProgressService svc = new ProgressService(content, r, () -> 0L, new Factory());
        svc.intro(Progress.Mode.DEMO, "fixture-1", true);
        a = svc.start(Progress.Mode.DEMO, "fixture-1").active;
      }
      GameProgressDatabase database = db(c, name);
      try (RoomProgressRepository r = new RoomProgressRepository(database)) {
        ProgressService svc = new ProgressService(content, r, () -> -1L, new Factory());
        Progress.Attempt restored = svc.start(Progress.Mode.DEMO, "fixture-1").active;
        assertEquals(a.id, restored.id);
        assertEquals(a.questions.get(0).id, restored.questions.get(0).id);
        assertEquals(
            a.questions.get(0).options.get(0).id, restored.questions.get(0).options.get(0).id);
        try {
          r.atomicUpdate(
              Progress.Mode.DEMO,
              "fixture-1",
              p -> {
                throw new IllegalStateException("rollback");
              });
          fail();
        } catch (IllegalStateException expected) {
        }
        assertEquals(a.id, r.read(Progress.Mode.DEMO, "fixture-1").active.id);
        Map<String, Progress.Result> history = new HashMap<>();
        history.put(a.id, new Progress.Result(a.id, a.contentVersion, answers(a, "a"), 3));
        Progress reward =
            new Progress(Progress.Mode.DEMO, "fixture-1", 0L, true, true, null, history);
        try {
          database.runInTransaction(
              () -> {
                database
                    .progressDao()
                    .save(
                        new GameProgressDatabase.Row("DEMO", "fixture-1", GameJson.encode(reward)));
                throw new IllegalStateException("after reward write");
              });
          fail();
        } catch (IllegalStateException expected) {
        }
        assertEquals(a.id, r.read(Progress.Mode.DEMO, "fixture-1").active.id);
      }
      try (RoomProgressRepository r = new RoomProgressRepository(db(c, name))) {
        assertFalse(r.read(Progress.Mode.DEMO, "fixture-1").stamp);
        assertEquals(a.id, r.read(Progress.Mode.DEMO, "fixture-1").active.id);
        ProgressService svc = new ProgressService(content, r, () -> -1L, new Factory());
        assertTrue(
            svc.submit(Progress.Mode.DEMO, "fixture-1", a.id, a.contentVersion, answers(a, "a"))
                .passed);
        assertFalse(r.read(Progress.Mode.NORMAL, "fixture-1").stamp);
      }
      try (RoomProgressRepository r = new RoomProgressRepository(db(c, name))) {
        assertTrue(r.read(Progress.Mode.DEMO, "fixture-1").stamp);
      }
      try (android.database.sqlite.SQLiteDatabase legacy =
              c.openOrCreateDatabase(sentinel, 0, null);
          android.database.Cursor cursor = legacy.rawQuery("SELECT value FROM sentinel", null)) {
        assertTrue(cursor.moveToFirst());
        assertEquals("kept", cursor.getString(0));
      }
    } finally {
      c.deleteDatabase(name);
      c.deleteDatabase(sentinel);
    }
  }

  @Test
  public void independentNormalWaitingAndAttemptOrderSurviveReopen() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    String name = "h02-waiting-" + UUID.randomUUID() + ".db";
    try {
      GameContent content = GameJson.catalog(asset(c));
      try (RoomProgressRepository r = new RoomProgressRepository(db(c, name))) {
        new ProgressService(content, r, () -> 1000L, new Factory())
            .intro(Progress.Mode.NORMAL, "fixture-5", false);
        new ProgressService(content, r, () -> 2000L, new Factory())
            .intro(Progress.Mode.NORMAL, "fixture-2", false);
      }
      Progress.Attempt original;
      try (RoomProgressRepository r = new RoomProgressRepository(db(c, name))) {
        ProgressService svc = new ProgressService(content, r, () -> 901000L, new Factory());
        assertTrue(svc.eligible(svc.read(Progress.Mode.NORMAL, "fixture-5")));
        assertFalse(svc.eligible(svc.read(Progress.Mode.NORMAL, "fixture-2")));
        assertEquals(
            Long.valueOf(1000L), svc.intro(Progress.Mode.NORMAL, "fixture-5", false).introDoneAt);
        assertNull(svc.read(Progress.Mode.DEMO, "fixture-5").introDoneAt);
        assertNull(svc.read(Progress.Mode.DEMO, "fixture-2").introDoneAt);
        original = svc.start(Progress.Mode.NORMAL, "fixture-5").active;
      }
      try (RoomProgressRepository r = new RoomProgressRepository(db(c, name))) {
        ProgressService svc = new ProgressService(content, r, () -> 0L, new Factory());
        Progress.Attempt restored = svc.start(Progress.Mode.NORMAL, "fixture-5").active;
        assertEquals(original.id, restored.id);
        assertEquals(original.contentVersion, restored.contentVersion);
        assertEquals(original.questions.size(), restored.questions.size());
        for (int i = 0; i < original.questions.size(); i++) {
          GameContent.Question before = original.questions.get(i),
              after = restored.questions.get(i);
          assertEquals(before.id, after.id);
          assertEquals(before.options.size(), after.options.size());
          for (int j = 0; j < before.options.size(); j++)
            assertEquals(before.options.get(j).id, after.options.get(j).id);
        }
        assertNull(svc.read(Progress.Mode.DEMO, "fixture-5").active);
        assertNull(svc.read(Progress.Mode.DEMO, "fixture-2").introDoneAt);
      }
    } finally {
      c.deleteDatabase(name);
    }
  }

  @Test
  public void concurrentSubmissionsAcrossHandlesAwardOnce() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    String name = "h02-concurrent-" + UUID.randomUUID() + ".db";
    RoomProgressRepository r1 = new RoomProgressRepository(db(c, name)),
        r2 = new RoomProgressRepository(db(c, name));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      GameContent content = GameJson.catalog(asset(c));
      ProgressService s1 = new ProgressService(content, r1, () -> 0L, new Factory()),
          s2 = new ProgressService(content, r2, () -> 0L, new Factory());
      s1.intro(Progress.Mode.DEMO, "fixture-1", true);
      Progress.Attempt a = s1.start(Progress.Mode.DEMO, "fixture-1").active;
      Map<String, String> ans = answers(a, "a");
      CountDownLatch gate = new CountDownLatch(1);
      Future<Progress.Result> f1 =
          pool.submit(
              () -> {
                gate.await();
                return s1.submit(Progress.Mode.DEMO, "fixture-1", a.id, a.contentVersion, ans);
              });
      Future<Progress.Result> f2 =
          pool.submit(
              () -> {
                gate.await();
                return s2.submit(Progress.Mode.DEMO, "fixture-1", a.id, a.contentVersion, ans);
              });
      gate.countDown();
      assertTrue(f1.get(10, TimeUnit.SECONDS).passed);
      assertTrue(f2.get(10, TimeUnit.SECONDS).passed);
      assertEquals(1, r1.read(Progress.Mode.DEMO, "fixture-1").history.size());
    } finally {
      pool.shutdownNow();
      r1.close();
      r2.close();
      c.deleteDatabase(name);
    }
  }

  @Test
  public void invalidSchemaAndRowKeysAreRejectedWithoutOverwrite() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    String name = "h02-invalid-" + UUID.randomUUID() + ".db";
    GameProgressDatabase database = db(c, name);
    RoomProgressRepository repo = new RoomProgressRepository(database);
    try {
      Progress p = Progress.empty(Progress.Mode.DEMO, "fixture-1");
      String valid = GameJson.encode(p);
      org.json.JSONObject missingIntro = new org.json.JSONObject(valid);
      missingIntro.remove("introDoneAt");
      org.json.JSONObject missingActive = new org.json.JSONObject(valid);
      missingActive.remove("active");
      org.json.JSONObject badTime = new org.json.JSONObject(valid).put("introDoneAt", 1.5);
      Map<String, String> submitted = new HashMap<>();
      submitted.put("q1", "a");
      submitted.put("q2", "a");
      submitted.put("q3", "a");
      Map<String, Progress.Result> history = new HashMap<>();
      history.put("attempt", new Progress.Result("attempt", "v1", submitted, 3));
      String completed =
          GameJson.encode(
              new Progress(Progress.Mode.DEMO, "fixture-1", 0L, true, true, null, history));
      org.json.JSONObject badScore = new org.json.JSONObject(completed);
      badScore.getJSONArray("history").getJSONObject(0).put("correct", 2.5);
      String[] invalid = {
        valid.replace("\"schema\":1", "\"schema\":99"),
        valid.replace("\"schema\":1", "\"schema\":1.5"),
        missingIntro.toString(),
        missingActive.toString(),
        badTime.toString(),
        badScore.toString(),
        GameJson.encode(Progress.empty(Progress.Mode.NORMAL, "fixture-1")),
        GameJson.encode(Progress.empty(Progress.Mode.DEMO, "fixture-2"))
      };
      for (String payload : invalid) {
        database.progressDao().save(new GameProgressDatabase.Row("DEMO", "fixture-1", payload));
        try {
          repo.atomicUpdate(Progress.Mode.DEMO, "fixture-1", x -> x);
          fail();
        } catch (IllegalArgumentException | IllegalStateException expected) {
        }
        assertEquals(payload, database.progressDao().find("DEMO", "fixture-1").payload);
      }
    } finally {
      repo.close();
      c.deleteDatabase(name);
    }
  }

  @Test
  public void singleBuildingCatalogParsesFromJson() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    org.json.JSONObject root = new org.json.JSONObject(asset(c));
    org.json.JSONArray one = new org.json.JSONArray();
    one.put(root.getJSONArray("buildings").getJSONObject(0));
    root.put("buildings", one);
    GameContent content = GameJson.catalog(root.toString());
    assertEquals(1, content.buildings.size());
    assertEquals(3, content.buildings.get("fixture-1").questions.size());
  }

  private org.json.JSONObject target(org.json.JSONObject root, String section) throws Exception {
    switch (section) {
      case "root":
        return root;
      case "building":
        return root.getJSONArray("buildings").getJSONObject(0);
      case "catalogQuestion":
        return target(root, "building").getJSONArray("questions").getJSONObject(0);
      case "catalogOption":
        return target(root, "catalogQuestion").getJSONArray("options").getJSONObject(0);
      case "active":
        return root.getJSONObject("active");
      case "question":
        return target(root, "active").getJSONArray("questions").getJSONObject(0);
      case "option":
        return target(root, "question").getJSONArray("options").getJSONObject(0);
      case "history":
        return root.getJSONArray("history").getJSONObject(0);
      case "answers":
        return target(root, "history").getJSONObject("answers");
      default:
        throw new AssertionError(section);
    }
  }

  private void assertMalformedPersistedTypesRejected(boolean update) throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    String name = "h02-types-" + UUID.randomUUID() + ".db";
    GameProgressDatabase database = db(c, name);
    try (RoomProgressRepository repo = new RoomProgressRepository(database)) {
      GameContent content = GameJson.catalog(asset(c));
      List<GameContent.Question> questions = content.buildings.get("fixture-1").questions;
      Progress.Attempt active = new Progress.Attempt("active-id", content.version, questions);
      Map<String, String> answers = new LinkedHashMap<>();
      for (GameContent.Question q : questions) answers.put(q.id, q.options.get(0).id);
      Map<String, Progress.Result> history = new LinkedHashMap<>();
      history.put("old-id", new Progress.Result("old-id", content.version, answers, 0));
      String valid =
          GameJson.encode(
              new Progress(Progress.Mode.DEMO, "fixture-1", 0L, true, false, active, history));
      String[][] fields = {
        {"root", "mode"},
        {"root", "buildingId"},
        {"active", "id"},
        {"active", "version"},
        {"question", "id"},
        {"question", "text"},
        {"question", "correctOptionId"},
        {"option", "id"},
        {"option", "text"},
        {"history", "id"},
        {"history", "version"},
        {"answers", questions.get(0).id}
      };
      List<String> invalid = new ArrayList<>();
      for (String[] field : fields) {
        for (Object bad : new Object[] {42, new org.json.JSONObject(), org.json.JSONObject.NULL}) {
          org.json.JSONObject root = new org.json.JSONObject(valid);
          target(root, field[0]).put(field[1], bad);
          invalid.add(root.toString());
        }
      }
      for (String key : new String[] {"instantDemo", "stamp"}) {
        for (String bad : new String[] {"true", "false"}) {
          invalid.add(new org.json.JSONObject(valid).put(key, bad).toString());
        }
      }
      for (String payload : invalid) {
        database.progressDao().save(new GameProgressDatabase.Row("DEMO", "fixture-1", payload));
        assertEquals(payload, database.progressDao().find("DEMO", "fixture-1").payload);
        try {
          if (update) repo.atomicUpdate(Progress.Mode.DEMO, "fixture-1", p -> p);
          else repo.read(Progress.Mode.DEMO, "fixture-1");
          fail((update ? "update" : "read") + " accepted " + payload);
        } catch (IllegalArgumentException | IllegalStateException expected) {
        }
        assertEquals(payload, database.progressDao().find("DEMO", "fixture-1").payload);
      }
    } finally {
      c.deleteDatabase(name);
    }
  }

  @Test
  public void malformedPersistedTypesAreRejectedByReadWithoutOverwrite() throws Exception {
    assertMalformedPersistedTypesRejected(false);
  }

  @Test
  public void malformedPersistedTypesAreRejectedByUpdateWithoutOverwrite() throws Exception {
    assertMalformedPersistedTypesRejected(true);
  }

  @Test
  public void malformedCatalogStringTypesAreRejected() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    String valid = asset(c);
    String[][] fields = {
      {"root", "version"}, {"building", "id"}, {"building", "label"},
      {"catalogQuestion", "id"}, {"catalogQuestion", "text"},
      {"catalogQuestion", "correctOptionId"},
      {"catalogOption", "id"}, {"catalogOption", "text"}
    };
    for (String[] field : fields) {
      for (Object bad : new Object[] {42, new org.json.JSONObject(), org.json.JSONObject.NULL}) {
        org.json.JSONObject root = new org.json.JSONObject(valid);
        target(root, field[0]).put(field[1], bad);
        try {
          GameJson.catalog(root.toString());
          fail("catalog accepted " + field[0] + "." + field[1]);
        } catch (IllegalArgumentException expected) {
        }
      }
    }
  }
}
