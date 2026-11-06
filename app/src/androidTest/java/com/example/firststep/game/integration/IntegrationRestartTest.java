package com.example.firststep.game.integration;

import static com.example.firststep.game.integration.IntegrationTestSupport.*;
import static org.junit.Assert.*;

import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.R;
import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.presentation.integration.NavigationPolicy;
import com.example.firststep.game.presentation.introquiz.IntroQuizController;
import com.example.firststep.game.ui.integration.IntegrationEntryActivity;
import java.nio.file.*;
import org.junit.*;
import org.junit.runner.RunWith;

/**
 * Invoke methods separately with an external force-stop between them; never mocks process death.
 */
@RunWith(AndroidJUnit4.class)
public final class IntegrationRestartTest {
  @After
  public void cleanup() {
    closeActivities();
  }

  private IntegrationContext requested() {
    String run = InstrumentationRegistry.getArguments().getString("h06RestartRun");
    assertTrue("Explicit canonical restart run required", IntegrationContext.canonicalUuid(run));
    return context(run, Progress.Mode.NORMAL);
  }

  private Intent entry(IntegrationContext c) {
    Intent i = new Intent(context(), IntegrationEntryActivity.class);
    IntegrationIntentCodec.writeContext(i, c);
    return i;
  }

  private Path expectation() {
    return context().getFileStreamPath("h06-restart-expectation.txt").toPath();
  }

  @Test
  public void prepareRestart() throws Exception {
    IntegrationContext c = requested();
    assertFalse(
        "Use a fresh explicit run",
        context().getDatabasePath("h04-debug-" + c.runId + ".db").exists());
    String stable = new StableRunStore(context()).resolve(null);
    assertNotNull(stable);
    try (ActivityScenario<IntegrationEntryActivity> s = ActivityScenario.launch(entry(c))) {
      await(
          "Entry context ready",
          () -> {
            boolean[] ready = {false};
            s.onActivity(
                a ->
                    ready[0] =
                        a.contextForTest() != null
                            && a.findViewById(R.id.game_integration_entry_normal).isEnabled());
            return ready[0];
          });
      press(R.id.game_integration_entry_normal);
      discover(c, false);
      press(R.id.game_intro_skip);
      stage(IntroQuizController.Stage.WAITING);
      assertEquals(Long.valueOf(START), intro().progress.introDoneAt);
      press(R.id.game_intro_back);
      homeReady();
      capture("before-real-process-restart-NORMAL-DEBUG_FAKE");
    }
    Files.write(
        expectation(), (c.runId + "\n" + stable).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    System.out.println(
        "H06_RESTART_PREPARED run="
            + c.runId
            + " stable="
            + stable
            + " pid="
            + android.os.Process.myPid());
  }

  @Test
  public void resumeAfterRestart() throws Exception {
    IntegrationContext c = requested();
    String[] expected =
        new String(Files.readAllBytes(expectation()), java.nio.charset.StandardCharsets.UTF_8)
            .split("\n");
    assertEquals(expected[0], c.runId);
    assertEquals(expected[1], new StableRunStore(context()).resolve(null));
    assertEquals(NavigationPolicy.Phase.CONSUMED, new NavigationStore(context()).load(c).phase);
    assertEquals(
        Long.valueOf(START), IntegrationRouteAccess.readProgress(context(), c).introDoneAt);
    try (ActivityScenario<IntegrationEntryActivity> s = ActivityScenario.launch(entry(c))) {
      await(
          "Restarted entry ready",
          () -> {
            boolean[] ready = {false};
            s.onActivity(
                a ->
                    ready[0] =
                        a.contextForTest() != null
                            && a.findViewById(R.id.game_integration_entry_normal).isEnabled());
            return ready[0];
          });
      press(R.id.game_integration_entry_normal);
      homeReady();
      press(R.id.game_home_prototype);
      stage(IntroQuizController.Stage.WAITING);
      assertFalse(intro().eligible);
      assertEquals(Long.valueOf(START), intro().progress.introDoneAt);
      capture("after-real-process-restart-same-run-waiting");
    }
    assertEquals(expected[1], new StableRunStore(context()).resolve(null));
    System.out.println(
        "H06_RESTART_RESUMED run="
            + c.runId
            + " stable="
            + expected[1]
            + " pid="
            + android.os.Process.myPid());
  }
}
