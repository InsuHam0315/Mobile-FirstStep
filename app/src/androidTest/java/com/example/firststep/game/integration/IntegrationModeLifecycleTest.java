package com.example.firststep.game.integration;

import static com.example.firststep.game.integration.IntegrationTestSupport.*;
import static org.junit.Assert.*;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.Lifecycle;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.firststep.R;
import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.data.local.*;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.ui.home.GameHomeActivity;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Controlled worker completion while paused; seeds distinguishable isolated fixture modes. */
@RunWith(AndroidJUnit4.class)
public final class IntegrationModeLifecycleTest {
  private static final long DEMO_TIME = START - 1234;

  @After
  public void cleanup() {
    closeActivities();
  }

  private IntegrationContext prepared() throws Exception {
    IntegrationContext c = context(Progress.Mode.NORMAL);
    IntegrationRouteAccess.bound(context(), c);
    IntegrationContext demo = context(c.runId, Progress.Mode.DEMO);
    IntegrationRouteAccess.bound(context(), demo);
    try (RoomProgressRepository repo =
        new RoomProgressRepository(
            Room.databaseBuilder(
                    context(), GameProgressDatabase.class, "h04-debug-" + c.runId + ".db")
                .build())) {
      repo.atomicUpdate(
          Progress.Mode.DEMO,
          c.targetId,
          p ->
              new Progress(
                  Progress.Mode.DEMO,
                  c.targetId,
                  DEMO_TIME,
                  true,
                  false,
                  null,
                  Collections.emptyMap()));
    }
    assertNull(IntegrationRouteAccess.readProgress(context(), c).introDoneAt);
    assertEquals(
        Long.valueOf(DEMO_TIME), IntegrationRouteAccess.readProgress(context(), demo).introDoneAt);
    return c;
  }

  private void consistent(GameHomeActivity a) {
    assertNotNull(a.integrationContextForTest());
    assertNotNull(a.snapshot());
    Progress.Mode mode = a.integrationContextForTest().mode;
    assertEquals("Context and controller must have one mode", mode, a.snapshot().mode);
    assertEquals(
        "Intent and controller must have one mode",
        mode,
        IntegrationIntentCodec.readContext(a.getIntent()).mode);
    if (a.snapshot().progress != null) {
      assertEquals(
          "Displayed progress must belong to current mode", mode, a.snapshot().progress.mode);
      assertEquals(
          mode == Progress.Mode.DEMO ? Long.valueOf(DEMO_TIME) : null,
          a.snapshot().progress.introDoneAt);
    }
  }

  private void loaded(ActivityScenario<GameHomeActivity> screen, Progress.Mode mode) {
    await(
        "Mode and its distinct progress loaded: " + mode,
        () -> {
          boolean[] ok = {false};
          screen.onActivity(
              a -> {
                if (a.snapshot() != null)
                  ok[0] =
                      a.integrationContextForTest().mode == mode
                          && a.snapshot().mode == mode
                          && !a.snapshot().loading
                          && a.snapshot().progress != null;
              });
          return ok[0];
        });
    screen.onActivity(this::consistent);
  }

  @Test
  public void pausedModeCompletionKeepsIdentityAtomicThenResumes() throws Exception {
    IntegrationContext c = prepared();
    try (ActivityScenario<GameHomeActivity> screen = ActivityScenario.launch(home(c))) {
      homeReady();
      loaded(screen, Progress.Mode.NORMAL);
      try (Gate gate = new Gate(screen)) {
        press(R.id.game_home_mode);
        gate.queueDrain();
        screen.moveToState(Lifecycle.State.CREATED);
        gate.releaseAndDrain();
        screen.onActivity(this::consistent);
        screen.moveToState(Lifecycle.State.RESUMED);
        loaded(screen, Progress.Mode.DEMO);
        screen.recreate();
        homeReady();
        loaded(screen, Progress.Mode.DEMO);
        capture("mode-paused-completion-resumed-DEMO-distinct-fixture");
        press(R.id.game_home_mode);
        loaded(screen, Progress.Mode.NORMAL);
      }
    }
  }

  @Test
  public void recreationRejectsOldCallbackAndRetainsRequestedMode() throws Exception {
    IntegrationContext c = prepared();
    try (ActivityScenario<GameHomeActivity> screen = ActivityScenario.launch(home(c))) {
      homeReady();
      loaded(screen, Progress.Mode.NORMAL);
      try (Gate gate = new Gate(screen)) {
        press(R.id.game_home_mode);
        gate.queueDrain();
        screen.moveToState(Lifecycle.State.CREATED);
        screen.recreate();
        gate.releaseAndDrain();
        screen.moveToState(Lifecycle.State.RESUMED);
        homeReady();
        loaded(screen, Progress.Mode.DEMO);
        screen.onActivity(this::consistent);
        capture("mode-recreation-old-callback-rejected-DEMO-fixture");
        press(R.id.game_home_mode);
        loaded(screen, Progress.Mode.NORMAL);
      }
    }
  }

  /**
   * Reflection schedules a gate on the existing serial worker; no production hook or timing guess.
   */
  private static final class Gate implements AutoCloseable {
    final CountDownLatch entered = new CountDownLatch(1),
        release = new CountDownLatch(1),
        drained = new CountDownLatch(1);
    final ExecutorService worker;

    Gate(ActivityScenario<GameHomeActivity> screen) throws Exception {
      ExecutorService[] found = {null};
      screen.onActivity(
          a -> {
            try {
              Field field = GameHomeActivity.class.getDeclaredField("integrationWorker");
              field.setAccessible(true);
              found[0] = (ExecutorService) field.get(a);
            } catch (ReflectiveOperationException e) {
              throw new AssertionError(e);
            }
          });
      worker = found[0];
      worker.execute(
          () -> {
            entered.countDown();
            try {
              if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("Gate timeout");
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new AssertionError(e);
            }
          });
      assertTrue("Worker gate entered", entered.await(10, TimeUnit.SECONDS));
    }

    void queueDrain() {
      worker.execute(() -> new Handler(Looper.getMainLooper()).post(drained::countDown));
    }

    void releaseAndDrain() throws Exception {
      release.countDown();
      assertTrue("Worker and posted callback drained", drained.await(10, TimeUnit.SECONDS));
    }

    public void close() {
      release.countDown();
    }
  }
}
