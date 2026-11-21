package com.example.firststep.game.introquiz;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static com.example.firststep.game.introquiz.IntroQuizTestSupport.*;
import static org.junit.Assert.*;

import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.firststep.R;
import com.example.firststep.game.composition.integration.IntegrationContext;
import com.example.firststep.game.composition.integration.IntegrationIntentCodec;
import com.example.firststep.game.composition.integration.IntegrationResult;
import com.example.firststep.game.composition.integration.NavigationStore;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.introquiz.IntroQuizController;
import com.example.firststep.game.ui.intro.IntroQuizActivity;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The software booklet uses a fresh local DEMO and a synthetic discovery receipt. */
@RunWith(AndroidJUnit4.class)
public final class SoftwareBookletNavigationTest {
  @Test public void finalPageCanReturnToPreviousWithoutFinishingIntroduction() throws Exception {
    IntegrationContext demo = IntegrationContext.create(IntegrationContext.SOFTWARE_DATASET,
        UUID.randomUUID().toString(), IntegrationContext.TARGET, Progress.Mode.DEMO,
        IntegrationContext.Clock.REAL, null);
    String nonce = UUID.randomUUID().toString();
    NavigationStore navigation = new NavigationStore(context());
    assertTrue(navigation.bindClock(demo));
    assertTrue(navigation.begin(demo, nonce));
    assertTrue(navigation.accept(new IntegrationResult(demo, nonce, new DiscoveryResult(
        1, demo.targetId, DiscoveryResult.Method.SHAKE, DiscoveryResult.Source.SIMULATED,
        true, true, true))));
    assertTrue(navigation.consume(demo, nonce));
    Intent launch = new Intent(context(), IntroQuizActivity.class);
    IntegrationIntentCodec.writeContext(launch, demo);
    launch.putExtra("game_intro_debug_speech_failure", true);
    try (ActivityScenario<IntroQuizActivity> screen = launch(launch)) {
      stage(screen, IntroQuizController.Stage.INTRO);
      for (int index = 0; index < 6; index++) press(R.id.game_booklet_next);
      assertEquals(6, state(screen).sentence);
      onView(withId(R.id.game_booklet_previous)).perform(reveal())
          .check(matches(isDisplayed())).check(matches(isEnabled()));
      press(R.id.game_booklet_previous);
      assertEquals(5, state(screen).sentence);
      assertEquals(IntroQuizController.Stage.INTRO, state(screen).stage);
      assertNull(state(screen).progress.introDoneAt);
      press(R.id.game_booklet_next);
      screen.recreate();
      stage(screen, IntroQuizController.Stage.INTRO);
      assertEquals(6, state(screen).sentence);
      press(R.id.game_booklet_previous);
      assertEquals(5, state(screen).sentence);
      assertNull(state(screen).progress.introDoneAt);
    }
  }
}
