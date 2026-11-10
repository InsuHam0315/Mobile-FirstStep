package com.example.firststep.game.composition.identity;

import com.example.firststep.game.sync.CommittedMilestoneSource;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.MilestoneReconciler;
import com.example.firststep.game.sync.OutboxState;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncCoordinator;
import com.example.firststep.game.sync.SyncScope;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Composition access for registered authored NORMAL gameplay. */
public final class AccountProgressAccess {
  private final DatasetRegistry registry;
  private final AccountProgressSession session;
  private final SyncCoordinator sync;
  private final Executor worker;

  AccountProgressAccess(
      DatasetRegistry registry, AccountProgressSession session, SyncCoordinator sync, Executor worker) {
    this.registry = registry;
    this.session = session;
    this.sync = sync;
    this.worker = worker;
  }

  /** Capture the current confirmed account for an allowlisted building and dataset. */
  public SyncScope capture(String datasetId, String buildingId) {
    SyncScope scope = session.capture(datasetId, buildingId);
    registry.requireEligible(scope);
    return scope;
  }

  public DatasetRegistry registry() {
    return registry;
  }

  public SessionOwner owner() {
    return session;
  }

  public MilestoneReconciler reconciler(CommittedMilestoneSource source) {
    return new MilestoneReconciler(registry, session, source, sync, worker);
  }

  /** Explicit networking step after an authored milestone has been reconciled. */
  public CompletableFuture<OutboxState> flush(SyncScope scope) {
    return sync.flush(scope);
  }
}
