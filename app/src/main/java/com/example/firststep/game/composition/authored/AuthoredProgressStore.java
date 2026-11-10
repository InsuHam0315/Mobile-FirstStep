package com.example.firststep.game.composition.authored;

import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.sync.SyncScope;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Blocking transactional persistence for authored gameplay. No record means null. */
public interface AuthoredProgressStore {
  Entry read(SyncScope scope);

  /** Apply one transition inside a transaction. Publish its result only after commit. */
  Entry update(SyncScope scope, UnaryOperator<Entry> transition);

  final class Entry {
    public final Progress progress;
    public final Long firstPassCommittedAtMillis;

    public Entry(Progress progress, Long firstPassCommittedAtMillis) {
      this.progress = Objects.requireNonNull(progress);
      if (progress.stamp != (firstPassCommittedAtMillis != null)) {
        throw new IllegalArgumentException("Missing or unexpected pass timestamp");
      }
      this.firstPassCommittedAtMillis = firstPassCommittedAtMillis;
    }
  }
}
