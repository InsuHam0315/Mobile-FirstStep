package com.example.firststep.game.home;

import static org.junit.Assert.*;
import android.location.Location;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.game.device.homemap.H05LocationAdapter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Explicit injected adapter inputs, without modifying providers or runtime permissions. */
@RunWith(AndroidJUnit4.class)
public class HomeLocationAdapterTest {
  @Test public void injectedFreshStaleAndPauseClearIndicators() {
    AtomicReference<Location> received = new AtomicReference<>();
    AtomicReference<String> message = new AtomicReference<>();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      try (H05LocationAdapter adapter = new H05LocationAdapter(
          ApplicationProvider.getApplicationContext(), (location, text) -> {
            received.set(location); message.set(text);
          })) {
        Location fake = new Location("h05-explicit-test-input");
        fake.setLatitude(35.9456011); fake.setLongitude(126.6802365);
        fake.setAccuracy(20); fake.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        adapter.debugInject(fake);
        assertNotNull(received.get());
        assertTrue(message.get().contains("시뮬레이션"));
        assertNotSame(fake, received.get());
        adapter.stop();
        assertNull(received.get());
        fake.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos() - 31_000_000_000L);
        adapter.debugInject(fake);
        assertNull(received.get());
        assertTrue(message.get().contains("오래되었거나"));
        fake.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        fake.setLatitude(Double.NaN);
        adapter.debugInject(fake);
        assertNull(received.get());
        adapter.close();
        fake.setLatitude(35.94);
        adapter.debugInject(fake);
        assertNull("Closed adapter must ignore new injected callbacks", received.get());
      }
    });
  }
}
