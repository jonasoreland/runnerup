package org.runnerup.tracker;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Durable terminal marker for saved activities, including GPS-less workouts. */
public final class ActivityFinalizationStore {
  public static final String TABLE = "relven_activity_finalization";
  private static final String ACTIVITY_ID = "activity_id";
  private static final String FINALIZED_AT = "finalized_at_millis";
  private static final String EXPORT_REQUESTED = "health_connect_export_requested";
  private static final String EXPORT_QUEUED = "health_connect_export_queued";

  private ActivityFinalizationStore() {}

  public static void ensureSchema(SQLiteDatabase database) {
    database.execSQL(
        "CREATE TABLE IF NOT EXISTS "
            + TABLE
            + " ("
            + ACTIVITY_ID
            + " INTEGER PRIMARY KEY, "
            + FINALIZED_AT
            + " INTEGER NOT NULL, "
            + EXPORT_REQUESTED
            + " INTEGER NOT NULL DEFAULT 0, "
            + EXPORT_QUEUED
            + " INTEGER NOT NULL DEFAULT 0)");
  }

  /** Inserts once so a retry cannot change the original export decision or completion time. */
  public static boolean markFinalized(
      SQLiteDatabase database, long activityId, long finalizedAtMillis, boolean exportRequested) {
    if (database == null || activityId <= 0 || finalizedAtMillis <= 0) return false;
    ensureSchema(database);
    ContentValues values = new ContentValues();
    values.put(ACTIVITY_ID, activityId);
    values.put(FINALIZED_AT, finalizedAtMillis);
    values.put(EXPORT_REQUESTED, exportRequested ? 1 : 0);
    values.put(EXPORT_QUEUED, 0);
    database.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    return isFinalized(database, activityId);
  }

  public static boolean isFinalized(SQLiteDatabase database, long activityId) {
    if (database == null || activityId <= 0) return false;
    ensureSchema(database);
    try (Cursor cursor =
        database.query(
            TABLE,
            new String[] {ACTIVITY_ID},
            ACTIVITY_ID + " = ?",
            new String[] {Long.toString(activityId)},
            null,
            null,
            null,
            "1")) {
      return cursor.moveToFirst();
    }
  }

  public static boolean isHealthConnectExportRequested(SQLiteDatabase database, long activityId) {
    if (database == null || activityId <= 0) return false;
    ensureSchema(database);
    try (Cursor cursor =
        database.query(
            TABLE,
            new String[] {EXPORT_REQUESTED},
            ACTIVITY_ID + " = ?",
            new String[] {Long.toString(activityId)},
            null,
            null,
            null,
            "1")) {
      return cursor.moveToFirst() && cursor.getInt(0) == 1;
    }
  }

  public static boolean markHealthConnectExportQueued(SQLiteDatabase database, long activityId) {
    if (database == null || activityId <= 0) return false;
    ensureSchema(database);
    ContentValues values = new ContentValues();
    values.put(EXPORT_QUEUED, 1);
    if (database.update(
            TABLE,
            values,
            ACTIVITY_ID + " = ? AND " + EXPORT_REQUESTED + " = 1",
            new String[] {Long.toString(activityId)})
        > 0) return true;
    try (Cursor cursor =
        database.query(
            TABLE,
            new String[] {EXPORT_QUEUED},
            ACTIVITY_ID + " = ? AND " + EXPORT_REQUESTED + " = 1",
            new String[] {Long.toString(activityId)},
            null,
            null,
            null,
            "1")) {
      return cursor.moveToFirst() && cursor.getInt(0) == 1;
    }
  }

  /** Export requests left unqueued by process death or a transient outbox failure. */
  public static List<Long> pendingHealthConnectExports(SQLiteDatabase database) {
    ensureSchema(database);
    List<Long> result = new ArrayList<>();
    try (Cursor cursor =
        database.query(
            TABLE,
            new String[] {ACTIVITY_ID},
            EXPORT_REQUESTED + " = 1 AND " + EXPORT_QUEUED + " = 0",
            null,
            null,
            null,
            ACTIVITY_ID + " ASC")) {
      while (cursor.moveToNext()) result.add(cursor.getLong(0));
    }
    return result;
  }
}
