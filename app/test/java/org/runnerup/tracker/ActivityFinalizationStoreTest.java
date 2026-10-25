package org.runnerup.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.preference.PreferenceManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ActivityFinalizationStoreTest {
  @Before
  public void resetHealthConnectPreference() {
    PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
        .edit()
        .putBoolean("relven_health_connect_export_enabled", false)
        .commit();
  }

  @Test
  public void trackerCanFinalizeWorkoutWithoutAnyLocation() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    SQLiteDatabase database = DBHelper.getWritableDatabase(context);
    ContentValues values = new ContentValues();
    values.put(Constants.DB.ACTIVITY.SPORT, Constants.DB.ACTIVITY.SPORT_GYM);
    long activityId = database.insert(Constants.DB.ACTIVITY.TABLE, null, values);

    ServiceController<Tracker> controller = Robolectric.buildService(Tracker.class).create();
    Tracker tracker = controller.get();
    try {
      setLong(tracker, "mActivityId", activityId);
      setLong(tracker, "mElapsedTimeNanos", 30_000_000_000L);
      setState(tracker, TrackerState.STOPPED);
      Field gateField = Tracker.class.getDeclaredField("activityCompletionGate");
      gateField.setAccessible(true);
      ((ActivityCompletionGate) gateField.get(tracker)).beginActivity();

      Method persist =
          Tracker.class.getDeclaredMethod("persistTerminalActivity", boolean.class, Double.class);
      persist.setAccessible(true);
      assertTrue((Boolean) persist.invoke(tracker, true, null));

      assertTrue(ActivityFinalizationStore.isFinalized(database, activityId));
      try (android.database.Cursor cursor =
          database.query(
              Constants.DB.LOCATION.TABLE,
              new String[] {"_id"},
              Constants.DB.LOCATION.ACTIVITY + " = ?",
              new String[] {Long.toString(activityId)},
              null,
              null,
              null)) {
        assertEquals(0, cursor.getCount());
      }
    } finally {
      controller.destroy();
      database.delete(
          ActivityFinalizationStore.TABLE,
          "activity_id = ?",
          new String[] {Long.toString(activityId)});
      database.delete(
          Constants.DB.ACTIVITY.TABLE, "_id = ?", new String[] {Long.toString(activityId)});
    }
  }

  @Test
  public void gpslessFinalizationIsDurableAndExportRequestCanBeRetriedAfterRestart() {
    SQLiteDatabase database = SQLiteDatabase.create(null);
    try {
      ActivityFinalizationStore.ensureSchema(database);
      database.execSQL(
          "CREATE TABLE activity (_id INTEGER PRIMARY KEY, deleted INTEGER DEFAULT 0)");
      database.execSQL("INSERT INTO activity (_id, deleted) VALUES (41, 0)");

      assertTrue(ActivityFinalizationStore.markFinalized(database, 41, 10_000L, true));

      ActivityFinalizationStore.ensureSchema(database);
      assertTrue(ActivityFinalizationStore.isFinalized(database, 41));
      assertTrue(ActivityFinalizationStore.isHealthConnectExportRequested(database, 41));
      assertEquals(
          Arrays.asList(41L), ActivityFinalizationStore.pendingHealthConnectExports(database));
      assertTrue(ActivityFinalizationStore.markHealthConnectExportQueued(database, 41));
      assertTrue(ActivityFinalizationStore.pendingHealthConnectExports(database).isEmpty());
    } finally {
      database.close();
    }
  }

  @Test
  public void repeatedFinalizationDoesNotChangeOriginalExportDecision() {
    SQLiteDatabase database = SQLiteDatabase.create(null);
    try {
      ActivityFinalizationStore.ensureSchema(database);

      assertTrue(ActivityFinalizationStore.markFinalized(database, 41, 10_000L, true));
      assertTrue(ActivityFinalizationStore.markFinalized(database, 41, 11_000L, false));
      assertTrue(ActivityFinalizationStore.isHealthConnectExportRequested(database, 41));
      assertFalse(ActivityFinalizationStore.markHealthConnectExportQueued(database, 42));
    } finally {
      database.close();
    }
  }

  private static void setLong(Tracker tracker, String name, long value) throws Exception {
    Field field = Tracker.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setLong(tracker, value);
  }

  @SuppressWarnings("unchecked")
  private static void setState(Tracker tracker, TrackerState newState) throws Exception {
    Field field = Tracker.class.getDeclaredField("state");
    field.setAccessible(true);
    ((org.runnerup.common.util.ValueModel<TrackerState>) field.get(tracker)).set(newState);
  }
}
