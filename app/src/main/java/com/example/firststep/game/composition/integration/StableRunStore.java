package com.example.firststep.game.composition.integration;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/** Blocking: call off the UI thread. Explicit test runs never change the entry run. */
public final class StableRunStore {
  private static final Object LOCK = new Object();
  private static boolean poisoned;
  private final SharedPreferences prefs;

  public StableRunStore(Context c) {
    prefs =
        c.getApplicationContext()
            .getSharedPreferences("game_integration_entry", Context.MODE_PRIVATE);
  }

  public String resolve(String explicitRun) {
    if (explicitRun != null)
      return IntegrationContext.canonicalUuid(explicitRun) ? explicitRun : null;
    synchronized (LOCK) {
      if (poisoned) return null;
      if (prefs.contains("run")) {
        String v;
        try {
          v = prefs.getString("run", null);
        } catch (RuntimeException e) {
          return null;
        }
        return IntegrationContext.canonicalUuid(v) ? v : null;
      }
      String v = UUID.randomUUID().toString();
      try {
        if (!prefs.edit().putString("run", v).commit()) {
          poisoned = true;
          return null;
        }
      } catch (RuntimeException e) {
        poisoned = true;
        return null;
      }
      return v;
    }
  }
}
