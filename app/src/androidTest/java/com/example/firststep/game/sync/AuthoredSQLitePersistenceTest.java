package com.example.firststep.game.sync;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.game.composition.authored.AuthoredProgressRepository;
import com.example.firststep.game.composition.authored.AuthoredProgressStore;
import com.example.firststep.game.composition.authored.SQLiteAuthoredProgressStore;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises actual SQLite commit, rollback and reopening in uniquely named test databases. */
@RunWith(AndroidJUnit4.class)
public class AuthoredSQLitePersistenceTest {
  @Test public void committedRowsSurviveReopenAndFailedTransitionsRollBack() {
    String run = UUID.randomUUID().toString();
    String dataset = "contract_" + run;
    String building = "building";
    DatasetRegistry registry = AuthoredRouteTestRegistry.create(dataset, building);
    ConfirmedSessionOwner owner = new ConfirmedSessionOwner("project");
    String uid = "user_" + run;
    owner.onAuthChanged(uid);
    owner.confirm(uid);
    SyncScope scope = owner.capture(dataset, Progress.Mode.NORMAL, building);
    SyncScope other = new SyncScope("project", "other_" + run, 1, dataset,
        Progress.Mode.NORMAL, building);
    long intro = 123L;
    long committedAt = intro + ProgressService.WAIT_MILLIS;
    Map<String, String> answers = new LinkedHashMap<>();
    answers.put("q1", "a1");
    answers.put("q2", "a2");
    answers.put("q3", "a3");
    Progress.Result pass = new Progress.Result("pass", "test-content", answers, 2);
    Milestone milestone = new Milestone(intro, new Milestone.Completion(
        pass.attemptId, pass.contentVersion, pass.correct, committedAt));
    try (IsolatedContext context = new IsolatedContext(
        InstrumentationRegistry.getInstrumentation().getTargetContext(), run)) {
      try (SQLiteAuthoredProgressStore progress = new SQLiteAuthoredProgressStore(context, registry);
           SQLiteSyncStore outbox = new SQLiteSyncStore(context, registry)) {
        assertNull(progress.read(scope));
        assertEquals(OutboxState.Status.EMPTY, outbox.read(scope).status);
        AuthoredProgressRepository repo =
            new AuthoredProgressRepository(registry, owner, scope, progress, () -> committedAt);
        repo.atomicUpdate(scope.mode, building, previous ->
            new Progress(scope.mode, building, intro, false, false, null, Collections.emptyMap()));
        repo.atomicUpdate(scope.mode, building, previous ->
            new Progress(scope.mode, building, previous.introDoneAt, false, true, null,
                Collections.singletonMap(pass.attemptId, pass)));
        try {
          progress.update(scope, previous -> { throw new IllegalStateException("abort"); });
          fail("transition should fail");
        } catch (IllegalStateException expected) { assertEquals("abort", expected.getMessage()); }
      }
      try (SQLiteAuthoredProgressStore progress = new SQLiteAuthoredProgressStore(context, registry);
           SQLiteSyncStore outbox = new SQLiteSyncStore(context, registry)) {
        AuthoredProgressStore.Entry row = progress.read(scope);
        assertEquals(Long.valueOf(intro), row.progress.introDoneAt);
        assertTrue(row.progress.stamp);
        assertEquals(answers, row.progress.history.get(pass.attemptId).answers);
        assertEquals(Long.valueOf(committedAt), row.firstPassCommittedAtMillis);
        assertEquals(OutboxState.Status.EMPTY, outbox.read(scope).status);
        AuthoredProgressRepository reopened =
            new AuthoredProgressRepository(registry, owner, scope, progress, () -> committedAt + 1);
        Milestone recovered = reopened.latest(scope);
        assertEquals(milestone, recovered);
        outbox.update(scope, previous -> previous.enqueue(recovered));
        assertThrows(IllegalStateException.class,
            () -> outbox.update(scope, previous -> { throw new IllegalStateException("abort"); }));
        assertNull(progress.read(other));
        assertEquals(OutboxState.Status.EMPTY, outbox.read(other).status);
      }
      try (SQLiteSyncStore outbox = new SQLiteSyncStore(context, registry)) {
        OutboxState restored = outbox.read(scope);
        assertEquals(1L, restored.revision);
        assertEquals(OutboxState.Status.PENDING, restored.status);
        assertEquals(milestone, restored.pending);
        outbox.update(scope, previous -> previous.acknowledge(previous.revision, milestone));
      }
      try (SQLiteSyncStore outbox = new SQLiteSyncStore(context, registry)) {
        OutboxState restored = outbox.read(scope);
        assertEquals(OutboxState.Status.CONFIRMED, restored.status);
        assertEquals(milestone, restored.confirmed);
        assertNull(restored.pending);
      }
    }
  }

  private static final class IsolatedContext extends ContextWrapper implements AutoCloseable {
    private static final String AUTHORED = "firststep-authored-gameplay.db";
    private static final String OUTBOX = "firststep-account-progress-sync.db";
    private final String prefix;
    IsolatedContext(Context base, String run) { super(base); prefix = "sqlite-contract-" + run + "-"; }
    @Override public Context getApplicationContext() { return this; }
    private String isolated(String name) {
      if (!AUTHORED.equals(name) && !OUTBOX.equals(name)) throw new AssertionError(name);
      return prefix + name;
    }
    @Override public File getDatabasePath(String name) { return super.getDatabasePath(isolated(name)); }
    @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
        SQLiteDatabase.CursorFactory factory) {
      return super.openOrCreateDatabase(isolated(name), mode, factory);
    }
    @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
        SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
      return super.openOrCreateDatabase(isolated(name), mode, factory, handler);
    }
    @Override public void close() {
      super.deleteDatabase(isolated(AUTHORED));
      super.deleteDatabase(isolated(OUTBOX));
    }
  }
}
