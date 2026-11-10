package com.example.firststep.game.composition.identity;

import com.example.firststep.game.auth.*;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.*;
import java.util.*;

/** Main-thread serialized auth mutations. Logout waits out an un-cancelable Firebase sign-in. */
public final class FirebaseAuthPort implements AuthPort, AutoCloseable {
  private final java.util.concurrent.Executor completionExecutor;
  private final FirebaseAuth auth;
  private final CredentialSource source;
  private final Set<AuthObserver> observers = new LinkedHashSet<>();
  private boolean closed;
  private final FirebaseAuth.AuthStateListener sdkListener;
  private boolean busy, firebasePending, logoutPending, clearDone;
  private long epoch;
  private Failure clearFailure;
  private Completion logoutCallback;
  private String exposedUid;

  public FirebaseAuthPort(FirebaseAuth auth, CredentialSource source) {
    this(
        auth,
        source,
        androidx.core.content.ContextCompat.getMainExecutor(auth.getApp().getApplicationContext()));
  }

  /**
   * Executor must serialize completion onto the main thread; enables deterministic delivery tests.
   */
  public FirebaseAuthPort(
      FirebaseAuth auth, CredentialSource source, java.util.concurrent.Executor executor) {
    this.completionExecutor = executor;
    this.auth = auth;
    this.source = source;
    sdkListener = a -> publish();
    auth.addAuthStateListener(sdkListener);
  }

  public boolean isConfigured() {
    return true;
  }

  public String currentUid() {
    if (closed) return null;
    FirebaseUser u = auth.getCurrentUser();
    return busy || u == null ? null : u.getUid();
  }

  public Subscription observe(AuthObserver observer) {
    String uid = currentUid();
    if (observers.isEmpty()) exposedUid = uid;
    observers.add(observer);
    observer.onChanged(uid);
    return () -> observers.remove(observer);
  }

  private void publish() {
    if (closed) return;
    String uid = currentUid();
    if (Objects.equals(uid, exposedUid)) return;
    exposedUid = uid;
    for (AuthObserver o : new ArrayList<>(observers)) o.onChanged(uid);
  }

  public void signIn(boolean reselect, Completion cb) {
    if (closed || busy) {
      cb.onResult(Failure.UNAVAILABLE);
      return;
    }
    busy = true;
    long token = ++epoch;
    publish();
    if (reselect) {
      auth.signOut();
      source.clear(
          f -> {
            if (token != epoch) return;
            if (f != null) {
              finishLogin(token, cb, f);
              return;
            }
            request(token, cb);
          });
    } else request(token, cb);
  }

  private void request(long token, Completion cb) {
    source.request(
        (credential, f) -> {
          if (token != epoch) return;
          if (f != null || credential == null) {
            finishLogin(token, cb, f == null ? Failure.UNKNOWN : f);
            return;
          }
          firebasePending = true;
          try {
            auth.signInWithCredential(credential)
                .addOnCompleteListener(
                    completionExecutor,
                    task -> {
                      if (closed) return;
                      firebasePending = false;
                      if (token != epoch) {
                        auth.signOut();
                        finishLogout();
                        return;
                      }
                      finishLogin(
                          token,
                          cb,
                          task.isSuccessful() && task.getResult().getUser() != null
                              ? null
                              : task.isCanceled()
                                  ? Failure.CANCELED
                                  : task.getException() instanceof FirebaseNetworkException
                                      ? Failure.UNAVAILABLE
                                      : Failure.UNKNOWN);
                    });
          } catch (RuntimeException invalid) {
            firebasePending = false;
            finishLogin(token, cb, Failure.UNKNOWN);
          }
        });
  }

  private void finishLogin(long token, Completion cb, Failure f) {
    if (closed || token != epoch) return;
    busy = false;
    publish();
    cb.onResult(f);
  }

  public void signOut(Completion cb) {
    if (closed) {
      cb.onResult(Failure.CANCELED);
      return;
    }
    long token = ++epoch;
    busy = true;
    logoutPending = true;
    clearDone = false;
    clearFailure = null;
    logoutCallback = cb;
    auth.signOut();
    publish();
    source.cancel();
    source.clear(
        f -> {
          if (token != epoch) return;
          clearDone = true;
          clearFailure = f;
          finishLogout();
        });
  }

  public void close() {
    closed = true;
    ++epoch;
    source.cancel();
    auth.removeAuthStateListener(sdkListener);
    observers.clear();
  }

  private void finishLogout() {
    if (!logoutPending || !clearDone || firebasePending) return;
    auth.signOut();
    logoutPending = false;
    busy = false;
    publish();
    Completion cb = logoutCallback;
    logoutCallback = null;
    if (cb != null) cb.onResult(clearFailure);
  }
}
