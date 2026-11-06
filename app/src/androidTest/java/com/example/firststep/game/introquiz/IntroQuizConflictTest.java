package com.example.firststep.game.introquiz;

import static com.example.firststep.game.introquiz.IntroQuizTestSupport.*;
import static org.junit.Assert.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.firststep.R;
import com.example.firststep.game.data.local.RoomProgressRepository;
import com.example.firststep.game.data.local.GameJson;
import com.example.firststep.game.domain.*;
import com.example.firststep.game.presentation.introquiz.IntroQuizController;
import com.example.firststep.game.ui.intro.IntroQuizActivity;
import java.io.InputStream;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Competing entry is simulated by a second service over the same real Room database. */
@RunWith(AndroidJUnit4.class)
public final class IntroQuizConflictTest {
    @Test public void competingPassReconcilesAndSurvivesRecreation() throws Exception {
        conflict(true, false);
    }

    @Test public void competingFailureOpensAuthoritativeRetry() throws Exception {
        conflict(false, false);
    }

    @Test public void competingFailureThenAdvancedPassDoesNotResubmitOldDraft() throws Exception {
        conflict(false, true);
    }

    private void conflict(boolean passed, boolean advanceRetry) throws Exception {
        String run = UUID.randomUUID().toString();
        try (ActivityScenario<IntroQuizActivity> screen = launch(intent(run, Progress.Mode.DEMO, START, true));
             RoomProgressRepository repo = new RoomProgressRepository(database(run))) {
            stage(screen, IntroQuizController.Stage.INTRO);
            press(R.id.game_intro_skip);
            stage(screen, IntroQuizController.Stage.WAITING);
            press(R.id.game_quiz_start);
            stage(screen, IntroQuizController.Stage.QUIZ);
            Progress.Attempt original = state(screen).progress.active;
            Map<String, String> saved = answers(original, passed);
            Map<String, String> stale = answers(original, !passed);
            for (String option : stale.values()) option(option);
            GameContent content;
            try (InputStream in = context().getAssets().open("game_intro/catalog.json")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
                content = GameJson.catalog(bytes.toString("UTF-8"));
            }
            ProgressService other = new ProgressService(content, repo, () -> START,
                    new ProgressService.AttemptFactory() {
                        public String nextId() { return UUID.randomUUID().toString(); }
                        public void shuffle(List<?> values) { Collections.shuffle(values); }
                    });
            other.submit(Progress.Mode.DEMO, TARGET, original.id, original.contentVersion, saved);
            Progress after = repo.read(Progress.Mode.DEMO, TARGET);
            String retryId = after.active == null ? null : after.active.id;
            if (advanceRetry) {
                other.submit(Progress.Mode.DEMO, TARGET, after.active.id,
                        after.active.contentVersion, answers(after.active, true));
            }
            try {
                other.submit(Progress.Mode.DEMO, TARGET, original.id, original.contentVersion, stale);
                fail("H02 must still reject a conflicting payload");
            } catch (IllegalArgumentException expected) { }
            press(R.id.game_quiz_submit); // Loaded UI is deliberately stale; second entry already committed.
            stage(screen, IntroQuizController.Stage.RESULT);
            assertEquals(original.id, state(screen).result.attemptId);
            assertEquals(saved, state(screen).result.answers);
            assertEquals(passed, state(screen).result.passed);
            screen.recreate();
            stage(screen, IntroQuizController.Stage.RESULT);
            assertEquals(original.id, state(screen).result.attemptId);
            capture(advanceRetry ? "conflict-old-failure-new-pass-demo" :
                    passed ? "conflict-authoritative-pass-demo" : "conflict-authoritative-failure-demo");
            if (!passed) {
                press(R.id.game_quiz_retry);
                stage(screen, advanceRetry ? IntroQuizController.Stage.RESULT : IntroQuizController.Stage.QUIZ);
                if (advanceRetry) assertEquals(retryId, state(screen).result.attemptId);
                else {
                    assertEquals(retryId, state(screen).progress.active.id);
                    assertTrue(state(screen).answers.isEmpty());
                }
            }
            assertEquals(advanceRetry ? 2 : 1, repo.read(Progress.Mode.DEMO, TARGET).history.size());
            assertNull(repo.read(Progress.Mode.NORMAL, TARGET).introDoneAt);
        }
    }

    private static Map<String, String> answers(Progress.Attempt attempt, boolean correct) {
        Map<String, String> values = new LinkedHashMap<>();
        for (GameContent.Question q : attempt.questions) {
            String answer = q.correctOptionId;
            if (!correct) for (GameContent.Option o : q.options)
                if (!o.id.equals(q.correctOptionId)) { answer = o.id; break; }
            values.put(q.id, answer);
        }
        return values;
    }
}
