package com.example.firststep.game.discovery;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.game.composition.discovery.DiscoveryTestDriver;
import com.example.firststep.game.presentation.discovery.DiscoveryState;
import com.example.firststep.game.ui.discovery.DiscoveryActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.util.function.Predicate;
import org.hamcrest.Matcher;

final class DiscoveryTestSupport {
    private DiscoveryTestSupport() {}

    static ActivityScenario<DiscoveryActivity> launch(boolean simulated) {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, DiscoveryActivity.class);
        if (simulated) intent.putExtra("com.example.firststep.discovery.MODE", "SIMULATED");
        ActivityScenario<DiscoveryActivity> screen = ActivityScenario.launch(intent);
        String requested = InstrumentationRegistry.getArguments().getString("h03Orientation");
        if (requested != null) {
            int expected = Integer.parseInt(requested);
            screen.onActivity(activity -> activity.setRequestedOrientation(
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
            assertEquals("Requested orientation must actually be applied", expected, actual[0]);
        }
        screen.onActivity(activity -> {
            String scale = InstrumentationRegistry.getArguments().getString("h03FontScale");
            if (scale != null) assertEquals(Float.parseFloat(scale),
                    activity.getResources().getConfiguration().fontScale, 0.01f);
        });
        return screen;
    }

    static void press(int id) {
        onView(withId(id)).perform(reveal(), click());
    }

    // Center in the inset viewport, then verify visibility before using Espresso's real click.
    // The default scrollTo action failed on lower controls; an actual device swipe was verified.
    private static ViewAction reveal() {
        return new ViewAction() {
            public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            public String getDescription() { return "scroll control into the visible inset viewport"; }
            public void perform(UiController ui, View view) {
                ViewParent parent = view.getParent();
                while (parent != null && !(parent instanceof ScrollView)) parent = parent.getParent();
                assertTrue("Discovery controls must be scrollable", parent instanceof ScrollView);
                ScrollView scroll = (ScrollView) parent;
                Rect bounds = new Rect();
                view.getDrawingRect(bounds);
                scroll.offsetDescendantRectToMyCoords(view, bounds);
                int viewport = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
                scroll.scrollTo(0, Math.max(0, bounds.centerY() - viewport / 2 - scroll.getPaddingTop()));
                ui.loopMainThreadForAtLeast(200);
                assertTrue("Control must actually be visible after scrolling", isDisplayingAtLeast(90).matches(view));
            }
        };
    }

    static DiscoveryState state(ActivityScenario<DiscoveryActivity> screen) {
        DiscoveryState[] value = new DiscoveryState[1];
        screen.onActivity(a -> value[0] = a.snapshot());
        assertNotNull(value[0]);
        return value[0];
    }

    static DiscoveryTestDriver driver(ActivityScenario<DiscoveryActivity> screen) {
        DiscoveryTestDriver[] value = new DiscoveryTestDriver[1];
        screen.onActivity(a -> value[0] = DiscoveryTestDriver.current());
        assertNotNull("Simulated driver must be explicit", value[0]);
        return value[0];
    }

    static void await(ActivityScenario<DiscoveryActivity> screen, Predicate<DiscoveryState> ready) {
        long deadline = SystemClock.elapsedRealtime() + 12000;
        do {
            if (ready.test(state(screen))) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertTrue("Expected discovery state was not reached", ready.test(state(screen)));
    }

    static void touch(ActivityScenario<DiscoveryActivity> screen, int id, int action) {
        screen.onActivity(a -> {
            View view = a.findViewById(id);
            long now = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(now, now, action,
                    view.getWidth() / 2f, view.getHeight() / 2f, 0);
            try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
        });
    }

    static void assertTextFits(ActivityScenario<DiscoveryActivity> screen, int id) {
        onView(withId(id)).perform(reveal());
        screen.onActivity(a -> {
            TextView view = a.findViewById(id);
            assertNotNull(view.getLayout());
            for (int i = 0; i < view.getLayout().getLineCount(); i++)
                assertEquals("Text must not be ellipsized", 0, view.getLayout().getEllipsisCount(i));
            assertTrue("Text must fit the View", view.getLayout().getHeight()
                    <= view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom());
            if (view.isClickable()) assertTrue("Touch target must be >=48dp",
                    view.getHeight() >= 48 * view.getResources().getDisplayMetrics().density);
        });
    }

    static void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(400);
        Context context = ApplicationProvider.getApplicationContext();
        File folder = new File(context.getExternalFilesDir(null), "h03-evidence");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        String prefix = InstrumentationRegistry.getArguments().getString("h03EvidencePrefix", "default");
        assertTrue(prefix.matches("[A-Za-z0-9_-]+"));
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Screenshot must come from the running APK", bitmap);
        try (FileOutputStream out = new FileOutputStream(new File(folder, prefix + "-" + name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { bitmap.recycle(); }
    }
}
