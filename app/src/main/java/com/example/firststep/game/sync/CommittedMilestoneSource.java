package com.example.firststep.game.sync;

/** Blocking read from committed authored gameplay. A null milestone means no completed intro. */
public interface CommittedMilestoneSource {
  Milestone latest(SyncScope scope);

  /** The saved gameplay stays intact when its observed clock cannot support a sync milestone. */
  final class UnusableMilestone extends IllegalStateException {
    public UnusableMilestone() {
      super("Committed gameplay does not meet sync milestone validation");
    }
  }
}
