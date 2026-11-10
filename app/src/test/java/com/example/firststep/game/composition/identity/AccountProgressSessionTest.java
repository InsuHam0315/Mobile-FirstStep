package com.example.firststep.game.composition.identity;

import static org.junit.Assert.*;

import com.example.firststep.game.auth.AuthPort;
import com.example.firststep.game.auth.Profile;
import com.example.firststep.game.auth.ProfilePort;
import com.example.firststep.game.auth.SessionController;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.Milestone;
import com.example.firststep.game.sync.OutboxState;
import com.example.firststep.game.sync.SessionOwner.StaleSession;
import com.example.firststep.game.sync.SyncBackend;
import com.example.firststep.game.sync.SyncCoordinator;
import com.example.firststep.game.sync.SyncScope;
import com.example.firststep.game.sync.SyncStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;
import org.junit.Test;

public final class AccountProgressSessionTest {
  private static final Profile COMPLETE = new Profile("Test Name", "00123", "Software");

  private static final class FakeAuth implements AuthPort {
    final Set<AuthObserver> observers = new LinkedHashSet<>();
    String uid = "user-a";
    String sdkUid = uid;
    Runnable beforeMutation = () -> {};

    @Override
    public boolean isConfigured() {
      return true;
    }

    @Override
    public String currentUid() {
      return uid;
    }

    @Override
    public Subscription observe(AuthObserver observer) {
      observers.add(observer);
      observer.onChanged(uid);
      return () -> observers.remove(observer);
    }

    @Override
    public void signIn(boolean reselect, Completion callback) {
      beforeMutation.run();
      changeTo(null);
      changeTo("user-a");
      callback.onResult(null);
    }

    @Override
    public void signOut(Completion callback) {
      beforeMutation.run();
      changeTo(null);
      callback.onResult(null);
    }

    void changeTo(String value) {
      uid = value;
      sdkUid = value;
      for (AuthObserver observer : new ArrayList<>(observers)) observer.onChanged(uid);
    }
  }

  private static final class FakeProfiles implements ProfilePort {
    final List<ProfileCallback> reads = new ArrayList<>();

    @Override
    public void load(String uid, ProfileCallback callback) {
      reads.add(callback);
    }

    @Override
    public void save(String uid, Profile profile, AuthPort.Completion callback) {
      throw new AssertionError("Unexpected profile save");
    }

    void latest(Source source) {
      reads.get(reads.size() - 1).onResult(COMPLETE, source, null);
    }
  }

  private final FakeAuth auth = new FakeAuth();
  private final FakeProfiles profiles = new FakeProfiles();
  private final AccountProgressSession session =
      new AccountProgressSession("test-project", auth, () -> auth.sdkUid);
  private final SessionController controller =
      new SessionController(auth, profiles, session::onConfirmedOwner);

  private SyncScope capture() {
    return session.capture("authored-course", "building-a");
  }

  @Test
  public void serverConfirmationSurvivesPauseAndSameAccountRefresh() {
    controller.resume();
    profiles.latest(ProfilePort.Source.CACHE);
    assertThrows(StaleSession.class, this::capture);
    profiles.latest(ProfilePort.Source.SERVER);
    SyncScope first = capture();
    assertEquals("test-project", first.projectId);
    assertEquals("user-a", first.uid);
    assertEquals(2, auth.observers.size());

    controller.pause();
    assertEquals(1, auth.observers.size());
    assertEquals("current", session.whileCurrent(first, () -> "current"));
    controller.resume();
    profiles.latest(ProfilePort.Source.SERVER);
    assertEquals(first.sessionGeneration, capture().sessionGeneration);
  }

  @Test
  public void pausedControllerCannotRestorePreviousOwnerAfterAccountSwitch() {
    controller.resume();
    profiles.latest(ProfilePort.Source.SERVER);
    SyncScope first = capture();
    ProfilePort.ProfileCallback old = profiles.reads.get(0);
    controller.pause();

    auth.changeTo("user-b");
    assertThrows(StaleSession.class, this::capture);
    assertThrows(StaleSession.class, () -> session.whileCurrent(first, () -> null));
    controller.resume();
    old.onResult(COMPLETE, ProfilePort.Source.SERVER, null);
    assertThrows(StaleSession.class, this::capture);
    profiles.latest(ProfilePort.Source.SERVER);
    assertEquals("user-b", capture().uid);
    assertThrows(StaleSession.class, () -> session.whileCurrent(first, () -> null));
  }

  @Test
  public void explicitReselectionAndLogoutRevokeBeforeAuthMutation() {
    controller.resume();
    profiles.latest(ProfilePort.Source.SERVER);
    SyncScope first = capture();
    auth.beforeMutation = () -> assertThrows(StaleSession.class, this::capture);

    controller.signIn(true);
    assertThrows(StaleSession.class, this::capture);
    profiles.latest(ProfilePort.Source.SERVER);
    SyncScope reauthenticated = capture();
    assertEquals(first.uid, reauthenticated.uid);
    assertNotEquals(first.sessionGeneration, reauthenticated.sessionGeneration);

    controller.logout();
    assertThrows(StaleSession.class, this::capture);
  }

  @Test
  public void queuedAuthNotificationDoesNotAdmitOldOwnerOrStaleConfirmation() {
    controller.resume();
    profiles.latest(ProfilePort.Source.SERVER);
    SyncScope first = capture();
    auth.sdkUid = "user-b";
    session.onConfirmedOwner("user-a");
    assertThrows(
        StaleSession.class,
        () ->
            session.whileCurrent(first, () -> {
              throw new AssertionError("Stale work was admitted");
            }));
    auth.changeTo("user-b");
    profiles.latest(ProfilePort.Source.SERVER);
    SyncScope next = capture();

    session.onConfirmedOwner("user-a");
    assertEquals(next.sessionGeneration, capture().sessionGeneration);
  }

  @Test
  public void closedRegistryRejectsCaptureAndReconciliationBeforeWork() {
    DatasetRegistry registry = DatasetRegistry.production();
    Executor noWork = command -> {
      throw new AssertionError("Unexpected worker task");
    };
    SyncCoordinator sync =
        new SyncCoordinator(registry, session, unusedStore(), unusedBackend(), noWork);
    AccountProgressAccess access = new AccountProgressAccess(registry, session, sync, noWork);
    controller.resume();
    profiles.latest(ProfilePort.Source.SERVER);
    SyncScope internal = capture();

    assertThrows(
        IllegalArgumentException.class, () -> access.capture("authored-course", "building-a"));
    assertThrows(
        IllegalArgumentException.class,
        () -> access.reconciler(scope -> {
          throw new AssertionError("Unexpected source read");
        }).reconcile(internal));
    assertThrows(IllegalArgumentException.class, () -> access.flush(internal));
    assertFalse(new UnconfiguredRuntime().accountProgress().isPresent());
  }

  private static SyncStore unusedStore() {
    return new SyncStore() {
      public OutboxState read(SyncScope scope) {
        throw new AssertionError("Unexpected local read");
      }

      public OutboxState update(SyncScope scope, UnaryOperator<OutboxState> change) {
        throw new AssertionError("Unexpected local update");
      }
    };
  }

  private static SyncBackend unusedBackend() {
    return new SyncBackend() {
      public CompletableFuture<Milestone> read(SyncScope scope) {
        throw new AssertionError("Unexpected remote read");
      }

      public CompletableFuture<Milestone> merge(SyncScope scope, Milestone milestone) {
        throw new AssertionError("Unexpected remote merge");
      }
    };
  }
}
