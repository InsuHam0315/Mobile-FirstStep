package com.example.firststep.game.sync;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Local-first sync. Each explicit flush attempts at most one pending revision.
 * The host must recheck its captured scope before rendering any returned state.
 */
public final class SyncCoordinator {
  private final DatasetRegistry registry;
  private final SessionOwner owner;
  private final SyncStore store;
  private final SyncBackend backend;
  private final Executor worker;

  public SyncCoordinator(DatasetRegistry registry, SessionOwner owner, SyncStore store,
      SyncBackend backend, Executor worker) {
    this.registry = Objects.requireNonNull(registry);
    this.owner = Objects.requireNonNull(owner);
    this.store = Objects.requireNonNull(store);
    this.backend = Objects.requireNonNull(backend);
    this.worker = Objects.requireNonNull(worker);
  }

  private <T> CompletableFuture<T> execute(SyncScope scope, Supplier<T> action) {
    registry.requireEligible(scope);
    return CompletableFuture.supplyAsync(() -> owner.whileCurrent(scope, action), worker);
  }

  public CompletableFuture<OutboxState> readLocal(SyncScope scope) {
    return execute(scope, () -> store.read(scope));
  }

  /** Persist the milestone and outbox together without depending on connectivity. */
  public CompletableFuture<OutboxState> record(SyncScope scope, Milestone milestone) {
    Objects.requireNonNull(milestone, "milestone");
    return execute(scope, () -> store.update(scope, state -> state.enqueue(milestone)));
  }

  public CompletableFuture<OutboxState> refresh(SyncScope scope) {
    return execute(scope, () -> backend.read(scope))
        .thenCompose(request -> request)
        .thenApplyAsync(remote -> owner.whileCurrent(scope,
            () -> store.update(scope, state -> state.observeRemote(remote))), worker);
  }

  public CompletableFuture<OutboxState> flush(SyncScope scope) {
    return readLocal(scope).thenComposeAsync(sent -> {
      if (sent.pending == null || sent.status == OutboxState.Status.PERMANENT_FAILURE) {
        return owner.whileCurrent(scope, () -> CompletableFuture.completedFuture(sent));
      }
      CompletableFuture<Milestone> request;
      try {
        request = owner.whileCurrent(scope, () -> backend.merge(scope, sent.pending));
      } catch (SyncBackend.Failure failure) {
        return execute(scope,
            () -> store.update(scope, state -> state.fail(sent.revision, failure.retryable)));
      }
      return request.handleAsync((remote, error) -> owner.whileCurrent(scope, () -> {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
          cause = cause.getCause();
        }
        if (cause != null && !(cause instanceof SyncBackend.Failure)) {
          throw new CompletionException(cause);
        }
        boolean retryable = cause instanceof SyncBackend.Failure
            && ((SyncBackend.Failure) cause).retryable;
        return store.update(scope, state -> {
          if (error == null && remote != null) return state.acknowledge(sent.revision, remote);
          return state.fail(sent.revision, retryable);
        });
      }), worker);
    }, worker);
  }
}
