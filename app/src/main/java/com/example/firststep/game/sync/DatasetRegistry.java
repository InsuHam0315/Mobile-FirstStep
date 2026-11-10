package com.example.firststep.game.sync;

import com.example.firststep.game.domain.Progress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Closed authored dataset allowlist. The current app has no sync-eligible authored episode. */
public final class DatasetRegistry {
  private final Map<String, Set<String>> targets;

  private DatasetRegistry(Map<String, Set<String>> registrations) {
    Map<String, Set<String>> validated = new HashMap<>();
    registrations.forEach((dataset, buildings) -> {
      SyncScope.identifier(dataset);
      if (excluded(dataset) || buildings.isEmpty()) {
        throw new IllegalArgumentException("Ineligible dataset registration");
      }
      Set<String> ids = new HashSet<>();
      for (String building : buildings) ids.add(SyncScope.identifier(building));
      validated.put(dataset, Collections.unmodifiableSet(ids));
    });
    targets = Collections.unmodifiableMap(validated);
  }

  public static DatasetRegistry production() {
    return new DatasetRegistry(Collections.emptyMap());
  }

  // Package-private so a production host cannot construct an allowlist from external input.
  static DatasetRegistry syntheticTests(Map<String, Set<String>> registrations) {
    return new DatasetRegistry(registrations);
  }

  public void requireEligible(SyncScope scope) {
    if (scope == null || scope.mode != Progress.Mode.NORMAL) {
      throw new IllegalArgumentException("Ineligible progress mode");
    }
    Set<String> buildings = targets.get(scope.datasetId);
    if (buildings == null || !buildings.contains(scope.buildingId)) {
      throw new IllegalArgumentException("Unregistered authored progress");
    }
  }

  private static boolean excluded(String id) {
    String value = id.toLowerCase(Locale.ROOT);
    return value.startsWith("legacy")
        || value.contains("fixture")
        || value.contains("practice")
        || value.contains("demo")
        || value.equals("synthetic-v1")
        || value.equals("software_guide_v1")
        || value.equals("software_narration_v1");
  }
}
