package com.example.firststep.game.integration;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.*;
import androidx.test.espresso.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import com.example.firststep.R;
import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.domain.*;
import com.example.firststep.game.presentation.introquiz.IntroQuizController;
import com.example.firststep.game.ui.discovery.DiscoveryActivity;
import com.example.firststep.game.ui.home.GameHomeActivity;
import com.example.firststep.game.ui.intro.IntroQuizActivity;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.*;
import org.hamcrest.Matcher;

final class IntegrationTestSupport {
  static final long START = 1_800_000_000_000L;

  static Context context() {
    return ApplicationProvider.getApplicationContext();
  }

  static void main(Runnable r) {
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) r.run();
    else InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
  }

  static IntegrationContext context(Progress.Mode mode) {
    return context(UUID.randomUUID().toString(), mode);
  }

  static IntegrationContext context(String run, Progress.Mode mode) {
    return IntegrationContext.create(
        IntegrationContext.DATASET,
        run,
        IntegrationContext.TARGET,
        mode,
        IntegrationContext.Clock.DEBUG_FAKE,
        START);
  }

  static Intent home(IntegrationContext c) {
    Intent i = new Intent(context(), GameHomeActivity.class);
    IntegrationIntentCodec.writeContext(i, c);
    return i;
  }

  static <T extends Activity> T top(Class<T> type) {
    Activity[] found = {null};
    main(
        () -> {
          for (Activity a :
              ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
            if (type.isInstance(a)) found[0] = a;
        });
    return type.cast(found[0]);
  }

  static void await(String message, BooleanSupplier condition) {
    long end = SystemClock.elapsedRealtime() + 15000;
    do {
      if (condition.getAsBoolean()) return;
      SystemClock.sleep(50);
    } while (SystemClock.elapsedRealtime() < end);
    assertTrue(message, condition.getAsBoolean());
  }

  static <T extends Activity> T waitTop(Class<T> type) {
    await("Expected resumed " + type.getSimpleName(), () -> top(type) != null);
    int expected =
        Integer.parseInt(InstrumentationRegistry.getArguments().getString("h06Orientation", "1"));
    T found = top(type);
    main(
        () ->
            found.setRequestedOrientation(
                expected == 2
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    await(
        "Actual requested orientation",
        () -> {
          T a = top(type);
          boolean[] ok = {false};
          if (a != null)
            main(() -> ok[0] = a.getResources().getConfiguration().orientation == expected);
          return ok[0];
        });
    return top(type);
  }

  static void homeReady() {
    waitTop(GameHomeActivity.class);
    await(
        "Home loaded",
        () -> {
          GameHomeActivity a = top(GameHomeActivity.class);
          if (a == null) return false;
          boolean[] ok = {false};
          main(
              () ->
                  ok[0] =
                      a.snapshot() != null
                          && !a.snapshot().loading
                          && a.snapshot().progress != null);
          return ok[0];
        });
  }

  static IntroQuizController.Snapshot intro() {
    IntroQuizActivity a = top(IntroQuizActivity.class);
    if (a == null) return null;
    IntroQuizController.Snapshot[] s = {null};
    main(() -> s[0] = a.snapshot());
    return s[0];
  }

  static void stage(IntroQuizController.Stage expected) {
    waitTop(IntroQuizActivity.class);
    await("Intro stage " + expected, () -> intro() != null && intro().stage == expected);
  }

  static ViewAction reveal() {
    return new ViewAction() {
      public Matcher<View> getConstraints() {
        return isAssignableFrom(View.class);
      }

      public String getDescription() {
        return "reveal H06 control in actual scroll viewport";
      }

      public void perform(UiController ui, View v) {
        ViewParent p = v.getParent();
        while (p != null && !(p instanceof ScrollView)) p = p.getParent();
        assertTrue(p instanceof ScrollView);
        ScrollView scroll = (ScrollView) p;
        Rect b = new Rect();
        v.getDrawingRect(b);
        scroll.offsetDescendantRectToMyCoords(v, b);
        int height = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        scroll.scrollTo(0, Math.max(0, b.centerY() - height / 2 - scroll.getPaddingTop()));
        ui.loopMainThreadForAtLeast(200);
        assertTrue(
            "Control visible id="
                + v.getId()
                + " height="
                + v.getHeight()
                + " viewport="
                + height
                + " scroll="
                + scroll.getScrollY(),
            isDisplayingAtLeast(90).matches(v));
      }
    };
  }

  static void press(int id) {
    onView(withId(id)).perform(reveal(), click());
  }

  static void option(String id) {
    onView(withTagValue(is((Object) id))).perform(reveal(), click());
  }

  static void hold() {
    onView(withId(R.id.game_discovery_hold))
        .perform(
            reveal(),
            new ViewAction() {
              public Matcher<View> getConstraints() {
                return isDisplayed();
              }

              public String getDescription() {
                return "explicit simulated 1.8 second hold input";
              }

              public void perform(UiController ui, View v) {
                long now = SystemClock.uptimeMillis();
                MotionEvent down =
                    MotionEvent.obtain(
                        now,
                        now,
                        MotionEvent.ACTION_DOWN,
                        v.getWidth() / 2f,
                        v.getHeight() / 2f,
                        0);
                v.dispatchTouchEvent(down);
                down.recycle();
                ui.loopMainThreadForAtLeast(1800);
                MotionEvent up =
                    MotionEvent.obtain(
                        now,
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP,
                        v.getWidth() / 2f,
                        v.getHeight() / 2f,
                        0);
                v.dispatchTouchEvent(up);
                up.recycle();
              }
            });
  }

  static void discover(IntegrationContext c, boolean recreate) throws Exception {
    homeReady();
    press(R.id.game_home_prototype);
    waitTop(DiscoveryActivity.class);
    await(
        "Discovery initialized",
        () -> {
          DiscoveryActivity a = top(DiscoveryActivity.class);
          boolean[] ok = {false};
          if (a != null) main(() -> ok[0] = a.snapshot() != null);
          return ok[0];
        });
    press(R.id.game_discovery_arrival);
    press(R.id.game_discovery_camera_fallback);
    press(R.id.game_discovery_align_fallback);
    hold();
    await(
        "Retained discovery result",
        () -> {
          DiscoveryActivity a = top(DiscoveryActivity.class);
          boolean[] ok = {false};
          if (a != null) main(() -> ok[0] = a.integrationResultForTest() != null);
          return ok[0];
        });
    assertNull(IntegrationRouteAccess.readProgress(context(), c).introDoneAt);
    assertEquals(
        com.example.firststep.game.presentation.integration.NavigationPolicy.Phase.PENDING,
        new NavigationStore(context()).load(c).phase);
    if (recreate) {
      DiscoveryActivity old = top(DiscoveryActivity.class);
      main(old::recreate);
      await(
          "Recreated completed discovery",
          () -> {
            DiscoveryActivity a = top(DiscoveryActivity.class);
            boolean[] ok = {false};
            if (a != null && a != old) main(() -> ok[0] = a.integrationResultForTest() != null);
            return ok[0];
          });
    }
    capture("discovery-simulated-arrival-hold-result");
    press(R.id.game_integration_discovery_continue);
    waitTop(IntroQuizActivity.class);
    stage(IntroQuizController.Stage.INTRO);
    assertNull(intro().progress.introDoneAt);
    assertEquals(
        com.example.firststep.game.presentation.integration.NavigationPolicy.Phase.CONSUMED,
        new NavigationStore(context()).load(c).phase);
  }

  static void capture(String name) throws Exception {
    Activity activity = waitTop(Activity.class);
    main(
        () -> {
          android.os.Bundle args = InstrumentationRegistry.getArguments();
          assertEquals(
              Integer.parseInt(args.getString("h06Orientation", "1")),
              activity.getResources().getConfiguration().orientation);
          assertEquals(
              Float.parseFloat(args.getString("h06FontScale", "1.0")),
              activity.getResources().getConfiguration().fontScale,
              0.01f);
        });
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    SystemClock.sleep(250);
    String prefix =
        InstrumentationRegistry.getArguments().getString("h06EvidencePrefix", "default");
    assertTrue(prefix.matches("[A-Za-z0-9_-]+"));
    File folder = new File(context().getExternalFilesDir(null), "h06-evidence");
    assertTrue(folder.isDirectory() || folder.mkdirs());
    Bitmap b = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(b);
    try (FileOutputStream out =
        new FileOutputStream(new File(folder, prefix + "-" + name + ".png"))) {
      assertTrue(b.compress(Bitmap.CompressFormat.PNG, 100, out));
    } finally {
      b.recycle();
    }
  }

  static void fits(int id) {
    onView(withId(id)).perform(reveal());
    Activity a = waitTop(Activity.class);
    main(
        () -> {
          TextView v = a.findViewById(id);
          assertNotNull(v.getLayout());
          for (int n = 0; n < v.getLayout().getLineCount(); n++)
            assertEquals(0, v.getLayout().getEllipsisCount(n));
          assertTrue(
              v.getLayout().getHeight()
                  <= v.getHeight() - v.getCompoundPaddingTop() - v.getCompoundPaddingBottom());
          if (v.isClickable())
            assertTrue(v.getHeight() >= 48 * v.getResources().getDisplayMetrics().density);
        });
  }

  static Map<String, String> databases() throws Exception {
    Map<String, String> result = new TreeMap<>();
    File dir = context().getDatabasePath("unused").getParentFile();
    if (dir != null && dir.isDirectory())
      for (File f : dir.listFiles())
        if (f.isFile())
          result.put(
              f.getName(),
              Base64.getEncoder()
                  .encodeToString(
                      MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath()))));
    return result;
  }

  static void preserved(Map<String, String> before) throws Exception {
    Map<String, String> after = databases();
    for (String name : before.keySet())
      assertEquals("Existing DB preserved: " + name, before.get(name), after.get(name));
  }

  static void closeActivities() {
    main(
        () -> {
          for (Stage stage :
              new Stage[] {Stage.RESUMED, Stage.STARTED, Stage.CREATED, Stage.STOPPED})
            for (Activity a :
                new ArrayList<>(
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)))
              a.finish();
        });
  }
}
