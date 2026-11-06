package com.example.firststep.game.introquiz;

import static com.example.firststep.game.introquiz.IntroQuizTestSupport.*;
import static org.junit.Assert.*;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.MainActivity;
import com.example.firststep.R;
import com.example.firststep.game.data.local.GameProgressDatabase;
import com.example.firststep.game.domain.GameContent;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.notifications.IntroQuizReminderReceiver;
import com.example.firststep.game.presentation.introquiz.IntroQuizController;
import com.example.firststep.game.ui.discovery.DiscoveryActivity;
import com.example.firststep.game.ui.intro.IntroQuizActivity;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real APK/Room checks. Campus content, clocks and injected speech failures are explicit fixtures. */
@RunWith(AndroidJUnit4.class)
public final class IntroQuizSmokeTest {
    @Test public void captionsExplicitSkipAndRecreationPreserveTimestamp() throws Exception {
        String run = UUID.randomUUID().toString();
        try (ActivityScenario<IntroQuizActivity> screen = launch(intent(run, Progress.Mode.NORMAL, START, true))) {
            stage(screen, IntroQuizController.Stage.INTRO);
            assertNull(state(screen).progress.introDoneAt);
            fits(screen, R.id.game_intro_caption);
            fits(screen, R.id.game_intro_next);
            capture("intro-synthetic-no-voice");
            press(R.id.game_intro_next);
            assertEquals(1, state(screen).sentence);
            press(R.id.game_intro_replay);
            screen.recreate();
            stage(screen, IntroQuizController.Stage.INTRO);
            assertEquals(1, state(screen).sentence);
            screen.moveToState(Lifecycle.State.CREATED);
            screen.moveToState(Lifecycle.State.RESUMED);
            stage(screen, IntroQuizController.Stage.INTRO);
            assertNull(state(screen).progress.introDoneAt);
            assertEquals(1, state(screen).sentence);
            capture("intro-recreated-synthetic");
            press(R.id.game_intro_skip);
            stage(screen, IntroQuizController.Stage.WAITING);
            assertEquals(Long.valueOf(START), state(screen).progress.introDoneAt);
            capture("normal-waiting-fake-clock");
        }
        try (ActivityScenario<IntroQuizActivity> screen = launch(intent(run, Progress.Mode.NORMAL, START + 20000, true))) {
            stage(screen, IntroQuizController.Stage.WAITING);
            press(R.id.game_intro_show_again);
            stage(screen, IntroQuizController.Stage.INTRO);
            press(R.id.game_intro_next);
            press(R.id.game_intro_next);
            press(R.id.game_intro_finish);
            stage(screen, IntroQuizController.Stage.WAITING);
            assertEquals(Long.valueOf(START), stored(run, Progress.Mode.NORMAL).introDoneAt);
        }
    }

    @Test public void normalBoundaryAndNotificationReentryUseStoredTime() throws Exception {
        String run = UUID.randomUUID().toString();
        try (ActivityScenario<IntroQuizActivity> screen = launch(intent(run, Progress.Mode.NORMAL, START, true))) {
            stage(screen, IntroQuizController.Stage.INTRO);
            press(R.id.game_intro_skip);
            stage(screen, IntroQuizController.Stage.WAITING);
        }
        Intent early = intent(run, Progress.Mode.NORMAL, START + ProgressService.WAIT_MILLIS - 1, true)
                .putExtra("game_intro_notification_entry", true);
        try (ActivityScenario<IntroQuizActivity> screen = launch(early)) {
            stage(screen, IntroQuizController.Stage.WAITING);
            screen.onActivity(a -> assertFalse("Early notification must not unlock the quiz",
                    a.findViewById(R.id.game_quiz_start).isEnabled()));
            assertNull(state(screen).progress.active);
        }
        Intent late = intent(run, Progress.Mode.NORMAL, START + ProgressService.WAIT_MILLIS, true)
                .putExtra("game_intro_notification_entry", true);
        try (ActivityScenario<IntroQuizActivity> screen = launch(late)) {
            stage(screen, IntroQuizController.Stage.WAITING);
            screen.onActivity(a -> assertTrue(a.findViewById(R.id.game_quiz_start).isEnabled()));
            press(R.id.game_quiz_start);
            stage(screen, IntroQuizController.Stage.QUIZ);
            assertNotNull(state(screen).progress.active);
            assertNull("NORMAL writes must not leak into DEMO", stored(run, Progress.Mode.DEMO).introDoneAt);
            capture("normal-eligible-notification-entry-fake-clock");
        }
    }

    @Test public void demoQuizRestoresAttemptRetriesAndStampsOnce() throws Exception {
        String run = UUID.randomUUID().toString();
        try (ActivityScenario<IntroQuizActivity> screen = launch(intent(run, Progress.Mode.DEMO, START, true))) {
            stage(screen, IntroQuizController.Stage.INTRO);
            press(R.id.game_intro_skip);
            stage(screen, IntroQuizController.Stage.WAITING);
            press(R.id.game_quiz_start);
            stage(screen, IntroQuizController.Stage.QUIZ);
            Progress.Attempt first = state(screen).progress.active;
            screen.onActivity(a -> assertFalse(a.findViewById(R.id.game_quiz_submit).isEnabled()));
            GameContent.Question firstQuestion = first.questions.get(0);
            String selected = firstQuestion.options.get(0).id;
            option(selected);
            screen.recreate();
            stage(screen, IntroQuizController.Stage.QUIZ);
            assertEquals(first.id, state(screen).progress.active.id);
            assertEquals(selected, state(screen).answers.get(firstQuestion.id));
            fits(screen, R.id.game_quiz_submit);
            capture("quiz-answer-restored-demo");
            for (GameContent.Question q : state(screen).progress.active.questions) option(wrong(q));
            press(R.id.game_quiz_submit);
            stage(screen, IntroQuizController.Stage.RESULT);
            assertEquals(0, state(screen).result.correct);
            assertFalse(state(screen).result.passed);
            String retryId = state(screen).progress.active.id;
            assertNotEquals(first.id, retryId);
            fits(screen, R.id.game_quiz_result);
            capture("quiz-failed-demo");
            screen.recreate();
            stage(screen, IntroQuizController.Stage.RESULT);
            assertEquals(first.id, state(screen).result.attemptId);
            press(R.id.game_quiz_retry);
            stage(screen, IntroQuizController.Stage.QUIZ);
            assertEquals(retryId, state(screen).progress.active.id);
            int index = 0;
            for (GameContent.Question q : state(screen).progress.active.questions)
                option(index++ < 2 ? q.correctOptionId : wrong(q));
            screen.onActivity(a -> {
                a.findViewById(R.id.game_quiz_submit).performClick();
                a.findViewById(R.id.game_quiz_submit).performClick();
            });
            stage(screen, IntroQuizController.Stage.RESULT);
            assertEquals(2, state(screen).result.correct);
            assertTrue(state(screen).result.passed);
            assertTrue(state(screen).progress.stamp);
            fits(screen, R.id.game_quiz_result);
            capture("quiz-passed-single-stamp-demo");
            screen.recreate();
            stage(screen, IntroQuizController.Stage.RESULT);
            assertEquals(retryId, state(screen).result.attemptId);
            Progress persisted = stored(run, Progress.Mode.DEMO);
            assertTrue(persisted.stamp);
            assertEquals(2, persisted.history.size());
            assertNull(persisted.active);
            assertNull(stored(run, Progress.Mode.NORMAL).introDoneAt);
            press(R.id.game_intro_show_again);
            stage(screen, IntroQuizController.Stage.INTRO);
            press(R.id.game_intro_next);
            screen.recreate();
            stage(screen, IntroQuizController.Stage.INTRO);
            assertEquals(1, state(screen).sentence);
            assertTrue(state(screen).progress.stamp);
            capture("passed-intro-replay-recreated-demo");
            press(R.id.game_intro_finish);
            stage(screen, IntroQuizController.Stage.RESULT);
            assertEquals(retryId, state(screen).result.attemptId);
            assertEquals(2, stored(run, Progress.Mode.DEMO).history.size());
        }
    }

    private static String wrong(GameContent.Question q) {
        for (GameContent.Option o : q.options) if (!o.id.equals(q.correctOptionId)) return o.id;
        throw new AssertionError("Fixture needs a wrong option");
    }

    @Test public void invalidTargetsAndCorruptRecordsRemainRecoverable() throws Exception {
        String run = UUID.randomUUID().toString();
        Intent invalid = intent(run, Progress.Mode.NORMAL, START, true)
                .putExtra("game_intro_building_id", "synthetic_cover_demo");
        try (ActivityScenario<IntroQuizActivity> screen = launch(invalid)) {
            screen.onActivity(a -> {
                assertNull(a.snapshot());
                assertTrue(((TextView) a.findViewById(R.id.game_intro_status)).getText().length() > 0);
                assertFalse(a.findViewById(R.id.game_intro_skip).isEnabled());
            });
        }
        String broken = "{\"schemaVersion\":\"wrong-type\"}";
        GameProgressDatabase db = database(run);
        try { db.progressDao().save(new GameProgressDatabase.Row("NORMAL", TARGET, broken)); }
        finally { db.close(); }
        try (ActivityScenario<IntroQuizActivity> screen = launch(intent(run, Progress.Mode.NORMAL, START, true))) {
            stage(screen, IntroQuizController.Stage.ERROR);
            press(R.id.game_quiz_retry);
            stage(screen, IntroQuizController.Stage.ERROR);
            capture("corrupt-record-visible-error-fixture");
        }
        db = database(run);
        try { assertEquals(broken, db.progressDao().find("NORMAL", TARGET).payload); }
        finally { db.close(); }
    }

    @Test public void actualSpeechAdapterKeepsCaptionsAndDoesNotFinish() throws Exception {
        String run = UUID.randomUUID().toString();
        try (ActivityScenario<IntroQuizActivity> screen = launch(intent(run, Progress.Mode.NORMAL, null, false))) {
            stage(screen, IntroQuizController.Stage.INTRO);
            fits(screen, R.id.game_intro_caption);
            press(R.id.game_intro_replay);
            SystemClock.sleep(1500);
            assertEquals(IntroQuizController.Stage.INTRO, state(screen).stage);
            assertNull(state(screen).progress.introDoneAt);
            press(R.id.game_intro_next);
            screen.recreate();
            stage(screen, IntroQuizController.Stage.INTRO);
            fits(screen, R.id.game_intro_caption);
            capture("actual-tts-adapter-readable-synthetic-caption");
            assertNull(stored(run, Progress.Mode.NORMAL).introDoneAt);
        }
        for (String failure : new String[] {"absent", "init_error", "play_error"}) {
            Intent fixture = intent(UUID.randomUUID().toString(), Progress.Mode.NORMAL, START, false)
                    .putExtra("game_intro_debug_speech_mode", failure);
            try (ActivityScenario<IntroQuizActivity> screen = launch(fixture)) {
                stage(screen, IntroQuizController.Stage.INTRO);
                press(R.id.game_intro_replay);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                screen.onActivity(a -> assertTrue(((TextView) a.findViewById(R.id.game_intro_status))
                        .getText().toString().contains(a.getString(failure.equals("play_error")
                                ? R.string.game_intro_speech_4 : R.string.game_intro_speech_1))));
                if (failure.equals("absent")) {
                    int[] obsolete = {0};
                    screen.onActivity(a -> obsolete[0] = a.debugSpeechGenerationForTest());
                    screen.moveToState(Lifecycle.State.CREATED);
                    screen.moveToState(Lifecycle.State.RESUMED);
                    stage(screen, IntroQuizController.Stage.INTRO);
                    String[] before = {null};
                    screen.onActivity(a -> {
                        before[0] = ((TextView) a.findViewById(R.id.game_intro_status)).getText().toString();
                        a.debugSpeechErrorForTest(obsolete[0]);
                    });
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    screen.onActivity(a -> assertEquals("Paused speech callbacks must be obsolete", before[0],
                            ((TextView) a.findViewById(R.id.game_intro_status)).getText().toString()));
                    screen.onActivity(a -> a.debugSpeechErrorForTest(a.debugSpeechGenerationForTest()));
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    screen.onActivity(a -> assertTrue(((TextView) a.findViewById(R.id.game_intro_status))
                            .getText().toString().contains(a.getString(R.string.game_intro_speech_4))));
                }
                assertEquals(IntroQuizController.Stage.INTRO, state(screen).stage);
                assertNull(state(screen).progress.introDoneAt);
                if (failure.equals("play_error")) capture("speech-playback-error-simulated");
            }
        }
    }

    @Test public void notificationPermissionDoesNotGateEligibilityAndIdentityIsSeparated() throws Exception {
        int before = context().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS);
        String expected = InstrumentationRegistry.getArguments().getString("h04ExpectedPermission");
        if (expected != null) assertEquals(expected.equals("Granted")
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED, before);
        String normal = IntroQuizReminderReceiver.identity("NORMAL", TARGET).toString();
        assertNotEquals(normal, IntroQuizReminderReceiver.identity("DEMO", TARGET).toString());
        NotificationManager notifications = context().getSystemService(NotificationManager.class);
        notifications.cancel(normal, 1);
        IntroQuizReminderReceiver receiver = new IntroQuizReminderReceiver();
        Intent wrong = new Intent(context(), IntroQuizReminderReceiver.class)
                .setData(IntroQuizReminderReceiver.identity("NORMAL", "synthetic_cover_demo"))
                .putExtra("mode", "NORMAL").putExtra("building", "synthetic_cover_demo");
        receiver.onReceive(context(), wrong);
        assertNull(findNotification(notifications, normal));
        Intent valid = new Intent(context(), IntroQuizReminderReceiver.class)
                .setData(IntroQuizReminderReceiver.identity("NORMAL", TARGET))
                .putExtra("mode", "NORMAL").putExtra("building", TARGET);
        try {
            receiver.onReceive(context(), valid); // Explicit injected delivery, not elapsed real 15 minutes.
            if (before == PackageManager.PERMISSION_GRANTED) {
                long deadline = SystemClock.elapsedRealtime() + 5000;
                while (findNotification(notifications, normal) == null && SystemClock.elapsedRealtime() < deadline)
                    SystemClock.sleep(50);
                StatusBarNotification posted = findNotification(notifications, normal);
                assertNotNull("Granted path must post a real Android notification", posted);
                Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
                Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(IntroQuizActivity.class.getName(), null, false);
                Activity activity = null;
                try {
                    posted.getNotification().contentIntent.send();
                    activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
                    assertNotNull("Actual notification PendingIntent must open H04", activity);
                    Activity opened = activity;
                    instrumentation.runOnMainSync(() -> assertEquals(TARGET,
                            opened.getIntent().getStringExtra("game_intro_building_id")));
                } finally {
                    if (activity != null) {
                        Activity opened = activity;
                        instrumentation.runOnMainSync(opened::finish);
                    }
                    instrumentation.removeMonitor(monitor);
                }
            } else {
                SystemClock.sleep(300);
                assertNull("Denied notification permission must not silently post", findNotification(notifications, normal));
            }
        } finally { notifications.cancel(normal, 1); }
        String run = UUID.randomUUID().toString();
        try (ActivityScenario<IntroQuizActivity> screen = launch(intent(run, Progress.Mode.DEMO, START, true))) {
            stage(screen, IntroQuizController.Stage.INTRO);
            press(R.id.game_intro_skip);
            stage(screen, IntroQuizController.Stage.WAITING);
            press(R.id.game_quiz_start);
            stage(screen, IntroQuizController.Stage.QUIZ);
        }
        assertEquals("Instrumentation must preserve notification permission", before,
                context().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));
    }

    private static StatusBarNotification findNotification(NotificationManager manager, String tag) {
        for (StatusBarNotification n : manager.getActiveNotifications()) if (tag.equals(n.getTag())) return n;
        return null;
    }

    @Test public void legacyAndDiscoveryLaunchersStillOpen() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(new Intent(context(), MainActivity.class))) {
            screen.onActivity(a -> assertNotNull(a.findViewById(R.id.shell)));
        }
        try (ActivityScenario<DiscoveryActivity> screen = ActivityScenario.launch(new Intent(context(), DiscoveryActivity.class)
                .putExtra("com.example.firststep.discovery.MODE", "SIMULATED"))) {
            screen.onActivity(a -> assertNotNull(a.snapshot()));
        }
    }
}
