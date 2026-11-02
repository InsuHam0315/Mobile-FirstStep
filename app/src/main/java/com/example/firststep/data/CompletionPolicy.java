package com.example.firststep.data;

import java.util.Set;

/** Pure eligibility checks shared by repository and JVM tests. */
public final class CompletionPolicy {
    private CompletionPolicy() { }
    public static boolean canComplete(AppMode mode, Mission mission, Set<String> completedIds,
                                      String discoveryMethod, String completionMethod,
                                      Iterable<Mission> catalog) {
        if (mode == null || mission == null || completedIds == null) return false;
        if (!"AR".equals(discoveryMethod) && !"CARD".equals(discoveryMethod)) return false;
        if (!isValidCompletionMethod(completionMethod)) return false;
        if (mode == AppMode.FIELD && !mission.coordinateVerified) return false;
        for (Mission earlier : catalog) {
            if (earlier.order < mission.order && !completedIds.contains(earlier.id)) return false;
        }
        return true;
    }

    private static boolean isValidCompletionMethod(String value) {
        return "PHOTO".equals(value) || "LIGHT".equals(value) || "HOLD".equals(value)
                || "SPEECH".equals(value) || "TYPED".equals(value)
                || "ACCELEROMETER".equals(value) || "LEGACY".equals(value);
    }
}
