package com.example.firststep.game.presentation.home.account;

import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.sync.Milestone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Main-thread controller. Reads run on a worker and are fenced by screen and account epochs. */
public final class AccountHomeController {
  public interface Source {
    Reading load();
  }

  /** Execute a brief publication or navigation only for the still-current captured owner. */
  public interface OwnerGuard {
    boolean publishIfCurrent(Runnable action);
  }

  public static final class Reading {
    final AccountHomeState.Status blocked;
    final Progress local;
    final Milestone confirmed;
    final OwnerGuard owner;
    final List<String> favorites;
    final boolean favoritesAvailable;

    private Reading(AccountHomeState.Status blocked, Progress local, Milestone confirmed,
        OwnerGuard owner, List<String> favorites, boolean favoritesAvailable) {
      this.blocked = blocked;
      this.local = local;
      this.confirmed = confirmed;
      this.owner = owner;
      this.favorites = Collections.unmodifiableList(new ArrayList<>(favorites));
      this.favoritesAvailable = favoritesAvailable;
    }

    public static Reading blocked(AccountHomeState.Status status, List<String> favorites,
        boolean favoritesAvailable) {
      if (status != AccountHomeState.Status.UNAVAILABLE && status != AccountHomeState.Status.NO_ACCOUNT
          && status != AccountHomeState.Status.FAILED) {
        throw new IllegalArgumentException("Invalid blocked state");
      }
      return new Reading(status, null, null, null, favorites, favoritesAvailable);
    }

    public static Reading progress(Progress local, Milestone confirmed, OwnerGuard owner,
        List<String> favorites, boolean favoritesAvailable) {
      Objects.requireNonNull(local);
      if (local.mode != Progress.Mode.NORMAL || local.instantDemo) {
        throw new IllegalArgumentException("Invalid account progress mode");
      }
      return new Reading(null, local, confirmed,
          Objects.requireNonNull(owner), favorites, favoritesAvailable);
    }
  }

  private final Source source;
  private final boolean registered;
  private final ProgressService.Clock clock;
  private final Executor worker, main;
  private final Consumer<AccountHomeState> renderer;
  private final Runnable openEpisode;
  private boolean active, opening;
  private long generation;
  private Reading reading;
  private AccountHomeState rendered;

  public AccountHomeController(Source source, boolean registered, ProgressService.Clock clock,
      Executor worker, Executor main, Consumer<AccountHomeState> renderer, Runnable openEpisode) {
    this.source = Objects.requireNonNull(source);
    this.registered = registered;
    this.clock = Objects.requireNonNull(clock);
    this.worker = Objects.requireNonNull(worker);
    this.main = Objects.requireNonNull(main);
    this.renderer = Objects.requireNonNull(renderer);
    this.openEpisode = Objects.requireNonNull(openEpisode);
  }

  public void resume() {
    active = true;
    opening = false;
    refresh();
  }

  public void pause() {
    active = false;
    generation++;
    reading = null;
    publish(initial());
  }

  public void refresh() {
    if (!active) return;
    final long request = ++generation;
    reading = null;
    publish(initial());
    worker.execute(() -> {
      Reading loaded;
      try {
        loaded = Objects.requireNonNull(source.load());
      } catch (RuntimeException failure) {
        loaded = Reading.blocked(AccountHomeState.Status.FAILED, Collections.emptyList(), false);
      }
      Reading response = loaded;
      main.execute(() -> {
        if (!active || generation != request) return;
        reading = response;
        tick();
      });
    });
  }

  public void tick() {
    Reading snapshot = reading;
    if (!active || snapshot == null) return;
    if (snapshot.local == null) {
      publish(new AccountHomeState(snapshot.blocked, AccountHomeState.Server.NONE, 0,
          snapshot.favorites, snapshot.favoritesAvailable));
      return;
    }
    AccountHomeState state = AccountHomeState.project(snapshot.local, snapshot.confirmed,
        clock.nowMillis(), snapshot.favorites, snapshot.favoritesAvailable);
    if (!snapshot.owner.publishIfCurrent(() -> publish(state))) loseOwner(snapshot);
  }

  public void openAuthored() {
    if (!active || opening) return;
    Reading snapshot = reading;
    // The existing unavailable screen is a safe destination for a closed registration.
    if (!registered) {
      navigate();
    } else if (snapshot != null && snapshot.local != null && rendered != null
        && rendered.status != AccountHomeState.Status.FAILED) {
      if (!snapshot.owner.publishIfCurrent(this::navigate)) loseOwner(snapshot);
    }
  }

  private void navigate() {
    opening = true;
    openEpisode.run();
  }

  private void loseOwner(Reading old) {
    generation++;
    reading = Reading.blocked(AccountHomeState.Status.NO_ACCOUNT,
        old.favorites, old.favoritesAvailable);
    tick();
  }

  private AccountHomeState initial() {
    return new AccountHomeState(
        registered ? AccountHomeState.Status.LOADING : AccountHomeState.Status.UNAVAILABLE,
        AccountHomeState.Server.NONE, 0, Collections.emptyList(), false);
  }

  private void publish(AccountHomeState state) {
    if (state.equals(rendered)) return;
    rendered = state;
    renderer.accept(state);
  }
}
