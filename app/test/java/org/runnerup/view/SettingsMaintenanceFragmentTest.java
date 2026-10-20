package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.relven.migration.LegacyRelvenMigration;
import org.runnerup.relven.migration.LegacyRelvenVault;
import org.runnerup.workout.WorkoutSerializer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SettingsMaintenanceFragmentTest {
  private FragmentActivity activity;
  private SettingsMaintenanceFragment fragment;

  @Before
  public void setUp() throws Exception {
    activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    var snapshot = LegacyRelvenVault.snapshotFile(activity);
    Files.createDirectories(snapshot.getParentFile().toPath());
    Files.writeString(snapshot.toPath(), archiveWithWorkout(), StandardCharsets.UTF_8);
    fragment = new SettingsMaintenanceFragment();
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .replace(android.R.id.content, fragment)
        .commitNow();
  }

  @After
  public void tearDown() {
    var snapshot = LegacyRelvenVault.snapshotFile(activity);
    if (snapshot.exists()) snapshot.delete();
  }

  @Test
  public void legacyReviewCancelLeavesWorkoutLibraryUnchanged() throws Exception {
    Preference review = preference(R.string.pref_import_legacy_relven_data);
    assertNotNull(review);
    assertTrue(review.isVisible());
    assertEquals(1, LegacyRelvenMigration.preview(activity).getSupportedWorkoutCount());
    fragment.showLegacyReview(LegacyRelvenMigration.preview(activity));
    assertTrue(ShadowDialog.getLatestDialog().isShowing());
    AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    TextView message = dialog.findViewById(android.R.id.message);
    assertNotNull(message);
    assertTrue(message.getText().toString().contains("workout"));
    assertTrue(message.getText().toString().contains("not activated"));
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    assertEquals(
        0,
        activity
            .getDir(WorkoutSerializer.WORKOUTS_DIR, android.content.Context.MODE_PRIVATE)
            .listFiles()
            .length);
  }

  @Test
  public void reviewKeepsLegacyArchiveExportAvailable() {
    Preference export = preference(R.string.pref_export_legacy_relven_data);
    assertNotNull(export);
    assertTrue(export.isVisible());
  }

  @Test
  public void legacyReviewConfirmImportsAndShowsCounts() throws Exception {
    var database = DBHelper.getWritableDatabase(activity);
    database.delete(Constants.DB.ACTIVITY.TABLE, null, null);
    database.delete(Constants.DB.LOCATION.TABLE, null, null);
    database.execSQL(
        "CREATE TABLE IF NOT EXISTS legacy_activity_import_v1 ("
            + "source_key TEXT PRIMARY KEY NOT NULL, activity_id INTEGER NOT NULL, "
            + "server_origin TEXT, owner_id TEXT, server_id TEXT)");
    database.delete("legacy_activity_import_v1", null, null);
    fragment.showLegacyReview(LegacyRelvenMigration.preview(activity));
    AlertDialog review = (AlertDialog) ShadowDialog.getLatestDialog();
    review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (count(database, "legacy_activity_import_v1") == 0 && System.nanoTime() < deadline) {
      Thread.sleep(10);
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    assertEquals(1, count(database, "legacy_activity_import_v1"));
    deadline = System.nanoTime() + 5_000_000_000L;
    TextView message = null;
    while (System.nanoTime() < deadline) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      AlertDialog current = (AlertDialog) ShadowDialog.getLatestDialog();
      if (current != null) {
        TextView candidate = current.findViewById(android.R.id.message);
        if (candidate != null && candidate.getText().toString().contains("Activities:")) {
          message = candidate;
          break;
        }
      }
      Thread.sleep(10);
    }
    assertNotNull(message);
    assertTrue(message.getText().toString().contains("Activities: 1 imported"));
  }

  private Preference preference(int keyResource) {
    return fragment.findPreference(activity.getString(keyResource));
  }

  private static String archiveWithWorkout() throws Exception {
    String workout =
        "{\"schemaVersion\":3,\"id\":\"test-run\",\"name\":\"Easy run\","
            + "\"sport\":\"running\",\"steps\":[{\"id\":\"easy\",\"kind\":\"segment\","
            + "\"phase\":\"work\",\"end\":{\"type\":\"time\",\"seconds\":600}}]}";
    return new org.json.JSONObject()
        .put("schemaVersion", 1)
        .put("snapshotType", "table-row-archive")
        .put("sourceDatabase", "relven-workouts.db")
        .put("sourceUserVersion", 18)
        .put(
            "tables",
            new org.json.JSONArray()
                .put(
                    new org.json.JSONObject()
                        .put("name", "cached_workout")
                        .put("createSql", "")
                        .put(
                            "columns",
                            new org.json.JSONArray()
                                .put("serverOrigin")
                                .put("ownerUserId")
                                .put("workoutId")
                                .put("name")
                                .put("revision")
                                .put("documentJson"))
                        .put(
                            "rows",
                            new org.json.JSONArray()
                                .put(
                                    new org.json.JSONArray()
                                        .put("https://relven.example")
                                        .put("owner")
                                        .put("run")
                                        .put("Easy run")
                                        .put(1)
                                        .put(workout))))
                .put(
                    new org.json.JSONObject()
                        .put("name", "cached_activity")
                        .put("createSql", "")
                        .put(
                            "columns",
                            new org.json.JSONArray()
                                .put("clientActivityId")
                                .put("serverOrigin")
                                .put("ownerUserId")
                                .put("serverActivityId")
                                .put("activityJson"))
                        .put(
                            "rows",
                            new org.json.JSONArray()
                                .put(
                                    new org.json.JSONArray()
                                        .put("activity-1")
                                        .put("https://relven.example")
                                        .put("owner")
                                        .put("server-activity-1")
                                        .put(activityDocument())))))
        .toString();
  }

  private static int count(android.database.sqlite.SQLiteDatabase database, String table) {
    try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
      cursor.moveToFirst();
      return cursor.getInt(0);
    }
  }

  private static String activityDocument() {
    return "{\"schemaVersion\":1,\"clientActivityId\":\"activity-1\","
        + "\"workout\":{\"name\":\"Easy run\",\"sport\":\"running\"},"
        + "\"startedAt\":\"2025-01-01T10:00:00Z\",\"endedAt\":\"2025-01-01T10:10:00Z\","
        + "\"activeDurationSeconds\":600,\"outcome\":\"completed\",\"samples\":["
        + "{\"type\":\"location\",\"elapsedMilliseconds\":0,\"data\":{\"latitude\":59.9,\"longitude\":10.7}},"
        + "{\"type\":\"location\",\"elapsedMilliseconds\":300000,\"data\":{\"latitude\":59.9005,\"longitude\":10.7005}}]}";
  }
}
