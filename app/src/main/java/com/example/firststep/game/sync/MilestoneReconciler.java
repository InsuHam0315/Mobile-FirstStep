package com.example.firststep.game.sync;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Replays the committed authored milestone into the local outbox after save or reopen. */
public final class MilestoneReconciler {
  private final DatasetRegistry registry;
  private final SessionOwner owner;
  private final CommittedMilestoneSource source;
  private final SyncCoordinator sync;
  private final Executor worker;

  public MilestoneReconciler(DatasetRegistry registry, SessionOwner owner,
      CommittedMilestoneSource source, SyncCoordinator sync, Executor worker) {
    this.registry = Objects.requireNonNull(registry);
    this.owner = Objects.requireNonNull(owner);
    this.source = Objects.requireNonNull(source);
    this.sync = Objects.requireNonNull(sync);
    this.worker = Objects.requireNonNull(worker);
  }

  /** A separate explicit flush is responsible for networking. */
  public CompletableFuture<OutboxState> reconcile(SyncScope scope) {
    registry.requireEligible(scope);
    return CompletableFuture.supplyAsync(
            () -> owner.whileCurrent(scope, () -> source.latest(scope)), worker)
        .thenCompose(milestone ->
            milestone == null ? sync.readLocal(scope) : sync.record(scope, milestone));
  }
}
