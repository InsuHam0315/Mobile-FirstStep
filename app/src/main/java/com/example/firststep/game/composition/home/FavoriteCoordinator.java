package com.example.firststep.game.composition.home;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Admits reads and writes immediately to a shared serial executor. */
public final class FavoriteCoordinator {
  public interface Store {
    Set<String> read();

    void set(String id, boolean desired);
  }

  public static final class Snapshot {
    public final Set<String> ids;
    public final long revision;
    public final Map<String, Boolean> failedFavorites;

    Snapshot(Set<String> ids, Map<String, Boolean> failedFavorites, long revision) {
      this.ids = ids;
      this.failedFavorites = Collections.unmodifiableMap(new LinkedHashMap<>(failedFavorites));
      this.revision = revision;
    }
  }

  private final java.util.concurrent.atomic.AtomicLong revision =
      new java.util.concurrent.atomic.AtomicLong();

  public long revision() {
    return revision.get();
  }

  private final Map<String, Boolean> failedFavorites = new LinkedHashMap<>();
  private final Executor serial;
  private final Store store;

  public FavoriteCoordinator(Executor serial, Store store) {
    this.serial = Objects.requireNonNull(serial);
    this.store = Objects.requireNonNull(store);
  }

  /** Null means the store read failed. */
  public void read(Consumer<Snapshot> callback) {
    serial.execute(
        () -> {
          Set<String> result;
          try {
            result = Collections.unmodifiableSet(new HashSet<>(store.read()));
          } catch (Exception failure) {
            result = null;
          }
          callback.accept(
              result == null ? null : new Snapshot(result, failedFavorites, revision.get()));
        });
  }

  public void set(String id, boolean desired, Consumer<Boolean> callback) {
    serial.execute(
        () -> {
          boolean success;
          try {
            store.set(id, desired);
            failedFavorites.remove(id);
            success = true;
          } catch (Exception failure) {
            failedFavorites.put(id, desired);
            success = false;
          }
          revision.incrementAndGet();
          callback.accept(success);
        });
  }
}
