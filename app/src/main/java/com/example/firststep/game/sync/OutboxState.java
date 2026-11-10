package com.example.firststep.game.sync;

import java.util.Objects;

/** One persisted row contains the visible remote projection and its pending local milestone. */
public final class OutboxState {
  public enum Status { EMPTY, CONFIRMED, PENDING, RETRYABLE_FAILURE, PERMANENT_FAILURE }

  public final long revision;
  public final Milestone confirmed, pending;
  public final Status status;

  public OutboxState(long revision, Milestone confirmed, Milestone pending, Status status) {
    this.status = Objects.requireNonNull(status, "status");
    if (revision < 0
        || (pending != null && revision == 0)
        || (pending == null && status != (confirmed == null ? Status.EMPTY : Status.CONFIRMED))
        || (pending != null && (status == Status.EMPTY || status == Status.CONFIRMED))) {
      throw new IllegalArgumentException("Invalid outbox state");
    }
    this.revision = revision;
    this.confirmed = confirmed;
    this.pending = pending;
  }

  public static OutboxState empty() {
    return new OutboxState(0, null, null, Status.EMPTY);
  }

  public Milestone effective() {
    return Milestone.merge(confirmed, pending);
  }

  OutboxState enqueue(Milestone milestone) {
    Milestone current = effective();
    Milestone next = Milestone.merge(current, Objects.requireNonNull(milestone, "milestone"));
    if (Objects.equals(current, next)) return this;
    return new OutboxState(Math.addExact(revision, 1), confirmed, next, Status.PENDING);
  }

  OutboxState observeRemote(Milestone remote) {
    Milestone next = Milestone.merge(confirmed, remote);
    if (Objects.equals(confirmed, next)) return this;
    return new OutboxState(revision, next, pending, pending == null ? Status.CONFIRMED : status);
  }

  OutboxState acknowledge(long sentRevision, Milestone remote) {
    OutboxState observed = observeRemote(Objects.requireNonNull(remote, "remote acknowledgement"));
    if (pending == null || revision != sentRevision) return observed;
    if (!Objects.equals(Milestone.merge(remote, pending), remote)) {
      return observed.fail(sentRevision, false);
    }
    return new OutboxState(revision, observed.confirmed, null, Status.CONFIRMED);
  }

  OutboxState fail(long sentRevision, boolean retryable) {
    if (pending == null || revision != sentRevision || status == Status.PERMANENT_FAILURE) {
      return this;
    }
    return new OutboxState(revision, confirmed, pending,
        retryable ? Status.RETRYABLE_FAILURE : Status.PERMANENT_FAILURE);
  }
}
