package com.example.firststep.game.sync;

import static org.junit.Assert.*;

import com.example.firststep.game.domain.Progress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.junit.Test;

public final class ProgressSyncContractTest {
  private static final String PROJECT = "test-project";
  private static final String DATASET = "authored-test-v1";
  private static final String BUILDING = "software";
  private static final Milestone INTRO = new Milestone(10_000, null);
  private static final Milestone EARLIER = new Milestone(8_000, null);
  private static final Milestone PASS = pass(10_000, "first-pass");
  private static final Milestone OTHER_PASS = pass(8_000, "second-pass");

  private static Milestone pass(long intro, String id) {
    return new Milestone(intro, new Milestone.Completion(id, "content-v1", 2, 920_000));
  }

  private static SyncScope scope(String project, String uid, long generation, String dataset,
      Progress.Mode mode, String building) {
    return new SyncScope(project, uid, generation, dataset, mode, building);
  }

  private static SyncScope scope(String uid, long generation) {
    return scope(PROJECT, uid, generation, DATASET, Progress.Mode.NORMAL, BUILDING);
  }

  private static DatasetRegistry registry() {
    Map<String, java.util.Set<String>> values = new HashMap<>();
    values.put(DATASET, new HashSet<>(Arrays.asList(BUILDING, "other-building")));
    values.put("authored-test-v2", Collections.singleton(BUILDING));
    return DatasetRegistry.syntheticTests(values);
  }

  private static final class Owner implements SessionOwner {
    private SyncScope current;

    synchronized void switchTo(SyncScope scope) {
      current = scope;
    }

    @Override public synchronized <T> T whileCurrent(SyncScope expected, Supplier<T> action) {
      if (current == null || !expected.matchesSession(
          current.projectId, current.uid, current.sessionGeneration)) {
        throw new StaleSession();
      }
      return action.get();
    }
  }

  /** Reopen from the production binary payload. This fake does not exercise Android SQLite. */
  private static final class PayloadStore implements SyncStore {
    final Map<String, byte[]> rows;
    int reads, updates;

    PayloadStore(Map<String, byte[]> rows) {
      this.rows = rows;
    }

    private static String key(SyncScope s) {
      return s.projectId + "/" + MilestoneDocument.path(s);
    }

    @Override public OutboxState read(SyncScope scope) {
      reads++;
      byte[] saved = rows.get(key(scope));
      return saved == null ? OutboxState.empty() : SQLiteSyncStore.decode(saved);
    }

    @Override public OutboxState update(SyncScope scope, UnaryOperator<OutboxState> change) {
      updates++;
      OutboxState next = change.apply(read(scope));
      rows.put(key(scope), SQLiteSyncStore.encode(next));
      return next;
    }
  }

  private static final class Backend implements SyncBackend {
    final Map<String, Milestone> canonical = new HashMap<>();
    int writes;
    CompletableFuture<Milestone> nextWrite, nextRead;

    @Override public CompletableFuture<Milestone> read(SyncScope scope) {
      return nextRead != null ? nextRead
          : CompletableFuture.completedFuture(canonical.get(key(scope)));
    }

    @Override public CompletableFuture<Milestone> merge(SyncScope scope, Milestone value) {
      writes++;
      if (nextWrite != null) {
        CompletableFuture<Milestone> request = nextWrite;
        nextWrite = null;
        return request;
      }
      Milestone merged = Milestone.merge(canonical.get(key(scope)), value);
      canonical.put(key(scope), merged);
      return CompletableFuture.completedFuture(merged);
    }

    private static String key(SyncScope scope) {
      return scope.projectId + "/" + MilestoneDocument.path(scope);
    }
  }

  private static final class Fixture {
    final SyncScope a = scope("alice", 1);
    final Owner owner = new Owner();
    final PayloadStore store = new PayloadStore(new HashMap<>());
    final Backend backend = new Backend();
    final SyncCoordinator sync = coordinator(store, backend);

    Fixture() {
      owner.switchTo(a);
    }

    SyncCoordinator coordinator(SyncStore store, SyncBackend backend) {
      return new SyncCoordinator(registry(), owner, store, backend, Runnable::run);
    }
  }

  @Test public void productionAndWrongScopesFailBeforeOpeningStorageOrBackend() {
    Fixture f = new Fixture();
    SyncCoordinator production = new SyncCoordinator(
        DatasetRegistry.production(), f.owner, f.store, f.backend, Runnable::run);
    assertThrows(IllegalArgumentException.class, () -> production.record(f.a, INTRO));
    for (SyncScope forbidden : Arrays.asList(
        scope(PROJECT, "alice", 1, DATASET, Progress.Mode.DEMO, BUILDING),
        scope(PROJECT, "alice", 1, "h04_fixture_v1", Progress.Mode.NORMAL, BUILDING),
        scope(PROJECT, "alice", 1, "software_episode_demo_v1", Progress.Mode.NORMAL, BUILDING),
        scope(PROJECT, "alice", 1, "software_guide_v1", Progress.Mode.NORMAL, BUILDING),
        scope(PROJECT, "alice", 1, DATASET, Progress.Mode.NORMAL, "unknown-building"))) {
      assertThrows(IllegalArgumentException.class, () -> f.sync.flush(forbidden));
    }
    assertThrows(IllegalArgumentException.class, () -> scope("guest", 1));
    assertThrows(IllegalArgumentException.class, () -> scope("alice/other", 1));
    assertThrows(IllegalArgumentException.class, () -> scope("alice", 0));
    assertEquals(0, f.store.reads);
    assertEquals(0, f.backend.writes);
  }

  @Test public void testRegistrationCannotAllowReservedData() {
    for (String dataset : Arrays.asList("h04_fixture_v1", "synthetic-v1",
        "software_episode_demo_v1", "software_guide_v1", "software_narration_v1",
        "legacy_v1", "campus-practice-v1")) {
      assertThrows(IllegalArgumentException.class, () -> DatasetRegistry.syntheticTests(
          Collections.singletonMap(dataset, Collections.singleton(BUILDING))));
    }
  }

  @Test public void projectAccountDatasetAndBuildingStaySeparated() {
    Fixture f = new Fixture();
    f.sync.record(f.a, PASS).join();
    f.sync.flush(f.a).join();
    for (SyncScope other : Arrays.asList(
        scope("bob", 2),
        scope("other-project", "alice", 2, DATASET, Progress.Mode.NORMAL, BUILDING),
        scope(PROJECT, "alice", 2, "authored-test-v2", Progress.Mode.NORMAL, BUILDING),
        scope(PROJECT, "alice", 2, DATASET, Progress.Mode.NORMAL, "other-building"))) {
      f.owner.switchTo(other);
      assertEquals(OutboxState.Status.EMPTY, f.sync.refresh(other).join().status);
    }
    SyncScope reopened = scope("alice", 3);
    f.owner.switchTo(reopened);
    assertEquals(PASS, f.sync.readLocal(reopened).join().confirmed);
  }

  @Test public void duplicateAndReorderedWritesRetainFirstPassAndEarliestIntro() {
    Fixture f = new Fixture();
    long revision = f.sync.record(f.a, PASS).join().revision;
    assertEquals(revision, f.sync.record(f.a, PASS).join().revision);
    assertEquals(PASS, f.sync.flush(f.a).join().confirmed);
    assertEquals(1, f.backend.writes);
    assertEquals(PASS, f.sync.flush(f.a).join().confirmed);
    assertEquals(1, f.backend.writes);
    f.sync.record(f.a, OTHER_PASS).join();
    OutboxState accepted = f.sync.flush(f.a).join();
    assertEquals(8_000, accepted.confirmed.introDoneAtMillis);
    assertEquals(PASS.completion, accepted.confirmed.completion);
    f.sync.record(f.a, INTRO).join();
    assertNull(f.sync.flush(f.a).join().pending);
    assertEquals(2, f.backend.writes);
  }

  @Test public void offlinePendingPayloadReopensOnlyForItsOriginalOwner() {
    Fixture f = new Fixture();
    f.sync.record(f.a, PASS).join();
    f.backend.nextWrite = new CompletableFuture<>();
    f.backend.nextWrite.completeExceptionally(new SyncBackend.Failure(true));
    assertEquals(OutboxState.Status.RETRYABLE_FAILURE, f.sync.flush(f.a).join().status);
    PayloadStore reopened = new PayloadStore(f.store.rows);
    SyncCoordinator restarted = f.coordinator(reopened, f.backend);
    SyncScope b = scope("bob", 2);
    f.owner.switchTo(b);
    assertEquals(OutboxState.Status.EMPTY, restarted.flush(b).join().status);
    assertEquals(1, f.backend.writes);
    SyncScope a2 = scope("alice", 3);
    f.owner.switchTo(a2);
    assertEquals(PASS, restarted.readLocal(a2).join().pending);
    assertEquals(PASS, restarted.flush(a2).join().confirmed);
    assertEquals(2, f.backend.writes);
  }

  @Test public void lateUploadAfterSwitchDoesNotAcknowledgeEitherAccount() {
    Fixture f = new Fixture();
    f.sync.record(f.a, PASS).join();
    CompletableFuture<Milestone> transport = new CompletableFuture<>();
    f.backend.nextWrite = transport;
    CompletableFuture<OutboxState> upload = f.sync.flush(f.a);
    SyncScope b = scope("bob", 2);
    f.owner.switchTo(b);
    transport.complete(PASS);
    assertTrue(assertThrows(CompletionException.class, upload::join).getCause()
        instanceof SessionOwner.StaleSession);
    assertEquals(OutboxState.Status.EMPTY, f.sync.readLocal(b).join().status);
    SyncScope a2 = scope("alice", 3);
    f.owner.switchTo(a2);
    assertEquals(PASS, f.sync.readLocal(a2).join().pending);
  }

  @Test public void newGenerationForSameUidRejectsLateRead() {
    Fixture f = new Fixture();
    CompletableFuture<Milestone> transport = new CompletableFuture<>();
    f.backend.nextRead = transport;
    CompletableFuture<OutboxState> read = f.sync.refresh(f.a);
    SyncScope a2 = scope("alice", 2);
    f.owner.switchTo(a2);
    transport.complete(PASS);
    assertTrue(assertThrows(CompletionException.class, read::join).getCause()
        instanceof SessionOwner.StaleSession);
    assertEquals(0, f.store.updates);
    assertEquals(OutboxState.Status.EMPTY, f.sync.readLocal(a2).join().status);
  }

  @Test public void olderAcknowledgementCannotClearNewerPendingCompletion() {
    Fixture f = new Fixture();
    f.sync.record(f.a, INTRO).join();
    CompletableFuture<Milestone> transport = new CompletableFuture<>();
    f.backend.nextWrite = transport;
    CompletableFuture<OutboxState> upload = f.sync.flush(f.a);
    OutboxState queued = f.sync.record(f.a, PASS).join();
    transport.complete(INTRO);
    OutboxState updated = upload.join();
    assertEquals(queued.revision, updated.revision);
    assertEquals(INTRO, updated.confirmed);
    assertEquals(PASS, updated.pending);
    assertEquals(PASS, f.sync.flush(f.a).join().confirmed);
  }

  @Test public void observedRemotePassWinsButPendingEarlierIntroRemains() {
    Fixture f = new Fixture();
    f.sync.record(f.a, OTHER_PASS).join();
    f.backend.canonical.put(Backend.key(f.a), PASS);
    OutboxState observed = f.sync.refresh(f.a).join();
    assertEquals(PASS, observed.confirmed);
    assertEquals(OTHER_PASS, observed.pending);
    assertEquals(PASS.completion, observed.effective().completion);
    assertEquals(8_000, observed.effective().introDoneAtMillis);
    assertEquals(observed.effective(), f.sync.flush(f.a).join().confirmed);
  }

  @Test public void permanentFailureDoesNotCreateARetryLoop() {
    Fixture f = new Fixture();
    f.sync.record(f.a, INTRO).join();
    f.backend.nextWrite = new CompletableFuture<>();
    f.backend.nextWrite.completeExceptionally(new SyncBackend.Failure(false));
    assertEquals(OutboxState.Status.PERMANENT_FAILURE, f.sync.flush(f.a).join().status);
    assertEquals(OutboxState.Status.PERMANENT_FAILURE, f.sync.flush(f.a).join().status);
    assertEquals(1, f.backend.writes);
    assertEquals(OutboxState.Status.PENDING, f.sync.record(f.a, EARLIER).join().status);
    assertEquals(EARLIER, f.sync.flush(f.a).join().confirmed);
    assertEquals(2, f.backend.writes);
  }

  @Test public void incompleteAcknowledgementDoesNotDiscardPendingPass() {
    Fixture f = new Fixture();
    f.sync.record(f.a, PASS).join();
    f.backend.nextWrite = CompletableFuture.completedFuture(INTRO);
    OutboxState result = f.sync.flush(f.a).join();
    assertEquals(OutboxState.Status.PERMANENT_FAILURE, result.status);
    assertEquals(PASS, result.pending);
    assertEquals(INTRO, result.confirmed);
  }

  @Test public void documentAndOutboxRejectMalformedData() {
    Map<String, Object> document = MilestoneDocument.encode(PASS);
    assertEquals(PASS, MilestoneDocument.decode(document));
    assertEquals("users/alice/progressDatasets/authored-test-v1/modes/NORMAL/buildings/software",
        MilestoneDocument.path(scope("alice", 1)));
    document.put("introDoneAtMillis", 10_000.0);
    assertThrows(IllegalArgumentException.class, () -> MilestoneDocument.decode(document));
    document.put("introDoneAtMillis", 10_000L);
    document.put("uid", "bob");
    assertThrows(IllegalArgumentException.class, () -> MilestoneDocument.decode(document));
    byte[] saved = SQLiteSyncStore.encode(OutboxState.empty().enqueue(PASS));
    assertEquals(PASS, SQLiteSyncStore.decode(saved).pending);
    byte[] trailing = Arrays.copyOf(saved, saved.length + 1);
    assertThrows(IllegalStateException.class, () -> SQLiteSyncStore.decode(trailing));
    byte[] truncated = Arrays.copyOf(saved, saved.length - 1);
    assertThrows(IllegalStateException.class, () -> SQLiteSyncStore.decode(truncated));
    assertThrows(IllegalArgumentException.class, () -> new Milestone(30_000, PASS.completion));
  }
}
