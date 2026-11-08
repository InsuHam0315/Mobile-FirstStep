package com.example.firststep.game.auth;

/**
 * Callbacks are serialized. CACHE must be followed by SERVER or a failure. SERVER is fetched from
 * the server. A save callback with null failure means the server acknowledged the write.
 */
public interface ProfilePort {
  enum Source {
    SERVER,
    CACHE
  }

  interface ProfileCallback {
    void onResult(Profile profile, Source source, Failure failure);
  }

  void load(String uid, ProfileCallback callback);

  void save(String uid, Profile profile, AuthPort.Completion callback);
}
