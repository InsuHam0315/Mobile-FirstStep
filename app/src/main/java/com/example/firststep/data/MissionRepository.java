package com.example.firststep.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.room.Dao;
import androidx.room.ColumnInfo;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.annotation.NonNull;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.Transaction;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local mission catalog, mode-isolated progress, and lightweight profile settings. */
public final class MissionRepository {
    private static volatile MissionRepository instance;
    private final Context appContext;
    private final CompletionDao dao;
    private final ProgressDatabase db;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile List<Mission> catalog;

    public interface MissionsCallback { void onResult(List<Mission> missions); }
    public interface CompletionsCallback { void onResult(List<MissionCompletion> completions); }
    public interface CompletionCallback { void onComplete(boolean success); }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE mission_completions ADD COLUMN completionMethod TEXT NOT NULL DEFAULT 'LEGACY'");
        }
    };

    private MissionRepository(Context context) {
        appContext = context.getApplicationContext();
        db = Room.databaseBuilder(appContext, ProgressDatabase.class,
                "firststep-progress.db").addMigrations(MIGRATION_1_2).build();
        dao = db.completionDao();
        preferences = appContext.getSharedPreferences("firststep-settings", Context.MODE_PRIVATE);
    }

    public static MissionRepository getInstance(Context context) {
        if (instance == null) synchronized (MissionRepository.class) {
            if (instance == null) instance = new MissionRepository(context);
        }
        return instance;
    }

    public List<Mission> getMissions() {
        if (catalog == null) synchronized (this) {
            if (catalog == null) catalog = Collections.unmodifiableList(loadCatalog());
        }
        return catalog;
    }

    /** Mode is accepted for a stable UI contract; catalog content is shared by both modes. */
    public void getMissions(AppMode mode, MissionsCallback callback) {
        List<Mission> result = getMissions();
        mainHandler.post(() -> callback.onResult(result));
    }

    public Mission getMission(String id) {
        for (Mission mission : getMissions()) if (mission.id.equals(id)) return mission;
        return null;
    }

    public void getCompletions(AppMode mode, CompletionsCallback callback) {
        executor.execute(() -> {
            List<MissionCompletion> result = new ArrayList<>();
            for (CompletionRow row : dao.byMode(mode.name())) result.add(row.toModel());
            mainHandler.post(() -> callback.onResult(Collections.unmodifiableList(result)));
        });
    }

    /** INSERT OR IGNORE plus the database unique key makes repeat awards idempotent. */
    public void completeMission(AppMode mode, String missionId, String photoUri,
                                String discoveryMethod, CompletionCallback callback) {
        completeMission(mode, missionId, photoUri, discoveryMethod, "LEGACY", callback);
    }

    /** Store AR/card discovery and the actual game input or fallback as separate audit fields. */
    public void completeMission(AppMode mode, String missionId, String photoUri,
                                String discoveryMethod, String completionMethod,
                                CompletionCallback callback) {
        executor.execute(() -> {
            boolean success = false;
            try {
                Mission mission = getMission(missionId);
                if (mission != null && mode != null) {
                    success = db.runInTransaction(() -> {
                        Set<String> completed = new HashSet<>(dao.idsByMode(mode.name()));
                        if (!CompletionPolicy.canComplete(mode, mission, completed,
                                discoveryMethod, completionMethod, getMissions())) return false;
                        dao.completeIfAbsent(new CompletionRow(mode.name(), missionId,
                                System.currentTimeMillis(), photoUri, discoveryMethod, completionMethod));
                        return true;
                    });
                }
            } catch (RuntimeException ignored) { }
            boolean result = success;
            mainHandler.post(() -> { if (callback != null) callback.onComplete(result); });
        });
    }

    public void resetProgress(AppMode mode, CompletionCallback callback) {
        executor.execute(() -> {
            boolean success = false;
            try {
                List<String> candidates = db.runInTransaction(() -> {
                    List<String> paths = dao.photoPathsByMode(mode.name());
                    dao.deleteMode(mode.name());
                    Set<String> stillReferenced = new HashSet<>(dao.allPhotoPaths());
                    paths.removeIf(path -> path == null || stillReferenced.contains(path));
                    return paths;
                });
                success = true;
                for (String path : candidates) {
                    File photo = appPrivateFile(path);
                    if (photo != null && photo.exists() && !photo.delete()) success = false;
                }
            } catch (RuntimeException ignored) { }
            boolean result = success;
            mainHandler.post(() -> { if (callback != null) callback.onComplete(result); });
        });
    }

    /** Only delete ordinary file URIs/paths that resolve beneath this app's files directory. */
    private File appPrivateFile(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(value);
            if (uri.getScheme() != null && !"file".equalsIgnoreCase(uri.getScheme())) return null;
            String path = uri.getScheme() == null ? value : uri.getPath();
            if (path == null) return null;
            File root = appContext.getFilesDir().getCanonicalFile();
            File file = new File(path).getCanonicalFile();
            String rootPath = root.getPath() + File.separator;
            return file.getPath().startsWith(rootPath) ? file : null;
        } catch (Exception ignored) { return null; }
    }

    public String getNickname() { return preferences.getString("nickname", ""); }
    public void setNickname(String nickname) {
        preferences.edit().putString("nickname", nickname == null ? "" : nickname.trim()).apply();
    }
    public AppMode getMode() {
        try { return AppMode.valueOf(preferences.getString("mode", AppMode.DEMO.name())); }
        catch (IllegalArgumentException e) { return AppMode.DEMO; }
    }
    public void setMode(AppMode mode) { preferences.edit().putString("mode", mode.name()).apply(); }

    private List<Mission> loadCatalog() {
        try (InputStream input = appContext.getAssets().open("missions.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            JSONArray array = new JSONArray(output.toString(StandardCharsets.UTF_8.name()));
            List<Mission> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Mission(item.getString("id"), item.getString("title"),
                        item.getString("place"), item.getString("type"), item.getString("story"),
                        item.getString("question"), item.getString("answer"),
                        strings(item.optJSONArray("acceptedAnswers")), strings(item.optJSONArray("options")),
                        item.getString("explanation"), item.getInt("order"),
                        item.optBoolean("coordinateVerified", false), nullableDouble(item, "latitude"),
                        nullableDouble(item, "longitude")));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Could not load bundled mission catalog", e);
        }
    }

    private static Double nullableDouble(JSONObject object, String key) {
        return object.isNull(key) || !object.has(key) ? null : object.optDouble(key);
    }
    private static List<String> strings(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array != null) for (int i = 0; i < array.length(); i++) result.add(array.optString(i));
        return result;
    }

    @Entity(tableName = "mission_completions", primaryKeys = {"mode", "missionId"})
    public static class CompletionRow {
        @NonNull
        public String mode;
        @NonNull
        public String missionId;
        public long savedAt;
        public String photoUri;
        public String discoveryMethod;
        @NonNull
        @ColumnInfo(defaultValue = "'LEGACY'")
        public String completionMethod = "LEGACY";
        public CompletionRow(String mode, String missionId, long savedAt, String photoUri,
                             String discoveryMethod, String completionMethod) {
            this.mode = mode; this.missionId = missionId; this.savedAt = savedAt;
            this.photoUri = photoUri; this.discoveryMethod = discoveryMethod;
            this.completionMethod = completionMethod;
        }
        MissionCompletion toModel() {
            return new MissionCompletion(AppMode.valueOf(mode), missionId, savedAt, photoUri,
                    discoveryMethod, completionMethod);
        }
    }

    @Dao public interface CompletionDao {
        @Insert(onConflict = OnConflictStrategy.IGNORE) long completeIfAbsent(CompletionRow row);
        @Query("SELECT * FROM mission_completions WHERE mode = :mode ORDER BY savedAt, missionId")
        List<CompletionRow> byMode(String mode);
        @Query("SELECT missionId FROM mission_completions WHERE mode = :mode")
        List<String> idsByMode(String mode);
        @Query("SELECT photoUri FROM mission_completions WHERE mode = :mode AND photoUri IS NOT NULL")
        List<String> photoPathsByMode(String mode);
        @Query("SELECT photoUri FROM mission_completions WHERE photoUri IS NOT NULL")
        List<String> allPhotoPaths();
        @Query("DELETE FROM mission_completions WHERE mode = :mode") void deleteMode(String mode);
    }

    @Database(entities = {CompletionRow.class}, version = 2, exportSchema = false)
    public abstract static class ProgressDatabase extends RoomDatabase {
        abstract CompletionDao completionDao();
    }
}
