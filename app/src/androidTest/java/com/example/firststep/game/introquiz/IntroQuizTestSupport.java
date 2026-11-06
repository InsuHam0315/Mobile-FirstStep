package com.example.firststep.game.introquiz;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.is;
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
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.game.data.local.GameProgressDatabase;
import com.example.firststep.game.data.local.RoomProgressRepository;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.presentation.introquiz.IntroQuizController;
import com.example.firststep.game.ui.intro.IntroQuizActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.util.function.Predicate;
import org.hamcrest.Matcher;

final class IntroQuizTestSupport {
    static final String TARGET = "digital_information_software";
    static final long START = 1_800_000_000_000L;
    private IntroQuizTestSupport() {}

    static Intent intent(String run, Progress.Mode mode, Long clock, boolean speechFailure) {
        Intent i = new Intent(context(), IntroQuizActivity.class)
                .putExtra("game_intro_building_id", TARGET)
                .putExtra("game_intro_mode", mode.name())
                .putExtra("game_intro_instant_demo", mode == Progress.Mode.DEMO)
                .putExtra("game_intro_debug_run", run)
                .putExtra("game_intro_debug_speech_failure", speechFailure);
        if (clock != null) i.putExtra("game_intro_debug_clock", clock);
        return i;
    }

    static Context context() { return ApplicationProvider.getApplicationContext(); }

    static ActivityScenario<IntroQuizActivity> launch(Intent intent) {
        ActivityScenario<IntroQuizActivity> screen = ActivityScenario.launch(intent);
        String requested = InstrumentationRegistry.getArguments().getString("h04Orientation", "1");
        int orientation = Integer.parseInt(requested);
        screen.onActivity(a -> a.setRequestedOrientation(orientation == Configuration.ORIENTATION_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        long deadline = SystemClock.elapsedRealtime() + 5000;
        int[] actual = {0};
        do {
            screen.onActivity(a -> actual[0] = a.getResources().getConfiguration().orientation);
            if (actual[0] == orientation) break;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertEquals("Requested orientation must be observed", orientation, actual[0]);
        screen.onActivity(a -> assertEquals(Float.parseFloat(InstrumentationRegistry.getArguments()
                        .getString("h04FontScale", "1.0")),
                a.getResources().getConfiguration().fontScale, 0.01f));
        return screen;
    }

    static IntroQuizController.Snapshot state(ActivityScenario<IntroQuizActivity> screen) {
        IntroQuizController.Snapshot[] value = {null};
        screen.onActivity(a -> value[0] = a.snapshot());
        return value[0];
    }

    static void await(ActivityScenario<IntroQuizActivity> screen,
                      Predicate<IntroQuizController.Snapshot> condition) {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        do {
            IntroQuizController.Snapshot value = state(screen);
            if (value != null && condition.test(value)) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        IntroQuizController.Snapshot value = state(screen);
        assertNotNull("Expected explicit screen state", value);
        assertTrue("Expected state; actual=" + value.stage + " error=" + value.error, condition.test(value));
    }

    static void stage(ActivityScenario<IntroQuizActivity> screen, IntroQuizController.Stage stage) {
        await(screen, s -> s.stage == stage);
    }

    static void press(int id) { onView(withId(id)).perform(reveal(), click()); }
    static void option(String id) { onView(withTagValue(is((Object) id))).perform(reveal(), click()); }

    static ViewAction reveal() {
        return new ViewAction() {
            public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            public String getDescription() { return "reveal an H04 control in the inset scroll viewport"; }
            public void perform(UiController ui, View view) {
                ViewParent p = view.getParent();
                while (p != null && !(p instanceof ScrollView)) p = p.getParent();
                assertTrue("H04 controls must be scrollable", p instanceof ScrollView);
                ScrollView scroll = (ScrollView) p;
                Rect bounds = new Rect();
                view.getDrawingRect(bounds);
                scroll.offsetDescendantRectToMyCoords(view, bounds);
                int viewport = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
                scroll.scrollTo(0, Math.max(0, bounds.centerY() - viewport / 2 - scroll.getPaddingTop()));
                ui.loopMainThreadForAtLeast(200);
                assertTrue("Control must actually be visible", isDisplayingAtLeast(90).matches(view));
            }
        };
    }

    static void fits(ActivityScenario<IntroQuizActivity> screen, int id) {
        onView(withId(id)).perform(reveal());
        screen.onActivity(a -> {
            TextView text = a.findViewById(id);
            assertNotNull(text.getLayout());
            for (int line = 0; line < text.getLayout().getLineCount(); line++)
                assertEquals("Text must not be ellipsized", 0, text.getLayout().getEllipsisCount(line));
            assertTrue("All lines must fit", text.getLayout().getHeight()
                    <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
            if (text.isClickable()) assertTrue("48dp minimum touch target",
                    text.getHeight() >= 48 * text.getResources().getDisplayMetrics().density);
        });
    }

    static GameProgressDatabase database(String run) {
        assertTrue(run.matches("[a-f0-9-]{36}"));
        return Room.databaseBuilder(context(), GameProgressDatabase.class, "h04-debug-" + run + ".db").build();
    }

    static Progress stored(String run, Progress.Mode mode) {
        try (RoomProgressRepository repo = new RoomProgressRepository(database(run))) {
            return repo.read(mode, TARGET);
        }
    }

    static void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(400);
        String prefix = InstrumentationRegistry.getArguments().getString("h04EvidencePrefix", "default");
        assertTrue(prefix.matches("[A-Za-z0-9_-]+"));
        File folder = new File(context().getExternalFilesDir(null), "h04-evidence");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Capture must come from the actual APK", screenshot);
        try (FileOutputStream out = new FileOutputStream(new File(folder, prefix + "-" + name + ".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { screenshot.recycle(); }
    }
}
