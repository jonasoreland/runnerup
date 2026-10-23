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
  public void pausedActivityWithPersistedSummaryRemainsRecoverable() {
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

  private static SQLiteDatabase createActivityDatabase() {
    SQLiteDatabase database = SQLiteDatabase.create(null);
    database.execSQL(
        "CREATE TABLE "
            + Constants.DB.ACTIVITY.TABLE
            + " (_id INTEGER PRIMARY KEY, "
            + Constants.DB.ACTIVITY.TIME
            + " INTEGER, deleted INTEGER NOT NULL DEFAULT 0)");
    return database;
  }
}
