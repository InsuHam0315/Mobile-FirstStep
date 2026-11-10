package com.example.firststep.game.composition.identity;

import android.app.Activity;
import com.example.firststep.game.auth.*;

/** SDK graph, never a progress source. No credentials or profile data in diagnostics. */
public interface IdentityRuntime extends AutoCloseable {
  enum Kind {
    UNCONFIGURED,
    LIVE,
    FAKE,
    LOCAL_EMULATOR
  }

  AuthPort auth();

  ProfilePort profiles();

  Kind kind();

  void attach(Activity activity);

  void detach(Activity activity);

  void close();
}
