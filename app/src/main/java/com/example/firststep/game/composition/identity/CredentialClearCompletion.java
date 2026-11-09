package com.example.firststep.game.composition.identity;

import com.example.firststep.game.auth.AuthPort;
import com.example.firststep.game.auth.Failure;
import java.util.concurrent.Executor;

/** One main-thread-confined clear operation with a finite terminal deadline. */
final class CredentialClearCompletion {
  interface Scheduler {
    Runnable schedule(Runnable task, long delayMillis);
  }

  private final AuthPort.Completion callback;
  private final Runnable cancelProvider;
  private Runnable cancelDeadline;
  private boolean done;

  CredentialClearCompletion(AuthPort.Completion callback, Runnable cancelProvider) {
    this.callback = callback;
    this.cancelProvider = cancelProvider;
  }

  void startDeadline(Scheduler scheduler, long delayMillis) {
    cancelDeadline = scheduler.schedule(() -> finish(Failure.UNAVAILABLE, true), delayMillis);
  }

  void providerSuccess(Executor executor) {
    executor.execute(this::success);
  }

  void providerError(Executor executor) {
    executor.execute(this::error);
  }

  void success() {
    finish(null, false);
  }

  void error() {
    finish(Failure.UNAVAILABLE, false);
  }

  void cancel() {
    finish(Failure.CANCELED, true);
  }

  private void finish(Failure failure, boolean cancelProviderRequest) {
    if (done) return;
    done = true;
    if (cancelDeadline != null) cancelDeadline.run();
    if (cancelProviderRequest) {
      try {
        cancelProvider.run();
      } catch (RuntimeException ignored) {
        // Completion must not depend on provider cancellation behaving correctly.
      }
    }
    callback.onResult(failure);
  }
}
