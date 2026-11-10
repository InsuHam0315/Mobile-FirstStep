package com.example.firststep.game.auth;

/** Observes the confirmed profile owner on the controller's serialized calling thread. */
@FunctionalInterface
public interface ConfirmedOwnerObserver {
  /** Receives the server-confirmed UID or null on revocation. Values can repeat. */
  void onConfirmedOwner(String uid);
}
