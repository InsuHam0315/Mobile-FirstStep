package com.example.firststep.game.composition.authored;

import com.example.firststep.game.composition.authored.AuthoredProgressStore.Entry;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressRepository;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.sync.CommittedMilestoneSource;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.Milestone;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncScope;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A blocking, single-building authored repository. Rebind it when the account epoch changes. */
public final class AuthoredProgressRepository
    implements ProgressRepository, CommittedMilestoneSource {
  private final SessionOwner owner;
  private final SyncScope scope;
  private final AuthoredProgressStore store;
  private final ProgressService.Clock clock;

  public AuthoredProgressRepository(DatasetRegistry registry, SessionOwner owner, SyncScope scope,
      AuthoredProgressStore store, ProgressService.Clock clock) {
    registry.requireEligible(scope);
    this.owner = Objects.requireNonNull(owner);
    this.scope = scope;
    this.store = Objects.requireNonNull(store);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override public Progress read(Progress.Mode mode, String buildingId) {
    requireBuilding(mode, buildingId);
    return owner.whileCurrent(scope, () -> progress(checked(store.read(scope))));
  }

  @Override public List<Progress> readAll(Progress.Mode mode) {
    requireBuilding(mode, scope.buildingId);
    return owner.whileCurrent(scope, () -> {
      Entry entry = checked(store.read(scope));
      return entry == null ? Collections.emptyList() : Collections.singletonList(entry.progress);
    });
  }

  @Override public Progress atomicUpdate(Progress.Mode mode, String buildingId,
      Transition transition) {
    requireBuilding(mode, buildingId);
    Objects.requireNonNull(transition);
    return owner.whileCurrent(scope, () -> store.update(scope, row -> {
      Entry current = checked(row);
      Progress previous = progress(current);
      Progress next = Objects.requireNonNull(transition.apply(previous));
      requireBuilding(next.mode, next.buildingId);
      if (previous.introDoneAt != null && !previous.introDoneAt.equals(next.introDoneAt)) {
        throw new IllegalArgumentException("Cannot change committed introduction");
      }
      if (previous.stamp && (!next.stamp || !samePass(passing(previous), passing(next)))) {
        throw new IllegalArgumentException("Cannot change committed pass");
      }
      if (current != null && next == previous) return current;
      Long committedAt = current == null ? null : current.firstPassCommittedAtMillis;
      if (next.stamp && !previous.stamp) committedAt = clock.nowMillis();
      return new Entry(next, committedAt);
    }).progress);
  }

  @Override public Milestone latest(SyncScope requested) {
    if (requested == null
        || !scope.matchesSession(requested.projectId, requested.uid, requested.sessionGeneration)
        || !scope.datasetId.equals(requested.datasetId)) {
      throw new IllegalArgumentException("Authored source scope mismatch");
    }
    requireBuilding(requested.mode, requested.buildingId);
    return owner.whileCurrent(scope, () -> {
      Entry entry = checked(store.read(scope));
      if (entry == null || entry.progress.introDoneAt == null) return null;
      try {
        Milestone.Completion completion = null;
        if (entry.progress.stamp) {
          Progress.Result pass = passing(entry.progress);
          completion = new Milestone.Completion(pass.attemptId, pass.contentVersion, pass.correct,
              entry.firstPassCommittedAtMillis);
        }
        return new Milestone(entry.progress.introDoneAt, completion);
      } catch (IllegalArgumentException invalid) {
        throw new UnusableMilestone();
      }
    });
  }

  private void requireBuilding(Progress.Mode mode, String buildingId) {
    if (mode != scope.mode || !scope.buildingId.equals(buildingId)) {
      throw new IllegalArgumentException("Unbound authored building or mode");
    }
  }

  private Entry checked(Entry entry) {
    if (entry != null) requireBuilding(entry.progress.mode, entry.progress.buildingId);
    return entry;
  }

  private Progress progress(Entry entry) {
    return entry == null ? Progress.empty(scope.mode, scope.buildingId) : entry.progress;
  }

  private static Progress.Result passing(Progress progress) {
    for (Progress.Result result : progress.history.values()) {
      if (result.passed) return result;
    }
    throw new IllegalStateException("Missing committed pass");
  }

  private static boolean samePass(Progress.Result previous, Progress.Result next) {
    return previous.attemptId.equals(next.attemptId)
        && previous.contentVersion.equals(next.contentVersion)
        && previous.correct == next.correct
        && previous.answers.equals(next.answers);
  }
}
