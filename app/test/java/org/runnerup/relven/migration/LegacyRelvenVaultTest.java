package org.runnerup.relven.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.ByteArrayOutputStream;
import java.io.File;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LegacyRelvenVaultTest {
  private Context context;

  @Before
  public void setUp() {
    context = RuntimeEnvironment.getApplication();
    context.deleteDatabase("relven-workouts.db");
    File snapshot = LegacyRelvenVault.snapshotFile(context);
    if (snapshot.exists()) assertTrue(snapshot.delete());
  }

  @Test
  public void noOldDatabaseCreatesNoSnapshot() throws Exception {
    assertFalse(LegacyRelvenVault.captureIfPresent(context));
    assertFalse(LegacyRelvenVault.isAvailable(context));
    assertFalse(LegacyRelvenVault.snapshotFile(context).exists());
  }

  @Test
  public void availabilityRemainsTrueAfterSourceDatabaseIsSnapshotted() throws Exception {
    File source = context.getDatabasePath("relven-workouts.db");
    try (SQLiteDatabase ignored = SQLiteDatabase.openOrCreateDatabase(source, null)) {
      assertTrue(LegacyRelvenVault.isAvailable(context));
      assertTrue(LegacyRelvenVault.captureIfPresent(context));
    }
    assertTrue(source.delete());
    assertTrue(LegacyRelvenVault.isAvailable(context));
    ByteArrayOutputStream exported = new ByteArrayOutputStream();
    LegacyRelvenVault.exportTo(context, exported);
    assertTrue(exported.size() > 0);
  }

  @Test
  public void invalidDatabaseDoesNotLeavePartialSnapshot() throws Exception {
    File source = context.getDatabasePath("relven-workouts.db");
    byte[] invalidDatabase = "not a sqlite database".getBytes(StandardCharsets.UTF_8);
    Files.write(source.toPath(), invalidDatabase);

    assertThrows(RuntimeException.class, () -> LegacyRelvenVault.captureIfPresent(context));

    assertFalse(LegacyRelvenVault.snapshotFile(context).exists());
    assertFalse(
        new File(
                LegacyRelvenVault.snapshotFile(context).getParentFile(),
                "legacy-relven-snapshot-v1.json.pending")
            .exists());
    assertEquals("not a sqlite database", Files.readString(source.toPath()));
  }

  @Test
  public void snapshotsLegacyTablesWithoutModifyingSourceOrOverwritingBackup() throws Exception {
    File source = context.getDatabasePath("relven-workouts.db");
    try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(source, null)) {
      db.execSQL(
          "CREATE TABLE cached_workout (workoutId TEXT, documentJson TEXT, revision INTEGER)");
      db.execSQL(
          "CREATE TABLE activity_outbox (clientActivityId TEXT, documentJson TEXT, payload BLOB)");
      db.execSQL("INSERT INTO cached_workout VALUES ('run-1', '{\"schemaVersion\":3}', 7)");
      db.execSQL("INSERT INTO activity_outbox VALUES ('act-1', NULL, x'00ff10')");
      db.setVersion(18);
    }
    assertTrue(LegacyRelvenVault.captureIfPresent(context));
    File snapshot = LegacyRelvenVault.snapshotFile(context);
    byte[] first = Files.readAllBytes(snapshot.toPath());
    JSONObject backup = new JSONObject(new String(first, StandardCharsets.UTF_8));
    assertEquals(18, backup.getInt("sourceUserVersion"));
    assertEquals("table-row-archive", backup.getString("snapshotType"));
    JSONArray tables = backup.getJSONArray("tables");
    assertTrue(tables.length() >= 2);
    JSONObject workout = table(tables, "cached_workout");
    assertEquals("run-1", workout.getJSONArray("rows").getJSONArray(0).getString(0));
    assertEquals(7, workout.getJSONArray("rows").getJSONArray(0).getInt(2));
    JSONObject activity = table(tables, "activity_outbox");
    assertTrue(activity.getJSONArray("rows").getJSONArray(0).isNull(1));
    JSONObject blob = activity.getJSONArray("rows").getJSONArray(0).getJSONObject(2);
    assertEquals("blob", blob.getString("type"));
    assertEquals("AP8Q", blob.getString("base64"));

    try (SQLiteDatabase db =
        SQLiteDatabase.openDatabase(source.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
      assertEquals(18, db.getVersion());
      assertEquals(
          1,
          android.database.DatabaseUtils.longForQuery(
              db, "SELECT count(*) FROM cached_workout", null));
    }
    assertTrue(LegacyRelvenVault.captureIfPresent(context));
    assertEquals(new String(first, StandardCharsets.UTF_8), Files.readString(snapshot.toPath()));

    ByteArrayOutputStream exported = new ByteArrayOutputStream();
    LegacyRelvenVault.exportTo(context, exported);
    assertEquals(
        new String(first, StandardCharsets.UTF_8), exported.toString(StandardCharsets.UTF_8));
  }

  @Test
  public void legacyArchiveIncludesCommittedWalRowsReadOnly() throws Exception {
    File source = context.getDatabasePath("relven-workouts.db");
    try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(source, null)) {
      db.enableWriteAheadLogging();
      db.execSQL("CREATE TABLE committed_activity (id TEXT NOT NULL)");
      db.execSQL("INSERT INTO committed_activity VALUES ('wal-activity')");
      assertEquals(
          "wal", android.database.DatabaseUtils.stringForQuery(db, "PRAGMA journal_mode", null));
      assertTrue(new File(source.getPath() + "-wal").isFile());

      assertTrue(LegacyRelvenVault.captureIfPresent(context));
      JSONObject archive =
          new JSONObject(Files.readString(LegacyRelvenVault.snapshotFile(context).toPath()));
      JSONObject table = table(archive.getJSONArray("tables"), "committed_activity");
      assertEquals("wal-activity", table.getJSONArray("rows").getJSONArray(0).getString(0));
      assertEquals(
          1,
          android.database.DatabaseUtils.longForQuery(
              db, "SELECT count(*) FROM committed_activity", null));
    }
  }

  private static JSONObject table(JSONArray tables, String name) throws Exception {
    for (int i = 0; i < tables.length(); i++) {
      JSONObject table = tables.getJSONObject(i);
      if (name.equals(table.getString("name"))) return table;
    }
    throw new AssertionError("Missing table " + name);
  }
}
