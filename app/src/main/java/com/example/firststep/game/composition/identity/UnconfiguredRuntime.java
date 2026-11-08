package com.example.firststep.game.composition.identity;

import android.app.Activity;
import com.example.firststep.game.auth.*;

/** Explicit configuration failure, never a synthetic authenticated user. */
final class UnconfiguredRuntime implements IdentityRuntime {
  private final AuthPort auth =
      new AuthPort() {
        public boolean isConfigured() {
          return false;
        }

        public String currentUid() {
          return null;
        }

        public Subscription observe(AuthObserver o) {
          o.onChanged(null);
          return () -> {};
        }

        public void signIn(boolean r, Completion cb) {
          cb.onResult(Failure.UNAVAILABLE);
        }

        public void signOut(Completion cb) {
          cb.onResult(null);
        }
      };

  public AuthPort auth() {
    return auth;
  }

  public ProfilePort profiles() {
    return new ProfilePort() {
      public void load(String u, ProfileCallback cb) {
        cb.onResult(null, Source.SERVER, Failure.UNAVAILABLE);
      }

      public void save(String u, Profile p, AuthPort.Completion cb) {
        cb.onResult(Failure.UNAVAILABLE);
      }
    };
  }

  public Kind kind() {
    return Kind.UNCONFIGURED;
  }

  public void attach(Activity a) {}

  public void detach(Activity a) {}

  public void close() {}
}
