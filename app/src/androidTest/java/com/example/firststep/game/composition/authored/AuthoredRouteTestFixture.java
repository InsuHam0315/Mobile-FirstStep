package com.example.firststep.game.composition.authored;

import android.content.Context;
import com.example.firststep.game.device.authored.ArrivalPolicy;
import com.example.firststep.game.device.authored.AuthoredTargetConfig;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.presentation.home.account.AccountHomeController.Reading;
import com.example.firststep.game.presentation.home.account.AccountHomeState;
import com.example.firststep.game.sync.AuthoredRouteTestRegistry;
import com.example.firststep.game.sync.CommittedMilestoneSource;
import com.example.firststep.game.sync.ConfirmedSessionOwner;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.Milestone;
import com.example.firststep.game.sync.MilestoneReconciler;
import com.example.firststep.game.sync.OutboxState;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncBackend;
import com.example.firststep.game.sync.SyncCoordinator;
import com.example.firststep.game.sync.SyncScope;
import com.example.firststep.game.sync.SyncStore;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** In-memory, instrumentation-only dependency for the real Activity. */
public final class AuthoredRouteTestFixture {
  private static final String DATASET = AuthoredEpisodeHost.DATASET;
  private static final String TARGET = AuthoredEpisodeHost.TARGET;
  private static final long INTRO = 1000000L;
  private static final String FIRST_USER = "synthetic-user";
  public final AtomicInteger opens = new AtomicInteger();
  public final AtomicInteger localWrites = new AtomicInteger();
  public final AtomicInteger remoteReads = new AtomicInteger();
  public final AtomicInteger remoteWrites = new AtomicInteger();
  public final AtomicLong now = new AtomicLong(INTRO);
  public volatile boolean atTarget;
  private final AtomicInteger attempts = new AtomicInteger();
  private final Map<String, AuthoredProgressStore.Entry> rows = new ConcurrentHashMap<>();
  private final Map<String, OutboxState> outboxes = new ConcurrentHashMap<>();
  private final Map<String, Milestone> remote = new ConcurrentHashMap<>();
  private final AtomicReference<CompletableFuture<Milestone>> deferredRead = new AtomicReference<>();
  private final AuthoredTargetConfig target = new AuthoredTargetConfig(TARGET,
      "synthetic-instrumentation", 10, 20, 50, 90, 15, 5_000_000_000L);
  private final DatasetRegistry registry = AuthoredRouteTestRegistry.create(DATASET, TARGET);
  private final ConfirmedSessionOwner owner = new ConfirmedSessionOwner("synthetic-project");
  private final AuthoredProgressStore store = new AuthoredProgressStore() {
    @Override public Entry read(SyncScope scope) { return rows.get(scope.uid); }
    @Override public Entry update(SyncScope scope, UnaryOperator<Entry> operation) {
      Entry committed = rows.compute(scope.uid, (uid, row) -> operation.apply(row));
      localWrites.incrementAndGet();
      return committed;
    }
  };
  private final SyncStore syncStore = new SyncStore() {
    @Override public OutboxState read(SyncScope scope) { return outbox(scope.uid); }
    @Override public OutboxState update(SyncScope scope, UnaryOperator<OutboxState> operation) {
      return outboxes.compute(scope.uid,
          (uid, row) -> operation.apply(row == null ? OutboxState.empty() : row));
    }
  };
  private final SyncBackend backend = new SyncBackend() {
    @Override public CompletableFuture<Milestone> read(SyncScope scope) {
      CompletableFuture<Milestone> deferred = deferredRead.getAndSet(null);
      remoteReads.incrementAndGet();
      return deferred == null ? CompletableFuture.completedFuture(remote.get(scope.uid)) : deferred;
    }
    @Override public CompletableFuture<Milestone> merge(SyncScope scope, Milestone value) {
      Milestone confirmed = remote.merge(scope.uid, value, Milestone::merge);
      remoteWrites.incrementAndGet();
      return CompletableFuture.completedFuture(confirmed);
    }
  };
  private final SyncCoordinator sync = new SyncCoordinator(registry, owner, syncStore, backend,
      Runnable::run);

  public AuthoredRouteTestFixture() {
    switchOwner(FIRST_USER);
    remote.put(FIRST_USER, new Milestone(INTRO, new Milestone.Completion(
        "synthetic-attempt", DATASET, 2, INTRO + ProgressService.WAIT_MILLIS)));
  }

  public void switchOwner(String uid) {
    owner.onAuthChanged(uid);
    owner.confirm(uid);
  }

  public void clearRemoteFixture() { remote.remove(FIRST_USER); }

  public CompletableFuture<Milestone> deferRemoteRead() {
    CompletableFuture<Milestone> pending = new CompletableFuture<>();
    if (!deferredRead.compareAndSet(null, pending)) {
      throw new IllegalStateException("A deferred read is already queued");
    }
    return pending;
  }

  public OutboxState outbox(String uid) {
    return outboxes.getOrDefault(uid, OutboxState.empty());
  }

  public SyncScope capture() {
    return owner.capture(DATASET, Progress.Mode.NORMAL, TARGET);
  }

  public Reading homeReading() {
    try {
      SyncScope scope = capture();
      return owner.whileCurrent(scope, () -> {
        AuthoredProgressStore.Entry row = store.read(scope);
        return Reading.progress(row == null ? Progress.empty(Progress.Mode.NORMAL, TARGET)
            : row.progress, syncStore.read(scope).confirmed, action -> {
              try {
                return owner.whileCurrent(scope, () -> { action.run(); return true; });
              } catch (SessionOwner.StaleSession stale) { return false; }
            }, Collections.emptyList(), true);
      });
    } catch (SessionOwner.StaleSession stale) {
      return Reading.blocked(AccountHomeState.Status.NO_ACCOUNT, Collections.emptyList(), true);
    }
  }

  public ArrivalPolicy.Verdict arrivalVerdict() {
    return ArrivalPolicy.evaluate(target, new ArrivalPolicy.Fix(
        atTarget ? 10 : 11, 20, 8, 1_000_000_000L, false), 2_000_000_000L);
  }

  public Progress localProgress() {
    AuthoredProgressStore.Entry row = rows.get(FIRST_USER);
    return row == null ? null : row.progress;
  }

  public AuthoredEpisodeHost.OpenResult open(Context context, SyncScope expectedOwner) {
    if (expectedOwner != null) throw new AssertionError("Fixture must not accept reminder entry");
    opens.incrementAndGet();
    AuthoredEpisodeHost.Access access = new AuthoredEpisodeHost.Access() {
      @Override public SyncScope capture() { return AuthoredRouteTestFixture.this.capture(); }
      @Override public DatasetRegistry registry() { return registry; }
      @Override public SessionOwner owner() { return owner; }
      @Override public CompletableFuture<OutboxState> reconcile(CommittedMilestoneSource source,
          SyncScope scope) {
        return new MilestoneReconciler(registry, owner, source, sync, Runnable::run).reconcile(scope);
      }
      @Override public CompletableFuture<OutboxState> flush(SyncScope scope) {
        return sync.flush(scope);
      }
      @Override public CompletableFuture<OutboxState> refresh(SyncScope scope) {
        return sync.refresh(scope);
      }
    };
    return AuthoredEpisodeHost.open(access,
        scope -> arrivalVerdict() == ArrivalPolicy.Verdict.ADMITTED
            ? AuthoredEpisodeHost.Arrival.ADMITTED : AuthoredEpisodeHost.Arrival.NOT_ARRIVED,
        () -> store, () -> AuthoredEpisodeHost.loadApprovedContent(context),
        now::get, new ProgressService.AttemptFactory() {
          @Override public String nextId() { return "synthetic-attempt-" + attempts.incrementAndGet(); }
          @Override public void shuffle(List<?> values) { }
        }, 90);
  }
}
