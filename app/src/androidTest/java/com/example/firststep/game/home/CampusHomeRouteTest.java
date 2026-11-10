package com.example.firststep.game.home;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.FacilityDirectoryActivity;
import com.example.firststep.R;
import com.example.firststep.RouteMapActivity;
import com.example.firststep.game.composition.home.CampusHomeRoute;
import com.example.firststep.game.ui.authored.AuthoredEpisodeActivity;
import com.example.firststep.game.ui.guide.GuideActivity;
import com.example.firststep.game.ui.home.CampusHomeActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CampusHomeRouteTest {
  @Test
  public void explicitEntryIsInternalAndHasNoIdentityOrProgressExtras() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Intent intent = CampusHomeRoute.intent(context);
    assertEquals(CampusHomeActivity.class.getName(), intent.getComponent().getClassName());
    assertNull(intent.getExtras());
    ActivityInfo info = context.getPackageManager().getActivityInfo(intent.getComponent(), 0);
    assertFalse(info.exported);
  }

  @Test
  public void authoredEntryUsesInternalComponentWithoutExtras() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Intent intent = AuthoredEpisodeActivity.intent(context);
    assertEquals(AuthoredEpisodeActivity.class.getName(), intent.getComponent().getClassName());
    assertNull(intent.getExtras());
    assertFalse(context.getPackageManager().getActivityInfo(intent.getComponent(), 0).exported);
    try (ActivityScenario<CampusHomeActivity> scenario =
        ActivityScenario.launch(CampusHomeRoute.intent(context))) {
      verifyDestination(R.id.game_campus_home_authored, AuthoredEpisodeActivity.class);
    }
  }

  @Test
  public void homeButtonsReachExistingCampusSurfaces() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    try (ActivityScenario<CampusHomeActivity> scenario =
        ActivityScenario.launch(CampusHomeRoute.intent(context))) {
      verifyDestination(R.id.game_campus_home_map, RouteMapActivity.class);
      verifyDestination(R.id.game_campus_home_facilities, FacilityDirectoryActivity.class);
      verifyDestination(R.id.game_campus_home_guide, GuideActivity.class);
    }
  }

  private static void verifyDestination(int buttonId, Class<? extends Activity> destination) {
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    Instrumentation.ActivityMonitor monitor =
        instrumentation.addMonitor(destination.getName(), null, false);
    try {
      onView(withId(buttonId)).perform(scrollTo(), click());
      Activity opened = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
      assertNotNull("Campus destination did not open", opened);
      instrumentation.runOnMainSync(opened::finish);
      instrumentation.waitForIdleSync();
    } finally {
      instrumentation.removeMonitor(monitor);
    }
  }
}
