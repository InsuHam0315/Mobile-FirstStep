package com.example.firststep.game.ui.identity;

import androidx.lifecycle.ViewModel;
import com.example.firststep.game.auth.SessionController;
import com.example.firststep.game.composition.identity.IdentityRuntime;

public final class IdentityViewModel extends ViewModel {
  final IdentityRuntime runtime;
  final SessionController controller;

  IdentityViewModel(IdentityRuntime runtime) {
    this.runtime = runtime;
    controller =
        new SessionController(runtime.auth(), runtime.profiles(), runtime.confirmedOwnerObserver());
  }

  @Override
  protected void onCleared() {
    controller.pause();
    runtime.close();
  }
}
