package org.runnerup.relven.migration;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LegacyRelvenActivityImporterTest {
  private Context context;
  private SQLiteDatabase database;

  @Before
  public void setUp() throws Exception {
    context = RuntimeEnvironment.getApplication();
    database = DBHelper.getWritableDatabase(context);
    database.delete(Constants.DB.ACTIVITY.TABLE, null, null);
    database.delete(Constants.DB.LOCATION.TABLE, null, null);
    database.execSQL(
        "CREATE TABLE IF NOT EXISTS legacy_activity_import_v1 ("
            + "source_key TEXT PRIMARY KEY NOT NULL, activity_id INTEGER NOT NULL, "
            + "server_origin TEXT, owner_id TEXT, server_id TEXT)");
    database.delete("legacy_activity_import_v1", null, null);
    FileSupport.clearArchive(context);
  }

  @Test
  public void activityImportMapsRunningSummaryAndRoute() throws Exception {
    writeArchive("valid", false);
    assertEquals(1, LegacyRelvenMigration.preview(context).getSupportedActivityCount());

    LegacyRelvenActivityImporter.ActivityImportResult result =
        LegacyRelvenActivityImporter.importActivities(context);

    assertEquals(
        "failed=" + result.getFailedCount() + " unsupported=" + result.getUnsupportedCount(),
        1,
        result.getImportedCount());
    assertEquals(1, count(Constants.DB.ACTIVITY.TABLE));
    assertEquals(2, count(Constants.DB.LOCATION.TABLE));
    assertEquals(1, count("legacy_activity_import_v1"));
    try (Cursor row =
        database.query(
            Constants.DB.ACTIVITY.TABLE,
            new String[] {Constants.DB.ACTIVITY.TIME, Constants.DB.ACTIVITY.DISTANCE},
            null,
            null,
            null,
            null,
            null)) {
      row.moveToFirst();
      assertEquals(600, row.getLong(0));
      org.junit.Assert.assertTrue(row.getDouble(1) > 0);
    }
  }

  @Test
  public void activityImportStoresTechnicalPauseMarkersTransactionally() throws Exception {
    writeArchive("pause", false);

    LegacyRelvenActivityImporter.ActivityImportResult result =
        LegacyRelvenActivityImporter.importActivities(context);

    assertEquals(
        "failed=" + result.getFailedCount() + " unsupported=" + result.getUnsupportedCount(),
        1,
        result.getImportedCount());
    assertEquals(4, count(Constants.DB.LOCATION.TABLE));
    assertEquals(1, countWhere(Constants.DB.LOCATION.TABLE, Constants.DB.LOCATION.TYPE + " = 4"));
    assertEquals(1, countWhere(Constants.DB.LOCATION.TABLE, Constants.DB.LOCATION.TYPE + " = 5"));
    assertEquals(1, count("legacy_activity_import_v1"));
  }

  @Test
  public void activityImportRejectsUnsupportedDocumentsAndIsIdempotent() throws Exception {
    writeArchive("unsupported", false);

    LegacyRelvenActivityImporter.ActivityImportResult rejected =
        LegacyRelvenActivityImporter.importActivities(context);
    assertEquals(1, rejected.getUnsupportedCount());
    assertEquals(0, count(Constants.DB.ACTIVITY.TABLE));

    writeArchive("valid", true);
    LegacyRelvenActivityImporter.ActivityImportResult first =
        LegacyRelvenActivityImporter.importActivities(context);
    LegacyRelvenActivityImporter.ActivityImportResult repeated =
        LegacyRelvenActivityImporter.importActivities(context);
    assertEquals(
        "failed=" + first.getFailedCount() + " unsupported=" + first.getUnsupportedCount(),
        1,
        first.getImportedCount());
    assertEquals(1, repeated.getAlreadyImportedCount());
    assertEquals(1, count(Constants.DB.ACTIVITY.TABLE));
  }

  private int count(String table) {
    try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
      cursor.moveToFirst();
      return cursor.getInt(0);
    }
  }

  private int countWhere(String table, String where) {
    try (Cursor cursor =
        database.rawQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where, null)) {
      cursor.moveToFirst();
      return cursor.getInt(0);
    }
  }

  private void writeArchive(String id, boolean duplicateCachedRow) throws Exception {
    String document = activity(id.equals("unsupported") ? "walking" : "running", id);
    JSONObject archive =
        new JSONObject()
            .put("schemaVersion", 1)
            .put("snapshotType", "table-row-archive")
            .put("sourceDatabase", "relven-workouts.db")
            .put("sourceUserVersion", 18)
            .put(
                "tables",
                new JSONArray()
                    .put(
                        table(
                            "activity_outbox",
                            new String[] {
                              "clientActivityId",
                              "serverOrigin",
                              "ownerUserId",
                              "serverActivityId",
                              "documentJson"
                            },
                            new JSONArray()
                                .put(
                                    new JSONArray()
                                        .put(id)
                                        .put("https://relven.example")
                                        .put("owner-1")
                                        .put("server-1")
                                        .put(document)))));
    if (duplicateCachedRow) {
      archive
          .getJSONArray("tables")
          .put(
              table(
                  "cached_activity",
                  new String[] {
                    "clientActivityId",
                    "serverOrigin",
                    "ownerUserId",
                    "serverActivityId",
                    "activityJson"
                  },
                  new JSONArray()
                      .put(
                          new JSONArray()
                              .put(id)
                              .put("https://relven.example")
                              .put("owner-1")
                              .put("server-1")
                              .put(document))));
    }
    var snapshot = LegacyRelvenVault.snapshotFile(context);
    Files.createDirectories(snapshot.getParentFile().toPath());
    Files.writeString(snapshot.toPath(), archive.toString(), StandardCharsets.UTF_8);
  }

  private static JSONObject table(String name, String[] columns, JSONArray rows) throws Exception {
    JSONArray names = new JSONArray();
    for (String column : columns) names.put(column);
    return new JSONObject()
        .put("name", name)
        .put("createSql", "")
        .put("columns", names)
        .put("rows", rows);
  }

  private static String activity(String sport, String id) throws Exception {
    JSONArray samples =
        new JSONArray().put(sample(0, 59.9, 10.7)).put(sample(300_000, 59.9005, 10.7005));
    JSONArray deviations = new JSONArray();
    if ("pause".equals(id)) {
      deviations.put(
          new JSONObject()
              .put("kind", "technical_pause")
              .put("startElapsedMilliseconds", 120_000)
              .put("endElapsedMilliseconds", 180_000));
    }
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("clientActivityId", id)
        .put("workout", new JSONObject().put("name", "Easy run").put("sport", sport))
        .put("startedAt", "2025-01-01T10:00:00Z")
        .put("endedAt", "2025-01-01T10:10:00Z")
        .put("activeDurationSeconds", 600)
        .put("outcome", "completed")
        .put("deviations", deviations)
        .put("samples", samples)
        .toString();
  }

  private static JSONObject sample(long elapsed, double latitude, double longitude)
      throws Exception {
    return new JSONObject()
        .put("type", "location")
        .put("elapsedMilliseconds", elapsed)
        .put("data", new JSONObject().put("latitude", latitude).put("longitude", longitude));
  }

  private static final class FileSupport {
    static void clearArchive(Context context) throws Exception {
      var snapshot = LegacyRelvenVault.snapshotFile(context);
      if (snapshot.exists()) assert snapshot.delete();
    }
  }
}
