package com.example.firststep.game.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Dedicated durable outbox. Call read and update on a worker thread. */
public final class SQLiteSyncStore implements SyncStore, AutoCloseable {
    private static final String DATABASE_NAME = "firststep-account-progress-sync.db";
    private static final int PAYLOAD_VERSION = 1;
    private static final int MAX_PAYLOAD_BYTES = 4096;
    private static final String WHERE = "project_id=? AND uid=? AND dataset_id=? AND mode=? AND building_id=?";
    private final DatasetRegistry registry;
    private final Helper helper;

    public SQLiteSyncStore(Context context, DatasetRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.helper = new Helper(Objects.requireNonNull(context, "context").getApplicationContext());
    }

    @Override public OutboxState read(SyncScope scope) {
        registry.requireEligible(scope);
        return readRow(helper.getReadableDatabase(), scope);
    }

    @Override public OutboxState update(SyncScope scope, UnaryOperator<OutboxState> change) {
        registry.requireEligible(scope);
        Objects.requireNonNull(change, "change");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            OutboxState current = readRow(db, scope);
            OutboxState next = Objects.requireNonNull(change.apply(current), "updated state");
            if (next == current) {
                db.setTransactionSuccessful();
                return current;
            }
            byte[] payload = encode(next);
            if (payload.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Sync outbox payload too large");
            }
            ContentValues values = new ContentValues();
            values.put("project_id", scope.projectId);
            values.put("uid", scope.uid);
            values.put("dataset_id", scope.datasetId);
            values.put("mode", scope.mode.name());
            values.put("building_id", scope.buildingId);
            values.put("payload", payload);
            if (db.insertWithOnConflict("outbox", null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1) {
                throw new IllegalStateException("Cannot persist sync outbox");
            }
            db.setTransactionSuccessful();
            return next;
        } finally {
            db.endTransaction();
        }
    }

    private static OutboxState readRow(SQLiteDatabase db, SyncScope scope) {
        String[] key = {scope.projectId, scope.uid, scope.datasetId, scope.mode.name(), scope.buildingId};
        try (Cursor cursor = db.query("outbox", new String[]{"payload"}, WHERE, key,
                null, null, null)) {
            if (!cursor.moveToFirst()) return OutboxState.empty();
            if (cursor.getType(0) != Cursor.FIELD_TYPE_BLOB) {
                throw new IllegalStateException("Corrupt sync outbox payload type");
            }
            OutboxState result = decode(cursor.getBlob(0));
            if (cursor.moveToNext()) throw new IllegalStateException("Duplicate sync outbox row");
            return result;
        }
    }

    static byte[] encode(OutboxState state) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeInt(PAYLOAD_VERSION);
            out.writeLong(state.revision);
            out.writeUTF(Objects.requireNonNull(state.status, "status").name());
            writeMilestone(out, state.confirmed);
            writeMilestone(out, state.pending);
            out.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot encode sync outbox", e);
        }
    }

    private static void writeMilestone(DataOutputStream out, Milestone milestone) throws IOException {
        out.writeBoolean(milestone != null);
        if (milestone == null) return;
        out.writeLong(milestone.introDoneAtMillis);
        out.writeBoolean(milestone.completion != null);
        if (milestone.completion != null) {
            out.writeUTF(Objects.requireNonNull(milestone.completion.attemptId, "attemptId"));
            out.writeUTF(Objects.requireNonNull(milestone.completion.contentVersion, "contentVersion"));
            out.writeInt(milestone.completion.correct);
            out.writeLong(milestone.completion.completedAtMillis);
        }
    }

    static OutboxState decode(byte[] payload) {
        try {
            if (payload == null) throw new IOException("Missing payload");
            if (payload.length > MAX_PAYLOAD_BYTES) throw new IOException("Payload too large");
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            if (in.readInt() != PAYLOAD_VERSION) throw new IOException("Unknown payload version");
            long revision = in.readLong();
            OutboxState.Status status = OutboxState.Status.valueOf(in.readUTF());
            Milestone confirmed = readMilestone(in);
            Milestone pending = readMilestone(in);
            if (in.available() != 0) throw new IOException("Trailing payload data");
            return new OutboxState(revision, confirmed, pending, status);
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("Corrupt sync outbox payload", e);
        }
    }

    private static boolean readFlag(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value > 1) throw new IOException("Invalid presence flag");
        return value == 1;
    }

    private static Milestone readMilestone(DataInputStream in) throws IOException {
        if (!readFlag(in)) return null;
        long intro = in.readLong();
        Milestone.Completion completion = null;
        if (readFlag(in)) {
            completion = new Milestone.Completion(in.readUTF(), in.readUTF(), in.readInt(), in.readLong());
        }
        return new Milestone(intro, completion);
    }

    @Override public void close() { helper.close(); }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DATABASE_NAME, null, 1, db -> {
                throw new IllegalStateException("Sync database is corrupt: " + db.getPath());
            });
        }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE outbox (project_id TEXT NOT NULL, uid TEXT NOT NULL, "
                    + "dataset_id TEXT NOT NULL, mode TEXT NOT NULL, building_id TEXT NOT NULL, "
                    + "payload BLOB NOT NULL, PRIMARY KEY (project_id, uid, dataset_id, mode, building_id))");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new IllegalStateException("Unsupported sync database upgrade");
        }
        @Override public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new IllegalStateException("Unsupported sync database downgrade");
        }
    }
}
