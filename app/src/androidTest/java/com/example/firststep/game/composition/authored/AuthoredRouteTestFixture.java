package com.example.firststep.game.composition.authored;

import android.content.Context;
import com.example.firststep.game.device.authored.ArrivalPolicy;
import com.example.firststep.game.device.authored.AuthoredTargetConfig;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.sync.AuthoredRouteTestRegistry;
import com.example.firststep.game.sync.CommittedMilestoneSource;
import com.example.firststep.game.sync.ConfirmedSessionOwner;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.Milestone;
import com.example.firststep.game.sync.OutboxState;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncScope;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

/** In-memory, instrumentation-only dependency for the real Activity. */
public final class AuthoredRouteTestFixture {
  private static final String DATASET = AuthoredEpisodeHost.DATASET;
  private static final String TARGET = AuthoredEpisodeHost.TARGET;
  private static final long INTRO = 1000000L;
  public final AtomicInteger opens = new AtomicInteger();
  public final AtomicInteger localWrites = new AtomicInteger();
  public final AtomicInteger remoteReads = new AtomicInteger();
  public final AtomicLong now = new AtomicLong(INTRO);
  public volatile boolean atTarget;
  private volatile AuthoredProgressStore.Entry row;
  private final AuthoredTargetConfig target = new AuthoredTargetConfig(TARGET,
      "synthetic-instrumentation", 10, 20, 50, 90, 15, 5_000_000_000L);
  private final DatasetRegistry registry = AuthoredRouteTestRegistry.create(DATASET, TARGET);
  private final ConfirmedSessionOwner owner = new ConfirmedSessionOwner("synthetic-project");
  private final AuthoredProgressStore store = new AuthoredProgressStore() {
    @Override public Entry read(SyncScope scope) { return row; }
    @Override public Entry update(SyncScope scope, UnaryOperator<Entry> operation) {
      Entry committed = operation.apply(row);
      localWrites.incrementAndGet();
      row = committed;
      return committed;
    }
  };

  public AuthoredRouteTestFixture() {
    owner.onAuthChanged("synthetic-user");
    owner.confirm("synthetic-user");
  }

  public ArrivalPolicy.Verdict arrivalVerdict() {
    return ArrivalPolicy.evaluate(target, new ArrivalPolicy.Fix(
        atTarget ? 10 : 11, 20, 8, 1_000_000_000L, false), 2_000_000_000L);
  }

  public Progress localProgress() { return row == null ? null : row.progress; }

  public AuthoredEpisodeHost.OpenResult open(Context context, SyncScope expectedOwner) {
    if (expectedOwner != null) throw new AssertionError("Fixture must not accept reminder entry");
    opens.incrementAndGet();
    AuthoredEpisodeHost.Access access = new AuthoredEpisodeHost.Access() {
      @Override public SyncScope capture() { return owner.capture(DATASET, Progress.Mode.NORMAL, TARGET); }
      @Override public DatasetRegistry registry() { return registry; }
      @Override public SessionOwner owner() { return owner; }
      @Override public CompletableFuture<OutboxState> reconcile(CommittedMilestoneSource source,
          SyncScope scope) {
        Milestone committed = source.latest(scope);
        return CompletableFuture.completedFuture(committed == null ? OutboxState.empty()
            : new OutboxState(1, null, committed, OutboxState.Status.PENDING));
      }
      @Override public CompletableFuture<OutboxState> flush(SyncScope scope) {
        throw new AssertionError("Readback must not flush");
      }
      @Override public CompletableFuture<OutboxState> refresh(SyncScope scope) {
        remoteReads.incrementAndGet();
        Milestone confirmed = new Milestone(INTRO, new Milestone.Completion(
            "synthetic-attempt", DATASET, 2, INTRO + ProgressService.WAIT_MILLIS));
        return CompletableFuture.completedFuture(
            new OutboxState(0, confirmed, null, OutboxState.Status.CONFIRMED));
      }
    };
    AtomicInteger attempts = new AtomicInteger();
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
