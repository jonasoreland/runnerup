package org.runnerup.tracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.runnerup.common.util.Constants;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TrackerRecoveryEligibilityTest {
  @Test
  public void nonDeletedActivityWithPersistedSummaryIsRecoverable() {
    SQLiteDatabase database = createActivityDatabase();
    try {
      database.execSQL(
          "INSERT INTO "
              + Constants.DB.ACTIVITY.TABLE
              + " (_id, "
              + Constants.DB.ACTIVITY.TIME
              + ", deleted) VALUES (81, 64, 0)");

      try (Cursor cursor =
          database.query(
              Constants.DB.ACTIVITY.TABLE,
              new String[] {Constants.DB.ACTIVITY.DELETED},
              "_id = ?",
              new String[] {"81"},
              null,
              null,
              null)) {
        assertTrue(Tracker.isRecoverableActivity(cursor));
      }
    } finally {
      database.close();
    }
  }

  @Test
  public void discardedActivityIsNotRecoverable() {
    SQLiteDatabase database = createActivityDatabase();
    try {
      database.execSQL(
          "INSERT INTO "
              + Constants.DB.ACTIVITY.TABLE
              + " (_id, "
              + Constants.DB.ACTIVITY.TIME
              + ", deleted) VALUES (81, 64, 1)");

      try (Cursor cursor =
          database.query(
              Constants.DB.ACTIVITY.TABLE,
              new String[] {Constants.DB.ACTIVITY.DELETED},
              "_id = ?",
              new String[] {"81"},
              null,
              null,
              null)) {
        assertFalse(Tracker.isRecoverableActivity(cursor));
      }
    } finally {
      database.close();
    }
  }

  @Test
  public void missingActivityIsNotRecoverable() {
    SQLiteDatabase database = createActivityDatabase();
    try {
      try (Cursor cursor = queryActivity(database, 81)) {
        assertFalse(Tracker.isRecoverableActivity(cursor));
      }
    } finally {
      database.close();
    }
  }

  @Test
  public void activityWithPersistedFinalizationMarkerIsNotRecoverable() {
    SQLiteDatabase database = createActivityDatabase();
    try {
      database.execSQL(
          "INSERT INTO "
              + Constants.DB.ACTIVITY.TABLE
              + " (_id, "
              + Constants.DB.ACTIVITY.TIME
              + ", deleted) VALUES (82, 64, 0)");
      ActivityFinalizationStore.markFinalized(database, 82, 10_000L, false);

      try (Cursor cursor = queryActivity(database, 82)) {
        assertFalse(
            Tracker.isRecoverableActivity(
                cursor, ActivityFinalizationStore.isFinalized(database, 82)));
      }
    } finally {
      database.close();
    }
  }

  private static Cursor queryActivity(SQLiteDatabase database, long activityId) {
    return database.query(
        Constants.DB.ACTIVITY.TABLE,
        new String[] {Constants.DB.ACTIVITY.DELETED},
        "_id = ?",
        new String[] {Long.toString(activityId)},
        null,
        null,
        null);
  }

  private static SQLiteDatabase createActivityDatabase() {
    SQLiteDatabase database = SQLiteDatabase.create(null);
    database.execSQL(
        "CREATE TABLE "
            + Constants.DB.ACTIVITY.TABLE
            + " (_id INTEGER PRIMARY KEY, "
            + Constants.DB.ACTIVITY.TIME
            + " INTEGER, "
            + Constants.DB.ACTIVITY.DELETED
            + " INTEGER NOT NULL DEFAULT 0)");
    database.execSQL(
        "CREATE TABLE "
            + Constants.DB.LOCATION.TABLE
            + " ("
            + Constants.DB.LOCATION.ACTIVITY
            + " INTEGER, "
            + Constants.DB.LOCATION.TYPE
            + " INTEGER)");
    ActivityFinalizationStore.ensureSchema(database);
    return database;
  }
}
