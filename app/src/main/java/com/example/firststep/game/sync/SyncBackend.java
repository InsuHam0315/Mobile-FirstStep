package com.example.firststep.game.sync;

import java.util.concurrent.CompletableFuture;

public interface SyncBackend {
  /** A null value means no server record. Cache-only responses are not confirmations. */
  CompletableFuture<Milestone> read(SyncScope scope);

  /** Atomically merge one building and return the server-accepted milestone. */
  CompletableFuture<Milestone> merge(SyncScope scope, Milestone desired);

  final class Failure extends RuntimeException {
    public final boolean retryable;

    public Failure(boolean retryable) {
      super(retryable ? "Sync temporarily unavailable" : "Sync operation rejected");
      this.retryable = retryable;
    }
  }
}
