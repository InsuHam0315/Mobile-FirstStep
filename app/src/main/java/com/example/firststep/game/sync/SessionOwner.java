package com.example.firststep.game.sync;

import java.util.function.Supplier;

/**
 * The account composition must admit only its current authenticated, complete-profile owner.
 * Serialize the project/UID/generation check and action with local session changes.
 * The guard is reentrant because both the coordinator and the backend may enforce it.
 * Start only bounded work here and never await a remote result while holding the session guard.
 */
public interface SessionOwner {
  <T> T whileCurrent(SyncScope scope, Supplier<T> action);

  final class StaleSession extends IllegalStateException {
    public StaleSession() {
      super("Sync session changed");
    }
  }
}
