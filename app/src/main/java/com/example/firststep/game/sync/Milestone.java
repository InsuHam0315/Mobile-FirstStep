package com.example.firststep.game.sync;

import com.example.firststep.game.domain.ProgressService;
import java.util.Objects;

/** Durable introduction and optional passing summary. Quiz answers stay in the gameplay store. */
public final class Milestone {
  private static final long MAX_TIME_MILLIS = 253402300799999L;
  public final long introDoneAtMillis;
  public final Completion completion;

  public Milestone(long introDoneAtMillis, Completion completion) {
    if (introDoneAtMillis < 0 || introDoneAtMillis > MAX_TIME_MILLIS
        || (completion != null
            && completion.completedAtMillis < introDoneAtMillis + ProgressService.WAIT_MILLIS)) {
      throw new IllegalArgumentException("Invalid milestone time");
    }
    this.introDoneAtMillis = introDoneAtMillis;
    this.completion = completion;
  }

  public static final class Completion {
    public final String attemptId, contentVersion;
    public final int correct;
    public final long completedAtMillis;

    public Completion(String attemptId, String contentVersion, int correct, long completedAtMillis) {
      this.attemptId = SyncScope.identifier(attemptId);
      this.contentVersion = SyncScope.identifier(contentVersion);
      if (correct < 2 || correct > 3 || completedAtMillis < 0
          || completedAtMillis > MAX_TIME_MILLIS) {
        throw new IllegalArgumentException("Invalid completion summary");
      }
      this.correct = correct;
      this.completedAtMillis = completedAtMillis;
    }

    @Override public boolean equals(Object value) {
      if (!(value instanceof Completion)) return false;
      Completion other = (Completion) value;
      return correct == other.correct && completedAtMillis == other.completedAtMillis
          && attemptId.equals(other.attemptId) && contentVersion.equals(other.contentVersion);
    }

    @Override public int hashCode() {
      return Objects.hash(attemptId, contentVersion, correct, completedAtMillis);
    }
  }

  /** The first accepted pass wins. Later data can only add a pass or move the intro earlier. */
  public static Milestone merge(Milestone accepted, Milestone incoming) {
    if (accepted == null) return incoming;
    if (incoming == null) return accepted;
    return new Milestone(Math.min(accepted.introDoneAtMillis, incoming.introDoneAtMillis),
        accepted.completion != null ? accepted.completion : incoming.completion);
  }

  @Override public boolean equals(Object value) {
    if (!(value instanceof Milestone)) return false;
    Milestone other = (Milestone) value;
    return introDoneAtMillis == other.introDoneAtMillis
        && Objects.equals(completion, other.completion);
  }

  @Override public int hashCode() {
    return Objects.hash(introDoneAtMillis, completion);
  }
}
