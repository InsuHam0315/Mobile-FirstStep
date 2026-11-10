package com.example.firststep.game.home.account;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.R;
import com.example.firststep.game.composition.home.CampusHomeRoute;
import com.example.firststep.game.composition.home.account.AccountHomeTestRuntime;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.presentation.home.account.AccountHomeController.Reading;
import com.example.firststep.game.sync.Milestone;
import com.example.firststep.game.ui.home.CampusHomeActivity;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AccountHomeRuntimeTest {
  @Test public void foregroundHomeUsesInjectedClockAndReloadsLocalProgressOnReturn() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    long intro = 10_000;
    AtomicLong clock = new AtomicLong(intro);
    AtomicReference<Progress> local = new AtomicReference<>(new Progress(
        Progress.Mode.NORMAL, "software", intro, false, false, null, Collections.emptyMap()));
    AtomicBoolean ownerCurrent = new AtomicBoolean(true);
    try (AutoCloseable lease = AccountHomeTestRuntime.install(
             () -> Reading.progress(local.get(), new Milestone(intro, null), action -> {
               if (!ownerCurrent.get()) return false;
               action.run();
               return true;
             }, Collections.singletonList("Test library"), true), true, clock::get);
         ActivityScenario<CampusHomeActivity> home =
             ActivityScenario.launch(CampusHomeRoute.intent(context))) {
      awaitText(home, R.id.game_campus_progress_state,
          context.getString(R.string.game_campus_progress_waiting, 15, 0));
      awaitText(home, R.id.game_campus_home_favorites, "Test library");
      awaitText(home, R.id.game_campus_progress_remote,
          context.getString(R.string.game_campus_progress_remote_intro));

      clock.set(intro + ProgressService.WAIT_MILLIS);
      awaitText(home, R.id.game_campus_progress_state,
          context.getString(R.string.game_campus_progress_ready));
      home.moveToState(Lifecycle.State.CREATED);
      local.set(Progress.empty(Progress.Mode.NORMAL, "software"));
      home.moveToState(Lifecycle.State.RESUMED);
      awaitText(home, R.id.game_campus_progress_state,
          context.getString(R.string.game_campus_progress_unstarted));

      ownerCurrent.set(false);
      awaitText(home, R.id.game_campus_progress_state,
          context.getString(R.string.game_campus_progress_no_account));
      awaitText(home, R.id.game_campus_home_favorites, "Test library");
    }
    assertNull(AccountHomeTestRuntime.current());
  }

  private static void awaitText(ActivityScenario<CampusHomeActivity> scenario, int id, String expected) {
    long timeout = SystemClock.uptimeMillis() + 5_000;
    AtomicReference<String> actual = new AtomicReference<>();
    do {
      scenario.onActivity(activity ->
          actual.set(((TextView) activity.findViewById(id)).getText().toString()));
      if (expected.equals(actual.get())) return;
      SystemClock.sleep(50);
    } while (SystemClock.uptimeMillis() < timeout);
    assertEquals(expected, actual.get());
  }
}
