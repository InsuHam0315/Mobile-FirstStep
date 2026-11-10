package com.example.firststep.game.sync;

import com.example.firststep.game.domain.Progress;
import java.util.Objects;
import java.util.function.Supplier;

/** Process-scoped owner guard. The identity host supplies auth and server confirmation events. */
public final class ConfirmedSessionOwner implements SessionOwner {
  private final String projectId;
  private String authenticatedUid;
  private boolean confirmed;
  private long generation;

  public ConfirmedSessionOwner(String projectId) {
    this.projectId = SyncScope.identifier(projectId);
  }

  /** A changed, missing or unsupported authentication identity invalidates the old grant. */
  public synchronized void onAuthChanged(String uid) {
    String next = eligibleUid(uid);
    if (Objects.equals(authenticatedUid, next)) return;
    confirmed = false;
    authenticatedUid = next;
    advance();
  }

  /** Called only for a server-confirmed profile. A stale mismatching event is ignored. */
  public synchronized void confirm(String uid) {
    if (uid == null || !uid.equals(authenticatedUid) || confirmed) return;
    advance();
    confirmed = true;
  }

  /** Invalidate confirmation before logout, reselection, or loss of server confirmation. */
  public synchronized void revoke() {
    if (!confirmed) return;
    confirmed = false;
    advance();
  }

  public synchronized SyncScope capture(String datasetId, Progress.Mode mode, String buildingId) {
    if (!confirmed) throw new StaleSession();
    return new SyncScope(projectId, authenticatedUid, generation, datasetId, mode, buildingId);
  }

  @Override public synchronized <T> T whileCurrent(SyncScope scope, Supplier<T> action) {
    if (!confirmed || scope == null
        || !scope.matchesSession(projectId, authenticatedUid, generation)) {
      throw new StaleSession();
    }
    return action.get();
  }

  private void advance() {
    generation = Math.addExact(generation, 1);
  }

  private static String eligibleUid(String uid) {
    if (uid == null || "guest".equalsIgnoreCase(uid) || "anonymous".equalsIgnoreCase(uid)) {
      return null;
    }
    try {
      return SyncScope.identifier(uid);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }
}
