package com.example.firststep.game.composition.authored;

import static org.junit.Assert.*;

import com.example.firststep.game.domain.GameContent;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.sync.AuthoredHostTestRegistry;
import com.example.firststep.game.sync.CommittedMilestoneSource;
import com.example.firststep.game.sync.ConfirmedSessionOwner;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.Milestone;
import com.example.firststep.game.sync.OutboxState;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.MilestoneReconciler;
import com.example.firststep.game.sync.SyncBackend;
import com.example.firststep.game.sync.SyncCoordinator;
import com.example.firststep.game.sync.SyncStore;
import com.example.firststep.game.sync.SyncScope;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.junit.Test;

public final class AuthoredEpisodeHostTest {
  private static final String D = AuthoredEpisodeHost.DATASET;
  private static final String B = AuthoredEpisodeHost.TARGET;
  private long now = 1_000_000L;
  private final ConfirmedSessionOwner owner = new ConfirmedSessionOwner("project");
  private final MemoryStore store = new MemoryStore();
  private final FakeAccess access = new FakeAccess();
  private AuthoredEpisodeHost.Arrival arrival = AuthoredEpisodeHost.Arrival.ADMITTED;
  private final AtomicInteger attempts = new AtomicInteger();
  private final ProgressService.AttemptFactory ids = new ProgressService.AttemptFactory() {
    @Override public String nextId() { return "attempt_" + attempts.incrementAndGet(); }
    @Override public void shuffle(List<?> values) { }
  };

  private static GameContent game() {
    java.util.ArrayList<GameContent.Question> questions = new java.util.ArrayList<>();
    for (int i = 0; i < 3; i++) questions.add(new GameContent.Question("q" + i, "question",
        "yes", Arrays.asList(new GameContent.Option("yes", "yes"),
            new GameContent.Option("no", "no"))));
    return new GameContent(D, Collections.singletonList(new GameContent.Building(B, "test", questions)));
  }

  private AuthoredEpisodeHost.OpenResult open() {
    return AuthoredEpisodeHost.open(access, scope -> arrival, () -> store,
        () -> new AuthoredEpisodeHost.Content(null, null, game()), () -> now, ids);
  }

  private AuthoredEpisodeHost active() {
    owner.onAuthChanged("alice");
    owner.confirm("alice");
    AuthoredEpisodeHost.OpenResult result = open();
    assertNull(result.reason);
    return result.host;
  }

  private static Map<String, String> answers(Progress p, boolean pass) {
    Map<String, String> answers = new LinkedHashMap<>();
    for (GameContent.Question q : p.active.questions) answers.put(q.id, pass ? "yes" : "no");
    return answers;
  }

  @Test public void productionPreflightNeverTouchesRuntimeOrContextWhenUnregistered() {
    AtomicInteger runtimeOrContextCalls = new AtomicInteger();
    AuthoredEpisodeHost.OpenResult result = AuthoredEpisodeHost.productionGate(() -> {
      runtimeOrContextCalls.incrementAndGet();
      throw new AssertionError("Closed registry must not initialize the graph");
    });
    assertEquals(AuthoredEpisodeHost.Block.UNREGISTERED, result.reason);
    assertEquals(0, runtimeOrContextCalls.get());
    assertEquals(AuthoredEpisodeHost.Block.UNREGISTERED,
        AuthoredEpisodeHost.openProduction(null).reason);
  }

  @Test public void emptyRegistryAndMissingOwnerDenyBeforeAnyStoreOrNetwork() {
    assertEquals(AuthoredEpisodeHost.Block.NO_ACCOUNT, open().reason);
    owner.onAuthChanged("alice"); owner.confirm("alice");
    access.registry = DatasetRegistry.production();
    assertEquals(AuthoredEpisodeHost.Block.UNREGISTERED, open().reason);
    assertEquals(0, store.reads + store.writes + access.reconciles + access.flushes);
    assertEquals(AuthoredEpisodeHost.Block.NO_ACCOUNT,
        AuthoredEpisodeHost.open(null, scope -> arrival, () -> store,
            () -> new AuthoredEpisodeHost.Content(null, null, game()), () -> now, ids).reason);
  }

  @Test public void admissionUnavailableAndDemoScopeFailClosed() {
    owner.onAuthChanged("alice"); owner.confirm("alice");
    arrival = AuthoredEpisodeHost.Arrival.UNAVAILABLE;
    assertEquals(AuthoredEpisodeHost.Block.ARRIVAL_UNAVAILABLE, open().reason);
    arrival = AuthoredEpisodeHost.Arrival.NOT_ARRIVED;
    assertEquals(AuthoredEpisodeHost.Block.NOT_ARRIVED, open().reason);
    access.forceDemo = true;
    assertEquals(AuthoredEpisodeHost.Block.UNREGISTERED, open().reason);
    assertEquals(0, store.reads + store.writes + access.reconciles + access.flushes);
  }

  @Test public void normalWaitRetryAndDuplicatePassAreDurable() {
    AuthoredEpisodeHost host = active();
    assertEquals(1, access.reconciles); // Reopen recovery before any local result.
    Progress introduced = host.finishIntroduction();
    assertEquals(Long.valueOf(now), introduced.introDoneAt);
    assertFalse(host.eligible(introduced));
    now += ProgressService.WAIT_MILLIS - 1;
    assertFalse(host.eligible(host.read()));
    try { host.startQuiz(); fail(); } catch (IllegalStateException expected) { }
    now++;
    Progress first = host.startQuiz();
    Progress.Result failure = host.submit(first.active.id, first.active.contentVersion, answers(first, false));
    assertFalse(failure.passed);
    Progress retry = host.read();
    assertNotEquals(first.active.id, retry.active.id);
    Map<String, String> passAnswers = answers(retry, true);
    Progress.Result pass = host.submit(retry.active.id, retry.active.contentVersion, passAnswers);
    assertTrue(pass.passed);
    assertEquals(pass.attemptId, host.submit(retry.active.id, retry.active.contentVersion, passAnswers).attemptId);
    assertTrue(host.read().stamp);
    assertEquals(1, host.read().history.values().stream().filter(result -> result.passed).count());
    assertNotNull(access.pending.completion);
    host.close();
  }

  @Test public void reconcileFailureDoesNotUndoCommitAndReopenRecoversGap() {
    AuthoredEpisodeHost host = active();
    access.throwOnReconcile = true;
    host.finishIntroduction();
    try { host.reconciliation().join(); fail(); } catch (CompletionException expected) { }
    assertNotNull(host.read().introDoneAt);
    now += ProgressService.WAIT_MILLIS;
    Progress quiz = host.startQuiz();
    assertTrue(host.submit(quiz.active.id, quiz.active.contentVersion, answers(quiz, true)).passed);
    assertTrue(host.read().stamp);
    host.close();
    access.throwOnReconcile = false;
    AuthoredEpisodeHost reopened = open().host;
    assertTrue(reopened.read().stamp);
    assertNotNull(reopened.reconciliation().join().pending.completion);
    reopened.reconcile().join();
    assertEquals(0, access.flushes); // Reopen/reconcile never triggers a network flush.
    assertEquals(OutboxState.Status.CONFIRMED, reopened.flush().join().status);
    assertEquals(1, access.flushes);
  }

  @Test public void ownerSwitchRejectsOldRenderingAndMutations() {
    AuthoredEpisodeHost host = active();
    host.finishIntroduction();
    assertTrue(host.renderIfCurrent(() -> {}));
    owner.onAuthChanged("bob"); owner.confirm("bob");
    assertFalse(host.isCurrent());
    assertFalse(host.renderIfCurrent(() -> fail("stale render")));
    try { host.read(); fail(); } catch (SessionOwner.StaleSession expected) { }
    try { host.finishIntroduction(); fail(); } catch (SessionOwner.StaleSession expected) { }
    owner.onAuthChanged("alice"); owner.confirm("alice");
    assertNotNull(open().host.read().introDoneAt);
  }

  @Test public void rollbackRejectsSyncButKeepsLocallyCommittedPass() {
    AuthoredEpisodeHost host = active();
    host.finishIntroduction();
    now += ProgressService.WAIT_MILLIS;
    Progress quiz = host.startQuiz();
    now -= ProgressService.WAIT_MILLIS + 1;
    assertTrue(host.submit(quiz.active.id, quiz.active.contentVersion, answers(quiz, true)).passed);
    assertTrue(host.read().stamp);
    try { host.reconciliation().join(); fail(); } catch (CompletionException expected) { }
    assertTrue(host.read().stamp);
  }

  @Test public void closeDefersStoreCloseUntilReconciliationFinishes() {
    AuthoredEpisodeHost host = active();
    access.delayedReconcile = new CompletableFuture<>();
    CompletableFuture<OutboxState> outstanding = host.reconcile();
    host.close();
    assertEquals(0, store.closes);
    assertFalse(host.renderIfCurrent(() -> fail("closed host rendered")));
    access.delayedReconcile.complete(OutboxState.empty());
    outstanding.join();
    assertEquals(1, store.closes);
    host.close();
    assertEquals(1, store.closes);
  }

  @Test public void realCoordinatorReopensAndRejectsLateAccountSwitchResult() {
    DatasetRegistry registry = AuthoredHostTestRegistry.create(D, B);
    owner.onAuthChanged("alice");
    owner.confirm("alice");
    final OutboxState[] outbox = { OutboxState.empty() };
    SyncStore syncStore = new SyncStore() {
      @Override public OutboxState read(SyncScope scope) { return outbox[0]; }
      @Override public OutboxState update(SyncScope scope, UnaryOperator<OutboxState> change) {
        outbox[0] = change.apply(outbox[0]);
        return outbox[0];
      }
    };
    CompletableFuture<Milestone> delayed = new CompletableFuture<>();
    AtomicInteger network = new AtomicInteger();
    SyncBackend backend = new SyncBackend() {
      @Override public CompletableFuture<Milestone> read(SyncScope scope) {
        throw new AssertionError("No implicit remote reads");
      }
      @Override public CompletableFuture<Milestone> merge(SyncScope scope, Milestone milestone) {
        network.incrementAndGet();
        return delayed;
      }
    };
    SyncCoordinator coordinator = new SyncCoordinator(registry, owner, syncStore, backend, Runnable::run);
    AuthoredEpisodeHost.Access actual = new AuthoredEpisodeHost.Access() {
      @Override public SyncScope capture() { return owner.capture(D, Progress.Mode.NORMAL, B); }
      @Override public DatasetRegistry registry() { return registry; }
      @Override public SessionOwner owner() { return owner; }
      @Override public CompletableFuture<OutboxState> reconcile(CommittedMilestoneSource source, SyncScope scope) {
        return new MilestoneReconciler(registry, owner, source, coordinator, Runnable::run).reconcile(scope);
      }
      @Override public CompletableFuture<OutboxState> flush(SyncScope scope) {
        return coordinator.flush(scope);
      }
    };
    AuthoredEpisodeHost host = AuthoredEpisodeHost.open(actual, scope -> arrival, () -> store,
        () -> new AuthoredEpisodeHost.Content(null, null, game()), () -> now, ids).host;
    host.finishIntroduction();
    now += ProgressService.WAIT_MILLIS;
    Progress quiz = host.startQuiz();
    assertTrue(host.submit(quiz.active.id, quiz.active.contentVersion, answers(quiz, true)).passed);
    assertEquals(OutboxState.Status.PENDING, host.reconciliation().join().status);
    assertEquals(0, network.get());
    host.close();
    AuthoredEpisodeHost reopened = AuthoredEpisodeHost.open(actual, scope -> arrival, () -> store,
        () -> new AuthoredEpisodeHost.Content(null, null, game()), () -> now, ids).host;
    assertTrue(reopened.read().stamp);
    assertEquals(OutboxState.Status.PENDING, reopened.reconciliation().join().status);
    Milestone original = outbox[0].pending;
    CompletableFuture<OutboxState> flushing = reopened.flush();
    assertEquals(1, network.get());
    owner.onAuthChanged("bob");
    owner.confirm("bob");
    delayed.complete(original);
    try { flushing.join(); fail(); } catch (CompletionException expected) {
      assertTrue(expected.getCause() instanceof SessionOwner.StaleSession);
    }
    assertEquals(OutboxState.Status.PENDING, outbox[0].status);
    assertFalse(reopened.renderIfCurrent(() -> fail("old owner rendered")));
    reopened.close();
  }

  private final class MemoryStore implements AuthoredProgressStore, AutoCloseable {
    final Map<String, Entry> rows = new LinkedHashMap<>();
    int reads, writes, closes;
    private String key(SyncScope scope) { return scope.uid + ":" + scope.datasetId + ":" + scope.buildingId; }
    @Override public Entry read(SyncScope scope) { reads++; return rows.get(key(scope)); }
    @Override public Entry update(SyncScope scope, UnaryOperator<Entry> change) {
      writes++; Entry entry = change.apply(rows.get(key(scope))); rows.put(key(scope), entry); return entry;
    }
    @Override public void close() { closes++; }
  }

  private final class FakeAccess implements AuthoredEpisodeHost.Access {
    DatasetRegistry registry = AuthoredHostTestRegistry.create(D, B);
    boolean forceDemo, throwOnReconcile;
    int reconciles, flushes;
    Milestone pending;
    CompletableFuture<OutboxState> delayedReconcile;
    @Override public SyncScope capture() {
      return owner.capture(D, forceDemo ? Progress.Mode.DEMO : Progress.Mode.NORMAL, B);
    }
    @Override public DatasetRegistry registry() { return registry; }
    @Override public SessionOwner owner() { return owner; }
    @Override public CompletableFuture<OutboxState> reconcile(CommittedMilestoneSource source, SyncScope scope) {
      reconciles++;
      if (throwOnReconcile) throw new IllegalStateException("local outbox unavailable");
      if (delayedReconcile != null) return delayedReconcile;
      Milestone latest = owner.whileCurrent(scope, () -> source.latest(scope));
      if (latest == null) return CompletableFuture.completedFuture(OutboxState.empty());
      pending = latest;
      return CompletableFuture.completedFuture(new OutboxState(1, null, latest, OutboxState.Status.PENDING));
    }
    @Override public CompletableFuture<OutboxState> flush(SyncScope scope) {
      flushes++;
      return CompletableFuture.completedFuture(new OutboxState(1, pending, null, OutboxState.Status.CONFIRMED));
    }
  }
}
