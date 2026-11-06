package com.example.firststep.game.domain;

import java.util.List;

/**
 * Blocking synchronous operations: callers must use a worker thread. Update executes exactly once
 * inside a transaction.
 */
public interface ProgressRepository {
  interface Transition {
    Progress apply(Progress current);
  }

  Progress read(Progress.Mode mode, String buildingId);

  List<Progress> readAll(Progress.Mode mode);

  Progress atomicUpdate(Progress.Mode mode, String buildingId, Transition transition);
}
