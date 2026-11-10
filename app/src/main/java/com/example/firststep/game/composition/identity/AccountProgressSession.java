package com.example.firststep.game.composition.identity;

import com.example.firststep.game.auth.AuthPort;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.sync.ConfirmedSessionOwner;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncScope;
import java.util.function.Supplier;

/** Process-owned bridge from identity events to the worker-safe account guard. */
final class AccountProgressSession implements SessionOwner {
  private final AuthPort auth;
  private final Supplier<String> sdkUid;
  private final ConfirmedSessionOwner owner;

  AccountProgressSession(String projectId, AuthPort auth, Supplier<String> sdkUid) {
    this.auth = auth;
    this.sdkUid = sdkUid;
    owner = new ConfirmedSessionOwner(projectId);
    // Auth and this listener have the same process lifetime. Activity pause cancels only
    // the controller's separate subscription.
    auth.observe(owner::onAuthChanged);
  }

  void onConfirmedOwner(String uid) {
    synchronized (owner) {
      if (uid == null) {
        owner.revoke();
      } else if (uid.equals(auth.currentUid()) && uid.equals(sdkUid.get())) {
        owner.onAuthChanged(uid);
        owner.confirm(uid);
      }
    }
  }

  SyncScope capture(String datasetId, String buildingId) {
    synchronized (owner) {
      SyncScope scope = owner.capture(datasetId, Progress.Mode.NORMAL, buildingId);
      return whileCurrent(scope, () -> scope);
    }
  }

  @Override
  public <T> T whileCurrent(SyncScope scope, Supplier<T> action) {
    return owner.whileCurrent(
        scope,
        () -> {
          // Firebase can change its current user before the main-thread listener is delivered.
          if (!scope.uid.equals(sdkUid.get())) {
            owner.revoke();
            throw new StaleSession();
          }
          return action.get();
        });
  }
}
