package com.example.firststep.game.sync;

import java.util.Collections;

/** Supplies only a test registration. Production registration remains closed. */
public final class AuthoredHostTestRegistry {
  public static DatasetRegistry create(String dataset, String building) {
    return DatasetRegistry.syntheticTests(Collections.singletonMap(dataset, Collections.singleton(building)));
  }
}
