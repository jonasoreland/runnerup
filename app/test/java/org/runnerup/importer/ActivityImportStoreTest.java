package org.runnerup.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.runnerup.common.util.Constants;
import org.runnerup.workout.Sport;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ActivityImportStoreTest {
  private SQLiteDatabase database;

  @Before
  public void setUp() {
    database = SQLiteDatabase.create(null);
    database.execSQL(
        "CREATE TABLE activity (_id INTEGER PRIMARY KEY, name TEXT, comment TEXT, start_time INTEGER, type INTEGER, distance REAL, time INTEGER, max_hr INTEGER, avg_hr INTEGER, avg_cadence REAL, deleted INTEGER, nullcolumnhack TEXT)");
    database.execSQL(
        "CREATE TABLE lap (_id INTEGER PRIMARY KEY, activity_id INTEGER NOT NULL, lap INTEGER NOT NULL, type INTEGER NOT NULL, time INTEGER, distance REAL, planned_time INTEGER, planned_distance REAL, planned_pace REAL, avg_hr INTEGER, max_hr INTEGER, avg_cadence REAL)");
    database.execSQL(
        "CREATE TABLE location (_id INTEGER PRIMARY KEY, activity_id INTEGER NOT NULL, lap INTEGER NOT NULL, type INTEGER NOT NULL, time INTEGER NOT NULL, longitude REAL NOT NULL, latitude REAL NOT NULL, altitude REAL, hr INTEGER, cadence REAL, temperature REAL, pressure REAL, elapsed REAL, distance REAL, gps_altitude REAL, accurancy REAL, speed REAL, bearing REAL, satellites INTEGER)");
    database.execSQL(
        "CREATE TABLE relven_activity_import_v1 (source_sha256 TEXT PRIMARY KEY NOT NULL, activity_id INTEGER NOT NULL)");
  }

  @After
  public void tearDown() {
    database.close();
  }

  @Test
  public void importsSummaryAndRouteAndDeduplicatesTheSameSourceFile() {
    ImportedActivity activity = sampleActivity();

    ActivityImportStore.Result first =
        ActivityImportStore.importActivity(database, activity, "a".repeat(64));
    ActivityImportStore.Result second =
        ActivityImportStore.importActivity(database, activity, "a".repeat(64));

    assertEquals(ActivityImportStore.Result.IMPORTED, first);
    assertEquals(ActivityImportStore.Result.ALREADY_IMPORTED, second);
    assertEquals(1, count(Constants.DB.ACTIVITY.TABLE));
    assertEquals(1, count(Constants.DB.LAP.TABLE));
    assertEquals(3, count(Constants.DB.LOCATION.TABLE));
    assertEquals(1, count("relven_activity_import_v1"));
    try (Cursor cursor =
        database.query(
            Constants.DB.ACTIVITY.TABLE,
            new String[] {
              Constants.DB.ACTIVITY.START_TIME,
              Constants.DB.ACTIVITY.TIME,
              Constants.DB.ACTIVITY.DISTANCE,
              Constants.DB.ACTIVITY.AVG_HR,
              Constants.DB.ACTIVITY.MAX_HR
            },
            null,
            null,
            null,
            null,
            null)) {
      cursor.moveToFirst();
      assertEquals(1_700_000_000L, cursor.getLong(0));
      assertEquals(120L, cursor.getLong(1));
      assertEquals(240.0, cursor.getDouble(2), 0.01);
      assertEquals(142, cursor.getInt(3));
      assertEquals(174, cursor.getInt(4));
    }
    try (Cursor cursor =
        database.query(
            Constants.DB.LOCATION.TABLE,
            new String[] {
              Constants.DB.LOCATION.TYPE,
              Constants.DB.LOCATION.TIME,
              Constants.DB.LOCATION.HR,
              Constants.DB.LOCATION.CADENCE
            },
            null,
            null,
            null,
            null,
            Constants.DB.PRIMARY_KEY)) {
      assertEquals(3, cursor.getCount());
      cursor.moveToFirst();
      assertEquals(Constants.DB.LOCATION.TYPE_GPS, cursor.getInt(0));
      assertEquals(1_700_000_000_000L, cursor.getLong(1));
      assertEquals(130, cursor.getInt(2));
      cursor.moveToNext();
      assertEquals(Constants.DB.LOCATION.TYPE_GPS, cursor.getInt(0));
      assertEquals(1_700_000_060_000L, cursor.getLong(1));
      assertTrue(cursor.isNull(2));
      assertEquals(84.0, cursor.getDouble(3), 0.01);
      cursor.moveToNext();
      assertEquals(Constants.DB.LOCATION.TYPE_GPS, cursor.getInt(0));
      assertEquals(1_700_000_120_000L, cursor.getLong(1));
      assertEquals(154, cursor.getInt(2));
    }
  }

  @Test
  public void rollsBackActivityRouteAndLedgerTogetherWhenPointInsertFails() {
    database.execSQL(
        "CREATE TRIGGER fail_imported_route BEFORE INSERT ON location "
            + "BEGIN SELECT RAISE(ABORT, 'route insert rejected'); END");

    try {
      ActivityImportStore.importActivity(database, sampleActivity(), "b".repeat(64));
    } catch (RuntimeException expected) {
      assertEquals(0, count(Constants.DB.ACTIVITY.TABLE));
      assertEquals(0, count(Constants.DB.LAP.TABLE));
      assertEquals(0, count(Constants.DB.LOCATION.TABLE));
      assertEquals(0, count("relven_activity_import_v1"));
      return;
    }
    throw new AssertionError("Expected route insertion to fail");
  }

  private ImportedActivity sampleActivity() {
    return new ImportedActivity(
        ImportedActivity.Format.GPX,
        Sport.RUNNING,
        1_700_000_000L,
        120L,
        240.0,
        142,
        174,
        java.util.List.of(
            new ActivityTrackPoint(1_700_000_000L, 59.0, 18.0, 10.0, 0.0, 130, null),
            new ActivityTrackPoint(1_700_000_060L, 59.001, 18.001, null, 120.0, null, 84),
            new ActivityTrackPoint(1_700_000_120L, 59.002, 18.002, 12.0, 240.0, 154, null)));
  }

  private int count(String table) {
    try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
      cursor.moveToFirst();
      return cursor.getInt(0);
    }
  }
}
