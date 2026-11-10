package com.example.firststep.game.sync;

import com.example.firststep.game.domain.Progress;
import java.util.Objects;

/** Immutable identity for one operation. Session generation is not a durable storage key. */
public final class SyncScope {
  public final String projectId, uid, datasetId, buildingId;
  public final Progress.Mode mode;
  public final long sessionGeneration;

  public SyncScope(
      String projectId, String uid, long sessionGeneration,
      String datasetId, Progress.Mode mode, String buildingId) {
    this.projectId = identifier(projectId);
    this.uid = identifier(uid);
    this.datasetId = identifier(datasetId);
    this.buildingId = identifier(buildingId);
    this.mode = Objects.requireNonNull(mode, "mode");
    if (sessionGeneration < 1 || "guest".equalsIgnoreCase(uid)
        || "anonymous".equalsIgnoreCase(uid)) {
      throw new IllegalArgumentException("Invalid sync owner");
    }
    this.sessionGeneration = sessionGeneration;
  }

  static String identifier(String value) {
    if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) {
      throw new IllegalArgumentException("Invalid sync identifier");
    }
    return value;
  }

  /** Compare with the signed-in owner and epoch supplied by the host. */
  public boolean matchesSession(String project, String user, long generation) {
    return projectId.equals(project) && uid.equals(user) && sessionGeneration == generation;
  }
}
