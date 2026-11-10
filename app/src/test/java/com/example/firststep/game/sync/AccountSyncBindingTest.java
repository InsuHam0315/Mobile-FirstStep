package com.example.firststep.game.sync;

import static org.junit.Assert.*;

import com.example.firststep.game.composition.authored.AuthoredProgressRepository;
import com.example.firststep.game.composition.authored.AuthoredProgressStore;
import com.example.firststep.game.domain.GameContent;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;
import org.junit.Test;

public final class AccountSyncBindingTest {
  private static final String PROJECT = "test-project";
  private static final String DATASET = "authored-test-v1";
  private static final String BUILDING = "software";
  private static final Progress.Mode NORMAL = Progress.Mode.NORMAL;
  private static final long INTRO_AT = 10_000;

  private static SyncScope capture(ConfirmedSessionOwner owner) {
    return owner.capture(DATASET, NORMAL, BUILDING);
  }

  private static String key(SyncScope scope) {
    return scope.projectId + "/" + MilestoneDocument.path(scope);
  }

  private static final class AuthoredStore implements AuthoredProgressStore {
    final Map<String, Entry> rows = new HashMap<>();
    boolean failCommit;
    int reads;

    @Override public Entry read(SyncScope scope) {
      reads++;
      return rows.get(key(scope));
    }

    @Override public Entry update(SyncScope scope, UnaryOperator<Entry> transition) {
      Entry next = transition.apply(rows.get(key(scope)));
      if (failCommit) throw new IllegalStateException("Injected commit failure");
      rows.put(key(scope), next);
      return next;
    }
  }

  private static final class OutboxStore implements SyncStore {
    final Map<String, byte[]> rows;
    int writes;

    OutboxStore(Map<String, byte[]> rows) {
      this.rows = rows;
    }

    @Override public OutboxState read(SyncScope scope) {
      byte[] row = rows.get(key(scope));
      return row == null ? OutboxState.empty() : SQLiteSyncStore.decode(row);
    }

    @Override public OutboxState update(SyncScope scope, UnaryOperator<OutboxState> change) {
      OutboxState previous = read(scope);
      OutboxState next = change.apply(previous);
      if (next != previous) {
        writes++;
        rows.put(key(scope), SQLiteSyncStore.encode(next));
      }
      return next;
    }
  }

  private static final SyncBackend NO_NETWORK = new SyncBackend() {
    @Override public CompletableFuture<Milestone> read(SyncScope scope) {
      throw new AssertionError("Reconciliation cannot read the network");
    }

    @Override public CompletableFuture<Milestone> merge(SyncScope scope, Milestone desired) {
      throw new AssertionError("Reconciliation cannot flush");
    }
  };

  private static final class QueuedWorker implements Executor {
    private final Deque<Runnable> work = new ArrayDeque<>();

    @Override public void execute(Runnable task) {
      work.add(task);
    }

    void step() {
      work.remove().run();
    }
  }

  private static final class Fixture {
    final DatasetRegistry registry = DatasetRegistry.syntheticTests(
        Collections.singletonMap(DATASET, Collections.singleton(BUILDING)));
    final ConfirmedSessionOwner owner = new ConfirmedSessionOwner(PROJECT);
    final SyncScope alice;
    final AuthoredStore gameplay = new AuthoredStore();
    final OutboxStore outbox = new OutboxStore(new HashMap<>());
    final AuthoredProgressRepository repository;
    final ProgressService progress;
    long now = INTRO_AT;
    int attempts;

    Fixture() {
      owner.onAuthChanged("alice");
      owner.confirm("alice");
      alice = capture(owner);
      repository = bind(alice);
      progress = service(repository);
    }

    AuthoredProgressRepository bind(SyncScope scope) {
      return new AuthoredProgressRepository(registry, owner, scope, gameplay, () -> now);
    }

    ProgressService service(AuthoredProgressRepository repository) {
      ArrayList<GameContent.Question> questions = new ArrayList<>();
      for (String id : Arrays.asList("q1", "q2", "q3")) {
        questions.add(new GameContent.Question(id, "Question", "yes", Arrays.asList(
            new GameContent.Option("yes", "Yes"), new GameContent.Option("no", "No"))));
      }
      GameContent content = new GameContent("content-v1", Collections.singletonList(
          new GameContent.Building(BUILDING, "Software", questions)));
      return new ProgressService(content, repository, () -> now,
          new ProgressService.AttemptFactory() {
            @Override public String nextId() { return "attempt-" + ++attempts; }
            @Override public void shuffle(java.util.List<?> values) {}
          });
    }

    MilestoneReconciler reconciler(CommittedMilestoneSource source,
        OutboxStore store, Executor worker) {
      SyncCoordinator sync = new SyncCoordinator(registry, owner, store, NO_NETWORK, worker);
      return new MilestoneReconciler(registry, owner, source, sync, worker);
    }

    Progress.Attempt start() {
      progress.intro(NORMAL, BUILDING, false);
      now += ProgressService.WAIT_MILLIS;
      return progress.start(NORMAL, BUILDING).active;
    }

    Progress.Result pass(Progress.Attempt attempt) {
      Map<String, String> answers = new HashMap<>();
      for (GameContent.Question q : attempt.questions) answers.put(q.id, "yes");
      return progress.submit(NORMAL, BUILDING, attempt.id, attempt.contentVersion, answers);
    }
  }

  @Test public void confirmationRequiresMatchingAuthAndRemainsStableAcrossRepeatedEvents() {
    ConfirmedSessionOwner owner = new ConfirmedSessionOwner(PROJECT);
    owner.confirm("alice");
    assertThrows(SessionOwner.StaleSession.class, () -> capture(owner));
    owner.onAuthChanged("alice");
    owner.confirm("bob");
    assertThrows(SessionOwner.StaleSession.class, () -> capture(owner));
    owner.confirm("alice");
    SyncScope first = capture(owner);
    // Child navigation has no auth or confirmation event.
    assertEquals(first.sessionGeneration, capture(owner).sessionGeneration);
    owner.onAuthChanged("alice");
    owner.confirm("alice");
    assertEquals(first.sessionGeneration, capture(owner).sessionGeneration);
    assertEquals("nested", owner.whileCurrent(first,
        () -> owner.whileCurrent(first, () -> "nested")));
  }

  @Test public void revocationAndSameUidReauthenticationInvalidateOldScope() {
    Fixture f = new Fixture();
    f.owner.revoke();
    assertThrows(SessionOwner.StaleSession.class, () -> capture(f.owner));
    f.owner.confirm("alice");
    SyncScope next = capture(f.owner);
    assertTrue(next.sessionGeneration > f.alice.sessionGeneration);
    assertThrows(SessionOwner.StaleSession.class,
        () -> f.owner.whileCurrent(f.alice, () -> "old"));
    assertEquals("new", f.owner.whileCurrent(next, () -> "new"));
  }

  @Test public void authSwitchAndLogoutRejectOldOwnershipWithoutRevokingNewConfirmation() {
    Fixture f = new Fixture();
    f.owner.onAuthChanged("bob");
    f.owner.confirm("alice");
    assertThrows(SessionOwner.StaleSession.class, () -> capture(f.owner));
    f.owner.confirm("bob");
    SyncScope bob = capture(f.owner);
    f.owner.confirm("alice");
    assertEquals(bob.sessionGeneration, capture(f.owner).sessionGeneration);
    assertThrows(SessionOwner.StaleSession.class,
        () -> f.owner.whileCurrent(f.alice, () -> "old"));
    f.owner.onAuthChanged(null);
    assertThrows(SessionOwner.StaleSession.class,
        () -> f.owner.whileCurrent(bob, () -> "logged out"));
    f.owner.onAuthChanged("anonymous");
    f.owner.confirm("anonymous");
    assertThrows(SessionOwner.StaleSession.class, () -> capture(f.owner));
  }

  @Test public void emptyProductionRegistryRejectsSourceAndRepositoryBeforeStorageAccess() {
    Fixture f = new Fixture();
    DatasetRegistry production = DatasetRegistry.production();
    assertThrows(IllegalArgumentException.class, () -> new AuthoredProgressRepository(
        production, f.owner, f.alice, f.gameplay, () -> f.now));
    SyncCoordinator sync = new SyncCoordinator(
        production, f.owner, f.outbox, NO_NETWORK, Runnable::run);
    MilestoneReconciler reconciler = new MilestoneReconciler(production, f.owner, scope -> {
      throw new AssertionError("Ineligible source read");
    }, sync, Runnable::run);
    assertThrows(IllegalArgumentException.class, () -> reconciler.reconcile(f.alice));
    assertEquals(0, f.gameplay.reads);
    assertEquals(0, f.outbox.writes);
  }

  @Test public void committedGameplayReconcilesAfterCrashGapAndAfterEnqueueReplay() {
    Fixture f = new Fixture();
    Progress.Attempt attempt = f.start();
    f.now += 123;
    assertTrue(f.pass(attempt).passed);
    long committedAt = f.now;
    assertNull(f.outbox.read(f.alice).pending);

    // Rebind after a restart using the original durable gameplay and outbox rows.
    f.owner.revoke();
    f.owner.confirm("alice");
    SyncScope reopened = capture(f.owner);
    AuthoredProgressRepository recovered = f.bind(reopened);
    OutboxStore restartedOutbox = new OutboxStore(f.outbox.rows);
    MilestoneReconciler reconciler = f.reconciler(recovered, restartedOutbox, Runnable::run);
    OutboxState first = reconciler.reconcile(reopened).join();
    assertEquals(OutboxState.Status.PENDING, first.status);
    assertEquals(INTRO_AT, first.pending.introDoneAtMillis);
    assertEquals(attempt.id, first.pending.completion.attemptId);
    assertEquals(committedAt, first.pending.completion.completedAtMillis);
    OutboxState duplicate = f.reconciler(f.bind(reopened),
        new OutboxStore(restartedOutbox.rows), Runnable::run).reconcile(reopened).join();
    assertEquals(first.revision, duplicate.revision);
    assertEquals(first.pending, duplicate.pending);
    assertTrue(recovered.read(NORMAL, BUILDING).stamp);
  }

  @Test public void failedAtomicCommitLeavesNeitherPassNorTimestamp() {
    Fixture f = new Fixture();
    Progress.Attempt attempt = f.start();
    f.gameplay.failCommit = true;
    assertThrows(IllegalStateException.class, () -> f.pass(attempt));
    AuthoredProgressStore.Entry rolledBack = f.gameplay.read(f.alice);
    assertFalse(rolledBack.progress.stamp);
    assertEquals(attempt.id, rolledBack.progress.active.id);
    assertNull(rolledBack.firstPassCommittedAtMillis);
    assertNull(f.repository.latest(f.alice).completion);
    assertNull(f.outbox.read(f.alice).pending);

    f.gameplay.failCommit = false;
    f.now += 1;
    assertTrue(f.pass(attempt).passed);
    assertEquals(Long.valueOf(f.now), f.gameplay.read(f.alice).firstPassCommittedAtMillis);
    long first = f.now;
    f.now += 100;
    assertTrue(f.pass(attempt).passed);
    assertEquals(Long.valueOf(first), f.gameplay.read(f.alice).firstPassCommittedAtMillis);
  }

  @Test public void clockRollbackPreservesLocalPassAndRejectsSyncWithoutForgingTime() {
    Fixture f = new Fixture();
    Progress.Attempt attempt = f.start();
    MilestoneReconciler reconciler = f.reconciler(f.repository, f.outbox, Runnable::run);
    OutboxState intro = reconciler.reconcile(f.alice).join();
    assertNull(intro.pending.completion);
    f.now = INTRO_AT + 1;
    assertTrue(f.pass(attempt).passed);
    assertEquals(Long.valueOf(f.now), f.gameplay.read(f.alice).firstPassCommittedAtMillis);
    assertTrue(assertThrows(CompletionException.class, () -> reconciler.reconcile(f.alice).join())
        .getCause() instanceof CommittedMilestoneSource.UnusableMilestone);
    assertTrue(f.repository.read(NORMAL, BUILDING).stamp);
    OutboxState after = f.outbox.read(f.alice);
    assertEquals(intro.revision, after.revision);
    assertEquals(intro.pending, after.pending);
  }

  @Test public void switchAfterSourceReadRejectsTheQueuedOutboxWrite() {
    Fixture f = new Fixture();
    f.start();
    QueuedWorker worker = new QueuedWorker();
    CompletableFuture<OutboxState> recovery =
        f.reconciler(f.repository, f.outbox, worker).reconcile(f.alice);
    worker.step();
    f.owner.onAuthChanged("bob");
    f.owner.confirm("bob");
    worker.step();
    assertTrue(assertThrows(CompletionException.class, recovery::join)
        .getCause() instanceof SessionOwner.StaleSession);
    assertEquals(0, f.outbox.writes);
    SyncScope bob = capture(f.owner);
    assertNull(f.bind(bob).latest(bob));
    assertEquals(OutboxState.Status.EMPTY, f.outbox.read(bob).status);
  }

  @Test public void staleRequestNeverReadsTheCommittedSource() {
    Fixture f = new Fixture();
    QueuedWorker worker = new QueuedWorker();
    CompletableFuture<OutboxState> recovery = f.reconciler(scope -> {
      throw new AssertionError("Stale source read");
    }, f.outbox, worker).reconcile(f.alice);
    f.owner.revoke();
    worker.step();
    assertTrue(assertThrows(CompletionException.class, recovery::join)
        .getCause() instanceof SessionOwner.StaleSession);
    assertEquals(0, f.outbox.writes);
  }

  @Test public void committedIntroductionAndPassCannotBeErasedByAReplacementTransition() {
    Fixture f = new Fixture();
    assertTrue(f.pass(f.start()).passed);
    assertThrows(IllegalArgumentException.class, () -> f.repository.atomicUpdate(
        NORMAL, BUILDING, ignored -> Progress.empty(NORMAL, BUILDING)));
    assertTrue(f.repository.read(NORMAL, BUILDING).stamp);
    assertThrows(IllegalArgumentException.class,
        () -> f.repository.read(Progress.Mode.DEMO, BUILDING));
  }
}
