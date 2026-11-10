package com.example.firststep.game.composition.authored;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.firststep.game.data.local.GameJson;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.SyncScope;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** Dedicated authored gameplay database. Call on a worker thread. */
public final class SQLiteAuthoredProgressStore implements AuthoredProgressStore, AutoCloseable {
  private static final String WHERE =
      "project_id=? AND uid=? AND dataset_id=? AND mode=? AND building_id=?";
  private final DatasetRegistry registry;
  private final Helper helper;

  public SQLiteAuthoredProgressStore(Context context, DatasetRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.helper = new Helper(Objects.requireNonNull(context, "context").getApplicationContext());
  }

  @Override public Entry read(SyncScope scope) {
    registry.requireEligible(scope);
    return readRow(helper.getReadableDatabase(), scope);
  }

  @Override public Entry update(SyncScope scope, UnaryOperator<Entry> transition) {
    registry.requireEligible(scope);
    Objects.requireNonNull(transition, "transition");
    SQLiteDatabase db = helper.getWritableDatabase();
    db.beginTransaction();
    try {
      Entry previous = readRow(db, scope);
      Entry next = Objects.requireNonNull(transition.apply(previous), "updated entry");
      validate(scope, next);
      if (next != previous) {
        ContentValues values = new ContentValues();
        values.put("project_id", scope.projectId);
        values.put("uid", scope.uid);
        values.put("dataset_id", scope.datasetId);
        values.put("mode", scope.mode.name());
        values.put("building_id", scope.buildingId);
        values.put("payload", GameJson.encode(next.progress));
        if (next.firstPassCommittedAtMillis == null) values.putNull("first_pass_at");
        else values.put("first_pass_at", next.firstPassCommittedAtMillis);
        if (db.insertWithOnConflict("progress", null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1) {
          throw new IllegalStateException("Cannot persist authored progress");
        }
      }
      db.setTransactionSuccessful();
      return next;
    } finally {
      db.endTransaction();
    }
  }

  private static Entry readRow(SQLiteDatabase db, SyncScope scope) {
    String[] key = {scope.projectId, scope.uid, scope.datasetId, scope.mode.name(), scope.buildingId};
    try (Cursor cursor = db.query("progress", new String[]{"payload", "first_pass_at"},
        WHERE, key, null, null, null)) {
      if (!cursor.moveToFirst()) return null;
      if (cursor.getType(0) != Cursor.FIELD_TYPE_STRING
          || (cursor.getType(1) != Cursor.FIELD_TYPE_NULL
              && cursor.getType(1) != Cursor.FIELD_TYPE_INTEGER)) {
        throw new IllegalStateException("Corrupt authored progress column type");
      }
      try {
        Progress progress = GameJson.decode(cursor.getString(0));
        Long time = cursor.isNull(1) ? null : cursor.getLong(1);
        Entry result = new Entry(progress, time);
        validate(scope, result);
        if (cursor.moveToNext()) throw new IllegalStateException("Duplicate authored progress row");
        return result;
      } catch (IllegalArgumentException | NullPointerException e) {
        throw new IllegalStateException("Corrupt authored progress payload", e);
      }
    }
  }

  private static void validate(SyncScope scope, Entry entry) {
    Progress progress = Objects.requireNonNull(entry.progress, "progress");
    if (progress.mode != scope.mode || !progress.buildingId.equals(scope.buildingId)
        || progress.stamp != (entry.firstPassCommittedAtMillis != null)) {
      throw new IllegalArgumentException("Authored progress does not match scope or pass timestamp");
    }
  }

  @Override public void close() { helper.close(); }

  private static final class Helper extends SQLiteOpenHelper {
    Helper(Context context) {
      super(context, "firststep-authored-gameplay.db", null, 1, db -> {
        throw new IllegalStateException("Authored progress database is corrupt");
      });
    }
    @Override public void onCreate(SQLiteDatabase db) {
      db.execSQL("CREATE TABLE progress (project_id TEXT NOT NULL, uid TEXT NOT NULL, "
          + "dataset_id TEXT NOT NULL, mode TEXT NOT NULL, building_id TEXT NOT NULL, "
          + "payload TEXT NOT NULL, first_pass_at INTEGER, "
          + "PRIMARY KEY (project_id, uid, dataset_id, mode, building_id))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      throw new IllegalStateException("Unsupported authored progress database upgrade");
    }
    @Override public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      throw new IllegalStateException("Unsupported authored progress database downgrade");
    }
  }
}
