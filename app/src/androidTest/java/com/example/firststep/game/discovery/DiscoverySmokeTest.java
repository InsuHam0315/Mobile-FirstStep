package com.example.firststep.game.discovery;

import static com.example.firststep.game.discovery.DiscoveryTestSupport.*;
import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.MainActivity;
import com.example.firststep.R;
import com.example.firststep.game.composition.discovery.DiscoveryTestDriver;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.discovery.DiscoveryState;
import com.example.firststep.game.ui.discovery.DiscoveryActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real APK UI/lifecycle checks; the fake clock and sensor driver are explicitly labeled. */
@RunWith(AndroidJUnit4.class)
public final class DiscoverySmokeTest {
    @Test public void simulatedShakeAndRecreationRejectOldSession() throws Exception {
        try (ActivityScenario<DiscoveryActivity> screen = launch(true)) {
            DiscoveryTestDriver original = driver(screen);
            screen.onActivity(a -> original.cameraState(DiscoveryState.CameraStatus.READY));
            press(R.id.game_discovery_arrival);
            screen.onActivity(a -> original.heading(359, true));
            assertEquals(DiscoveryState.HeadingStatus.ALIGNED, state(screen).heading);
            assertTextFits(screen, R.id.game_discovery_status);
            capture("simulated-ready");
            screen.onActivity(a -> {
                original.acceleration(24);
                for (int i = 0; i < 8; i++) original.acceleration(24);
                assertEquals("Sustained high input counts once", 1, original.snapshot().shakeCount);
                for (int i = 0; i < 4; i++) {
                    original.acceleration(9.8f);
                    original.advanceTime(200);
                    original.acceleration(24);
                }
            });
            DiscoveryResult result = state(screen).result;
            assertNotNull(result);
            assertEquals(DiscoveryResult.Source.SIMULATED, result.source);
            assertEquals(DiscoveryResult.Method.SHAKE, result.method);
            assertEquals("digital_information_software", result.targetId);
            assertTrue(result.arrivalSimulated);
            assertFalse(result.cameraFallback);
            assertFalse(result.headingFallback);
            screen.onActivity(a -> {
                original.acceleration(24);
                original.advanceTime(100);
                original.tick();
            });
            assertSame("Repeated callbacks do not emit another result", result, state(screen).result);
            assertTextFits(screen, R.id.game_discovery_result);
            SystemClock.sleep(600);
            capture("simulated-shake-complete");
            screen.recreate();
            DiscoveryState fresh = state(screen);
            assertFalse(fresh.arrived);
            assertFalse(fresh.completed);
            assertNotEquals(result.sessionId, fresh.sessionId);
            screen.onActivity(a -> {
                original.cameraState(DiscoveryState.CameraStatus.READY);
                original.heading(0, true);
                original.acceleration(30);
                original.advanceTime(5000);
            });
            assertFalse(state(screen).arrived);
            assertFalse(state(screen).completed);
            assertTextFits(screen, R.id.game_discovery_arrival);
            capture("recreated-fresh-simulated");
        }
    }

    @Test public void simulatedCoverSupportsStableOnChangeSample() throws Exception {
        try (ActivityScenario<DiscoveryActivity> screen = launch(true)) {
            DiscoveryTestDriver input = driver(screen);
            screen.onActivity(a -> input.cameraState(DiscoveryState.CameraStatus.READY));
            press(R.id.game_discovery_arrival);
            press(R.id.game_discovery_method_light);
            screen.onActivity(a -> {
                input.heading(0, true);
                input.light(100);
                input.light(10);
                input.advanceTime(700);
                input.heading(0, true);
                input.advanceTime(799);
                assertFalse(input.snapshot().completed);
                input.advanceTime(1);
            });
            DiscoveryResult result = state(screen).result;
            assertNotNull(result);
            assertEquals(DiscoveryResult.Method.LIGHT, result.method);
            assertEquals("synthetic_cover_demo", result.targetId);
            assertEquals(DiscoveryResult.Source.SIMULATED, result.source);
            assertTextFits(screen, R.id.game_discovery_result);
            capture("simulated-cover-complete");
        }
    }

    @Test public void cameraFailureAndUnusableLightHaveCancelableFallback() throws Exception {
        try (ActivityScenario<DiscoveryActivity> screen = launch(true)) {
            DiscoveryTestDriver input = driver(screen);
            screen.onActivity(a -> input.cameraState(DiscoveryState.CameraStatus.UNAVAILABLE));
            assertTextFits(screen, R.id.game_discovery_camera_fallback);
            capture("simulated-camera-unavailable");
            screen.onActivity(a -> input.cameraState(DiscoveryState.CameraStatus.ERROR));
            assertEquals(DiscoveryState.CameraStatus.ERROR, state(screen).camera);
            press(R.id.game_discovery_camera_fallback);
            press(R.id.game_discovery_arrival);
            screen.onActivity(a -> input.heading(Float.NaN, false));
            press(R.id.game_discovery_align_fallback);
            press(R.id.game_discovery_method_light);
            screen.onActivity(a -> {
                input.light(0);
                input.advanceTime(2000);
            });
            assertFalse("Dark entry cannot automatically summon", state(screen).completed);
            assertTextFits(screen, R.id.game_discovery_hold);
            capture("dark-light-fallback-simulated");
            touch(screen, R.id.game_discovery_hold, MotionEvent.ACTION_DOWN);
            screen.onActivity(a -> input.advanceTime(700));
            touch(screen, R.id.game_discovery_hold, MotionEvent.ACTION_CANCEL);
            screen.onActivity(a -> input.advanceTime(2000));
            assertFalse(state(screen).completed);
            touch(screen, R.id.game_discovery_hold, MotionEvent.ACTION_DOWN);
            screen.onActivity(a -> input.advanceTime(700));
            touch(screen, R.id.game_discovery_hold, MotionEvent.ACTION_UP);
            screen.onActivity(a -> input.advanceTime(2000));
            assertFalse(state(screen).completed);
            touch(screen, R.id.game_discovery_hold, MotionEvent.ACTION_DOWN);
            screen.onActivity(a -> input.advanceTime(1500));
            touch(screen, R.id.game_discovery_hold, MotionEvent.ACTION_UP);
            DiscoveryResult result = state(screen).result;
            assertNotNull(result);
            assertEquals(DiscoveryResult.Source.LONG_PRESS, result.source);
            assertEquals("synthetic_cover_demo", result.targetId);
            assertTrue(result.cameraFallback);
            assertTrue(result.headingFallback);
            assertTrue(result.arrivalSimulated);
            assertTextFits(screen, R.id.game_discovery_result);
            capture("labeled-fallback-complete");
        }
    }

    @Test public void pauseCancelsHeldInputAndRequiresFreshArrival() throws Exception {
        try (ActivityScenario<DiscoveryActivity> screen = launch(true)) {
            DiscoveryTestDriver old = driver(screen);
            screen.onActivity(a -> old.cameraState(DiscoveryState.CameraStatus.READY));
            press(R.id.game_discovery_arrival);
            press(R.id.game_discovery_align_fallback);
            assertTextFits(screen, R.id.game_discovery_hold);
            touch(screen, R.id.game_discovery_hold, MotionEvent.ACTION_DOWN);
            screen.onActivity(a -> old.advanceTime(700));
            screen.moveToState(Lifecycle.State.CREATED);
            screen.moveToState(Lifecycle.State.RESUMED);
            screen.onActivity(a -> old.advanceTime(5000));
            assertFalse(state(screen).arrived);
            assertFalse(state(screen).completed);
            assertFalse(state(screen).headingFallback);
            assertNotSame(old, driver(screen));
        }
    }

    @Test public void actualCameraMatchesInitialPermissionAndHasFallback() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        int initial = context.checkSelfPermission(Manifest.permission.CAMERA);
        String expected = InstrumentationRegistry.getArguments().getString("h03ExpectedCamera", "AUTO");
        if ("STREAMING".equals(expected)) assertEquals(PackageManager.PERMISSION_GRANTED, initial);
        if ("DENIED".equals(expected)) assertEquals(PackageManager.PERMISSION_DENIED, initial);
        try (ActivityScenario<DiscoveryActivity> screen = launch(false)) {
            screen.onActivity(a -> assertNull("Actual mode must not use the fake driver", DiscoveryTestDriver.current()));
            await(screen, s -> s.camera != DiscoveryState.CameraStatus.STARTING);
            if ("STREAMING".equals(expected)) assertEquals(DiscoveryState.CameraStatus.READY, state(screen).camera);
            if (initial == PackageManager.PERMISSION_DENIED) {
                assertTrue(state(screen).camera == DiscoveryState.CameraStatus.DENIED
                        || state(screen).camera == DiscoveryState.CameraStatus.PERMISSION_REQUIRED);
                assertTextFits(screen, R.id.game_discovery_status);
                capture("camera-permission-denied");
            } else if (state(screen).camera == DiscoveryState.CameraStatus.READY) {
                screen.onActivity(a -> assertEquals(PreviewView.StreamState.STREAMING,
                        ((PreviewView) a.findViewById(R.id.game_discovery_preview)).getPreviewStreamState().getValue()));
                assertTextFits(screen, R.id.game_discovery_status);
                capture("actual-camerax-virtual-scene");
            } else {
                assertTrue(state(screen).camera == DiscoveryState.CameraStatus.ERROR
                        || state(screen).camera == DiscoveryState.CameraStatus.UNAVAILABLE);
                capture("actual-camera-unavailable");
            }
            press(R.id.game_discovery_camera_fallback);
            press(R.id.game_discovery_arrival);
            press(R.id.game_discovery_align_fallback);
            assertTextFits(screen, R.id.game_discovery_hold);
            touch(screen, R.id.game_discovery_hold, MotionEvent.ACTION_DOWN);
            SystemClock.sleep(1700);
            touch(screen, R.id.game_discovery_hold, MotionEvent.ACTION_UP);
            await(screen, s -> s.completed);
            assertEquals(DiscoveryResult.Source.LONG_PRESS, state(screen).result.source);
            assertEquals("digital_information_software", state(screen).result.targetId);
            assertTrue(state(screen).result.cameraFallback);
            assertTrue(state(screen).result.headingFallback);
            assertTextFits(screen, R.id.game_discovery_result);
            capture("actual-mode-manual-fallback");
            screen.recreate();
            assertFalse(state(screen).completed);
            assertFalse(state(screen).arrived);
        }
        assertEquals("Instrumentation must preserve initial camera permission", initial,
                context.checkSelfPermission(Manifest.permission.CAMERA));
    }

    @Test public void legacyLauncherStillOpens() {
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(new Intent(context, MainActivity.class))) {
            screen.onActivity(a -> {
                assertNotNull(a.findViewById(R.id.shell));
                assertFalse(a.isFinishing());
            });
        }
    }
}
