package com.example.firststep.data;

import com.example.firststep.auth.AuthSession;
import com.example.firststep.auth.CloudApi;
import java.io.File;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Account-scoped MySQL snapshots. A remote reset generation prevents stale offline resurrection. */
final class AccountSync {
  private final MissionRepository repository;
  AccountSync(MissionRepository repository) { this.repository = repository; }

  private CloudApi api() throws Exception {
    AuthSession session = new AuthSession(repository.appContext);
    AuthSession.Snapshot captured = session.snapshot();
    if (!repository.isAccountRepository() || !repository.ownerId.equals(captured.userId))
      throw new IllegalStateException("Account changed");
    String token = captured.accessToken;
    if (token.isEmpty()) throw new IllegalStateException("Session missing");
    return new CloudApi(repository.appContext, token);
  }

  void sync(MissionRepository.CompletionCallback callback) {
    repository.syncExecutor.execute(() -> {
      boolean ok = false;
      try {
        CloudApi api = api();
        boolean reset = false;
        for (AppMode mode : AppMode.values()) reset |= syncMode(api, mode);
        repository.syncStatus = reset ? "다른 기기에서 초기화한 서버 기록을 반영했어요." : "계정 기록과 사진을 서버에 동기화했어요.";
        ok = true;
      } catch (Exception error) { repository.syncStatus = message(error); }
      repository.postCloudResult(callback, ok);
    });
  }

  private boolean syncMode(CloudApi api, AppMode mode) throws Exception {
    String path = "/v1/progress/" + mode.name();
    JSONObject remote = api.request("GET", path, null);
    int generation = remote.getInt("generation");
    int previous = repository.syncPreferences.getInt(mode.name(), -1);
    if ((previous >= 0 && previous != generation) || (previous < 0 && generation > 0)) {
      apply(mode, remote, true);
      return true;
    }
    Set<String> alreadySaved = new HashSet<>();
    JSONArray existing = remote.getJSONArray("completions");
    for (int i = 0; i < existing.length(); i++) alreadySaved.add(existing.getJSONObject(i).getString("mission_id"));
    JSONArray rows = new JSONArray();
    for (MissionRepository.CompletionRow local : repository.dao.byMode(mode.name())) {
      if (alreadySaved.contains(local.missionId)) continue;
      if ("LEGACY".equals(local.completionMethod)) throw new IllegalStateException("Legacy record needs a new mission attempt");
      JSONObject row = new JSONObject().put("mission_id", local.missionId).put("saved_at", local.savedAt)
          .put("discovery_method", local.discoveryMethod).put("completion_method", local.completionMethod)
          .put("photo_base64", local.photoUri == null ? JSONObject.NULL : CloudPhotos.encode(repository.appContext, local.photoUri));
      rows.put(row);
    }
    JSONObject result = api.request("POST", path + "/sync", new JSONObject().put("generation", generation).put("completions", rows));
    apply(mode, result, false);
    return false;
  }

  private void apply(AppMode mode, JSONObject snapshot, boolean replace) throws Exception {
    int generation = snapshot.getInt("generation");
    if (generation < 0) throw new IllegalStateException("Invalid generation");
    List<MissionRepository.CompletionRow> incoming = new ArrayList<>();
    Map<String, MissionRepository.CompletionRow> current = new HashMap<>();
    for (MissionRepository.CompletionRow row : repository.dao.byMode(mode.name())) current.put(row.missionId, row);
    List<String> created = new ArrayList<>();
    boolean committed = false;
    try {
      JSONArray rows = snapshot.getJSONArray("completions");
      for (int i = 0; i < rows.length(); i++) {
        JSONObject row = rows.getJSONObject(i);
        String missionId = row.getString("mission_id");
        if (repository.getMission(missionId) == null) throw new IllegalStateException("Unknown server mission");
        long savedAt = row.getLong("saved_at");
        String photo = null;
        if (!row.isNull("photo_base64")) {
          MissionRepository.CompletionRow old = current.get(missionId);
          if (old != null && old.savedAt == savedAt && old.photoUri != null && new File(old.photoUri).isFile()) photo = old.photoUri;
          else {
            photo = CloudPhotos.save(repository.appContext, repository.namespace, row.getString("photo_base64"));
            created.add(photo);
          }
        }
        incoming.add(new MissionRepository.CompletionRow(mode.name(), missionId, savedAt, photo,
            row.getString("discovery_method"), row.getString("completion_method")));
      }
      repository.db.runInTransaction(() -> {
        if (replace) repository.dao.deleteMode(mode.name());
        for (MissionRepository.CompletionRow row : incoming) repository.dao.replace(row);
      });
      committed = true;
      repository.syncPreferences.edit().putInt(mode.name(), generation).commit();
      List<String> replaced = new ArrayList<>();
      for (MissionRepository.CompletionRow row : current.values()) if (row.photoUri != null) replaced.add(row.photoUri);
      if (replace) {
        String name = "guest".equals(repository.namespace) ? "mission_checkpoints" : "mission_checkpoints-" + repository.namespace;
        android.content.SharedPreferences checkpoints = repository.appContext.getSharedPreferences(name, android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor edit = checkpoints.edit();
        for (Map.Entry<String, ?> entry : checkpoints.getAll().entrySet()) if (entry.getKey().startsWith(mode.name() + ":")) {
          if (entry.getKey().endsWith(":photo") && entry.getValue() instanceof String) replaced.add((String) entry.getValue());
          edit.remove(entry.getKey());
        }
        edit.commit();
      }
      cleanup(replaced);
    } catch (Exception error) {
      if (!committed) for (String path : created) new File(path).delete();
      throw error;
    }
  }

  void importGuest(MissionRepository.CompletionCallback callback) {
    repository.syncExecutor.execute(() -> {
      boolean ok = false;
      List<String> copies = new ArrayList<>();
      boolean committed = false;
      try {
        CloudApi api = api();
        for (AppMode mode : AppMode.values()) syncMode(api, mode);
        MissionRepository guest = MissionRepository.forOwner(repository.appContext, "guest");
        List<MissionRepository.CompletionRow> imported = new ArrayList<>();
        for (AppMode mode : AppMode.values()) {
          Set<String> ids = new HashSet<>(repository.dao.idsByMode(mode.name()));
          for (MissionRepository.CompletionRow row : guest.dao.byMode(mode.name())) {
            if (ids.contains(row.missionId)) continue;
            Mission mission = repository.getMission(row.missionId);
            if (!CompletionPolicy.canComplete(mode, mission, ids, row.discoveryMethod, row.completionMethod, repository.getMissions()))
              throw new IllegalStateException("Unsupported guest record");
            String photo = null;
            if (row.photoUri != null) {
              photo = CloudPhotos.save(repository.appContext, repository.namespace, CloudPhotos.encode(repository.appContext, row.photoUri));
              copies.add(photo);
            }
            imported.add(new MissionRepository.CompletionRow(row.mode, row.missionId, row.savedAt, photo, row.discoveryMethod, row.completionMethod));
            ids.add(row.missionId);
          }
        }
        repository.db.runInTransaction(() -> { for (MissionRepository.CompletionRow row : imported) repository.dao.completeIfAbsent(row); });
        committed = true;
        cleanup(copies);
        for (AppMode mode : AppMode.values()) syncMode(api, mode);
        repository.syncStatus = "기존 기기 기록을 이 계정에 복사하고 서버에 저장했어요. 원래 기록도 유지됩니다.";
        ok = true;
      } catch (Exception error) {
        if (!committed) for (String path : copies) new File(path).delete();
        repository.syncStatus = committed ? "기기 기록은 계정에 복사됐지만 서버 동기화는 실패했어요. 다시 동기화해 주세요." : message(error);
      }
      repository.postCloudResult(callback, ok);
    });
  }

  private void cleanup(List<String> candidates) {
    Set<String> keep = new HashSet<>(repository.dao.allPhotoPaths());
    String name = "guest".equals(repository.namespace) ? "mission_checkpoints" : "mission_checkpoints-" + repository.namespace;
    for (Map.Entry<String, ?> entry : repository.appContext.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).getAll().entrySet())
      if (entry.getKey().endsWith(":photo") && entry.getValue() instanceof String) keep.add((String) entry.getValue());
    for (String path : candidates) if (path != null && !keep.contains(path)) {
      try { CloudPhotos.privateFile(repository.appContext, path).delete(); }
      catch (java.io.IOException ignored) { }
    }
  }

  void reset(AppMode mode, MissionRepository.CompletionCallback callback) {
    repository.syncExecutor.execute(() -> {
      boolean ok = false;
      try {
        CloudApi api = api();
        String path = "/v1/progress/" + mode.name();
        int generation = api.request("GET", path, null).getInt("generation");
        JSONObject result = api.request("DELETE", path + "?generation=" + generation, null);
        List<String> candidates = repository.dao.photoPathsByMode(mode.name());
        apply(mode, result, true);
        Set<String> keep = new HashSet<>(repository.dao.allPhotoPaths());
        for (String candidate : candidates) if (!keep.contains(candidate)) {
          try { CloudPhotos.privateFile(repository.appContext, candidate).delete(); }
          catch (java.io.IOException ignored) { }
        }
        repository.syncStatus = "선택한 모드의 계정 기록을 기기와 서버에서 초기화했어요.";
        ok = true;
      } catch (Exception error) { repository.syncStatus = "서버에 초기화를 반영하지 못해 기기 기록도 유지했어요. " + message(error); }
      repository.postCloudResult(callback, ok);
    });
  }

  private String message(Exception error) {
    if (error instanceof CloudApi.ApiException) {
      int status = ((CloudApi.ApiException) error).status;
      if (status == 401) return "로그인이 만료됐어요. 기기 기록은 유지됩니다. 다시 로그인해 주세요.";
      if (status == 409) return "서버의 기록 구성이 바뀌었어요. 다시 동기화해 주세요.";
      if (status == 0) return "서버에 연결하지 못했어요. 기록은 이 기기에 보관 중이며 다음 동기화 때 다시 전송합니다.";
    }
    return "계정 기록을 동기화하지 못했어요. 기기 기록은 유지됩니다. 연결과 기록 상태를 확인해 주세요.";
  }
}
