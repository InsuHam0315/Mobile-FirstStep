package com.example.firststep.game.presentation.home.account;

import static org.junit.Assert.*;

import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.presentation.home.account.AccountHomeController.Reading;
import com.example.firststep.game.presentation.home.account.AccountHomeState.Server;
import com.example.firststep.game.presentation.home.account.AccountHomeState.Status;
import com.example.firststep.game.sync.Milestone;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.Test;

public final class AccountHomeControllerTest {
  private static final String TARGET = "software";
  private static final long INTRO = 10_000L;
  private static final long DUE = INTRO + ProgressService.WAIT_MILLIS;
  private static final List<String> FAVORITES = Collections.singletonList("Library");

  private final Queue<Runnable> worker = new ArrayDeque<>();
  private final Queue<Runnable> main = new ArrayDeque<>();
  private long now = INTRO;
  private boolean ownerCurrent = true;
  private AccountHomeState visible;
  private int openings;
  private Reading next;

  @Test public void closedRegistrationStillShowsDeviceFavoritesAndSafeEntry() {
    next = Reading.blocked(Status.UNAVAILABLE, FAVORITES, true);
    AccountHomeController home = controller(false);
    home.resume();
    deliver();
    assertEquals(Status.UNAVAILABLE, visible.status);
    assertEquals(FAVORITES, visible.favorites);
    home.openAuthored();
    home.openAuthored();
    assertEquals(1, openings);
  }

  @Test public void reloadUsesPersistedIntroAndTicksAcrossExactEligibilityBoundary() {
    AccountHomeController home = controller(true);
    next = read(Progress.empty(Progress.Mode.NORMAL, TARGET), null);
    home.resume();
    assertEquals(Status.LOADING, visible.status);
    deliver();
    assertEquals(Status.UNSTARTED, visible.status);

    home.pause();
    next = read(intro(), null);
    now = DUE - 1;
    home.resume();
    deliver();
    assertEquals(Status.WAITING, visible.status);
    assertEquals(1, visible.remainingMillis);
    now = DUE;
    home.tick();
    assertEquals(Status.READY, visible.status);
    assertEquals(0, visible.remainingMillis);
    home.openAuthored();
    assertEquals(1, openings);

    home.pause();
    next = read(completed(), null);
    home.resume();
    deliver();
    assertEquals(Status.COMPLETE, visible.status);
  }

  @Test public void serverOnlyPassDoesNotMakeLocalQuizOrStampAvailable() {
    next = read(Progress.empty(Progress.Mode.NORMAL, TARGET),
        new Milestone(INTRO, new Milestone.Completion("attempt", "version", 2, DUE)));
    AccountHomeController home = controller(true);
    home.resume();
    deliver();
    assertEquals(Status.UNSTARTED, visible.status);
    assertEquals(Server.COMPLETE, visible.server);
  }

  @Test public void ownerChangeBeforePublicationOrNavigationClearsProgress() {
    next = read(completed(), new Milestone(INTRO, null));
    AccountHomeController home = controller(true);
    home.resume();
    worker.remove().run();
    ownerCurrent = false;
    main.remove().run();
    assertEquals(Status.NO_ACCOUNT, visible.status);
    assertEquals(FAVORITES, visible.favorites);
    assertEquals(Server.NONE, visible.server);

    ownerCurrent = true;
    home.refresh();
    deliver();
    assertEquals(Status.COMPLETE, visible.status);
    ownerCurrent = false;
    home.openAuthored();
    assertEquals(0, openings);
    assertEquals(Status.NO_ACCOUNT, visible.status);
  }

  @Test public void pausedScreenDiscardsQueuedResponseAndCanRetryFailedLoad() {
    AccountHomeController home = controller(true);
    next = read(completed(), null);
    home.resume();
    worker.remove().run();
    home.pause();
    main.remove().run();
    assertEquals(Status.LOADING, visible.status);
    home.openAuthored();
    assertEquals(0, openings);

    next = null;
    home.resume();
    deliver();
    assertEquals(Status.FAILED, visible.status);
    next = read(intro(), null);
    home.refresh();
    deliver();
    assertEquals(Status.WAITING, visible.status);
  }

  @Test public void demoCannotEnterAccountProjection() {
    assertThrows(IllegalArgumentException.class,
        () -> read(Progress.empty(Progress.Mode.DEMO, TARGET), null));
  }

  private AccountHomeController controller(boolean registered) {
    return new AccountHomeController(() -> next, registered, () -> now, worker::add, main::add,
        state -> visible = state, () -> openings++);
  }

  private void deliver() {
    worker.remove().run();
    main.remove().run();
  }

  private Reading read(Progress progress, Milestone confirmed) {
    return Reading.progress(progress, confirmed, action -> {
      if (!ownerCurrent) return false;
      action.run();
      return true;
    }, FAVORITES, true);
  }

  private static Progress intro() {
    return new Progress(Progress.Mode.NORMAL, TARGET, INTRO, false, false, null,
        Collections.emptyMap());
  }

  private static Progress completed() {
    Map<String, String> answers = new HashMap<>();
    answers.put("q1", "a1");
    answers.put("q2", "a2");
    answers.put("q3", "a3");
    Progress.Result result = new Progress.Result("attempt", "version", answers, 2);
    return new Progress(Progress.Mode.NORMAL, TARGET, INTRO, false, true, null,
        Collections.singletonMap(result.attemptId, result));
  }
}
