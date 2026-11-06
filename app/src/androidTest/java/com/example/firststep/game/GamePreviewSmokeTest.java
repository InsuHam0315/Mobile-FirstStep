package com.example.firststep.game;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.MainActivity;
import com.example.firststep.R;
import com.example.firststep.game.ui.preview.GamePreviewActivity;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real APK checks with simulated catalog data; grants and radios are configured by the runner. */
@RunWith(AndroidJUnit4.class)
public final class GamePreviewSmokeTest {
    @Test public void fakePreviewWorksWithoutPermissionsAndRestoresSelection() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        assertDenied(context, Manifest.permission.CAMERA);
        assertDenied(context, Manifest.permission.ACCESS_FINE_LOCATION);
        assertDenied(context, Manifest.permission.ACCESS_COARSE_LOCATION);
        try (ActivityScenario<GamePreviewActivity> screen = ActivityScenario.launch(
                new Intent(context, GamePreviewActivity.class))) {
            String orientation = InstrumentationRegistry.getArguments().getString("h01Orientation");
            if (orientation != null) {
                int expected = Integer.parseInt(orientation);
                screen.onActivity(activity -> activity.setRequestedOrientation(
                        expected == Configuration.ORIENTATION_LANDSCAPE
                                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                waitForOrientation(screen, expected);
            }
            waitForText(R.string.game_preview_ready);
            onView(withText(R.string.game_preview_notice)).perform(scrollTo())
                    .check(matches(isCompletelyDisplayed()));
            capture("preview-ready-simulated");
            onView(withId(R.id.game_preview_item)).perform(scrollTo())
                    .check(matches(isCompletelyDisplayed()));
            screen.onActivity(activity -> {
                Button button = activity.findViewById(R.id.game_preview_item);
                assertTrue("Touch target must be at least 48dp",
                        button.getHeight() >= 48 * activity.getResources().getDisplayMetrics().density);
                assertTextFits(button);
                String expectedScale = InstrumentationRegistry.getArguments().getString("h01FontScale");
                if (expectedScale != null) {
                    assertEquals(Float.parseFloat(expectedScale),
                            activity.getResources().getConfiguration().fontScale, 0.01f);
                }
                String expectedOrientation = InstrumentationRegistry.getArguments().getString("h01Orientation");
                if (expectedOrientation != null) {
                    assertEquals(Integer.parseInt(expectedOrientation),
                            activity.getResources().getConfiguration().orientation);
                }
            });
            onView(withId(R.id.game_preview_item)).perform(click());
            waitForText(R.string.game_preview_selected);
            capture("preview-selected-simulated");
            screen.recreate();
            waitForText(R.string.game_preview_selected);
            screen.moveToState(Lifecycle.State.CREATED);
            screen.moveToState(Lifecycle.State.RESUMED);
            waitForText(R.string.game_preview_selected);
            onView(withId(R.id.game_preview_item)).perform(scrollTo())
                    .check(matches(isCompletelyDisplayed()));
            capture("preview-restored-simulated");
        }
        assertDenied(context, Manifest.permission.CAMERA);
        assertDenied(context, Manifest.permission.ACCESS_FINE_LOCATION);
        assertDenied(context, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    @Test public void legacyLauncherStillOpens() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(
                new Intent(context, MainActivity.class))) {
            onView(withId(R.id.shell)).check(matches(isDisplayed()));
            screen.onActivity(activity -> assertTrue(!activity.isFinishing()));
            capture("legacy-launcher");
        }
    }

    private static void assertDenied(Context context, String permission) {
        assertEquals(permission + " must remain denied", PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(permission));
    }

    private static void assertTextFits(TextView view) {
        assertNotNull(view.getLayout());
        for (int i = 0; i < view.getLayout().getLineCount(); i++) {
            assertEquals("Button text must not be ellipsized", 0, view.getLayout().getEllipsisCount(i));
        }
        assertTrue("Button must fit all text lines", view.getLayout().getHeight()
                <= view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom());
    }

    private static void waitForText(int stringId) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        do {
            try {
                onView(withId(R.id.game_preview_status)).check(matches(withText(stringId)));
                return;
            } catch (AssertionError notReady) {
                SystemClock.sleep(50);
            }
        } while (SystemClock.elapsedRealtime() < deadline);
        onView(withId(R.id.game_preview_status)).check(matches(withText(stringId)));
    }

    private static void waitForOrientation(ActivityScenario<GamePreviewActivity> screen, int expected) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        int[] actual = {Configuration.ORIENTATION_UNDEFINED};
        do {
            screen.onActivity(activity -> actual[0] = activity.getResources().getConfiguration().orientation);
            if (actual[0] == expected) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertEquals("Requested test orientation must be applied", expected, actual[0]);
    }

    private static void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // Activity window transitions run outside Espresso's main-thread idling.
        SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Context context = ApplicationProvider.getApplicationContext();
        File folder = new File(context.getExternalFilesDir(null), "h01-evidence");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        String prefix = InstrumentationRegistry.getArguments().getString("h01EvidencePrefix", "default");
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Actual APK screenshot", bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(folder, prefix + "-" + name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            bitmap.recycle();
        }
    }
}
