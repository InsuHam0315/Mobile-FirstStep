package com.example.firststep.game.ui.identity;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.firststep.game.auth.Profile;
import com.example.firststep.game.auth.SessionController;
import com.example.firststep.game.composition.identity.IdentityRuntime;
import com.example.firststep.game.composition.home.entry.LocalCampusChoices;
import com.example.firststep.game.presentation.home.entry.CampusChoice;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Retains identity drafts and explicit local selection; neither promotes local progress. */
public final class IdentityViewModel extends ViewModel {
  final IdentityRuntime runtime;
  final SessionController controller;
  final MutableLiveData<Integer> localChanges = new MutableLiveData<>(0);
  private final LocalCampusChoices choices;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private boolean scoped, closed;
  private long generation;
  private final AtomicInteger pendingWrites = new AtomicInteger();
  String localUid, collegeDraft = "";
  Profile guestDraft = new Profile("", "", "");
  CampusChoice choice = new CampusChoice("", "");
  boolean localReady, localBusy, localFailed, localSaved;

  IdentityViewModel(IdentityRuntime runtime, Context context) {
    this.runtime = runtime;
    controller = new SessionController(runtime.auth(), runtime.profiles(), runtime.confirmedOwnerObserver());
    choices = new LocalCampusChoices(context);
  }

  void selectOwner(String uid) {
    if (scoped && Objects.equals(uid, localUid)) return;
    scoped = true;
    localUid = uid;
    final long token = ++generation;
    localReady = localBusy = localFailed = localSaved = false;
    choice = new CampusChoice("", "");
    collegeDraft = "";
    guestDraft = new Profile("", "", "");
    worker.execute(() -> {
      CampusChoice loaded = null;
      Profile guest = null;
      try {
        loaded = choices.load(uid);
        if (uid == null) guest = choices.loadGuestProfile();
      } catch (RuntimeException unavailable) { loaded = null; /* Preserve invalid existing data. */ }
      final CampusChoice selected = loaded;
      final Profile profile = guest;
      main.post(() -> {
        if (closed || token != generation) return;
        localReady = selected != null;
        localFailed = !localReady;
        if (localReady) {
          choice = selected;
          collegeDraft = selected.getCollege();
          if (profile != null) guestDraft = profile;
        }
        changed();
      });
    });
  }

  void saveLocal(Profile profile, CampusChoice selected) {
    if (!localReady || localBusy) return;
    final String owner = localUid;
    final long token = generation;
    localBusy = true;
    localFailed = localSaved = false;
    pendingWrites.incrementAndGet();
    worker.execute(() -> {
      boolean saved;
      try {
        saved = owner == null ? choices.saveGuest(profile, selected) : choices.save(owner, selected);
      } catch (RuntimeException unavailable) { saved = false; }
      finally { pendingWrites.decrementAndGet(); }
      final boolean success = saved;
      main.post(() -> {
        if (closed || token != generation || !Objects.equals(owner, localUid)) return;
        localBusy = false;
        localFailed = !success;
        localSaved = success;
        if (success) choice = selected;
        changed();
      });
    });
    changed();
  }

  private void changed() { localChanges.setValue(localChanges.getValue() + 1); }

  boolean hasPendingLocalWrites() { return pendingWrites.get() != 0; }

  @Override protected void onCleared() {
    closed = true;
    ++generation;
    worker.shutdown();
    controller.pause();
    runtime.close();
  }
}
