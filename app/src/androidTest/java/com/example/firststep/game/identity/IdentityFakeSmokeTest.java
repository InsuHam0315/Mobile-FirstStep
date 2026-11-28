package com.example.firststep.game.identity;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;
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
import com.example.firststep.game.presentation.home.entry.CampusChoice;
import com.example.firststep.game.ui.guide.GuideActivity;
import com.example.firststep.game.ui.identity.IdentityActivity;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class IdentityFakeSmokeTest {
  private final FakeIdentityRuntime fake = new FakeIdentityRuntime();
  private SharedPreferences prefs;
  private Map<String, ?> original;
  private IdentityActivity launched;
  private static Profile profile(String name) { return new Profile(name, "00123", "Sample department"); }
  private static void main(Runnable r) { InstrumentationRegistry.getInstrumentation().runOnMainSync(r); }

  @Before public void setup() {
    Context c = ApplicationProvider.getApplicationContext();
    prefs = c.getSharedPreferences("local_campus_choices", Context.MODE_PRIVATE);
    original = new HashMap<>(prefs.getAll());
    assertTrue(prefs.edit().clear().commit());
    main(() -> H07VariantFactory.setTestFactory(() -> fake));
  }
  @After public void reset() {
    long deadline = SystemClock.elapsedRealtime() + 5000;
    while (launched != null && launched.hasPendingLocalWritesForTest()
        && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(40);
    assertFalse("Local writes must finish before restoring the preference snapshot",
        launched != null && launched.hasPendingLocalWritesForTest());
    main(() -> H07VariantFactory.setTestFactory(null));
    SharedPreferences.Editor e = prefs.edit().clear();
    for (Map.Entry<String, ?> entry : original.entrySet()) {
      Object v = entry.getValue(); String k = entry.getKey();
      if (v instanceof String) e.putString(k, (String) v);
      else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
      else if (v instanceof Integer) e.putInt(k, (Integer) v);
      else if (v instanceof Long) e.putLong(k, (Long) v);
      else if (v instanceof Float) e.putFloat(k, (Float) v);
      else if (v instanceof java.util.Set) {
        @SuppressWarnings("unchecked") java.util.Set<String> set = (java.util.Set<String>) v;
        e.putStringSet(k, set);
      }
    }
    assertTrue(e.commit());
  }
  private ActivityScenario<IdentityActivity> launch() {
    ActivityScenario<IdentityActivity> screen = ActivityScenario.launch(
        new Intent(ApplicationProvider.getApplicationContext(), IdentityActivity.class));
    screen.onActivity(a -> launched = a);
    String orientation = InstrumentationRegistry.getArguments().getString("h07Orientation");
    if (orientation != null) {
      int expected = Integer.parseInt(orientation);
      assertTrue(expected == 1 || expected == 2);
      screen.onActivity(a -> a.setRequestedOrientation(expected == 2
          ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
          : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      long end = SystemClock.elapsedRealtime() + 5000;
      int[] actual = {0};
      do {
        screen.onActivity(a -> actual[0] = a.getResources().getConfiguration().orientation);
        if (actual[0] == expected) break;
        SystemClock.sleep(40);
      } while (SystemClock.elapsedRealtime() < end);
      assertEquals(expected, actual[0]);
    }
    String fontScale = InstrumentationRegistry.getArguments().getString("h07FontScale");
    if (fontScale != null) screen.onActivity(a -> assertEquals(Float.parseFloat(fontScale),
        a.getResources().getConfiguration().fontScale, 0.01f));
    return screen;
  }
  private static ViewAction reveal() {
    return new ViewAction() {
      public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
      public String getDescription() { return "reveal reachable shell control"; }
      public void perform(UiController ui, View v) {
        ViewParent p = v.getParent();
        while (p != null && !(p instanceof ScrollView)) p = p.getParent();
        if (p instanceof ScrollView) {
          ScrollView s = (ScrollView) p; Rect r = new Rect(); v.getDrawingRect(r);
          s.offsetDescendantRectToMyCoords(v, r);
          s.scrollTo(0, Math.max(0, r.centerY() - s.getHeight() / 2));
          ui.loopMainThreadForAtLeast(150);
        }
        assertTrue(isDisplayingAtLeast(90).matches(v));
      }
    };
  }
  private static void tap(int id) { onView(withId(id)).perform(reveal(), click()); }
  private static void fill(int id, String value) {
    onView(withId(id)).perform(reveal(), replaceText(value), closeSoftKeyboard());
  }
  private static void waitEnabled(ActivityScenario<IdentityActivity> screen, int id) {
    long end = SystemClock.elapsedRealtime() + 5000;
    boolean[] ready = {false};
    do {
      screen.onActivity(a -> ready[0] = a.findViewById(id).isEnabled());
      if (ready[0]) return;
      SystemClock.sleep(40);
    } while (SystemClock.elapsedRealtime() < end);
    assertTrue("control not enabled: " + id, ready[0]);
  }
  private static void waitLocalSaved(ActivityScenario<IdentityActivity> screen) {
    long deadline = SystemClock.elapsedRealtime() + 5000;
    boolean[] saved = {false};
    do {
      screen.onActivity(a -> saved[0] = !a.hasPendingLocalWritesForTest()
          && a.getString(R.string.game_main_local_saved).contentEquals(
              ((android.widget.TextView) a.findViewById(R.id.game_main_local_status)).getText()));
      if (saved[0]) return;
      SystemClock.sleep(40);
    } while (SystemClock.elapsedRealtime() < deadline);
    assertTrue("Explicit local selection save must complete", saved[0]);
  }
  private void login() {
    tap(R.id.game_identity_welcome_login);
    assertNotNull(fake.login);
    main(() -> fake.loginAs("fake-A"));
    onView(withId(R.id.game_main_dashboard)).check(matches(isDisplayed()));
  }
  private static void my() { tap(R.id.game_nav_my); }
  private static void form() {
    fill(R.id.game_identity_name, "Sample");
    fill(R.id.game_identity_student, "00123");
    fill(R.id.game_identity_department, "Sample department");
  }
  @Test public void optionalLoginAndMatchingServerConfirmation() {
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      evidence("start");
      screen.onActivity(a -> {
        com.google.android.material.button.MaterialButton button =
            a.findViewById(R.id.game_identity_welcome_login);
        assertEquals("Google로 계속하기", button.getText().toString());
        assertNotNull(button.getIcon());
        assertNull(button.getIconTint());
        assertTrue(button.getHeight() >= 48 * a.getResources().getDisplayMetrics().density);
      });
      login();
      screen.onActivity(a -> assertEquals(SessionState.Stage.PROFILE_LOADING, a.stateForTest().getStage()));
      my(); main(() -> fake.serverLatest(null));
      waitEnabled(screen, R.id.game_identity_name); form();
      onView(withId(R.id.game_identity_student)).check(matches(withText("00123")));
      tap(R.id.game_identity_save); waitLocalSaved(screen); main(() -> fake.saves.get(0).onResult(null));
      main(() -> fake.latestLoad().onResult(profile("Sample"), ProfilePort.Source.CACHE, null));
      screen.onActivity(a -> assertEquals(SessionState.Stage.SAVING, a.stateForTest().getStage()));
      main(() -> fake.serverLatest(profile("Different")));
      screen.onActivity(a -> assertEquals(SessionState.Stage.PENDING_FAILURE, a.stateForTest().getStage()));
      tap(R.id.game_nav_home);
      onView(withId(R.id.game_main_dashboard)).check(matches(isDisplayed()));
      my(); tap(R.id.game_identity_retry); main(() -> fake.saves.get(1).onResult(null));
      main(() -> fake.serverLatest(profile("Sample")));
      screen.onActivity(a -> assertEquals(SessionState.Stage.COMPLETE, a.stateForTest().getStage()));
      onView(withId(R.id.game_identity_begin_edit)).check(matches(isDisplayed()));
      onView(withId(R.id.game_identity_name)).check(matches(notEnabled()));
      tap(R.id.game_identity_begin_edit); waitEnabled(screen, R.id.game_identity_name);
      fill(R.id.game_identity_name, "Updated");
      tap(R.id.game_identity_save); waitLocalSaved(screen); main(() -> fake.saves.get(2).onResult(null));
      main(() -> fake.serverLatest(profile("Updated")));
      screen.onActivity(a -> assertEquals(SessionState.Stage.COMPLETE, a.stateForTest().getStage()));
      evidence("profile");
    }
  }
  private static Matcher<View> notEnabled() { return org.hamcrest.Matchers.not(isEnabled()); }

  private static void evidence(String name) {
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    instrumentation.waitForIdleSync();
    Context context = ApplicationProvider.getApplicationContext();
    File folder = new File(context.getExternalFilesDir(null), "h07-evidence");
    assertTrue(folder.isDirectory() || folder.mkdirs());
    String prefix = InstrumentationRegistry.getArguments().getString("h07EvidencePrefix", "default")
        .replaceAll("[^A-Za-z0-9_-]", "_");
    Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
    assertNotNull(bitmap);
    try (FileOutputStream out = new FileOutputStream(new File(folder, prefix + "-main-" + name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
    } catch (java.io.IOException unavailable) { throw new AssertionError(unavailable); }
    finally { bitmap.recycle(); }
  }
  @Test public void cacheAndReadFailureNeverConfirmButBrowsingRemainsAvailable() {
    fake.uid = "fake-A";
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      onView(withId(R.id.game_main_dashboard)).check(matches(isDisplayed())); my();
      main(() -> fake.latestLoad().onResult(profile("Cached"), ProfilePort.Source.CACHE, null));
      main(() -> fake.latestLoad().onResult(null, ProfilePort.Source.SERVER, Failure.UNAVAILABLE));
      screen.onActivity(a -> { assertEquals(SessionState.Stage.PENDING_FAILURE, a.stateForTest().getStage()); assertTrue(a.stateForTest().isCached()); });
      onView(withId(R.id.game_identity_name)).check(matches(notEnabled()));
      tap(R.id.game_nav_home); onView(withId(R.id.game_main_dashboard)).check(matches(isDisplayed()));
      my(); tap(R.id.game_identity_retry); main(() -> fake.serverLatest(profile("Returning")));
      screen.onActivity(a -> assertEquals(SessionState.Stage.COMPLETE, a.stateForTest().getStage()));
      tap(R.id.game_identity_begin_edit); waitEnabled(screen, R.id.game_identity_name);
      onView(withId(R.id.game_identity_name)).check(matches(withText("Returning")));
    }
  }
  @Test public void canceledLoginAndFailedSaveCanRetry() {
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      tap(R.id.game_identity_welcome_login); main(() -> fake.login.onResult(Failure.CANCELED));
      screen.onActivity(a -> assertEquals(Failure.CANCELED, a.stateForTest().getFailure()));
      tap(R.id.game_identity_welcome_guest); onView(withId(R.id.game_main_dashboard)).check(matches(isDisplayed()));
      tap(R.id.game_main_google); main(() -> fake.loginAs("fake-A")); my();
      main(() -> fake.serverLatest(null)); waitEnabled(screen, R.id.game_identity_name); form();
      tap(R.id.game_identity_save); waitLocalSaved(screen); main(() -> fake.saves.get(0).onResult(Failure.PERMISSION_DENIED));
      onView(withId(R.id.game_identity_student)).check(matches(withText("00123")));
      screen.onActivity(a -> assertEquals(Failure.PERMISSION_DENIED, a.stateForTest().getFailure()));
      tap(R.id.game_identity_retry); main(() -> fake.saves.get(1).onResult(null));
      main(() -> fake.serverLatest(profile("Sample")));
      screen.onActivity(a -> assertEquals(SessionState.Stage.COMPLETE, a.stateForTest().getStage()));
    }
  }
  @Test public void staleUidAndLogoutCallbacksCannotRestoreOldProfile() {
    fake.uid = "fake-A";
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      ProfilePort.ProfileCallback late = fake.latestLoad();
      main(() -> fake.switchTo("fake-B")); main(() -> late.onResult(profile("Old"), ProfilePort.Source.SERVER, null));
      main(() -> fake.serverLatest(profile("New"))); my();
      screen.onActivity(a -> { assertEquals("fake-B", a.stateForTest().getUid()); assertEquals("New", a.stateForTest().getDraft().getName()); });
      tap(R.id.game_identity_logout);
      main(() -> { fake.uid = null; fake.logout.onResult(null); late.onResult(profile("Old"), ProfilePort.Source.SERVER, null); });
      screen.onActivity(a -> assertNull(a.stateForTest().getDraft()));
      tap(R.id.game_nav_home); onView(withId(R.id.game_main_dashboard)).check(matches(isDisplayed()));
    }
  }
  @Test public void draftSurvivesRecreationAndClearsOnSwitch() {
    fake.uid = "fake-A";
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      my(); main(() -> fake.serverLatest(null)); waitEnabled(screen, R.id.game_identity_name);
      fill(R.id.game_identity_name, "Draft"); fill(R.id.game_identity_student, "00123");
      screen.moveToState(Lifecycle.State.CREATED); screen.moveToState(Lifecycle.State.RESUMED);
      main(() -> fake.serverLatest(null)); screen.recreate(); main(() -> fake.serverLatest(null));
      waitEnabled(screen, R.id.game_identity_name);
      onView(withId(R.id.game_identity_name)).check(matches(withText("Draft")));
      main(() -> fake.switchTo("fake-B")); main(() -> fake.serverLatest(null));
      waitEnabled(screen, R.id.game_identity_name);
      onView(withId(R.id.game_identity_name)).check(matches(withText("")));
      screen.onActivity(a -> assertEquals("fake-B", a.stateForTest().getUid()));
    }
  }
  @Test public void recreatedShellBrowsesDuringNewAccountConfirmation() {
    fake.uid = "fake-A";
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      ProfilePort.ProfileCallback old = fake.latestLoad(); main(() -> fake.serverLatest(profile("A")));
      screen.moveToState(Lifecycle.State.CREATED); main(() -> fake.uid = "fake-B");
      screen.moveToState(Lifecycle.State.RESUMED); screen.recreate();
      main(() -> old.onResult(profile("A"), ProfilePort.Source.SERVER, null));
      main(() -> fake.latestLoad().onResult(profile("Cached B"), ProfilePort.Source.CACHE, null));
      onView(withId(R.id.game_main_dashboard)).check(matches(isDisplayed()));
      screen.onActivity(a -> assertEquals(SessionState.Stage.PROFILE_LOADING, a.stateForTest().getStage()));
      main(() -> fake.serverLatest(profile("B")));
      screen.onActivity(a -> { assertEquals("fake-B", a.stateForTest().getUid()); assertEquals("B", a.stateForTest().getDraft().getName()); });
    }
  }
  @Test public void guestChoiceRoutesToGuideAndTabsReturn() {
    try (ActivityScenario<IdentityActivity> screen = launch()) {
      tap(R.id.game_identity_welcome_guest); my(); waitEnabled(screen, R.id.game_identity_name);
      fill(R.id.game_identity_name, "Guest"); fill(R.id.game_identity_student, "00001");
      fill(R.id.game_identity_college, "Other college"); fill(R.id.game_identity_department, CampusChoice.SOFTWARE_DEPARTMENT);
      tap(R.id.game_identity_save); waitLocalSaved(screen);
      tap(R.id.game_nav_home); onView(withId(R.id.game_main_open_guide)).check(matches(notEnabled()));
      my(); fill(R.id.game_identity_college, CampusChoice.SOFTWARE_COLLEGE);
      fill(R.id.game_identity_department, CampusChoice.SOFTWARE_DEPARTMENT);
      tap(R.id.game_identity_save); waitLocalSaved(screen);
      screen.recreate(); tap(R.id.game_nav_home); waitEnabled(screen, R.id.game_main_open_guide);
      Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
      Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(GuideActivity.class.getName(), null, false);
      try {
        tap(R.id.game_main_open_guide);
        android.app.Activity guide = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
        assertNotNull(guide);
        try {
          GuideActivity actual = (GuideActivity) guide;
          boolean[] loaded = {false};
          long deadline = SystemClock.elapsedRealtime() + 5000;
          do {
            main(() -> loaded[0] = actual.guideForTest() != null);
            if (loaded[0]) break;
            SystemClock.sleep(40);
          } while (SystemClock.elapsedRealtime() < deadline);
          assertTrue("selected software asset must load", loaded[0]);
          main(() -> {
            assertEquals("software_guide_v1", actual.guideForTest().getDatasetId());
            assertEquals("digital_information_software", actual.guideForTest().getTargetId());
          });
        } finally { main(guide::finish); instrumentation.waitForIdleSync(); }
      } finally { instrumentation.removeMonitor(monitor); }
      tap(R.id.game_nav_map); onView(withId(R.id.campus_map)).check(matches(isDisplayed()));
      evidence("map");
      tap(R.id.game_nav_my); onView(withId(R.id.game_identity_root)).check(matches(isDisplayed()));
      tap(R.id.game_nav_home); screen.recreate();
      onView(withId(R.id.game_main_dashboard)).check(matches(isDisplayed()));
      tap(R.id.game_nav_map); onView(withId(R.id.campus_map)).check(matches(isDisplayed()));
      tap(R.id.game_nav_home); onView(withId(R.id.game_main_dashboard)).check(matches(isDisplayed()));
      my(); screen.recreate();
      onView(withId(R.id.game_identity_root)).check(matches(isDisplayed()));
      tap(R.id.game_nav_home); evidence("home");
    }
  }
}
