package com.example.firststep.game.composition.integration;

import static org.junit.Assert.*;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.firststep.game.domain.Progress;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SoftwareEpisodeIntentCodecTest {
  @Test
  public void matchingDemoExtrasAreRejectedByReleaseDecoder() {
    IntegrationContext software = IntegrationContext.create(
        IntegrationContext.SOFTWARE_DATASET,
        UUID.randomUUID().toString(),
        IntegrationContext.TARGET,
        Progress.Mode.DEMO,
        IntegrationContext.Clock.REAL,
        null);
    Intent extras = new Intent();
    IntegrationIntentCodec.writeContext(extras, software);
    assertTrue(software.equalsIdentity(IntegrationIntentCodec.readContext(extras)));
    assertNull(IntegrationIntentCodec.readContext(extras, false));

    IntegrationContext fixture = IntegrationContext.create(
        IntegrationContext.DATASET, software.runId, software.targetId, software.mode,
        software.clock, software.fakeNow);
    Intent fixtureExtras = new Intent();
    IntegrationIntentCodec.writeContext(fixtureExtras, fixture);
    assertTrue(fixture.equalsIdentity(IntegrationIntentCodec.readContext(fixtureExtras, false)));
  }

  @Test
  public void typedDemoRouteRejectsMismatchedExtras() {
    IntegrationContext software = IntegrationContext.create(
        IntegrationContext.SOFTWARE_DATASET,
        UUID.randomUUID().toString(),
        IntegrationContext.TARGET,
        Progress.Mode.DEMO,
        IntegrationContext.Clock.REAL,
        null);
    Intent intent = new Intent();
    IntegrationIntentCodec.writeContext(intent, software);
    assertNull(IntegrationIntentCodec.readContext(new Intent(intent)
        .putExtra(IntegrationIntentCodec.TARGET, "unregistered")));
    assertNull(IntegrationIntentCodec.readContext(new Intent(intent)
        .putExtra(IntegrationIntentCodec.MODE, "NORMAL")));
    assertNull(IntegrationIntentCodec.readContext(new Intent(intent)
        .putExtra(IntegrationIntentCodec.FAKE_NOW, 100L)));
    assertNull(IntegrationIntentCodec.readContext(new Intent(intent)
        .putExtra(IntegrationIntentCodec.CLOCK, 1)));
  }
}
