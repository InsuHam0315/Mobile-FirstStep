package com.example.firststep.game.sync;

import java.util.Collections;

/** Exposes synthetic registration to the identity composition test source set only. */
public final class AccountReadbackTestRegistry {
  private AccountReadbackTestRegistry() {}

  public static DatasetRegistry forBuilding(String datasetId, String buildingId) {
    return DatasetRegistry.syntheticTests(
        Collections.singletonMap(datasetId, Collections.singleton(buildingId)));
  }
}
