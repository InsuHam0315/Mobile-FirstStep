package com.example.firststep.game.integration;

import static com.example.firststep.game.integration.IntegrationTestSupport.*;
import static org.junit.Assert.*;

import android.content.Intent;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.firststep.R;
import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.domain.*;
import com.example.firststep.game.presentation.integration.NavigationPolicy;
import com.example.firststep.game.presentation.introquiz.IntroQuizController;
import com.example.firststep.game.ui.discovery.DiscoveryActivity;
import com.example.firststep.game.ui.home.GameHomeActivity;
import com.example.firststep.game.ui.intro.IntroQuizActivity;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

/**
 * Actual APK navigation, isolated fixtures, simulated arrival/hold/fake clock. Never mutates
 * permissions.
 */
@RunWith(AndroidJUnit4.class)
public final class IntegrationLoopTest {
  @After
  public void cleanup() {
    closeActivities();
  }

  @Test
  public void demoLoopRetriesOneStampAndRefreshesHome() throws Exception {
    Map<String, String> before = databases();
    IntegrationContext c = context(Progress.Mode.DEMO);
    try (ActivityScenario<GameHomeActivity> home = ActivityScenario.launch(home(c))) {
      homeReady();
      capture("home-demo-fixture-start");
      discover(c, true);
      fits(R.id.game_intro_caption);
      capture("intro-fixture-shared-run");
      press(R.id.game_intro_skip);
      stage(IntroQuizController.Stage.WAITING);
      assertTrue(intro().eligible);
      press(R.id.game_quiz_start);
      stage(IntroQuizController.Stage.QUIZ);
      for (GameContent.Question q : intro().progress.active.questions) option(wrong(q));
      press(R.id.game_quiz_submit);
      stage(IntroQuizController.Stage.RESULT);
      assertFalse(intro().result.passed);
      assertFalse(intro().progress.stamp);
      capture("quiz-failed-fixture-demo");
      press(R.id.game_quiz_retry);
      stage(IntroQuizController.Stage.QUIZ);
      for (GameContent.Question q : intro().progress.active.questions) option(q.correctOptionId);
      IntroQuizActivity quiz = top(IntroQuizActivity.class);
      main(
          () -> {
            quiz.findViewById(R.id.game_quiz_submit).performClick();
            quiz.findViewById(R.id.game_quiz_submit).performClick();
          });
      stage(IntroQuizController.Stage.RESULT);
      assertTrue(intro().progress.stamp);
      assertEquals(2, intro().progress.history.size());
      fits(R.id.game_quiz_result);
      capture("quiz-single-stamp-fixture-demo");
      press(R.id.game_intro_back);
      homeReady();
      await(
          "Authoritative home stamp",
          () -> {
            boolean[] ok = {false};
            main(() -> ok[0] = top(GameHomeActivity.class).snapshot().progress.stamp);
            return ok[0];
          });
      capture("home-refreshed-one-stamp-fixture-demo");
      main(() -> assertFalse(top(GameHomeActivity.class).snapshot().endingAvailable));
      press(R.id.game_home_prototype);
      stage(IntroQuizController.Stage.RESULT);
      assertEquals(2, intro().progress.history.size());
      press(R.id.game_intro_back);
      homeReady();
      press(R.id.game_home_mode);
      await(
          "NORMAL isolation",
          () -> {
            boolean[] ok = {false};
            GameHomeActivity a = top(GameHomeActivity.class);
            if (a != null)
              main(
                  () -> ok[0] = a.snapshot().mode == Progress.Mode.NORMAL && !a.snapshot().loading);
            return ok[0];
          });
      main(
          () -> {
            assertNull(top(GameHomeActivity.class).snapshot().progress.introDoneAt);
            assertFalse(top(GameHomeActivity.class).snapshot().progress.stamp);
          });
    }
    preserved(before);
  }

  @Test
  public void normalFakeBoundaryReopensWithoutDiscovery() throws Exception {
    IntegrationContext c = context(Progress.Mode.NORMAL);
    try (ActivityScenario<GameHomeActivity> home = ActivityScenario.launch(home(c))) {
      discover(c, false);
      press(R.id.game_intro_skip);
      stage(IntroQuizController.Stage.WAITING);
      assertFalse(intro().eligible);
      assertEquals(Long.valueOf(START), intro().progress.introDoneAt);
      press(R.id.game_intro_back);
      homeReady();
      press(R.id.game_home_prototype);
      stage(IntroQuizController.Stage.WAITING);
      IntroQuizActivity a = top(IntroQuizActivity.class);
      main(() -> a.setDebugClockForTest(START + ProgressService.WAIT_MILLIS - 1));
      await(
          "Persisted early fake clock",
          () ->
              new NavigationStore(context()).load(c).context.fakeNow
                  == START + ProgressService.WAIT_MILLIS - 1);
      assertFalse(intro().eligible);
      main(() -> a.setDebugClockForTest(START + ProgressService.WAIT_MILLIS));
      await("Exact boundary", () -> intro() != null && intro().eligible);
      capture("normal-exact-15min-DEBUG_FAKE");
      main(a::recreate);
      await(
          "Recreated eligible",
          () -> top(IntroQuizActivity.class) != a && intro() != null && intro().eligible);
      assertEquals(Long.valueOf(START), intro().progress.introDoneAt);
      press(R.id.game_intro_back);
      homeReady();
      await(
          "Home reads persisted fake time",
          () -> {
            boolean[] ok = {false};
            main(() -> ok[0] = top(GameHomeActivity.class).snapshot().eligible);
            return ok[0];
          });
      capture("home-normal-eligible-DEBUG_FAKE");
      assertNull(
          IntegrationRouteAccess.readProgress(context(), context(c.runId, Progress.Mode.DEMO))
              .introDoneAt);
    }
  }

  @Test
  public void cancellationAndPendingRecreationDoNotAdvance() throws Exception {
    IntegrationContext c = context(Progress.Mode.NORMAL);
    try (ActivityScenario<GameHomeActivity> home = ActivityScenario.launch(home(c))) {
      homeReady();
      press(R.id.game_home_prototype);
      DiscoveryActivity old = waitTop(DiscoveryActivity.class);
      await(
          "Discovery ready",
          () -> {
            boolean[] ok = {false};
            main(() -> ok[0] = old.snapshot() != null);
            return ok[0];
          });
      press(R.id.game_discovery_arrival);
      main(old::recreate);
      await(
          "Recreated fresh observations",
          () -> {
            DiscoveryActivity a = top(DiscoveryActivity.class);
            boolean[] ok = {false};
            if (a != null && a != old)
              main(() -> ok[0] = a.snapshot() != null && !a.snapshot().arrived);
            return ok[0];
          });
      press(R.id.game_discovery_exit);
      homeReady();
      await(
          "Canceled pending request",
          () -> new NavigationStore(context()).load(c).phase == NavigationPolicy.Phase.EMPTY);
      assertNull(IntegrationRouteAccess.readProgress(context(), c).introDoneAt);
      assertFalse(IntegrationRouteAccess.readProgress(context(), c).stamp);
      home.recreate();
      homeReady();
    }
  }

  @Test
  public void malformedAndUnpreparedRoutesNeverOpenDefaultDb() throws Exception {
    Map<String, String> before = databases();
    try (ActivityScenario<GameHomeActivity> s =
        ActivityScenario.launch(
            new Intent(context(), GameHomeActivity.class)
                .putExtra(IntegrationIntentCodec.MARKER, true))) {
      s.onActivity(
          a -> {
            assertNull(a.snapshot());
            assertTrue(((TextView) a.findViewById(R.id.game_home_status)).getText().length() > 0);
          });
    }
    IntegrationContext c = context(Progress.Mode.NORMAL);
    Intent i = new Intent(context(), IntroQuizActivity.class);
    IntegrationIntentCodec.writeContext(i, c);
    try (ActivityScenario<IntroQuizActivity> s = ActivityScenario.launch(i)) {
      await(
          "Unprepared intro rejected",
          () -> {
            boolean[] error = {false};
            s.onActivity(
                a ->
                    error[0] =
                        a.snapshot() == null
                            && ((TextView) a.findViewById(R.id.game_intro_status))
                                .getText()
                                .toString()
                                .contains("전환하지"));
            return error[0];
          });
    }
    Intent d = new Intent(context(), DiscoveryActivity.class);
    IntegrationIntentCodec.writeContext(d, c);
    try (ActivityScenario<DiscoveryActivity> s = ActivityScenario.launch(d)) {
      s.onActivity(a -> assertNull(a.snapshot()));
    }
    preserved(before);
    assertFalse(
        context().getDatabasePath("h04-debug-" + c.runId + ".db").exists()
            && new NavigationStore(context()).load(c) == null);
  }

  private static String wrong(GameContent.Question q) {
    for (GameContent.Option o : q.options) if (!o.id.equals(q.correctOptionId)) return o.id;
    throw new AssertionError();
  }
}
