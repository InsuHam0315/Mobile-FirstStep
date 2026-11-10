package com.example.firststep.game.composition.home.account;

import android.content.Context;
import com.example.firststep.facility.Facility;
import com.example.firststep.facility.FacilityRepository;
import com.example.firststep.game.composition.authored.AuthoredEpisodeHost;
import com.example.firststep.game.composition.authored.AuthoredProgressRepository;
import com.example.firststep.game.composition.authored.SQLiteAuthoredProgressStore;
import com.example.firststep.game.composition.identity.AccountProgressAccess;
import com.example.firststep.game.composition.identity.FirebaseIdentityRuntime;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.presentation.home.account.AccountHomeController;
import com.example.firststep.game.presentation.home.account.AccountHomeController.Reading;
import com.example.firststep.game.presentation.home.account.AccountHomeState;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.Milestone;
import com.example.firststep.game.sync.SQLiteSyncStore;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Worker-only local reads. Home never requests a server refresh or starts a write. */
public final class AccountHomeReader implements AccountHomeController.Source {
  private final Context app;
  private final boolean registered;

  public AccountHomeReader(Context context) {
    app = context.getApplicationContext();
    registered = registered(DatasetRegistry.production());
  }

  public boolean isRegistered() {
    return registered;
  }

  static boolean registered(DatasetRegistry registry) {
    try {
      registry.requireEligible(new SyncScope("registration-check", "registration-check", 1,
          AuthoredEpisodeHost.DATASET, Progress.Mode.NORMAL, AuthoredEpisodeHost.TARGET));
      return true;
    } catch (IllegalArgumentException closed) {
      return false;
    }
  }

  @Override public Reading load() {
    List<String> favorites = Collections.emptyList();
    boolean favoritesAvailable = false;
    try {
      FacilityRepository facilities = new FacilityRepository(app);
      favorites = favoriteNames(facilities.load(), facilities.favorites());
      favoritesAvailable = true;
    } catch (Exception unavailable) {
      // Failure in device-local favorites must not hide the account progress card.
    }
    if (!registered) {
      return Reading.blocked(AccountHomeState.Status.UNAVAILABLE, favorites, favoritesAvailable);
    }
    try {
      Optional<AccountProgressAccess> access = FirebaseIdentityRuntime.get(app).accountProgress();
      if (!access.isPresent()) {
        return Reading.blocked(AccountHomeState.Status.NO_ACCOUNT, favorites, favoritesAvailable);
      }
      AccountProgressAccess account = access.get();
      SyncScope scope = account.capture(AuthoredEpisodeHost.DATASET, AuthoredEpisodeHost.TARGET);
      SessionOwner owner = account.owner();
      try (SQLiteAuthoredProgressStore gameplay = new SQLiteAuthoredProgressStore(app, account.registry());
           SQLiteSyncStore projection = new SQLiteSyncStore(app, account.registry())) {
        AuthoredProgressRepository repository = new AuthoredProgressRepository(
            account.registry(), owner, scope, gameplay, System::currentTimeMillis);
        Progress local = repository.read(Progress.Mode.NORMAL, scope.buildingId);
        Milestone confirmed = owner.whileCurrent(scope, () -> projection.read(scope).confirmed);
        return Reading.progress(local, confirmed,
            action -> publishIfCurrent(owner, scope, action), favorites, favoritesAvailable);
      }
    } catch (SessionOwner.StaleSession stale) {
      return Reading.blocked(AccountHomeState.Status.NO_ACCOUNT, favorites, favoritesAvailable);
    } catch (RuntimeException failure) {
      return Reading.blocked(AccountHomeState.Status.FAILED, favorites, favoritesAvailable);
    }
  }

  private static boolean publishIfCurrent(SessionOwner owner, SyncScope scope, Runnable action) {
    try {
      return owner.whileCurrent(scope, () -> {
        action.run();
        return true;
      });
    } catch (SessionOwner.StaleSession stale) {
      return false;
    }
  }

  static List<String> favoriteNames(List<Facility> catalog, Set<String> savedIds) {
    List<String> names = new ArrayList<>();
    for (Facility facility : catalog) if (savedIds.contains(facility.id)) names.add(facility.name);
    return names;
  }
}
