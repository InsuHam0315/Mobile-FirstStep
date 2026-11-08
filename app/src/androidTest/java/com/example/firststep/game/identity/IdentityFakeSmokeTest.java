package com.example.firststep.game.identity;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.R;
import com.example.firststep.game.auth.*;
import com.example.firststep.game.composition.identity.H07VariantFactory;
import com.example.firststep.game.ui.identity.IdentityActivity;
import java.io.File;
import java.io.FileOutputStream;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class IdentityFakeSmokeTest {
  private final FakeIdentityRuntime fake = new FakeIdentityRuntime();

  private static Profile profile(String name) {
    return new Profile(name, "00123", "Sample department");
  }

  private static void main(Runnable action) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
  }

  @After
  public void reset() {
    main(() -> H07VariantFactory.setTestFactory(null));
  }

  private ActivityScenario<IdentityActivity> launch() {
    main(() -> H07VariantFactory.setTestFactory(() -> fake));
    Context context = ApplicationProvider.getApplicationContext();
    ActivityScenario<IdentityActivity> screen =
        ActivityScenario.launch(new Intent(context, IdentityActivity.class));
    String orientation = InstrumentationRegistry.getArguments().getString("h07Orientation");
    if (orientation != null) {
      int expected = Integer.parseInt(orientation);
      screen.onActivity(
          a ->
              a.setRequestedOrientation(
                  expected == Configuration.ORIENTATION_LANDSCAPE
                      ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                      : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      long deadline = SystemClock.elapsedRealtime() + 5000;
      int[] actual = {0};
      do {
        screen.onActivity(a -> actual[0] = a.getResources().getConfiguration().orientation);
        if (actual[0] == expected) break;
        SystemClock.sleep(50);
      } while (SystemClock.elapsedRealtime() < deadline);
      assertEquals(expected, actual[0]);
    }
    screen.onActivity(
        a -> {
          String scale = InstrumentationRegistry.getArguments().getString("h07FontScale");
          if (scale != null)
            assertEquals(
                Float.parseFloat(scale), a.getResources().getConfiguration().fontScale, 0.01f);
        });
    onView(withId(R.id.game_identity_environment))
        .check(matches(withText(R.string.game_identity_fake)));
    screen.onActivity(
        a -> {
          TextView status = a.findViewById(R.id.game_identity_status);
          assertNotNull(status.getLayout());
          for (int i = 0; i < status.getLayout().getLineCount(); i++)
            assertEquals(0, status.getLayout().getEllipsisCount(i));
        });
    return screen;
  }

  private static ViewAction reveal() {
    return new ViewAction() {
      public Matcher<View> getConstraints() {
        return isAssignableFrom(View.class);
      }

      public String getDescription() {
        return "reveal actual H07 fake UI control";
      }

      public void perform(UiController ui, View v) {
        ViewParent p = v.getParent();
        while (p != null && !(p instanceof ScrollView)) p = p.getParent();
        assertTrue(p instanceof ScrollView);
        ScrollView scroll = (ScrollView) p;
        Rect b = new Rect();
        v.getDrawingRect(b);
        scroll.offsetDescendantRectToMyCoords(v, b);
        int h = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        scroll.scrollTo(0, Math.max(0, b.centerY() - h / 2 - scroll.getPaddingTop()));
        ui.loopMainThreadForAtLeast(200);
        assertTrue(isDisplayingAtLeast(90).matches(v));
      }
    };
  }

  private static void tap(int id) {
    onView(withId(id)).perform(reveal(), click());
  }

  private static void fill(int id, String text) {
    onView(withId(id)).perform(reveal(), replaceText(text), closeSoftKeyboard());
  }

  private void startLogin() {
    tap(R.id.game_identity_login);
    main(() -> fake.loginAs("A"));
  }

  private static void evidence(String name) throws Exception {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    SystemClock.sleep(300);
    Context context = ApplicationProvider.getApplicationContext();
    File dir = new File(context.getExternalFilesDir(null), "h07-evidence");
    assertTrue(dir.isDirectory() || dir.mkdirs());
    String prefix =
        InstrumentationRegistry.getArguments().getString("h07EvidencePrefix", "default");
    Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(bitmap);
    try (FileOutputStream out =
        new FileOutputStream(new File(dir, prefix + "-fake-" + name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
    } finally {
      bitmap.recycle();
    }
  }

  @Test
  public void missingProfileNeedsServerConfirmedSave() throws Exception {
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      onView(withId(R.id.game_identity_login))
          .perform(reveal())
          .check(matches(isCompletelyDisplayed()));
      screen.onActivity(
          a -> {
            TextView button = a.findViewById(R.id.game_identity_login);
            assertTrue(button.getHeight() >= 48 * a.getResources().getDisplayMetrics().density);
            assertNotNull(button.getLayout());
            for (int i = 0; i < button.getLayout().getLineCount(); i++)
              assertEquals(0, button.getLayout().getEllipsisCount(i));
            assertTrue(
                button.getLayout().getHeight()
                    <= button.getHeight()
                        - button.getCompoundPaddingTop()
                        - button.getCompoundPaddingBottom());
          });
      startLogin();
      main(() -> fake.serverLatest(null));
      onView(withId(R.id.game_identity_save)).check(matches(not(isEnabled())));
      fill(R.id.game_identity_name, "Sample");
      fill(R.id.game_identity_student, "00123");
      fill(R.id.game_identity_department, "Sample department");
      onView(withId(R.id.game_identity_student)).check(matches(withText("00123")));
      tap(R.id.game_identity_save);
      main(() -> fake.saves.get(0).onResult(null));
      screen.onActivity(a -> assertEquals(SessionState.Stage.SAVING, a.stateForTest().getStage()));
      main(() -> fake.latestLoad().onResult(profile("Sample"), ProfilePort.Source.CACHE, null));
      screen.onActivity(a -> assertEquals(SessionState.Stage.SAVING, a.stateForTest().getStage()));
      main(() -> fake.serverLatest(profile("Sample")));
      onView(withId(R.id.game_identity_landing)).perform(reveal()).check(matches(isDisplayed()));
      evidence("complete");
    }
  }

  @Test
  public void returningAndIncompleteProfilesRouteCorrectly() throws Exception {
    fake.uid = "A";
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      main(() -> fake.latestLoad().onResult(profile("Cached"), ProfilePort.Source.CACHE, null));
      main(() -> fake.latestLoad().onResult(null, ProfilePort.Source.SERVER, Failure.UNAVAILABLE));
      screen.onActivity(
          a -> {
            assertEquals(SessionState.Stage.PENDING_FAILURE, a.stateForTest().getStage());
            assertTrue(a.stateForTest().isCached());
            assertTrue(
                ((TextView) a.findViewById(R.id.game_identity_status))
                    .getText()
                    .toString()
                    .contains(a.getString(R.string.game_identity_cached)));
            assertFalse(a.findViewById(R.id.game_identity_name).isEnabled());
            assertFalse(a.findViewById(R.id.game_identity_student).isEnabled());
            assertFalse(a.findViewById(R.id.game_identity_department).isEnabled());
          });
      evidence("cached-unconfirmed");
      tap(R.id.game_identity_retry);
      main(() -> fake.serverLatest(profile("Returning")));
      screen.onActivity(a -> assertFalse(a.stateForTest().isCached()));
      onView(withId(R.id.game_identity_landing)).perform(reveal()).check(matches(isDisplayed()));
      tap(R.id.game_identity_reselect);
      main(() -> fake.loginAs("B"));
      main(() -> fake.serverLatest(new Profile("", "00123", "Sample department")));
      onView(withId(R.id.game_identity_student))
          .perform(reveal())
          .check(matches(withText("00123")));
      screen.onActivity(a -> assertEquals(SessionState.Stage.FORM, a.stateForTest().getStage()));
      evidence("incomplete");
    }
  }

  @Test
  public void canceledLoginAndFailedSaveRetainDraftForRetry() throws Exception {
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      tap(R.id.game_identity_login);
      main(() -> fake.login.onResult(Failure.CANCELED));
      screen.onActivity(a -> assertEquals(Failure.CANCELED, a.stateForTest().getFailure()));
      onView(withId(R.id.game_identity_login))
          .perform(reveal())
          .check(matches(isCompletelyDisplayed()));
      screen.onActivity(
          a -> {
            TextView button = a.findViewById(R.id.game_identity_login);
            assertTrue(button.getHeight() >= 48 * a.getResources().getDisplayMetrics().density);
            assertNotNull(button.getLayout());
            for (int i = 0; i < button.getLayout().getLineCount(); i++)
              assertEquals(0, button.getLayout().getEllipsisCount(i));
            assertTrue(
                button.getLayout().getHeight()
                    <= button.getHeight()
                        - button.getCompoundPaddingTop()
                        - button.getCompoundPaddingBottom());
          });
      startLogin();
      main(() -> fake.serverLatest(null));
      fill(R.id.game_identity_name, "Sample");
      fill(R.id.game_identity_student, "00123");
      fill(R.id.game_identity_department, "Sample department");
      tap(R.id.game_identity_save);
      main(() -> fake.saves.get(0).onResult(Failure.PERMISSION_DENIED));
      onView(withId(R.id.game_identity_student))
          .perform(reveal())
          .check(matches(withText("00123")));
      screen.onActivity(
          a -> assertEquals(Failure.PERMISSION_DENIED, a.stateForTest().getFailure()));
      evidence("save-denied");
      tap(R.id.game_identity_retry);
      main(() -> fake.saves.get(1).onResult(null));
      main(() -> fake.serverLatest(profile("Sample")));
      screen.onActivity(
          a -> assertEquals(SessionState.Stage.COMPLETE, a.stateForTest().getStage()));
    }
  }

  @Test
  public void logoutAndSwitchRejectLateCallbacks() throws Exception {
    fake.uid = "A";
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      ProfilePort.ProfileCallback late = fake.latestLoad();
      main(() -> fake.switchTo("B"));
      main(() -> late.onResult(profile("Old"), ProfilePort.Source.SERVER, null));
      main(() -> fake.serverLatest(profile("New")));
      screen.onActivity(
          a -> {
            assertEquals("B", a.stateForTest().getUid());
            assertEquals("New", a.stateForTest().getDraft().getName());
          });
      tap(R.id.game_identity_logout);
      main(
          () -> {
            fake.uid = null;
            fake.logout.onResult(null);
            late.onResult(profile("Old"), ProfilePort.Source.SERVER, null);
          });
      onView(withId(R.id.game_identity_login)).perform(reveal()).check(matches(isDisplayed()));
      screen.onActivity(a -> assertNull(a.stateForTest().getDraft()));
      evidence("logged-out");
    }
  }

  @Test
  public void recreationPreservesSameAccountDraftAndClearsOnSwitch() throws Exception {
    fake.uid = "A";
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      main(() -> fake.serverLatest(null));
      fill(R.id.game_identity_name, "Draft");
      fill(R.id.game_identity_student, "00123");
      screen.moveToState(Lifecycle.State.CREATED);
      screen.moveToState(Lifecycle.State.RESUMED);
      main(() -> fake.serverLatest(null));
      screen.recreate();
      main(() -> fake.serverLatest(null));
      onView(withId(R.id.game_identity_name)).perform(reveal()).check(matches(withText("Draft")));
      main(() -> fake.switchTo("B"));
      main(() -> fake.serverLatest(null));
      onView(withId(R.id.game_identity_name)).perform(reveal()).check(matches(withText("")));
      screen.onActivity(a -> assertEquals("B", a.stateForTest().getUid()));
      evidence("switch-form");
    }
  }
}
