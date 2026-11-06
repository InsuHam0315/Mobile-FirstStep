package com.example.firststep.game.data.local;

import android.content.Context;
import androidx.room.Room;
import com.example.firststep.game.domain.*;
import java.util.*;

/**
 * Blocking adapter: invoke every operation on a worker thread. Own and close the database at
 * lifecycle end.
 */
public final class RoomProgressRepository implements ProgressRepository, AutoCloseable {
  private final GameProgressDatabase db;

  public RoomProgressRepository(GameProgressDatabase db) {
    this.db = Objects.requireNonNull(db);
  }

  public static RoomProgressRepository open(Context context) {
    return new RoomProgressRepository(
        Room.databaseBuilder(
                context.getApplicationContext(),
                GameProgressDatabase.class,
                GameProgressDatabase.FILE_NAME)
            .build());
  }

  private Progress from(GameProgressDatabase.Row row, Progress.Mode mode, String id) {
    if (row == null) return Progress.empty(mode, id);
    Progress p = GameJson.decode(row.payload);
    if (p.mode != mode || !p.buildingId.equals(id))
      throw new IllegalStateException("row key mismatch");
    return p;
  }

  @Override
  public Progress read(Progress.Mode mode, String id) {
    Objects.requireNonNull(mode);
    GameContent.id(id);
    return from(db.progressDao().find(mode.name(), id), mode, id);
  }

  @Override
  public List<Progress> readAll(Progress.Mode mode) {
    Objects.requireNonNull(mode);
    List<Progress> out = new ArrayList<>();
    for (GameProgressDatabase.Row row : db.progressDao().all(mode.name()))
      out.add(from(row, mode, row.buildingId));
    return Collections.unmodifiableList(out);
  }

  @Override
  public Progress atomicUpdate(Progress.Mode mode, String id, Transition transition) {
    Objects.requireNonNull(mode);
    GameContent.id(id);
    Objects.requireNonNull(transition);
    return db.runInTransaction(
        () -> {
          Progress next = Objects.requireNonNull(transition.apply(read(mode, id)));
          if (next.mode != mode || !next.buildingId.equals(id))
            throw new IllegalArgumentException("transition key mismatch");
          db.progressDao()
              .save(new GameProgressDatabase.Row(mode.name(), id, GameJson.encode(next)));
          return next;
        });
  }

  @Override
  public void close() {
    db.close();
  }
}
