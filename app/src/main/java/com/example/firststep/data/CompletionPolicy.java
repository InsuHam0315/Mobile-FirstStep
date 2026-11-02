package com.example.firststep.data;

import java.util.Set;

/** Pure eligibility checks shared by repository and JVM tests. */
public final class CompletionPolicy {
  private CompletionPolicy() {}

  public static boolean canComplete(
      AppMode mode,
      Mission mission,
      Set<String> completedIds,
      String discoveryMethod,
      String completionMethod,
      Iterable<Mission> catalog) {
    if (mode == null || mission == null || completedIds == null || catalog == null) return false;
    if (!"AR".equals(discoveryMethod) && !"CARD".equals(discoveryMethod)) return false;
    if ("LEGACY".equals(completionMethod)) {
      if (!completedIds.contains(mission.id)) return false;
    } else if (!supportsMethod(mission.type, completionMethod)) return false;
    if (mode == AppMode.FIELD && !mission.coordinateVerified) return false;
    for (Mission earlier : catalog) {
      if (earlier.order < mission.order && !completedIds.contains(earlier.id)) return false;
    }
    return true;
  }

  private static boolean supportsMethod(String type, String method) {
    if (type == null || method == null) return false;
    switch (type) {
      case "PHOTO_QUIZ":
        return "PHOTO".equals(method);
      case "LIGHT_QUIZ":
        return "LIGHT".equals(method) || "HOLD".equals(method);
      case "VOICE_QUIZ":
        return "SPEECH".equals(method) || "TYPED".equals(method);
      case "DIRECTION":
        return "COMPASS".equals(method) || "HOLD".equals(method);
      case "ACCELERATION_QUIZ":
        return "ACCELEROMETER".equals(method) || "HOLD".equals(method);
      default:
        return false;
    }
  }
}
