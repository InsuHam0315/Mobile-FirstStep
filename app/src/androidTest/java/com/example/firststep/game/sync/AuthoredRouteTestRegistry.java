package com.example.firststep.game.sync;

import java.util.Collections;

/** Synthetic registration compiled only into the instrumentation APK. */
public final class AuthoredRouteTestRegistry {
  private AuthoredRouteTestRegistry() {}

  public static DatasetRegistry create(String dataset, String target) {
    return DatasetRegistry.syntheticTests(
        Collections.singletonMap(dataset, Collections.singleton(target)));
  }
}
