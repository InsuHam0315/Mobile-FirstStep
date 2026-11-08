package com.example.firststep.game.identity;

import android.app.Activity;
import com.example.firststep.game.auth.*;
import com.example.firststep.game.composition.identity.IdentityRuntime;
import java.util.ArrayList;
import java.util.List;

/** Synthetic, explicitly labeled fixture. Test code dispatches all calls on the main thread. */
final class FakeIdentityRuntime implements IdentityRuntime {
  String uid;
  AuthPort.AuthObserver observer;
  AuthPort.Completion login, logout;
  final List<ProfilePort.ProfileCallback> loads = new ArrayList<>();
  final List<AuthPort.Completion> saves = new ArrayList<>();
  final AuthPort auth =
      new AuthPort() {
        public boolean isConfigured() {
          return true;
        }

        public String currentUid() {
          return uid;
        }

        public Subscription observe(AuthObserver value) {
          observer = value;
          return () -> {};
        }

        public void signIn(boolean reselect, Completion callback) {
          login = callback;
        }

        public void signOut(Completion callback) {
          logout = callback;
        }
      };
  final ProfilePort profiles =
      new ProfilePort() {
        public void load(String uid, ProfileCallback callback) {
          loads.add(callback);
        }

        public void save(String uid, Profile profile, AuthPort.Completion callback) {
          saves.add(callback);
        }
      };

  public AuthPort auth() {
    return auth;
  }

  public ProfilePort profiles() {
    return profiles;
  }

  public Kind kind() {
    return Kind.FAKE;
  }

  public void attach(Activity activity) {}

  public void detach(Activity activity) {}

  public void close() {}

  void loginAs(String value) {
    uid = value;
    login.onResult(null);
  }

  void switchTo(String value) {
    uid = value;
    observer.onChanged(value);
  }

  void server(int i, Profile profile) {
    loads.get(i).onResult(profile, ProfilePort.Source.SERVER, null);
  }

  ProfilePort.ProfileCallback latestLoad() {
    return loads.get(loads.size() - 1);
  }

  void serverLatest(Profile profile) {
    latestLoad().onResult(profile, ProfilePort.Source.SERVER, null);
  }
}
