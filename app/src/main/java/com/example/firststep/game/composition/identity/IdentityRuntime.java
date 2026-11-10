package com.example.firststep.game.composition.identity;

import android.app.Activity;
import com.example.firststep.game.auth.*;
import java.util.Optional;

/** Identity ports and optional account-progress composition. */
public interface IdentityRuntime extends AutoCloseable {
  enum Kind {
    UNCONFIGURED,
    LIVE,
    FAKE,
    LOCAL_EMULATOR
  }

  AuthPort auth();

  ProfilePort profiles();

  default ConfirmedOwnerObserver confirmedOwnerObserver() {
    return ignored -> {};
  }

  default Optional<AccountProgressAccess> accountProgress() {
    return Optional.empty();
  }

  Kind kind();

  void attach(Activity activity);

  void detach(Activity activity);

  void close();
}
