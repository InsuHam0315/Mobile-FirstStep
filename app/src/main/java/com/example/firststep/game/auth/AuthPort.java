package com.example.firststep.game.auth;

/** All callbacks and calls must be serialized on the same thread. */
public interface AuthPort {
  interface Subscription {
    void cancel();
  }

  interface AuthObserver {
    void onChanged(String uid);
  }

  interface Completion {
    void onResult(Failure failure);
  }

  boolean isConfigured();

  String currentUid();

  Subscription observe(AuthObserver observer);

  void signIn(boolean reselect, Completion callback);

  void signOut(Completion callback);
}
