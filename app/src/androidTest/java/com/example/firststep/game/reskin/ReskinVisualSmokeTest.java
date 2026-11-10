package com.example.firststep.game.reskin;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.RouteMapActivity;
import com.example.firststep.game.auth.SessionState;
import com.example.firststep.game.composition.home.CampusHomeRoute;
import com.example.firststep.game.composition.integration.IntegrationContext;
import com.example.firststep.game.composition.integration.IntegrationIntentCodec;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.ui.discovery.DiscoveryActivity;
import com.example.firststep.game.ui.guide.GuideActivity;
import com.example.firststep.game.ui.guide.NarrationActivity;
import com.example.firststep.game.ui.home.CampusHomeActivity;
import com.example.firststep.game.ui.home.GameHomeActivity;
import com.example.firststep.game.ui.identity.IdentityActivity;
import com.example.firststep.map.CampusMapView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Pixel evidence from real Activities. The debug DEMO uses a fresh local run and no account. */
@RunWith(AndroidJUnit4.class)
public final class ReskinVisualSmokeTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();
  private int orientation;
  private float fontScale;
  private String prefix;

  @Before public void readCaptureEnvironment() {
    Bundle args = InstrumentationRegistry.getArguments();
    orientation = Integer.parseInt(args.getString("reskinOrientation", "1"));
    assertTrue(orientation == Configuration.ORIENTATION_PORTRAIT
        || orientation == Configuration.ORIENTATION_LANDSCAPE);
    fontScale = Float.parseFloat(args.getString("reskinFontScale", "1.0"));
    assertTrue(Float.isFinite(fontScale) && fontScale > 0);
    prefix = args.getString("reskinEvidencePrefix", "default");
    assertTrue(prefix.matches("[A-Za-z0-9_-]{1,50}"));
  }

  @Test public void unconfiguredIdentityShowsReachableDisabledGoogleControl() throws Exception {
    assertFalse("Use an unconfigured APK for this evidence", BuildConfig.H07_CONFIGURED);
    try (ActivityScenario<IdentityActivity> screen =
        ActivityScenario.launch(new Intent(context, IdentityActivity.class))) {
      ready(IdentityActivity.class, activity ->
          activity.stateForTest().getStage() == SessionState.Stage.UNCONFIGURED
              && activity.stateForTest().getUid() == null
              && activity.findViewById(R.id.game_identity_welcome).isShown());
      capture(IdentityActivity.class, "identity-welcome");
      tap(R.id.game_identity_welcome_guest);
      ready(GuideActivity.class, activity ->
          activity.guideForTest() != null && "GUIDE".equals(activity.panelForTest()));
      androidx.test.espresso.Espresso.pressBack();
      ready(IdentityActivity.class, activity ->
          activity.stateForTest().getUid() == null
              && activity.findViewById(R.id.game_identity_welcome).isShown());
      tap(R.id.game_identity_welcome_login);
      capture(IdentityActivity.class, "identity-unconfigured");
      onView(withId(R.id.game_identity_login)).perform(reveal()).check(matches(
          allOf(withText(R.string.game_identity_login), not(isEnabled()), isDisplayed())));
      onView(withId(R.id.game_identity_status)).perform(reveal())
          .check(matches(withText(R.string.game_identity_unconfigured)));
      capture(IdentityActivity.class, "identity-unconfigured-controls");
      tap(R.id.game_identity_back);
    }
  }

  @Test public void campusHomeReachesBundledGuideNarrationAndTransientPractice() throws Exception {
    try (ActivityScenario<CampusHomeActivity> screen =
        ActivityScenario.launch(CampusHomeRoute.intent(context))) {
      ready(CampusHomeActivity.class, activity -> showsText(activity,
          R.id.game_campus_progress_state, R.string.game_campus_progress_unavailable));
      capture(CampusHomeActivity.class, "campus-home-closed-registration");
      onView(withId(R.id.game_campus_progress_state)).perform(reveal()).check(matches(isDisplayed()));
      onView(withId(R.id.game_campus_home_map)).perform(reveal()).check(matches(isEnabled()));
      onView(withId(R.id.game_campus_home_facilities)).perform(reveal()).check(matches(isEnabled()));
      tap(R.id.game_campus_home_guide);
      ready(GuideActivity.class, activity ->
          activity.guideForTest() != null && "GUIDE".equals(activity.panelForTest()));
      onView(withText(R.string.game_guide_unverified)).perform(reveal()).check(matches(isDisplayed()));
      capture(GuideActivity.class, "software-guide-bundled");
      onView(withId(R.id.game_guide_demo_entry)).perform(reveal()).check(matches(isEnabled()));
      tap(R.id.game_narration_entry);

      ready(NarrationActivity.class, activity -> activity.findViewById(R.id.narration_controls).isShown());
      onView(withId(R.id.narration_caption)).perform(reveal())
          .check(matches(withText("안녕하세요. 디지털정보관을 소개할 안내 요정이에요.")));
      capture(NarrationActivity.class, "software-narration-bundled");
      tap(R.id.narration_next);
      onView(withId(R.id.narration_position)).perform(reveal())
          .check(matches(withText(context.getString(R.string.game_narration_position, 2, 7))));
      tap(R.id.narration_skip);

      ready(GuideActivity.class, activity -> "PRACTICE".equals(activity.panelForTest()));
      choose("location", "digital_first");
      capture(GuideActivity.class, "software-practice-transient");
      choose("values", "communicate");
      choose("notice", "official");
      tap(R.id.game_guide_primary);
      ready(GuideActivity.class, activity -> "RESULT".equals(activity.panelForTest()));
      onView(withTagValue(is("guide_score"))).perform(reveal()).check(matches(
          withText(context.getString(R.string.game_guide_score, 3, 3))));
      capture(GuideActivity.class, "software-practice-result-no-reward");
      tap(R.id.game_guide_close);
      ready(CampusHomeActivity.class, activity -> true);
      tap(R.id.game_campus_home_back);
    }
  }

  @Test public void isolatedLocalDemoReachesDisclosureAndDiscoveryWithoutCompleting() throws Exception {
    assertTrue("DEMO route belongs to debug APK", BuildConfig.DEBUG);
    IntegrationContext run = IntegrationContext.create(IntegrationContext.SOFTWARE_DATASET,
        UUID.randomUUID().toString(), IntegrationContext.TARGET, Progress.Mode.DEMO,
        IntegrationContext.Clock.REAL, null);
    Intent launch = new Intent(context, GameHomeActivity.class);
    IntegrationIntentCodec.writeContext(launch, run);
    try (ActivityScenario<GameHomeActivity> screen = ActivityScenario.launch(launch)) {
      ready(GameHomeActivity.class, activity -> activity.findViewById(R.id.game_home_prototype).isEnabled()
          && showsText(activity, R.id.game_home_status, R.string.game_software_demo_ready));
      onView(withId(R.id.game_integration_home_context)).perform(reveal())
          .check(matches(withText(R.string.game_software_demo_disclosure)));
      capture(GameHomeActivity.class, "software-local-demo-isolated");
      tap(R.id.game_home_prototype);
      ready(DiscoveryActivity.class, activity -> activity.snapshot() != null
          && activity.findViewById(R.id.game_discovery_arrival).isEnabled());
      capture(DiscoveryActivity.class, "software-local-demo-simulated-arrival-entry");
      if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        onView(withId(R.id.game_discovery_arrival)).check(matches(
            allOf(isDisplayingAtLeast(90), isEnabled())));
        onView(withId(R.id.game_discovery_camera_fallback)).check(matches(
            allOf(isDisplayingAtLeast(90), isEnabled())));
      }
      onView(withId(R.id.game_integration_discovery_context)).perform(reveal())
          .check(matches(withText(R.string.game_software_demo_discovery_disclosure)));
      onView(withId(R.id.game_discovery_arrival)).perform(reveal()).check(matches(isEnabled()));
      onView(withId(R.id.game_discovery_camera_fallback)).perform(reveal())
          .check(matches(isEnabled()));
      tap(R.id.game_discovery_exit);
      ready(GameHomeActivity.class, activity -> activity.findViewById(R.id.game_home_back).isShown());
      tap(R.id.game_home_back);
    }
  }

  @Test public void selectedFacilityMapShowsRealCanvasAndAttribution() throws Exception {
    try (ActivityScenario<RouteMapActivity> screen = ActivityScenario.launch(
        RouteMapActivity.facilityBrowseIntent(context, "library-central"))) {
      String selected = context.getString(R.string.map_facility_context_unverified, "중앙도서관");
      ready(RouteMapActivity.class, activity -> {
        TextView label = activity.findViewById(R.id.map_facility_context);
        return selected.contentEquals(label.getText());
      });
      capture(RouteMapActivity.class, "browse-map-selected-unverified-facility");
      ready(RouteMapActivity.class, ReskinVisualSmokeTest::hasVisibleMapAndCredit);
      onView(withId(R.id.map_facility_context)).perform(reveal()).check(matches(withText(selected)));
      for (int id : new int[] {R.id.map_attribution, R.id.map_zoom_in, R.id.map_zoom_out,
          R.id.map_campus, R.id.map_location, R.id.map_retry, R.id.map_facility_lookup,
          R.id.map_back}) {
        onView(withId(id)).perform(reveal()).check(matches(isEnabled()));
      }
      capture(RouteMapActivity.class, "browse-map-controls-reachable");
      ready(RouteMapActivity.class, ReskinVisualSmokeTest::hasVisibleMapAndCredit);
      tap(R.id.map_back);
    }
  }

  private static boolean hasVisibleMapAndCredit(RouteMapActivity activity) {
    CampusMapView map = activity.findViewById(R.id.campus_map);
    Rect viewport = new Rect();
    if (!map.getLocalVisibleRect(viewport) || viewport.isEmpty()) return false;
    Rect credit = map.attributionBoundsForTest();
    // Reserve at least two 48dp touch-target heights for the drawable map above the credit.
    int minimumMapHeight = (int) Math.ceil(96 * map.getResources().getDisplayMetrics().density);
    return !credit.isEmpty() && viewport.contains(credit)
        && credit.top - viewport.top >= minimumMapHeight;
  }

  private static boolean showsText(Activity activity, int id, int label) {
    TextView view = activity.findViewById(id);
    return view != null && activity.getString(label).contentEquals(view.getText());
  }

  private <A extends Activity> void ready(Class<A> type, Predicate<A> settled) {
    int requested = orientation == Configuration.ORIENTATION_PORTRAIT
        ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    await(type.getSimpleName() + " visible with expected content and orientation", () -> {
      AtomicBoolean result = new AtomicBoolean();
      instrumentation.runOnMainSync(() -> {
        for (Activity activity : resumedActivities()) {
          if (!type.isInstance(activity)) continue;
          if (activity.getRequestedOrientation() != requested) {
            activity.setRequestedOrientation(requested);
            continue;
          }
          Configuration config = activity.getResources().getConfiguration();
          if (config.orientation != orientation) continue;
          assertEquals("Prepare actual font scale outside instrumentation", fontScale, config.fontScale, 0.01f);
          result.set(settled.test(type.cast(activity)));
        }
      });
      return result.get();
    });
  }

  private static Iterable<Activity> resumedActivities() {
    return ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
  }

  private static void await(String description, BooleanSupplier condition) {
    long deadline = SystemClock.uptimeMillis() + 10_000;
    while (SystemClock.uptimeMillis() < deadline) {
      if (condition.getAsBoolean()) return;
      SystemClock.sleep(50);
    }
    assertTrue(description, condition.getAsBoolean());
  }

  private static void tap(int id) {
    onView(withId(id)).perform(reveal(), click());
  }

  private static void choose(String question, String option) {
    onView(withTagValue(is("guide_option:" + question + ":" + option))).perform(reveal(), click());
  }

  private static ViewAction reveal() {
    return new ViewAction() {
      @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
      @Override public String getDescription() { return "Reveal and check a real control"; }

      @Override public void perform(UiController ui, View view) {
        ViewParent parent = view.getParent();
        while (parent != null && !(parent instanceof ScrollView)) parent = parent.getParent();
        if (parent instanceof ScrollView) {
          ScrollView scroll = (ScrollView) parent;
          Rect bounds = new Rect();
          view.getDrawingRect(bounds);
          scroll.offsetDescendantRectToMyCoords(view, bounds);
          int available = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
          scroll.scrollTo(0, Math.max(0, bounds.centerY() - available / 2 - scroll.getPaddingTop()));
          ui.loopMainThreadUntilIdle();
        }
        assertTrue("View should be reachable", isDisplayingAtLeast(90).matches(view));
        if (view instanceof Button) {
          assertTrue("Touch height must be at least 48dp",
              view.getHeight() + 1 >= 48 * view.getResources().getDisplayMetrics().density);
          TextView button = (TextView) view;
          assertNotNull(button.getLayout());
          for (int line = 0; line < button.getLayout().getLineCount(); line++)
            assertEquals("Action label should not be ellipsized", 0, button.getLayout().getEllipsisCount(line));
        }
      }
    };
  }

  private void capture(Class<? extends Activity> type, String name) throws Exception {
    ready(type, activity -> activity.getWindow().getDecorView().hasWindowFocus());
    instrumentation.waitForIdleSync();
    File external = context.getExternalFilesDir(null);
    assertNotNull("App-specific external files directory", external);
    File directory = new File(external, "reskin-evidence");
    assertTrue(directory.isDirectory() || directory.mkdirs());
    Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
    assertNotNull(screenshot);
    try {
      assertEquals("Full-display screenshot orientation", orientation == Configuration.ORIENTATION_LANDSCAPE,
          screenshot.getWidth() > screenshot.getHeight());
      try (FileOutputStream output = new FileOutputStream(new File(directory, prefix + "-" + name + ".png"))) {
        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
      }
    } finally {
      screenshot.recycle();
    }
  }
}
