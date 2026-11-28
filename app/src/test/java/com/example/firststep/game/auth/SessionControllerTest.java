package com.example.firststep.game.auth;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SessionControllerTest {
  static final class Auth implements AuthPort {
    String uid;
    boolean configured = true;
    AuthObserver observer;
    Completion login, logout;
    int loginCount;
    boolean emitOnObserve;

    public boolean isConfigured() {
      return configured;
    }

    public String currentUid() {
      if (!configured) throw new AssertionError("unconfigured uid access");
      return uid;
    }

    public Subscription observe(AuthObserver value) {
      if (!configured) throw new AssertionError("unconfigured observer");
      observer = value;
      if (emitOnObserve) value.onChanged(uid);
      return () -> {};
    }

    public void signIn(boolean reselect, Completion callback) {
      login = callback;
      loginCount++;
    }

    public void signOut(Completion callback) {
      logout = callback;
    }

    void change(String value) {
      uid = value;
      observer.onChanged(value);
    }
  }

  static final class Store implements ProfilePort {
    final List<ProfileCallback> loads = new ArrayList<>();
    final List<AuthPort.Completion> saves = new ArrayList<>();

    public void load(String uid, ProfileCallback callback) {
      loads.add(callback);
    }

    public void save(String uid, Profile profile, AuthPort.Completion callback) {
      saves.add(callback);
    }

    void server(int i, Profile p) {
      loads.get(i).onResult(p, Source.SERVER, null);
    }
  }

  private static Profile profile(String name) {
    return new Profile(name, "001", "dept");
  }

  @Test
  public void validationPreservesLeadingZerosAndLimits() {
    Profile p = new Profile(" a ", " 001 ", " dept ");
    assertTrue(p.isComplete());
    assertEquals("001", p.getStudentNumber());
    assertFalse(new Profile("", "1", "d").isComplete());
    assertFalse(new Profile(new String(new char[81]).replace('\0', 'x'), "1", "d").isComplete());
    assertFalse(new Profile("a", new String(new char[41]).replace('\0', 'x'), "d").isComplete());
    assertFalse(new Profile("a", "1", new String(new char[121]).replace('\0', 'x')).isComplete());
  }

  @Test
  public void cacheAndSaveNeedMatchingServerConfirmation() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    p.loads.get(0).onResult(profile("cached"), ProfilePort.Source.CACHE, null);
    assertEquals(SessionState.Stage.PROFILE_LOADING, c.state().getStage());
    c.save();
    assertTrue(p.saves.isEmpty());
    p.server(0, null);
    assertEquals(SessionState.Stage.FORM, c.state().getStage());
    c.edit("new", "001", "dept");
    c.save();
    p.saves.get(0).onResult(null);
    p.server(1, new Profile("", "001", "dept"));
    assertEquals(SessionState.Stage.PENDING_FAILURE, c.state().getStage());
    c.retry();
    p.saves.get(1).onResult(null);
    p.server(2, profile("new"));
    assertEquals(SessionState.Stage.COMPLETE, c.state().getStage());
  }

  @Test
  public void switchAndPauseRejectLateCallbacksAndKeepSameUidDraft() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    p.server(0, null);
    c.edit("draft", "001", "dept");
    c.pause();
    final List<SessionState> received = new ArrayList<>();
    c.setListener(received::add);
    assertTrue(received.isEmpty());
    c.resume();
    p.server(1, null);
    assertEquals("draft", c.state().getDraft().getName());
    c.save();
    AuthPort.Completion oldSave = p.saves.get(0);
    a.change("B");
    assertNull(c.state().getDraft());
    oldSave.onResult(null);
    p.server(2, profile("B"));
    assertEquals("B", c.state().getDraft().getName());
    assertEquals(SessionState.Stage.COMPLETE, c.state().getStage());
  }

  @Test
  public void logoutInvalidatesLoginAndSaveAndLoadFailuresCannotOverwrite() {
    Auth a = new Auth();
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    c.signIn(false);
    AuthPort.Completion login = a.login;
    c.logout();
    a.uid = null;
    a.logout.onResult(null);
    a.uid = "A";
    login.onResult(null);
    assertEquals(SessionState.Stage.LOGGED_OUT, c.state().getStage());
    a.change("A");
    p.loads.get(0).onResult(null, null, Failure.PERMISSION_DENIED);
    c.edit("x", "1", "d");
    c.save();
    assertTrue(p.saves.isEmpty());
    c.retry();
    p.server(1, null);
    c.edit("x", "1", "d");
    c.save();
    AuthPort.Completion save = p.saves.get(0);
    c.logout();
    a.uid = null;
    a.logout.onResult(null);
    save.onResult(null);
    assertEquals(SessionState.Stage.LOGGED_OUT, c.state().getStage());
    assertNull(c.state().getDraft());
  }

  @Test
  public void saveFailurePreservesDraftAndRetryWritesAgain() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    p.server(0, new Profile("", "002", "dept"));
    c.edit("name", "002", "dept");
    c.save();
    p.saves.get(0).onResult(Failure.CANCELED);
    assertEquals(Failure.CANCELED, c.state().getFailure());
    assertEquals("002", c.state().getDraft().getStudentNumber());
    c.retry();
    assertEquals(2, p.saves.size());
  }

  @Test
  public void pausedSaveCanBeConfirmedOnResumeWithoutDiscardingDifferentDraft() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    p.server(0, null);
    c.edit("saved", "001", "dept");
    c.save();
    c.pause();
    c.resume();
    p.server(1, profile("saved"));
    assertEquals(SessionState.Stage.COMPLETE, c.state().getStage());
    c.logout();
    a.uid = null;
    a.logout.onResult(null);
    a.change("A");
    p.server(2, null);
    c.edit("pending", "001", "dept");
    c.save();
    c.pause();
    c.resume();
    p.server(3, new Profile("", "001", "dept"));
    assertEquals(SessionState.Stage.PENDING_FAILURE, c.state().getStage());
    assertEquals("pending", c.state().getDraft().getName());
  }

  @Test
  public void unconfiguredAndAuthenticationFailuresRemainVisible() {
    Auth a = new Auth();
    a.configured = false;
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    c.signIn(false);
    c.state();
    c.setListener(state -> {});
    assertNull(a.login);
    assertEquals(SessionState.Stage.UNCONFIGURED, c.state().getStage());
    a.configured = true;
    c.pause();
    c.resume();
    c.signIn(false);
    a.login.onResult(Failure.CANCELED);
    assertEquals(Failure.CANCELED, c.state().getFailure());
    a.uid = "A";
    a.observer.onChanged("A");
    p.server(0, profile("A"));
    c.logout();
    a.logout.onResult(Failure.UNAVAILABLE);
    assertEquals(Failure.UNAVAILABLE, c.state().getFailure());
    assertEquals(SessionState.Stage.PENDING_FAILURE, c.state().getStage());
    assertNull(c.state().getDraft());
    c.retry();
    p.loads.get(1).onResult(null, null, Failure.PERMISSION_DENIED);
    assertEquals(Failure.PERMISSION_DENIED, c.state().getFailure());
    c.retry();
    p.loads.get(2).onResult(null, null, Failure.UNAVAILABLE);
    assertEquals(Failure.UNAVAILABLE, c.state().getFailure());
  }

  @Test
  public void lateResultsAndOldObserverCannotReplaceNewAccount() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    AuthPort.AuthObserver oldObserver = a.observer;
    ProfilePort.ProfileCallback oldLoad = p.loads.get(0);
    c.pause();
    a.uid = "B";
    c.resume();
    oldObserver.onChanged("A");
    oldLoad.onResult(profile("A"), ProfilePort.Source.CACHE, null);
    oldLoad.onResult(profile("A"), ProfilePort.Source.SERVER, null);
    p.server(1, profile("B"));
    oldLoad.onResult(profile("A"), ProfilePort.Source.SERVER, null);
    assertEquals("B", c.state().getUid());
    assertEquals("B", c.state().getDraft().getName());
  }

  @Test
  public void cachedDraftRemainsMarkedDuringFailureAndRetry() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    p.loads.get(0).onResult(profile("cached"), ProfilePort.Source.CACHE, null);
    p.loads.get(0).onResult(null, null, Failure.UNAVAILABLE);
    assertEquals(SessionState.Stage.PENDING_FAILURE, c.state().getStage());
    assertTrue(c.state().isCached());
    assertEquals("A", c.state().getUid());
    c.retry();
    assertTrue(c.state().isCached());
    p.server(1, profile("server"));
    assertFalse(c.state().isCached());
    assertEquals(SessionState.Stage.COMPLETE, c.state().getStage());
  }

  @Test
  public void duplicateCanceledLoginCompletionIsIgnored() {
    Auth a = new Auth();
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    c.signIn(false);
    AuthPort.Completion old = a.login;
    old.onResult(Failure.CANCELED);
    assertEquals(Failure.CANCELED, c.state().getFailure());
    a.uid = "A";
    old.onResult(null);
    assertEquals(SessionState.Stage.PROFILE_LOADING, c.state().getStage());
    assertNull(c.state().getFailure());
    assertTrue(p.loads.isEmpty());
  }

  @Test
  public void failedSaveWhilePausedCanRetryAfterAuthoritativeMissingResult() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    p.server(0, null);
    c.edit("saved", "001", "dept");
    c.save();
    AuthPort.Completion old = p.saves.get(0);
    c.pause();
    old.onResult(Failure.UNAVAILABLE);
    c.resume();
    p.server(1, null);
    assertEquals(SessionState.Stage.PENDING_FAILURE, c.state().getStage());
    assertEquals("saved", c.state().getDraft().getName());
    c.retry();
    assertEquals(2, p.saves.size());
    p.saves.get(1).onResult(null);
    p.server(2, profile("saved"));
    assertEquals(SessionState.Stage.COMPLETE, c.state().getStage());
  }

  @Test
  public void availabilityFollowsConfirmedProfileAndRetryKind() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    assertFalse(c.state().canEdit());
    assertFalse(c.state().canSave());
    c.resume();
    p.loads.get(0).onResult(null, null, Failure.UNAVAILABLE);
    assertFalse(c.state().canEdit());
    assertFalse(c.state().canSave());
    c.retry();
    p.server(1, null);
    assertTrue(c.state().canEdit());
    assertFalse(c.state().canSave());
    c.edit("name", "001", "dept");
    assertTrue(c.state().canEdit());
    assertTrue(c.state().canSave());
    c.save();
    assertFalse(c.state().canEdit());
    assertFalse(c.state().canSave());
    p.saves.get(0).onResult(null);
    p.loads.get(2).onResult(null, null, Failure.UNAVAILABLE);
    assertFalse(c.state().canEdit());
    assertFalse(c.state().canSave());
    c.retry();
    p.server(3, null);
    assertTrue(c.state().canEdit());
    assertTrue(c.state().canSave());
    c.pause();
    assertFalse(c.state().canEdit());
    assertFalse(c.state().canSave());
  }

  @Test
  public void differentCompleteServerProfileDoesNotConfirmSave() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    p.server(0, null);
    c.edit("Typed", "001", "dept");
    c.save();
    p.saves.get(0).onResult(null);
    p.loads.get(1).onResult(profile("Server"), ProfilePort.Source.CACHE, null);
    assertEquals(SessionState.Stage.SAVING, c.state().getStage());
    p.server(1, profile("Server"));
    assertEquals(SessionState.Stage.PENDING_FAILURE, c.state().getStage());
    assertEquals("Typed", c.state().getDraft().getName());
    assertTrue(c.state().canSave());
    assertEquals(1, p.saves.size());
  }

  @Test
  public void explicitEditRetainsDraftAndRequiresExactServerConfirmation() {
    Auth a = new Auth(); a.uid = "A"; Store p = new Store();
    List<String> owners = new ArrayList<>();
    SessionController c = new SessionController(a, p, owners::add);
    c.resume(); c.beginEdit();
    assertEquals(SessionState.Stage.PROFILE_LOADING, c.state().getStage());
    p.server(0, profile("old"));
    c.beginEdit();
    assertEquals(SessionState.Stage.FORM, c.state().getStage());
    assertNull(owners.get(owners.size() - 1));
    c.edit("new", "00023", "new department");
    c.pause(); c.resume(); p.server(1, profile("old"));
    assertEquals("00023", c.state().getDraft().getStudentNumber());
    assertEquals(SessionState.Stage.FORM, c.state().getStage());
    c.save(); p.saves.get(0).onResult(Failure.UNAVAILABLE);
    assertEquals(SessionState.Stage.PENDING_FAILURE, c.state().getStage());
    assertNull(owners.get(owners.size() - 1));
    c.retry(); p.saves.get(1).onResult(null);
    p.loads.get(2).onResult(new Profile("new", "00023", "new department"), ProfilePort.Source.CACHE, null);
    assertEquals(SessionState.Stage.SAVING, c.state().getStage());
    p.server(2, profile("old"));
    assertEquals(SessionState.Stage.PENDING_FAILURE, c.state().getStage());
    assertNull(owners.get(owners.size() - 1));
    c.retry(); p.saves.get(2).onResult(null);
    p.server(3, new Profile("new", "00023", "new department"));
    assertEquals(SessionState.Stage.COMPLETE, c.state().getStage());
    assertEquals("A", owners.get(owners.size() - 1));
  }

  @Test
  public void editCannotCrossAccountsOrAcceptLateConfirmation() {
    Auth a = new Auth(); a.uid = "A"; Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume(); p.server(0, profile("old")); c.beginEdit(); c.edit("new", "001", "dept");
    c.save(); p.saves.get(0).onResult(null); a.change("B");
    p.server(1, profile("new"));
    assertEquals("B", c.state().getUid());
    assertNull(c.state().getDraft());
    p.server(2, profile("B"));
    assertEquals("B", c.state().getDraft().getName());
  }

  @Test
  public void supplementaryCharactersRespectUtf16FieldBounds() {
    String emoji = "\uD83D\uDE00";
    StringBuilder chars = new StringBuilder();
    for (int i = 0; i < 60; i++) chars.append(emoji);
    String name80 = chars.substring(0, 40 * emoji.length());
    String student40 = chars.substring(0, 20 * emoji.length());
    String department120 = chars.toString();
    assertTrue(new Profile(name80, student40, department120).isComplete());
    assertFalse(new Profile(name80 + emoji, student40, department120).isComplete());
    assertFalse(new Profile(name80, student40 + emoji, department120).isComplete());
    assertFalse(new Profile(name80, student40, department120 + emoji).isComplete());
  }

  @Test
  public void pausedAuthFailuresSurviveResumeAndSynchronousNullObservation() {
    for (Failure failure :
        new Failure[] {Failure.CANCELED, Failure.NO_CREDENTIAL, Failure.UNAVAILABLE}) {
      Auth a = new Auth();
      a.emitOnObserve = true;
      SessionController c = new SessionController(a, new Store());
      c.resume();
      c.signIn(false);
      c.pause();
      a.login.onResult(failure);
      c.resume();
      assertEquals(SessionState.Stage.LOGGED_OUT, c.state().getStage());
      assertEquals(failure, c.state().getFailure());
      c.pause();
      c.resume();
      assertEquals(failure, c.state().getFailure());
    }
  }

  @Test
  public void pendingPickerSurvivesPauseAndCannotStartDuplicate() {
    Auth a = new Auth();
    a.emitOnObserve = true;
    SessionController c = new SessionController(a, new Store());
    c.resume();
    c.signIn(false);
    c.pause();
    c.resume();
    assertEquals(SessionState.Stage.AUTH_BUSY, c.state().getStage());
    c.signIn(false);
    assertEquals(1, a.loginCount);
    a.login.onResult(Failure.CANCELED);
    assertEquals(Failure.CANCELED, c.state().getFailure());
    c.pause();
    c.resume();
    assertEquals(Failure.CANCELED, c.state().getFailure());
  }

  @Test
  public void observedUidBeforeCompletionWinsAndStaleFailureCannotReload() {
    Auth a = new Auth();
    a.emitOnObserve = true;
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    c.signIn(false);
    AuthPort.Completion old = a.login;
    a.change("A");
    assertEquals(1, p.loads.size());
    a.change("B");
    assertEquals(2, p.loads.size());
    old.onResult(Failure.UNAVAILABLE);
    assertEquals(2, p.loads.size());
    p.server(1, profile("B"));
    assertEquals("B", c.state().getUid());
    assertEquals("B", c.state().getDraft().getName());
  }

  @Test
  public void deferredFailureLosesToCurrentAccountAndLogoutOrNewAttempt() {
    Auth a = new Auth();
    a.emitOnObserve = true;
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    c.signIn(false);
    AuthPort.Completion old = a.login;
    c.pause();
    old.onResult(Failure.NO_CREDENTIAL);
    a.uid = "B";
    c.resume();
    p.server(0, profile("B"));
    assertEquals("B", c.state().getUid());
    assertNull(c.state().getFailure());
    c.logout();
    a.uid = null;
    a.logout.onResult(null);
    c.signIn(false);
    old.onResult(Failure.UNAVAILABLE);
    assertEquals(SessionState.Stage.AUTH_BUSY, c.state().getStage());
    a.login.onResult(Failure.CANCELED);
    c.logout();
    a.logout.onResult(null);
    c.pause();
    c.resume();
    assertNull(c.state().getFailure());
  }

  @Test
  public void successfulLoginAfterResumeLoadsCurrentUidOnce() {
    Auth a = new Auth();
    a.emitOnObserve = true;
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    c.signIn(false);
    c.pause();
    c.resume();
    a.change("A");
    a.login.onResult(null);
    assertEquals(1, p.loads.size());
    p.server(0, profile("A"));
    assertEquals(SessionState.Stage.COMPLETE, c.state().getStage());
    assertEquals("A", c.state().getUid());
  }

  @Test
  public void supersededCompletionWaitsForMatchingObserverToLoadLatestAccount() {
    Auth a = new Auth();
    a.emitOnObserve = true;
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    c.signIn(false);
    AuthPort.Completion old = a.login;
    a.change("A");
    assertEquals(1, p.loads.size());
    a.uid = "B";
    old.onResult(Failure.UNAVAILABLE);
    assertEquals(1, p.loads.size());
    a.observer.onChanged("B");
    assertEquals(2, p.loads.size());
    p.server(1, profile("B"));
    assertEquals("B", c.state().getUid());
    assertEquals("B", c.state().getDraft().getName());
  }

  @Test
  public void listenerReconciliationClearsLoggedOutFailureBeforeLaterSignOut() {
    Auth a = new Auth();
    Store p = new Store();
    SessionController c = new SessionController(a, p);
    c.resume();
    c.signIn(false);
    a.login.onResult(Failure.NO_CREDENTIAL);
    a.uid = "A";
    c.setListener(state -> {});
    a.change("A");
    p.server(0, profile("A"));
    assertEquals(SessionState.Stage.COMPLETE, c.state().getStage());
    c.pause();
    a.uid = null;
    c.resume();
    assertEquals(SessionState.Stage.LOGGED_OUT, c.state().getStage());
    assertNull(c.state().getUid());
    assertNull(c.state().getDraft());
    assertNull(c.state().getFailure());
  }

  @Test
  public void ownerRequiresServerConfirmationAfterFirstSave() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    List<String> owners = new ArrayList<>();
    SessionController c = new SessionController(a, p, owners::add);
    c.resume();
    p.loads.get(0).onResult(profile("cached"), ProfilePort.Source.CACHE, null);
    assertTrue(owners.isEmpty());
    p.server(0, null);
    c.edit("name", "001", "dept");
    c.save();
    p.saves.get(0).onResult(null);
    p.loads.get(1).onResult(profile("cached"), ProfilePort.Source.CACHE, null);
    assertFalse(owners.contains("A"));
    p.server(1, profile("name"));
    assertEquals("A", owners.get(owners.size() - 1));
    assertEquals(SessionState.Stage.COMPLETE, c.state().getStage());
  }

  @Test
  public void navigationRetainsOwnerUntilServerConfirmationFails() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    List<String> owners = new ArrayList<>();
    SessionController c = new SessionController(a, p, owners::add);
    c.resume();
    p.server(0, profile("A"));
    c.pause();
    c.resume();
    p.loads.get(1).onResult(profile("cached"), ProfilePort.Source.CACHE, null);
    assertEquals(List.of("A"), owners);
    p.loads.get(1).onResult(null, ProfilePort.Source.SERVER, Failure.UNAVAILABLE);
    assertNull(owners.get(1));
    c.retry();
    p.server(2, profile("A"));
    assertEquals("A", owners.get(2));
    c.pause();
    a.uid = null;
    c.resume();
    assertNull(owners.get(3));
  }

  @Test
  public void reselectionAndLogoutRevokeBeforeSameUidCanBeConfirmedAgain() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    List<String> owners = new ArrayList<>();
    SessionController c = new SessionController(a, p, owners::add);
    c.resume();
    p.server(0, profile("A"));
    c.signIn(true);
    assertNull(owners.get(1));
    assertNotNull(a.login);
    a.login.onResult(null);
    assertEquals(2, owners.size());
    p.server(1, profile("A"));
    assertEquals("A", owners.get(2));
    c.logout();
    assertNull(owners.get(3));
    assertNotNull(a.logout);
  }

  @Test
  public void mismatchingUidRevokesBeforeReplacementAndStaleLoadCannotConfirm() {
    Auth a = new Auth();
    a.uid = "A";
    Store p = new Store();
    List<String> owners = new ArrayList<>();
    SessionController c = new SessionController(a, p, owners::add);
    c.resume();
    p.server(0, profile("A"));
    c.pause();
    c.resume();
    a.uid = "B";
    p.server(1, profile("A before auth observation"));
    assertNull(owners.get(1));
    assertEquals(SessionState.Stage.PROFILE_LOADING, c.state().getStage());
    a.change("B");
    int events = owners.size();
    p.server(1, profile("late A"));
    assertEquals(events, owners.size());
    p.server(2, profile("B"));
    assertEquals("B", owners.get(events));
    p.server(1, profile("A after B confirmation"));
    assertEquals(events + 1, owners.size());
  }
}
