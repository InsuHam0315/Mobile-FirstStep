package com.example.firststep.game.composition.integration;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/** Blocking: call off the UI thread. Explicit test runs never change the entry run. */
public final class StableRunStore {
  private static final Object LOCK = new Object();
  private static final java.util.Set<String> POISONED = new java.util.HashSet<>();
  private final SharedPreferences prefs;

  public StableRunStore(Context c) {
    prefs =
        c.getApplicationContext()
            .getSharedPreferences("game_integration_entry", Context.MODE_PRIVATE);
  }

  public String resolve(String explicitRun) {
    return resolve(IntegrationContext.DATASET, explicitRun);
  }

  public String resolve(String datasetId, String explicitRun) {
    if (!IntegrationComposition.allowedDataset(datasetId, com.example.firststep.BuildConfig.DEBUG)) return null;
    String key = IntegrationContext.DATASET.equals(datasetId) ? "run" : "run_" + datasetId;
    if (explicitRun != null)
      return IntegrationContext.canonicalUuid(explicitRun) ? explicitRun : null;
    synchronized (LOCK) {
      if (POISONED.contains(key)) return null;
      if (prefs.contains(key)) {
        String v;
        try {
          v = prefs.getString(key, null);
        } catch (RuntimeException e) {
          return null;
        }
        return IntegrationContext.canonicalUuid(v) ? v : null;
      }
      String v = UUID.randomUUID().toString();
      try {
        if (!prefs.edit().putString(key, v).commit()) {
          POISONED.add(key);
          return null;
        }
      } catch (RuntimeException e) {
        POISONED.add(key);
        return null;
      }
      return v;
    }
  }
}
