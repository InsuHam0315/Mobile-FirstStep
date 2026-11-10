package com.example.firststep.game.auth;

import java.util.Objects;

/** Lifecycle-bound coordinator. Ports must serialize callbacks onto the calling thread. */
public final class SessionController {
  public interface Listener {
    void onState(SessionState state);
  }

  private enum Retry {
    LOAD,
    SAVE,
    CONFIRM
  }

  private static final class AuthAttempt {
    // Null means the request is pending. Only a non-sensitive failure survives a pause.
    Failure failure;
  }

  private final AuthPort auth;
  private final ProfilePort profiles;
  private final ConfirmedOwnerObserver ownerObserver;
  private Listener listener;
  private AuthPort.Subscription subscription;
  private SessionState state;
  private String uid;
  private Profile draft;
  private boolean dirty, active, signingOut, cachedDraft;
  private long generation, nonce;
  private Retry retry;
  private Profile awaitingConfirmation;
  private AuthAttempt authAttempt;

  public SessionController(AuthPort auth, ProfilePort profiles) {
    this(auth, profiles, ignored -> {});
  }

  public SessionController(AuthPort auth, ProfilePort profiles, ConfirmedOwnerObserver ownerObserver) {
    this.auth = auth;
    this.profiles = profiles;
    this.ownerObserver = Objects.requireNonNull(ownerObserver);
    state =
        new SessionState(
            auth.isConfigured() ? SessionState.Stage.LOGGED_OUT : SessionState.Stage.UNCONFIGURED,
            null,
            null,
            null,
            false);
  }

  public SessionState state() {
    if (active
        && state.getStage() != SessionState.Stage.UNCONFIGURED
        && !signingOut
        && state.getStage() != SessionState.Stage.AUTH_BUSY
        && (uid != null
            || draft != null
            || (state.getStage() == SessionState.Stage.LOGGED_OUT
                && state.getFailure() != null))
        && !Objects.equals(uid, auth.currentUid())) {
      ownerObserver.onConfirmedOwner(null);
      return new SessionState(SessionState.Stage.PROFILE_LOADING, null, null, null, false);
    }
    return state;
  }

  public void setListener(Listener listener) {
    this.listener = listener;
    if (listener != null && active) {
      if (!signingOut
          && state.getStage() != SessionState.Stage.UNCONFIGURED
          && state.getStage() != SessionState.Stage.AUTH_BUSY
          && !Objects.equals(uid, auth.currentUid())) reconcile(auth.currentUid());
      else listener.onState(state);
    }
  }

  public void resume() {
    if (active) return;
    active = true;
    final long g = ++generation;
    if (!auth.isConfigured()) {
      publish(SessionState.Stage.UNCONFIGURED, null, false);
      return;
    }
    String current = auth.currentUid();
    if (current != null || authAttempt == null) {
      reconcile(current);
    } else {
      Failure failure = authAttempt.failure;
      publish(
          failure == null ? SessionState.Stage.AUTH_BUSY : SessionState.Stage.LOGGED_OUT,
          failure,
          false);
    }
    subscription =
        auth.observe(
            observed -> {
              if (!active
                  || generation != g
                  || signingOut
                  || !Objects.equals(observed, auth.currentUid())) return;
              if (Objects.equals(observed, uid)) return;
              reconcile(observed);
            });
  }

  public void pause() {
    if (!active) return;
    active = false;
    ++generation;
    ++nonce;
    if (subscription != null) {
      subscription.cancel();
      subscription = null;
    }
    signingOut = false;
    state =
        new SessionState(
            auth.isConfigured() ? SessionState.Stage.LOGGED_OUT : SessionState.Stage.UNCONFIGURED,
            null,
            null,
            null,
            false);
  }

  public void signIn(boolean reselect) {
    if (!active
        || !auth.isConfigured()
        || state.getStage() == SessionState.Stage.AUTH_BUSY
        || signingOut) return;
    ownerObserver.onConfirmedOwner(null);
    uid = null;
    draft = null;
    dirty = false;
    cachedDraft = false;
    retry = null;
    awaitingConfirmation = null;
    ++nonce;
    final AuthAttempt attempt = new AuthAttempt();
    authAttempt = attempt;
    publish(SessionState.Stage.AUTH_BUSY, null, false);
    auth.signIn(
        reselect,
        failure -> {
          if (authAttempt != attempt || attempt.failure != null || signingOut) return;
          String current = auth.currentUid();
          if (current != null) {
            if (active) reconcile(current);
            else authAttempt = null;
            return;
          }
          attempt.failure = failure == null ? Failure.UNKNOWN : failure;
          if (active) publish(SessionState.Stage.LOGGED_OUT, attempt.failure, false);
        });
  }

  public void logout() {
    if (!active || !auth.isConfigured()) return;
    ownerObserver.onConfirmedOwner(null);
    authAttempt = null;
    uid = null;
    draft = null;
    dirty = false;
    cachedDraft = false;
    retry = null;
    awaitingConfirmation = null;
    signingOut = true;
    final long g = generation, n = ++nonce;
    publish(SessionState.Stage.AUTH_BUSY, null, false);
    auth.signOut(
        failure -> {
          if (!active || generation != g || nonce != n || !signingOut) return;
          signingOut = false;
          if (auth.currentUid() != null) {
            if (failure != null) authFailure(auth.currentUid(), failure);
            else authFailure(auth.currentUid(), Failure.UNKNOWN);
            return;
          }
          publish(SessionState.Stage.LOGGED_OUT, failure, false);
        });
  }

  public void edit(String name, String studentNumber, String department) {
    if (!active || uid == null || !Objects.equals(uid, auth.currentUid())) return;
    if (state.getStage() != SessionState.Stage.FORM
        && state.getStage() != SessionState.Stage.PENDING_FAILURE) return;
    if (retry == Retry.LOAD || retry == Retry.CONFIRM) return;
    draft = new Profile(name, studentNumber, department);
    dirty = true;
    cachedDraft = false;
    publish(SessionState.Stage.FORM, null, false);
  }

  public void save() {
    if (!active
        || uid == null
        || !Objects.equals(uid, auth.currentUid())
        || draft == null
        || !draft.isComplete()) return;
    if (state.getStage() != SessionState.Stage.FORM
        && !(state.getStage() == SessionState.Stage.PENDING_FAILURE && retry == Retry.SAVE)) return;
    final String requestedUid = uid;
    final Profile submitted = draft;
    final long g = generation, n = ++nonce;
    retry = Retry.SAVE;
    awaitingConfirmation = submitted;
    publish(SessionState.Stage.SAVING, null, false);
    profiles.save(
        requestedUid,
        submitted,
        failure -> {
          if (!valid(g, n, requestedUid)) return;
          ++nonce;
          if (failure != null) {
            publish(SessionState.Stage.PENDING_FAILURE, failure, false);
            return;
          }
          load(requestedUid, submitted);
        });
  }

  public void retry() {
    if (!active || state.getStage() != SessionState.Stage.PENDING_FAILURE) return;
    if (retry == Retry.SAVE) save();
    else if (retry == Retry.CONFIRM && uid != null && Objects.equals(uid, auth.currentUid()))
      load(uid, awaitingConfirmation);
    else if (uid != null && Objects.equals(uid, auth.currentUid())) load(uid, null);
  }

  private void reconcile(String next) {
    authAttempt = null;
    ++nonce;
    if (!Objects.equals(uid, next)) {
      if (uid != null && next != null) ownerObserver.onConfirmedOwner(null);
      uid = next;
      draft = null;
      dirty = false;
      cachedDraft = false;
      retry = null;
      awaitingConfirmation = null;
    }
    if (next == null) {
      publish(SessionState.Stage.LOGGED_OUT, null, false);
      return;
    }
    load(
        next,
        dirty && (retry == Retry.SAVE || retry == Retry.CONFIRM) ? awaitingConfirmation : null);
  }

  private void authFailure(String current, Failure failure) {
    uid = current;
    draft = null;
    dirty = false;
    cachedDraft = false;
    awaitingConfirmation = null;
    retry = Retry.LOAD;
    publish(SessionState.Stage.PENDING_FAILURE, failure, false);
  }

  private void load(String requestedUid, Profile submitted) {
    final long g = generation, n = ++nonce;
    retry = submitted == null ? Retry.LOAD : Retry.CONFIRM;
    awaitingConfirmation = submitted;
    publish(
        submitted == null ? SessionState.Stage.PROFILE_LOADING : SessionState.Stage.SAVING,
        null,
        false);
    profiles.load(
        requestedUid,
        (profile, source, failure) -> {
          if (!valid(g, n, requestedUid)) return;
          if (failure != null) {
            ++nonce;
            publish(SessionState.Stage.PENDING_FAILURE, failure, false);
            return;
          }
          if (source == ProfilePort.Source.CACHE) {
            if (submitted == null && !dirty && profile != null) {
              draft = profile;
              cachedDraft = true;
            }
            publish(
                submitted == null ? SessionState.Stage.PROFILE_LOADING : SessionState.Stage.SAVING,
                null,
                true);
            return;
          }
          if (source != ProfilePort.Source.SERVER) {
            ++nonce;
            publish(SessionState.Stage.PENDING_FAILURE, Failure.UNKNOWN, false);
            return;
          }
          ++nonce;
          cachedDraft = false;
          if (submitted != null) {
            if (profile == null || !profile.isComplete()) {
              retry = Retry.SAVE;
              publish(SessionState.Stage.PENDING_FAILURE, Failure.UNAVAILABLE, false);
              return;
            }
            draft = profile;
            dirty = false;
            retry = null;
            publish(SessionState.Stage.COMPLETE, null, false);
            return;
          }
          if (!dirty) draft = profile;
          if (!dirty && profile != null && profile.isComplete()) {
            retry = null;
            publish(SessionState.Stage.COMPLETE, null, false);
          } else {
            if (draft == null) draft = new Profile("", "", "");
            retry = null;
            publish(SessionState.Stage.FORM, null, false);
          }
        });
  }

  private boolean valid(long g, long n, String requestedUid) {
    if (!active || generation != g || nonce != n || !Objects.equals(uid, requestedUid)) return false;
    if (Objects.equals(auth.currentUid(), requestedUid)) return true;
    ownerObserver.onConfirmedOwner(null);
    return false;
  }

  private void publish(SessionState.Stage stage, Failure failure, boolean cached) {
    boolean editable =
        active
            && uid != null
            && (stage == SessionState.Stage.FORM
                || (stage == SessionState.Stage.PENDING_FAILURE && retry == Retry.SAVE));
    state =
        new SessionState(
            stage,
            uid,
            draft,
            failure,
            cached || cachedDraft,
            editable,
            editable && draft != null && draft.isComplete());
    switch (stage) {
      case COMPLETE:
        ownerObserver.onConfirmedOwner(uid);
        break;
      case UNCONFIGURED:
      case LOGGED_OUT:
      case FORM:
      case PENDING_FAILURE:
        ownerObserver.onConfirmedOwner(null);
        break;
      default:
        // Loading and cached refreshes leave the last server decision in place.
        break;
    }
    if (active && listener != null) listener.onState(state);
  }
}
