package com.example.firststep.game.sync;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Bounded Firestore schema. The owner, dataset, mode and building are fixed by the path. */
final class MilestoneDocument {
  private static final Set<String> FIELDS =
      new HashSet<>(Arrays.asList("schemaVersion", "introDoneAtMillis", "completion"));
  private static final Set<String> COMPLETION_FIELDS = new HashSet<>(
      Arrays.asList("attemptId", "contentVersion", "correct", "completedAtMillis"));

  private MilestoneDocument() {}

  static String path(SyncScope scope) {
    return "users/" + scope.uid + "/progressDatasets/" + scope.datasetId
        + "/modes/" + scope.mode.name() + "/buildings/" + scope.buildingId;
  }

  static Map<String, Object> encode(Milestone milestone) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("schemaVersion", 1L);
    data.put("introDoneAtMillis", milestone.introDoneAtMillis);
    Map<String, Object> completion = null;
    if (milestone.completion != null) {
      Milestone.Completion value = milestone.completion;
      completion = new LinkedHashMap<>();
      completion.put("attemptId", value.attemptId);
      completion.put("contentVersion", value.contentVersion);
      completion.put("correct", (long) value.correct);
      completion.put("completedAtMillis", value.completedAtMillis);
    }
    data.put("completion", completion);
    return data;
  }

  static Milestone decode(Map<String, Object> data) {
    if (data == null || !data.keySet().equals(FIELDS)
        || integer(data, "schemaVersion") != 1) {
      throw new IllegalArgumentException("Invalid progress document");
    }
    Object raw = data.get("completion");
    Milestone.Completion completion = null;
    if (raw != null) {
      if (!(raw instanceof Map) || !((Map<?, ?>) raw).keySet().equals(COMPLETION_FIELDS)) {
        throw new IllegalArgumentException("Invalid progress completion");
      }
      Map<?, ?> fields = (Map<?, ?>) raw;
      long score = integer(fields, "correct");
      if (score < 2 || score > 3) throw new IllegalArgumentException("Invalid score");
      completion = new Milestone.Completion(
          string(fields, "attemptId"), string(fields, "contentVersion"),
          (int) score, integer(fields, "completedAtMillis"));
    }
    return new Milestone(integer(data, "introDoneAtMillis"), completion);
  }

  private static long integer(Map<?, ?> data, String key) {
    Object value = data.get(key);
    if (!(value instanceof Long) && !(value instanceof Integer)) {
      throw new IllegalArgumentException("Expected integer field");
    }
    return ((Number) value).longValue();
  }

  private static String string(Map<?, ?> data, String key) {
    Object value = data.get(key);
    if (!(value instanceof String)) throw new IllegalArgumentException("Expected text field");
    return (String) value;
  }
}
