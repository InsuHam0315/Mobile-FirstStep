package com.example.firststep.game.composition.identity;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
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
  private final Context context;
  private final String webClient;
  private WeakReference<Activity> activity = new WeakReference<>(null);
  private Pending pending;

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
      GetGoogleIdOption option =
          new GetGoogleIdOption.Builder()
              .setServerClientId(webClient)
              .setFilterByAuthorizedAccounts(false)
              .setAutoSelectEnabled(false)
              .build();
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
                  if (e instanceof NoCredentialException) {
                    request.finish(null, Failure.UNAVAILABLE);
                  } else
                    request.finish(
                        null,
                        e instanceof GetCredentialCancellationException
                            ? Failure.CANCELED
                            : Failure.UNAVAILABLE);
                }
              });
    } catch (RuntimeException e) {
      request.finish(null, Failure.UNKNOWN);
    }
  }

  public void clear(AuthPort.Completion callback) {
    try {
      CredentialManager.create(context)
          .clearCredentialStateAsync(
              new ClearCredentialStateRequest(),
              new CancellationSignal(),
              ContextCompat.getMainExecutor(context),
              new CredentialManagerCallback<Void, ClearCredentialException>() {
                public void onResult(Void unused) {
                  callback.onResult(null);
                }

                public void onError(ClearCredentialException error) {
                  callback.onResult(Failure.UNAVAILABLE);
                }
              });
    } catch (RuntimeException unavailable) {
      callback.onResult(Failure.UNAVAILABLE);
    }
  }
}
