package com.example.firststep.game.composition.identity;

import android.app.Activity;
import com.example.firststep.game.auth.*;
import com.google.firebase.auth.AuthCredential;

public interface CredentialSource {
  interface Callback {
    void complete(AuthCredential credential, Failure failure);
  }

  void attach(Activity activity);

  void detach(Activity activity);

  void request(Callback callback);

  void cancel();

  void clear(AuthPort.Completion callback);
}
