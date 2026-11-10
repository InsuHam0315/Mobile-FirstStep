package com.example.firststep.game.identity;

import static org.junit.Assert.*;

import android.content.Intent;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.firststep.*;
import com.example.firststep.game.auth.SessionState;
import com.example.firststep.game.composition.identity.H07VariantFactory;
import com.example.firststep.game.ui.identity.IdentityActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class IdentityConfigurationSmokeTest {
  @Test
  public void cleanUnconfiguredRouteNeverStartsMockLogin() {
    assertFalse("Invoke on explicitly unconfigured build, never skip", BuildConfig.H07_CONFIGURED);
    H07VariantFactory.setTestFactory(null);
    try (ActivityScenario<IdentityActivity> s =
        ActivityScenario.launch(
            new Intent(ApplicationProvider.getApplicationContext(), IdentityActivity.class))) {
      s.onActivity(
          a -> {
            assertEquals(SessionState.Stage.UNCONFIGURED, a.stateForTest().getStage());
            assertNull(a.stateForTest().getUid());
            assertTrue(a.findViewById(R.id.game_identity_welcome).isShown());
            assertTrue(a.findViewById(R.id.game_identity_welcome_login).performClick());
            assertTrue(a.findViewById(R.id.game_identity_login).isShown());
            assertFalse(a.findViewById(R.id.game_identity_login).isEnabled());
          });
    }
  }

  @Test
  public void legacyLauncherRemainsIndependent() {
    try (ActivityScenario<MainActivity> s =
        ActivityScenario.launch(
            new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class))) {
      s.onActivity(a -> assertFalse(a.isFinishing()));
    }
  }
}
