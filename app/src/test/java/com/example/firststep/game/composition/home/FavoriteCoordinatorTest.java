package com.example.firststep.game.composition.home;

import static org.junit.Assert.*;

import com.example.firststep.facility.Facility;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.presentation.home.HomeController;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.junit.Test;

public class FavoriteCoordinatorTest {
  @Test
  public void recreatedHostReadWaitsForOldHostBlockedWrite() {
    ArrayDeque<Runnable> queue = new ArrayDeque<>();
    Executor serial = queue::add;
    Set<String> saved = new HashSet<>(Collections.singleton("unknown"));
    List<FavoriteCoordinator.Snapshot> observed = new ArrayList<>();
    FavoriteCoordinator[] shared = new FavoriteCoordinator[1];
    FavoriteCoordinator.Store store =
        new FavoriteCoordinator.Store() {
          public Set<String> read() {
            return new HashSet<>(saved);
          }

          public void set(String id, boolean desired) {
            // The replacement host admits its read while the old host's write is blocked.
            shared[0].read(observed::add);
            if (desired) saved.add(id);
            else saved.remove(id);
          }
        };
    shared[0] = new FavoriteCoordinator(serial, store);
    shared[0].set("library", true, ok -> assertTrue(ok));
    queue.remove().run();
    assertTrue(observed.isEmpty());
    queue.remove().run();
    assertEquals(new HashSet<>(Arrays.asList("library", "unknown")), observed.get(0).ids);
    assertEquals(1, observed.get(0).revision);
  }

  @Test
  public void revisionDetectsDelayedSnapshotAndFailedWriteDoesNotAdvance() {
    ArrayDeque<Runnable> queue = new ArrayDeque<>();
    Set<String> saved = new HashSet<>();
    FavoriteCoordinator c =
        new FavoriteCoordinator(
            queue::add,
            new FavoriteCoordinator.Store() {
              public Set<String> read() {
                return new HashSet<>(saved);
              }

              public void set(String id, boolean desired) {
                if (id.equals("bad")) throw new IllegalStateException();
                if (desired) saved.add(id);
                else saved.remove(id);
              }
            });
    List<FavoriteCoordinator.Snapshot> reads = new ArrayList<>();
    List<Boolean> writes = new ArrayList<>();
    c.read(reads::add);
    c.set("library", true, writes::add);
    c.set("bad", true, writes::add);
    queue.remove().run();
    queue.remove().run();
    queue.remove().run();
    assertNotEquals(reads.get(0).revision, c.revision());
    assertEquals(2, c.revision());
    assertEquals(Arrays.asList(true, false), writes);
  }

  @Test
  public void failureSurvivesRecreationAndOtherSuccessUntilSameIdSucceeds() {
    ArrayDeque<Runnable> queue = new ArrayDeque<>();
    Set<String> saved = new HashSet<>(Collections.singleton("unknown"));
    boolean[] failA = {true};
    FavoriteCoordinator c =
        new FavoriteCoordinator(
            queue::add,
            new FavoriteCoordinator.Store() {
              public Set<String> read() {
                return new HashSet<>(saved);
              }

              public void set(String id, boolean desired) {
                if (id.equals("a") && failA[0]) throw new IllegalStateException();
                if (desired) saved.add(id);
                else saved.remove(id);
              }
            });
    c.set("a", true, ok -> assertFalse(ok));
    c.set("b", true, ok -> assertTrue(ok));
    queue.remove().run();
    queue.remove().run();
    List<FavoriteCoordinator.Snapshot> snapshots = new ArrayList<>();
    c.read(snapshots::add);
    queue.remove().run();
    assertEquals(Collections.singletonMap("a", true), snapshots.get(0).failedFavorites);
    assertTrue(snapshots.get(0).ids.contains("unknown"));
    assertTrue(snapshots.get(0).ids.contains("b"));
    failA[0] = false;
    c.set("a", true, ok -> assertTrue(ok));
    queue.remove().run();
    c.read(snapshots::add);
    queue.remove().run();
    assertTrue(snapshots.get(1).failedFavorites.isEmpty());
    assertTrue(snapshots.get(1).ids.contains("a"));
    try {
      snapshots.get(0).failedFavorites.clear();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void failedDestroyedHostWriteIsAdoptedAndRetriedByReplacement() {
    ArrayDeque<Runnable> queue = new ArrayDeque<>();
    Set<String> saved = new HashSet<>();
    boolean[] fail = {true};
    FavoriteCoordinator coordinator =
        new FavoriteCoordinator(
            queue::add,
            new FavoriteCoordinator.Store() {
              public Set<String> read() {
                return new HashSet<>(saved);
              }

              public void set(String id, boolean desired) {
                if (fail[0]) throw new IllegalStateException();
                if (desired) saved.add(id);
                else saved.remove(id);
              }
            });
    Facility item =
        new Facility(
            "library",
            "library",
            "학습",
            "summary",
            Arrays.asList("study"),
            Arrays.asList("library"),
            "https://kunsan.ac.kr",
            "source",
            "2026-01-01",
            "unknown",
            "unknown");
    List<Facility> list = Collections.singletonList(item);
    class Host implements HomeController.Port {
      HomeController controller;
      boolean closed;

      public void load(long token, Progress.Mode mode, String q, String category, boolean only) {
        coordinator.read(
            s -> {
              if (!closed)
                controller.loaded(
                    token,
                    new HomeController.Data(
                        list,
                        list,
                        s.ids,
                        Progress.empty(mode, "digital_information_software"),
                        false,
                        false,
                        0,
                        s.failedFavorites));
            });
      }

      public void setFavorite(String id, boolean desired, Consumer<Boolean> callback) {
        coordinator.set(
            id,
            desired,
            ok -> {
              if (!closed) callback.accept(ok);
            });
      }
    }
    Host old = new Host();
    old.controller = new HomeController(old, s -> {});
    old.controller.resume();
    queue.remove().run();
    old.controller.favorite("library", true);
    old.controller.pause();
    old.closed = true;
    Host replacement = new Host();
    replacement.controller = new HomeController(replacement, s -> {});
    replacement.controller.resume();
    queue.remove().run();
    queue.remove().run();
    assertEquals("favorite", replacement.controller.snapshot().error);
    fail[0] = false;
    replacement.controller.retry();
    queue.remove().run();
    queue.remove().run();
    assertNull(replacement.controller.snapshot().error);
    assertTrue(replacement.controller.snapshot().favoriteIds.contains("library"));
  }
}
