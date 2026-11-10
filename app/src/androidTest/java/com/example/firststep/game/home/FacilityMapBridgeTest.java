package com.example.firststep.game.home;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.FacilityDirectoryActivity;
import com.example.firststep.R;
import com.example.firststep.RouteMapActivity;
import com.example.firststep.facility.FacilityRepository;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FacilityMapBridgeTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();

  @Test public void directoryDetailOpensMapContextWithoutChangingFavorites() {
    Set<String> before = new FacilityRepository(context).favorites();
    Instrumentation.ActivityMonitor monitor =
        instrumentation.addMonitor(RouteMapActivity.class.getName(), null, false);
    try (ActivityScenario<FacilityDirectoryActivity> directory =
        ActivityScenario.launch(new Intent(context, FacilityDirectoryActivity.class))) {
      selectFacility("중앙도서관");
      onView(withText(R.string.facility_map_open)).perform(scrollTo(), click());
      Activity map = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
      assertNotNull(map);
      try {
        awaitContext(map, context.getString(R.string.map_facility_context_unverified, "중앙도서관"));
        instrumentation.runOnMainSync(() -> {
          assertEquals(View.GONE, map.findViewById(R.id.map_route).getVisibility());
          assertEquals(View.VISIBLE, map.findViewById(R.id.map_attribution).getVisibility());
        });
        assertEquals(before, new FacilityRepository(context).favorites());
      } finally {
        instrumentation.runOnMainSync(map::finish);
      }
    } finally {
      instrumentation.removeMonitor(monitor);
    }
  }

  @Test public void mapLookupUpdatesExistingMapAndSurvivesRecreationAndCancel() {
    try (ActivityScenario<RouteMapActivity> scenario =
        ActivityScenario.launch(RouteMapActivity.browseIntent(context))) {
      onView(withId(R.id.map_facility_lookup)).perform(click());
      selectFacility("보건진료소");
      onView(withText(R.string.facility_map_select)).perform(scrollTo(), click());
      AtomicReference<RouteMapActivity> activity = new AtomicReference<>();
      scenario.onActivity(activity::set);
      String expected = context.getString(R.string.map_facility_context_unverified, "보건진료소");
      awaitContext(activity.get(), expected);

      onView(withId(R.id.map_facility_lookup)).perform(click());
      onView(withText("←  돌아가기")).perform(click());
      scenario.onActivity(visible -> assertSame(activity.get(), visible));
      awaitContext(activity.get(), expected);

      scenario.recreate();
      scenario.onActivity(activity::set);
      awaitContext(activity.get(), expected);
    }
  }

  @Test public void unknownCatalogIdShowsUnavailableRatherThanEchoingInput() {
    try (ActivityScenario<RouteMapActivity> scenario =
        ActivityScenario.launch(RouteMapActivity.facilityBrowseIntent(context, "unknown-facility"))) {
      AtomicReference<RouteMapActivity> activity = new AtomicReference<>();
      scenario.onActivity(activity::set);
      awaitContext(activity.get(), context.getString(R.string.map_facility_context_unavailable));
    }
  }

  private static void selectFacility(String name) {
    onView(withContentDescription("시설 검색")).perform(replaceText(name), closeSoftKeyboard());
    onView(withContentDescription(startsWith(name + ", "))).perform(scrollTo(), click());
  }

  private void awaitContext(Activity map, String expected) {
    AtomicReference<String> actual = new AtomicReference<>();
    long until = SystemClock.uptimeMillis() + 5000L;
    do {
      instrumentation.runOnMainSync(() -> actual.set(
          ((TextView) map.findViewById(R.id.map_facility_context)).getText().toString()));
      if (expected.equals(actual.get())) return;
      SystemClock.sleep(50L);
    } while (SystemClock.uptimeMillis() < until);
    assertEquals(expected, actual.get());
  }
}
