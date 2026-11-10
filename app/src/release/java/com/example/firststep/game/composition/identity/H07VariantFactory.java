package com.example.firststep.game.composition.identity;

import android.content.Context;

public final class H07VariantFactory {
  public static IdentityRuntime create(Context c) {
    return FirebaseIdentityRuntime.get(c);
  }
}
