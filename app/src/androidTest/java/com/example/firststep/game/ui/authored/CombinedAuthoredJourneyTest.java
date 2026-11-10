package com.example.firststep.game.ui.authored;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.firststep.R;
import com.example.firststep.game.composition.authored.AuthoredEpisodeHost;
import com.example.firststep.game.composition.authored.AuthoredRouteTestFixture;
import com.example.firststep.game.composition.home.CampusHomeRoute;
import com.example.firststep.game.composition.home.account.AccountHomeTestRuntime;
import com.example.firststep.game.device.authored.ArrivalPolicy;
import com.example.firststep.game.device.authored.ForegroundArrival;
import com.example.firststep.game.device.authored.NormalDiscoverySession;
import com.example.firststep.game.domain.GameContent;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.presentation.discovery.DiscoveryController;
import com.example.firststep.game.presentation.discovery.DiscoveryState;
import com.example.firststep.game.sync.Milestone;
import com.example.firststep.game.sync.OutboxState;
import com.example.firststep.game.sync.SyncScope;
import com.example.firststep.game.ui.home.CampusHomeActivity;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CombinedAuthoredJourneyTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();
  private final AuthoredRouteTestFixture fixture = new AuthoredRouteTestFixture();
  private final AtomicLong elapsed = new AtomicLong(1000L);
  private final AtomicReference<NormalDiscoverySession> discovery = new AtomicReference<>();

  @Test
  public void homeReentryQuizAndExplicitSyncUseOneSyntheticAccount() throws Exception {
    fixture.clearRemoteFixture();
    fixture.atTarget = true;
    SyncScope original = fixture.capture();
    try (AutoCloseable homeLease = AccountHomeTestRuntime.install(
             fixture::homeReading, true, fixture.now::get);
         AutoCloseable episodeLease = AuthoredEpisodeActivity.installDebugRuntime(runtime());
         ActivityScenario<CampusHomeActivity> home =
             ActivityScenario.launch(CampusHomeRoute.intent(context));
         AutoCloseable episodeCleanup = trackEpisodes()) {
      homeState(R.string.game_campus_progress_unstarted);
      click(CampusHomeActivity.class, R.id.game_campus_home_authored);
      episodeState(R.string.game_authored_arrival_confirmed);
      assertTrue(episode(a -> a.findViewById(R.id.game_authored_synthetic_runtime).isShown()));

      episodeButton(R.string.game_authored_discovery_start);
      await("synthetic discovery input", () -> discovery.get() != null);
      episode(a -> {
        NormalDiscoverySession session = discovery.get();
        shake(session, 180);
        assertEquals(DiscoveryState.HeadingStatus.MISALIGNED, session.snapshot().heading);
        assertEquals(0, session.snapshot().shakeCount);
        assertFalse(session.snapshot().completed);
        session.heading(90, true);
        assertEquals(DiscoveryState.HeadingStatus.ALIGNED, session.snapshot().heading);
        shake(session, 90);
        assertTrue(session.snapshot().completed);
        return true;
      });
      episodeState(R.string.game_authored_intro);
      episodeButton(R.string.game_authored_skip_intro);
      episodeState(R.string.game_authored_wait);
      long introduced = fixture.localProgress().introDoneAt;
      assertEquals(fixture.now.get(), introduced);
      assertEquals(0, fixture.remoteReads.get());
      assertEquals(0, fixture.remoteWrites.get());

      fixture.atTarget = false;
      click(AuthoredEpisodeActivity.class, R.id.game_authored_back);
      homeState(R.string.game_campus_progress_waiting, 15, 0);
      fixture.now.set(introduced + ProgressService.WAIT_MILLIS - 1);
      homeState(R.string.game_campus_progress_waiting, 0, 1);
      click(CampusHomeActivity.class, R.id.game_campus_home_authored);
      episodeState(R.string.game_authored_wait);
      assertFalse(episode(a -> findButton(a, context.getString(R.string.game_authored_start_quiz)) != null));
      assertEquals(2, fixture.opens.get());
      assertEquals(original.sessionGeneration, fixture.capture().sessionGeneration);
      assertEquals(Long.valueOf(introduced), fixture.localProgress().introDoneAt);

      fixture.now.incrementAndGet();
      episodeState(R.string.game_authored_quiz_ready);
      episodeButton(R.string.game_authored_start_quiz);
      episodeState(R.string.game_authored_quiz);
      String failed = answer(0);
      episodeButton(R.string.game_authored_submit);
      episodeState(R.string.game_authored_failed, 0);
      assertFalse(fixture.localProgress().stamp);
      assertFalse(fixture.localProgress().history.get(failed).passed);

      episodeButton(R.string.game_authored_retry_quiz);
      episodeState(R.string.game_authored_quiz);
      String passed = answer(2);
      assertNotEquals(failed, passed);
      episodeButton(R.string.game_authored_submit);
      episodeState(R.string.game_authored_saved, 2);
      Progress committed = fixture.localProgress();
      assertTrue(committed.stamp);
      assertEquals(2, committed.history.size());
      assertEquals(2, committed.history.get(passed).correct);
      int localWrites = fixture.localWrites.get();
      assertEquals(0, fixture.remoteWrites.get());

      click(AuthoredEpisodeActivity.class, R.id.game_authored_sync_retry);
      remotePass(2);
      assertEquals(1, fixture.remoteWrites.get());
      assertEquals(0, fixture.remoteReads.get());
      OutboxState sent = fixture.outbox(original.uid);
      assertEquals(OutboxState.Status.CONFIRMED, sent.status);
      assertEquals(passed, sent.confirmed.completion.attemptId);

      click(AuthoredEpisodeActivity.class, R.id.game_authored_sync_read);
      await("explicit read", () -> fixture.remoteReads.get() == 1);
      awaitButton(AuthoredEpisodeActivity.class, R.id.game_authored_sync_read);
      remotePass(2);
      assertEquals(localWrites, fixture.localWrites.get());
      assertSame(committed, fixture.localProgress());
      click(AuthoredEpisodeActivity.class, R.id.game_authored_back);
      homeState(R.string.game_campus_progress_complete);
      text(CampusHomeActivity.class, R.id.game_campus_progress_remote,
          R.string.game_campus_progress_remote_complete);

      click(CampusHomeActivity.class, R.id.game_campus_home_authored);
      episodeState(R.string.game_authored_saved, 2);
      CompletableFuture<Milestone> oldRead = fixture.deferRemoteRead();
      try {
        click(AuthoredEpisodeActivity.class, R.id.game_authored_sync_read);
        await("in-flight read", () -> fixture.remoteReads.get() == 2);
        fixture.switchOwner("synthetic-user-b");
        // An earlier intro would change A's projection if this stale response were applied.
        oldRead.complete(new Milestone(introduced - 1, null));
        episodeState(R.string.game_authored_no_account);
        assertEquals("", episode(a -> readText(a, R.id.game_authored_remote)));
        assertEquals(introduced, fixture.outbox(original.uid).confirmed.introDoneAtMillis);
        assertEquals(OutboxState.Status.EMPTY, fixture.outbox("synthetic-user-b").status);
        assertEquals(localWrites, fixture.localWrites.get());
        click(AuthoredEpisodeActivity.class, R.id.game_authored_back);
        homeState(R.string.game_campus_progress_unstarted);
        assertTrue(onResumed(CampusHomeActivity.class,
            a -> !a.findViewById(R.id.game_campus_progress_remote).isShown()));
      } finally {
        oldRead.cancel(false);
      }
    }
  }

  private AutoCloseable trackEpisodes() throws Exception {
    Set<Activity> episodes = new LinkedHashSet<>();
    ActivityLifecycleCallback callback = (activity, stage) -> {
      if (activity instanceof AuthoredEpisodeActivity) {
        if (stage == Stage.PRE_ON_CREATE) episodes.add(activity);
        if (stage == Stage.DESTROYED) episodes.remove(activity);
      }
    };
    onMain(() -> {
      ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback);
      return true;
    });
    return () -> {
      onMain(() -> {
        ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback);
        for (Activity episode : episodes) {
          if (!episode.isFinishing() && !episode.isDestroyed()) episode.finish();
        }
        return true;
      });
      instrumentation.waitForIdleSync();
    };
  }

  private AuthoredEpisodeActivity.DebugRuntime runtime() {
    return new AuthoredEpisodeActivity.DebugRuntime() {
      @Override public AuthoredEpisodeHost.OpenResult open(Context c, SyncScope expected) {
        return fixture.open(c, expected);
      }
      @Override public DiscoveryController.Clock discoveryClock() { return elapsed::get; }
      @Override public AuthoredEpisodeActivity.ArrivalInput arrival(ForegroundArrival.Listener listener) {
        return new AuthoredEpisodeActivity.ArrivalInput() {
          @Override public void start() {
            listener.onState(ForegroundArrival.State.valueOf(currentVerdict().name()));
          }
          @Override public void retry() { start(); }
          @Override public void stop() {}
          @Override public ArrivalPolicy.Verdict currentVerdict() { return fixture.arrivalVerdict(); }
        };
      }
      @Override public AuthoredEpisodeActivity.DiscoveryInput discovery(NormalDiscoverySession session) {
        return new AuthoredEpisodeActivity.DiscoveryInput() {
          @Override public void start() { session.camera(DiscoveryState.CameraStatus.READY); discovery.set(session); }
          @Override public void close() { discovery.compareAndSet(session, null); }
        };
      }
    };
  }

  private void shake(NormalDiscoverySession session, float degrees) {
    for (int pulse = 0; pulse < 5; pulse++) {
      elapsed.addAndGet(200);
      session.heading(degrees, true);
      session.acceleration(23);
      session.acceleration(9.8f);
    }
  }

  private String answer(int correctCount) throws Exception {
    Progress.Attempt attempt = fixture.localProgress().active;
    assertNotNull(attempt);
    episode(a -> {
      ViewGroup body = a.findViewById(R.id.game_authored_body);
      int questionIndex = 0;
      for (int i = 0; i < body.getChildCount(); i++) {
        if (!(body.getChildAt(i) instanceof RadioGroup)) continue;
        RadioGroup choices = (RadioGroup) body.getChildAt(i);
        GameContent.Question question = attempt.questions.get(questionIndex);
        boolean correct = questionIndex++ < correctCount;
        GameContent.Option option = question.options.stream()
            .filter(candidate -> question.correctOptionId.equals(candidate.id) == correct)
            .findFirst().orElseThrow(AssertionError::new);
        boolean clicked = false;
        for (int j = 0; j < choices.getChildCount(); j++) {
          RadioButton button = (RadioButton) choices.getChildAt(j);
          if (option.text.contentEquals(button.getText())) clicked = button.performClick();
        }
        assertTrue(clicked);
      }
      assertEquals(attempt.questions.size(), questionIndex);
      return true;
    });
    return attempt.id;
  }

  private void episodeState(int string, Object... arguments) throws Exception {
    text(AuthoredEpisodeActivity.class, R.id.game_authored_state, string, arguments);
  }

  private void homeState(int string, Object... arguments) throws Exception {
    text(CampusHomeActivity.class, R.id.game_campus_progress_state, string, arguments);
  }

  private void remotePass(int correct) throws Exception {
    text(AuthoredEpisodeActivity.class, R.id.game_authored_remote,
        R.string.game_authored_sync_pass_confirmed, correct);
  }

  private <A extends Activity> void text(Class<A> activity, int view, int string, Object... args)
      throws Exception {
    String expected = context.getString(string, args);
    await("text " + expected, () -> expected.equals(onResumed(activity, a -> readText(a, view))));
  }

  private static String readText(Activity activity, int id) {
    return ((TextView) activity.findViewById(id)).getText().toString();
  }

  private void episodeButton(int string) throws Exception {
    String label = context.getString(string);
    await("button " + label, () -> Boolean.TRUE.equals(onResumed(AuthoredEpisodeActivity.class, a -> {
      Button view = findButton(a, label);
      return clickable(view) && view.performClick();
    })));
  }

  private static Button findButton(Activity activity, String label) {
    ViewGroup body = activity.findViewById(R.id.game_authored_body);
    for (int i = 0; i < body.getChildCount(); i++) {
      View view = body.getChildAt(i);
      if (view instanceof Button && label.contentEquals(((Button) view).getText())) {
        return (Button) view;
      }
    }
    return null;
  }

  private <A extends Activity> void click(Class<A> type, int id) throws Exception {
    await("click " + context.getResources().getResourceEntryName(id),
        () -> Boolean.TRUE.equals(onResumed(type, a -> {
          View view = a.findViewById(id);
          return clickable(view) && view.performClick();
        })));
  }

  private <A extends Activity> void awaitButton(Class<A> type, int id) throws Exception {
    await("enabled " + context.getResources().getResourceEntryName(id),
        () -> Boolean.TRUE.equals(onResumed(type, a -> clickable(a.findViewById(id)))));
  }

  private static boolean clickable(View view) {
    return view != null && view.isShown() && view.isEnabled();
  }

  private <T> T episode(Function<AuthoredEpisodeActivity, T> action) throws Exception {
    T result = onResumed(AuthoredEpisodeActivity.class, action);
    assertNotNull("Authored Activity is not resumed", result);
    return result;
  }

  private <A extends Activity, T> T onResumed(Class<A> type, Function<A, T> action)
      throws Exception {
    return onMain(() -> {
      for (Activity current : ActivityLifecycleMonitorRegistry.getInstance()
          .getActivitiesInStage(Stage.RESUMED)) {
        if (type.isInstance(current)) return action.apply(type.cast(current));
      }
      return null;
    });
  }

  private <T> T onMain(Supplier<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action::get);
    instrumentation.runOnMainSync(task);
    return task.get(5, TimeUnit.SECONDS);
  }

  private interface Check { boolean ready() throws Exception; }

  private static void await(String expected, Check check) throws Exception {
    long deadline = SystemClock.elapsedRealtime() + 5_000;
    do {
      if (check.ready()) return;
      SystemClock.sleep(40);
    } while (SystemClock.elapsedRealtime() < deadline);
    fail("Timed out waiting for " + expected);
  }
}
