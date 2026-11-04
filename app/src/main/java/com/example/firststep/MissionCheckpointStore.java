package com.example.firststep;

import android.content.Context;
import android.content.SharedPreferences;

/** Durable, mode-scoped in-progress UI state; never stores location or sensor measurements. */
public final class MissionCheckpointStore {
  static final class State {
    String stage = "DISCOVERY", discovery = "CARD", completion, photo, typed = "";
  }

  /**
   * Clear a selected mode, then remove private drafts only after checking both modes' DB ownership.
   */
  public static void clearMode(Context context, boolean demo) {
    Context app = context.getApplicationContext();
    SharedPreferences prefs = app.getSharedPreferences(com.example.firststep.data.AccountStorage.checkpointPreferences(app), Context.MODE_PRIVATE);
    String mode = (demo ? "DEMO" : "FIELD") + ":";
    java.util.List<String> candidates = new java.util.ArrayList<>();
    SharedPreferences.Editor editor = prefs.edit();
    for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet())
      if (entry.getKey().startsWith(mode)) {
        if (entry.getKey().endsWith(":photo") && entry.getValue() instanceof String)
          candidates.add((String) entry.getValue());
        editor.remove(entry.getKey());
      }
    editor.apply();
    cleanupCandidates(app, candidates);
  }

  static void cleanupDraft(Context context, String path) {
    if (path != null)
      cleanupCandidates(context.getApplicationContext(), java.util.Collections.singletonList(path));
  }

  private static void cleanupCandidates(Context app, java.util.List<String> candidates) {
    if (candidates.isEmpty()) return;
    com.example.firststep.data.MissionRepository repo =
        com.example.firststep.data.MissionRepository.getInstance(app);
    repo.getCompletions(
        com.example.firststep.data.AppMode.DEMO,
        demoRecords ->
            repo.getCompletions(
                com.example.firststep.data.AppMode.FIELD,
                fieldRecords -> {
                  java.util.Set<String> protectedPaths = new java.util.HashSet<>();
                  for (com.example.firststep.data.MissionCompletion record : demoRecords) {
                    java.io.File file = privateFile(app, record.photoUri);
                    if (file != null) protectedPaths.add(file.getPath());
                  }
                  for (com.example.firststep.data.MissionCompletion record : fieldRecords) {
                    java.io.File file = privateFile(app, record.photoUri);
                    if (file != null) protectedPaths.add(file.getPath());
                  }
                  for (String path : candidates) {
                    java.io.File file = privateFile(app, path);
                    if (file != null && !protectedPaths.contains(file.getPath())) file.delete();
                  }
                }));
  }

  private static java.io.File privateFile(Context context, String path) {
    if (path == null) return null;
    try {
      android.net.Uri uri = android.net.Uri.parse(path);
      if (uri.getScheme() != null && !"file".equals(uri.getScheme())) return null;
      String raw = uri.getScheme() == null ? path : uri.getPath();
      if (raw == null) return null;
      java.io.File root = context.getFilesDir().getCanonicalFile(),
          file = new java.io.File(raw).getCanonicalFile();
      return file.getParentFile() != null && file.getParentFile().equals(root) ? file : null;
    } catch (java.io.IOException ignored) {
      return null;
    }
  }

  private final SharedPreferences prefs;
  private final String prefix;
  private final SharedPreferences syncPreferences;
  private final String mode;
  private final int initialGeneration;

  MissionCheckpointStore(Context context, boolean demo, String missionId) {
    prefs = context.getSharedPreferences(com.example.firststep.data.AccountStorage.checkpointPreferences(context), Context.MODE_PRIVATE);
    mode = demo ? "DEMO" : "FIELD";
    prefix = mode + ":" + missionId + ":";
    syncPreferences = context.getSharedPreferences("firststep-sync-" + com.example.firststep.auth.AuthSession.namespace(context), Context.MODE_PRIVATE);
    initialGeneration = syncPreferences.getInt(mode, -1);
  }

  boolean isCurrentGeneration() {
    int current = syncPreferences.getInt(mode, -1);
    return initialGeneration < 0 ? current <= 0 : current == initialGeneration;
  }

  State load() {
    State s = new State();
    s.stage = prefs.getString(prefix + "stage", "DISCOVERY");
    if (!java.util.Arrays.asList(
            "DISCOVERY", "STORY", "GAME", "PHOTO_PREVIEW", "QUIZ", "SAVING", "SUCCESS")
        .contains(s.stage)) s.stage = "DISCOVERY";
    s.discovery = prefs.getString(prefix + "discovery", "CARD");
    s.completion = prefs.getString(prefix + "completion", null);
    s.photo = prefs.getString(prefix + "photo", null);
    s.typed = prefs.getString(prefix + "typed", "");
    return s;
  }

  void save(State s) {
    if (!isCurrentGeneration()) return;
    prefs
        .edit()
        .putString(prefix + "stage", s.stage)
        .putString(prefix + "discovery", s.discovery)
        .putString(prefix + "completion", s.completion)
        .putString(prefix + "photo", s.photo)
        .putString(prefix + "typed", s.typed)
        .apply();
  }

  void clear() {
    prefs
        .edit()
        .remove(prefix + "stage")
        .remove(prefix + "discovery")
        .remove(prefix + "completion")
        .remove(prefix + "photo")
        .remove(prefix + "typed")
        .apply();
  }
}
