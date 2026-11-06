package com.example.firststep.facility;

import static org.junit.Assert.*;

import android.content.SharedPreferences;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Reproduces two repositories sharing one preferences file without Android runtime dependencies.
 */
public class FacilityRepositoryConcurrencyTest {
  private static final long LIMIT_SECONDS = 5;

  @Test
  public void concurrentAddsPreserveBothAndUnknown() throws Exception {
    assertConcurrent(
        new HashSet<>(Arrays.asList("unknown")),
        "alpha",
        true,
        "beta",
        true,
        new HashSet<>(Arrays.asList("unknown", "alpha", "beta")));
  }

  @Test
  public void concurrentAddAndRemovePreserveUnrelated() throws Exception {
    assertConcurrent(
        new HashSet<>(Arrays.asList("unknown", "remove")),
        "alpha",
        true,
        "remove",
        false,
        new HashSet<>(Arrays.asList("unknown", "alpha")));
  }

  private void assertConcurrent(
      Set<String> initial,
      String firstId,
      boolean firstDesired,
      String secondId,
      boolean secondDesired,
      Set<String> expected)
      throws Exception {
    FakePreferences prefs = new FakePreferences(initial);
    FacilityRepository a = new FacilityRepository(prefs), b = new FacilityRepository(prefs);
    AtomicReference<Throwable> errorA = new AtomicReference<>(), errorB = new AtomicReference<>();
    CountDownLatch secondDone = new CountDownLatch(1);
    Thread first =
        new Thread(
            () -> {
              try {
                a.setFavorite(firstId, firstDesired);
              } catch (Throwable t) {
                errorA.set(t);
              }
            },
            "facility-first");
    Thread second =
        new Thread(
            () -> {
              try {
                b.setFavorite(secondId, secondDesired);
              } catch (Throwable t) {
                errorB.set(t);
              } finally {
                secondDone.countDown();
              }
            },
            "facility-second");
    boolean secondStarted = false;
    try {
      first.start();
      assertTrue(
          "first read did not occur", prefs.firstRead.await(LIMIT_SECONDS, TimeUnit.SECONDS));
      second.start();
      secondStarted = true;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIMIT_SECONDS);
      boolean sawOldRead = false, sawRepositoryLock = false;
      while (System.nanoTime() < deadline) {
        if (prefs.secondRead.getCount() == 0) {
          sawOldRead = true;
          break;
        }
        if (blockedInRepository(second)) {
          sawRepositoryLock = true;
          break;
        }
        if (!second.isAlive()) break;
        Thread.yield();
      }
      assertTrue(
          "second thread neither read old state nor blocked on repository monitor",
          sawOldRead || sawRepositoryLock);
      if (sawOldRead)
        assertTrue(
            "second write did not finish before first is released",
            secondDone.await(LIMIT_SECONDS, TimeUnit.SECONDS));
    } finally {
      prefs.releaseFirst.countDown();
      first.join(TimeUnit.SECONDS.toMillis(LIMIT_SECONDS));
      if (secondStarted) second.join(TimeUnit.SECONDS.toMillis(LIMIT_SECONDS));
    }
    assertFalse("first writer hung", first.isAlive());
    assertFalse("second writer hung", second.isAlive());
    assertNull("first writer failed", errorA.get());
    assertNull("second writer failed", errorB.get());
    assertEquals(expected, prefs.copy());
  }

  private static boolean blockedInRepository(Thread thread) {
    if (thread.getState() != Thread.State.BLOCKED) return false;
    StackTraceElement[] frames = thread.getStackTrace();
    if (frames.length == 0) return false;
    StackTraceElement frame = frames[0];
    return frame.getClassName().equals(FacilityRepository.class.getName())
        && (frame.getMethodName().equals("setFavorite")
            || frame.getMethodName().equals("favorites"));
  }

  @Test
  public void favoritesReturnsDefensiveCopy() {
    FakePreferences prefs = new FakePreferences(new HashSet<>(Arrays.asList("unknown")));
    prefs.blockFirst = false;
    FacilityRepository repo = new FacilityRepository(prefs);
    Set<String> read = repo.favorites();
    read.add("injected");
    assertEquals(Collections.singleton("unknown"), repo.favorites());
  }

  @Test
  public void writeFailurePropagates() {
    FakePreferences prefs = new FakePreferences(Collections.singleton("unknown"));
    prefs.blockFirst = false;
    prefs.failWrite = true;
    try {
      new FacilityRepository(prefs).setFavorite("alpha", true);
      fail("expected failure");
    } catch (IllegalStateException expected) {
      assertEquals(Collections.singleton("unknown"), prefs.copy());
    }
  }

  private static final class FakePreferences implements SharedPreferences {
    private Set<String> values;
    private int reads;
    volatile boolean blockFirst = true, failWrite;
    final CountDownLatch firstRead = new CountDownLatch(1),
        secondRead = new CountDownLatch(1),
        releaseFirst = new CountDownLatch(1);

    FakePreferences(Set<String> initial) {
      values = new HashSet<>(initial);
    }

    synchronized Set<String> copy() {
      return new HashSet<>(values);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> fallback) {
      final Set<String> snapshot;
      final int index;
      synchronized (this) {
        snapshot = new HashSet<>(values);
        index = ++reads;
      }
      if (index == 1 && blockFirst) {
        firstRead.countDown();
        try {
          if (!releaseFirst.await(LIMIT_SECONDS, TimeUnit.SECONDS))
            throw new AssertionError("first read release timed out");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError(e);
        }
      } else if (index == 2) secondRead.countDown();
      return snapshot;
    }

    @Override
    public Editor edit() {
      return new FakeEditor();
    }

    private final class FakeEditor implements Editor {
      private Set<String> next;

      @Override
      public Editor putStringSet(String key, Set<String> value) {
        next = new HashSet<>(value);
        return this;
      }

      @Override
      public void apply() {
        synchronized (FakePreferences.this) {
          if (failWrite) throw new IllegalStateException("write failed");
          values = new HashSet<>(next);
        }
      }

      @Override
      public boolean commit() {
        apply();
        return true;
      }

      @Override
      public Editor putString(String k, String v) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Editor putInt(String k, int v) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Editor putLong(String k, long v) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Editor putFloat(String k, float v) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Editor putBoolean(String k, boolean v) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Editor remove(String k) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Editor clear() {
        throw new UnsupportedOperationException();
      }
    }

    @Override
    public Map<String, ?> getAll() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getString(String k, String d) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getInt(String k, int d) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getLong(String k, long d) {
      throw new UnsupportedOperationException();
    }

    @Override
    public float getFloat(String k, float d) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean getBoolean(String k, boolean d) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(String k) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
  }
}
