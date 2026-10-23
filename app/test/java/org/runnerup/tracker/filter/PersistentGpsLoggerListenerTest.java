package org.runnerup.tracker.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import androidx.core.location.LocationCompat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.runnerup.common.util.Constants.DB;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PersistentGpsLoggerListenerTest {
  private SQLiteDatabase database;
  private PersistentGpsLoggerListener logger;

  @Before
  public void setUp() {
    database = SQLiteDatabase.create(null);
    database.execSQL(
        "CREATE TABLE location (activity_id INTEGER, time INTEGER, latitude REAL, longitude REAL, altitude REAL, accurancy REAL, gps_altitude REAL, speed REAL, bearing REAL, satellites INTEGER, pressure REAL, elapsed REAL, distance REAL, hr INTEGER, cadence REAL, temperature REAL)");
    ContentValues key = new ContentValues();
    key.put(DB.LOCATION.ACTIVITY, 1L);
    logger = new PersistentGpsLoggerListener(database, DB.LOCATION.TABLE, key, true);
  }

  @After
  public void tearDown() {
    database.close();
  }

  @Test
  public void unreliableGpsAltitudeIsNotPersistedToTheRawExportField() {
    Location location = locationWithVerticalAccuracy(7.0f);

    logger.onLocationChanged(location, null, null, null, null, null, null, null);

    try (var cursor =
        database.query(
            DB.LOCATION.TABLE,
            new String[] {DB.LOCATION.GPS_ALTITUDE},
            null,
            null,
            null,
            null,
            null)) {
      assertTrue(cursor.moveToFirst());
      assertTrue(cursor.isNull(0));
    }
  }

  @Test
  public void reliableGpsAltitudeRemainsAvailableToAccuracyExport() {
    Location location = locationWithVerticalAccuracy(3.0f);

    logger.onLocationChanged(location, 42.0, null, null, null, null, null, null);

    try (var cursor =
        database.query(
            DB.LOCATION.TABLE,
            new String[] {DB.LOCATION.ALTITUDE, DB.LOCATION.GPS_ALTITUDE},
            null,
            null,
            null,
            null,
            null)) {
      assertTrue(cursor.moveToFirst());
      assertEquals(42.0, cursor.getDouble(0), 0.0);
      assertEquals(42.0, cursor.getDouble(1), 0.0);
    }
  }

  private static Location locationWithVerticalAccuracy(float verticalAccuracy) {
    Location location = new Location("gps");
    location.setTime(1_729_000_000_000L);
    location.setLatitude(59.3);
    location.setLongitude(18.1);
    location.setAltitude(42.0);
    location.setAccuracy(4.0f);
    LocationCompat.setVerticalAccuracyMeters(location, verticalAccuracy);
    return location;
  }
}
