package com.example.firststep.game.composition.identity;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.example.firststep.game.auth.*;
import com.google.android.libraries.identity.googleid.*;
import com.google.firebase.auth.*;
import java.lang.ref.WeakReference;

/**
 * Real configured web OAuth client only. Cancel completes locally even if a provider never calls
 * back.
 */
public final class GoogleCredentialSource implements CredentialSource {
  private static final long CLEAR_DEADLINE_MILLIS = 10_000L;
  private final Context context;
  private final String webClient;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private WeakReference<Activity> activity = new WeakReference<>(null);
  private Pending pending;
  private PendingClear pendingClear;

  private final class Pending {
    final CancellationSignal signal = new CancellationSignal();
    final Callback callback;
    boolean done;

    Pending(Callback callback) {
      this.callback = callback;
    }

    void finish(AuthCredential credential, Failure failure) {
      if (done) return;
      done = true;
      if (pending == this) pending = null;
      callback.complete(
          signal.isCanceled() ? null : credential,
          signal.isCanceled() ? Failure.CANCELED : failure);
    }
  }

  private final class PendingClear {
    final CancellationSignal signal = new CancellationSignal();
    final CredentialClearCompletion completion;

    PendingClear(AuthPort.Completion callback) {
      completion =
          new CredentialClearCompletion(
              failure -> {
                if (pendingClear == this) pendingClear = null;
                callback.onResult(failure);
              },
              signal::cancel);
    }
  }

  public GoogleCredentialSource(Context c, String webClient) {
    context = c.getApplicationContext();
    this.webClient = webClient;
  }

  public void attach(Activity a) {
    activity = new WeakReference<>(a);
  }

  public void detach(Activity a) {
    if (activity.get() == a) {
      activity.clear();
      cancel();
    }
  }

  public void cancel() {
    Pending old = pending;
    if (old != null) {
      try {
        old.signal.cancel();
      } catch (RuntimeException providerFailure) {
        /* local terminal cancellation still applies */
      }
      old.finish(null, Failure.CANCELED);
    }
  }

  public void request(Callback callback) {
    Activity a = activity.get();
    if (a == null || a.isFinishing() || a.isDestroyed()) {
      callback.complete(null, Failure.CANCELED);
      return;
    }
    Pending request = new Pending(callback);
    pending = request;
    try {
      GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(webClient).build();
      GetCredentialRequest args =
          new GetCredentialRequest.Builder().addCredentialOption(option).build();
      CredentialManager.create(context)
          .getCredentialAsync(
              a,
              args,
              request.signal,
              ContextCompat.getMainExecutor(context),
              new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                public void onResult(GetCredentialResponse response) {
                  if (request.done) return;
                  if (request.signal.isCanceled()) {
                    request.finish(null, Failure.CANCELED);
                    return;
                  }
                  try {
                    Credential c = response.getCredential();
                    if (!(c instanceof CustomCredential)
                        || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(
                            c.getType())) throw new IllegalArgumentException();
                    String token = GoogleIdTokenCredential.createFrom(c.getData()).getIdToken();
                    request.finish(GoogleAuthProvider.getCredential(token, null), null);
                  } catch (RuntimeException e) {
                    request.finish(null, Failure.UNKNOWN);
                  }
                }

                public void onError(GetCredentialException e) {
                  request.finish(null, classifyError(e));
                }
              });
    } catch (RuntimeException e) {
      request.finish(null, Failure.UNKNOWN);
    }
  }

  static Failure classifyError(GetCredentialException error) {
    if (error instanceof NoCredentialException) return Failure.NO_CREDENTIAL;
    if (error instanceof GetCredentialCancellationException) return Failure.CANCELED;
    return Failure.UNAVAILABLE;
  }

  public void clear(AuthPort.Completion callback) {
    PendingClear previous = pendingClear;
    PendingClear request = new PendingClear(callback);
    pendingClear = request;
    if (previous != null) previous.completion.cancel();
    // A previous completion may have reentrantly replaced this request.
    if (pendingClear != request) return;
    request.completion.startDeadline(
        (task, delay) -> {
          mainHandler.postDelayed(task, delay);
          return () -> mainHandler.removeCallbacks(task);
        },
        CLEAR_DEADLINE_MILLIS);
    try {
      CredentialManager.create(context)
          .clearCredentialStateAsync(
              new ClearCredentialStateRequest(),
              request.signal,
              ContextCompat.getMainExecutor(context),
              new CredentialManagerCallback<Void, ClearCredentialException>() {
                public void onResult(Void unused) {
                  // The framework may call its clear-success receiver on a Binder thread.
                  request.completion.providerSuccess(mainHandler::post);
                }

                public void onError(ClearCredentialException error) {
                  request.completion.providerError(mainHandler::post);
                }
              });
    } catch (RuntimeException unavailable) {
      request.completion.error();
    }
  }
}
