package com.example.firststep.game.data.local;

import androidx.annotation.NonNull;
import androidx.room.*;
import java.util.List;

/**
 * Device-local NORMAL/DEMO progress, isolated from every legacy mission database. No account
 * namespace, guest import, or destructive migration is configured.
 */
@Database(
    entities = {GameProgressDatabase.Row.class},
    version = 1,
    exportSchema = false)
public abstract class GameProgressDatabase extends RoomDatabase {
  public static final String FILE_NAME = "firststep-game-progress.db";

  public abstract ProgressDao progressDao();

  @Entity(
      tableName = "game_progress",
      primaryKeys = {"mode", "buildingId"})
  public static class Row {
    @NonNull public String mode;
    @NonNull public String buildingId;
    @NonNull public String payload;

    public Row(@NonNull String mode, @NonNull String buildingId, @NonNull String payload) {
      this.mode = mode;
      this.buildingId = buildingId;
      this.payload = payload;
    }
  }

  @Dao
  public interface ProgressDao {
    @Query("SELECT * FROM game_progress WHERE mode = :mode AND buildingId = :buildingId")
    Row find(String mode, String buildingId);

    @Query("SELECT * FROM game_progress WHERE mode = :mode")
    List<Row> all(String mode);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(Row row);
  }
}
