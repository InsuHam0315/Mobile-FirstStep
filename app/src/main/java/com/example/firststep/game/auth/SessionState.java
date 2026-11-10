package com.example.firststep.game.auth;

public final class SessionState {
  public enum Stage {
    UNCONFIGURED,
    LOGGED_OUT,
    AUTH_BUSY,
    PROFILE_LOADING,
    FORM,
    SAVING,
    PENDING_FAILURE,
    COMPLETE
  }

  private final Stage stage;
  private final String uid;
  private final Profile draft;
  private final Failure failure;
  private final boolean cached;
  private final boolean canEdit;
  private final boolean canSave;

  SessionState(Stage stage, String uid, Profile draft, Failure failure, boolean cached) {
    this(stage, uid, draft, failure, cached, false, false);
  }

  SessionState(
      Stage stage,
      String uid,
      Profile draft,
      Failure failure,
      boolean cached,
      boolean canEdit,
      boolean canSave) {
    this.stage = stage;
    this.uid = uid;
    this.draft = draft;
    this.failure = failure;
    this.cached = cached;
    this.canEdit = canEdit;
    this.canSave = canSave;
  }

  public Stage getStage() {
    return stage;
  }

  public String getUid() {
    return uid;
  }

  public Profile getDraft() {
    return draft;
  }

  public Failure getFailure() {
    return failure;
  }

  public boolean canEdit() {
    return canEdit;
  }

  public boolean canSave() {
    return canSave;
  }

  public boolean isCached() {
    return cached;
  }
}
