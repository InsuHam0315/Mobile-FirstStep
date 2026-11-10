package com.example.firststep.game.composition.identity;

import android.content.Context;
import java.util.function.Supplier;

public final class H07VariantFactory {
  private static Supplier<IdentityRuntime> override;

  public static void setTestFactory(Supplier<IdentityRuntime> factory) {
    override = factory;
  }

  public static IdentityRuntime create(Context c) {
    return override == null ? FirebaseIdentityRuntime.get(c) : override.get();
  }
}
