package org.runnerup.tracker;

import static org.junit.Assert.assertEquals;

import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.runnerup.common.util.Constants;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TrackerRecoveryLapTest {
  @Test
  public void checkpointLapWinsWhenLatestPersistedLocationIsFromPreviousLap() {
    SQLiteDatabase database = SQLiteDatabase.create(null);
    try {
      database.execSQL(
          "CREATE TABLE "
              + Constants.DB.LAP.TABLE
              + " (_id INTEGER PRIMARY KEY, "
              + Constants.DB.LAP.ACTIVITY
              + " INTEGER NOT NULL, "
              + Constants.DB.LAP.LAP
              + " INTEGER NOT NULL)");
      database.execSQL(
          "INSERT INTO "
              + Constants.DB.LAP.TABLE
              + " (_id, "
              + Constants.DB.LAP.ACTIVITY
              + ", "
              + Constants.DB.LAP.LAP
              + ") VALUES (12, 81, 4)");

      assertEquals(4, Tracker.resolveRecoveryLapNumber(database, 81, 12, 3));
    } finally {
      database.close();
    }
  }

  @Test
  public void invalidOrForeignCheckpointLapFallsBackToLatestPersistedLocation() {
    SQLiteDatabase database = SQLiteDatabase.create(null);
    try {
      database.execSQL(
          "CREATE TABLE "
              + Constants.DB.LAP.TABLE
              + " (_id INTEGER PRIMARY KEY, "
              + Constants.DB.LAP.ACTIVITY
              + " INTEGER NOT NULL, "
              + Constants.DB.LAP.LAP
              + " INTEGER NOT NULL)");
      database.execSQL(
          "INSERT INTO "
              + Constants.DB.LAP.TABLE
              + " (_id, "
              + Constants.DB.LAP.ACTIVITY
              + ", "
              + Constants.DB.LAP.LAP
              + ") VALUES (12, 81, 4)");

      assertEquals(3, Tracker.resolveRecoveryLapNumber(database, 90, 12, 3));
      assertEquals(3, Tracker.resolveRecoveryLapNumber(database, 81, 0, 3));
    } finally {
      database.close();
    }
  }
}
