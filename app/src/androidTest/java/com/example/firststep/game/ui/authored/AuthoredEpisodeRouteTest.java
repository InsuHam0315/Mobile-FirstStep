package com.example.firststep.game.ui.authored;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.R;
import com.example.firststep.game.composition.authored.AuthoredEpisodeHost;
import com.example.firststep.game.composition.authored.AuthoredRouteTestFixture;
import com.example.firststep.game.device.authored.ArrivalPolicy;
import com.example.firststep.game.device.authored.ForegroundArrival;
import com.example.firststep.game.device.authored.NormalDiscoverySession;
import com.example.firststep.game.domain.GameContent;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.presentation.discovery.DiscoveryController;
import com.example.firststep.game.presentation.discovery.DiscoveryState;
import com.example.firststep.game.sync.SyncScope;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AuthoredEpisodeRouteTest {
  private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

  @Test public void actualActivitySeparatesRemotePassFromUnstartedLocalGame() throws Exception {
    AuthoredRouteTestFixture fixture = new AuthoredRouteTestFixture();
    try (AutoCloseable lease = AuthoredEpisodeActivity.installDebugRuntime(fixture::open);
        ActivityScenario<AuthoredEpisodeActivity> scenario =
        ActivityScenario.launch(AuthoredEpisodeActivity.intent(context))) {
      String waitingForArrival = context.getString(R.string.game_authored_arrival_unavailable);
      awaitText(scenario, R.id.game_authored_state, waitingForArrival);
      assertEquals(0, fixture.remoteReads.get());
      scenario.onActivity(activity -> {
        Button read = activity.findViewById(R.id.game_authored_sync_read);
        assertTrue(read.isEnabled());
        assertTrue(read.performClick());
      });
      awaitText(scenario, R.id.game_authored_remote,
          context.getString(R.string.game_authored_sync_pass_confirmed, 2));
      awaitText(scenario, R.id.game_authored_state, waitingForArrival);
      assertEquals(1, fixture.remoteReads.get());
      assertEquals(0, fixture.remoteWrites.get());
      assertEquals(0, fixture.localWrites.get());
      assertNull(fixture.localProgress());
    }
  }

  @Test public void malformedReminderNeverFallsBackToNormalOpen() throws Exception {
    AuthoredRouteTestFixture fixture = new AuthoredRouteTestFixture();
    Intent malformed = AuthoredEpisodeActivity.intent(context)
        .setAction("com.example.firststep.AUTHORED_REMINDER_ENTRY");
    try (AutoCloseable lease = AuthoredEpisodeActivity.installDebugRuntime(fixture::open);
        ActivityScenario<AuthoredEpisodeActivity> scenario = ActivityScenario.launch(malformed)) {
      awaitText(scenario, R.id.game_authored_state,
          context.getString(R.string.game_authored_reminder_invalid));
      assertEquals(0, fixture.opens.get());
    }
  }

  @Test public void actualActivityRunsSyntheticArrivalDiscoveryIntroAndQuizAway() throws Exception {
    AuthoredRouteTestFixture fixture = new AuthoredRouteTestFixture();
    fixture.atTarget = true;
    AtomicLong elapsed = new AtomicLong(1000L);
    AuthoredEpisodeActivity.DebugRuntime runtime = new AuthoredEpisodeActivity.DebugRuntime() {
      @Override public AuthoredEpisodeHost.OpenResult open(Context context, SyncScope expected) {
        return fixture.open(context, expected);
      }

      @Override public AuthoredEpisodeActivity.ArrivalInput arrival(ForegroundArrival.Listener listener) {
        return new AuthoredEpisodeActivity.ArrivalInput() {
          @Override public void start() {
            listener.onState(ForegroundArrival.State.valueOf(currentVerdict().name()));
          }
          @Override public void retry() { start(); }
          @Override public void stop() { }
          @Override public ArrivalPolicy.Verdict currentVerdict() { return fixture.arrivalVerdict(); }
        };
      }

      @Override public DiscoveryController.Clock discoveryClock() { return elapsed::get; }

      @Override public AuthoredEpisodeActivity.DiscoveryInput discovery(NormalDiscoverySession session) {
        return new AuthoredEpisodeActivity.DiscoveryInput() {
          @Override public void start() {
            session.camera(DiscoveryState.CameraStatus.READY);
            session.heading(90, true);
            for (int pulse = 0; pulse < 5; pulse++) {
              elapsed.addAndGet(200);
              session.heading(90, true);
              session.acceleration(23);
              session.acceleration(9.8f);
            }
          }
          @Override public void close() { }
        };
      }
    };
    try (AutoCloseable lease = AuthoredEpisodeActivity.installDebugRuntime(runtime);
        ActivityScenario<AuthoredEpisodeActivity> scenario =
            ActivityScenario.launch(AuthoredEpisodeActivity.intent(context))) {
      awaitText(scenario, R.id.game_authored_state,
          context.getString(R.string.game_authored_arrival_confirmed));
      click(scenario, context.getString(R.string.game_authored_discovery_start), Button.class);
      awaitText(scenario, R.id.game_authored_state, context.getString(R.string.game_authored_intro));
      click(scenario, context.getString(R.string.game_authored_skip_intro), Button.class);
      awaitText(scenario, R.id.game_authored_state, context.getString(R.string.game_authored_wait));
      assertNotNull(fixture.localProgress().introDoneAt);

      fixture.atTarget = false;
      scenario.recreate();
      awaitText(scenario, R.id.game_authored_state, context.getString(R.string.game_authored_wait));
      fixture.now.addAndGet(ProgressService.WAIT_MILLIS);
      awaitText(scenario, R.id.game_authored_state,
          context.getString(R.string.game_authored_quiz_ready));
      click(scenario, context.getString(R.string.game_authored_start_quiz), Button.class);
      awaitText(scenario, R.id.game_authored_state, context.getString(R.string.game_authored_quiz));
      for (GameContent.Question question : fixture.localProgress().active.questions) {
        for (GameContent.Option option : question.options) {
          if (question.correctOptionId.equals(option.id)) click(scenario, option.text, RadioButton.class);
        }
      }
      click(scenario, context.getString(R.string.game_authored_submit), Button.class);
      awaitText(scenario, R.id.game_authored_state,
          context.getString(R.string.game_authored_saved, 3));
      assertTrue(fixture.localProgress().stamp);
      assertEquals(0, fixture.remoteReads.get());
    }
  }

  private static void click(ActivityScenario<AuthoredEpisodeActivity> scenario, String label,
      Class<? extends TextView> type) {
    scenario.onActivity(activity -> {
      View found = find(activity.findViewById(R.id.game_authored_body), v ->
          type.isInstance(v) && label.contentEquals(((TextView) v).getText()));
      assertNotNull("Missing " + label, found);
      assertTrue(found.isEnabled());
      assertTrue(found.performClick());
    });
  }

  private static View find(View view, Function<View, Boolean> match) {
    if (match.apply(view)) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = find(group.getChildAt(i), match);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static void awaitText(ActivityScenario<AuthoredEpisodeActivity> scenario, int id,
      String expected) {
    AtomicReference<String> actual = new AtomicReference<>();
    long deadline = SystemClock.elapsedRealtime() + 5000;
    do {
      scenario.onActivity(activity -> {
        TextView view = activity.findViewById(id);
        actual.set(view.getText().toString());
      });
      if (expected.equals(actual.get())) return;
      SystemClock.sleep(40);
    } while (SystemClock.elapsedRealtime() < deadline);
    assertEquals(expected, actual.get());
  }
}
