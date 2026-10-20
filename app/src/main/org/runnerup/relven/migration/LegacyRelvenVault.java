package org.runnerup.relven.migration;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import android.util.JsonWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Archives readable rows from the old Relven Room database without modifying that file. */
public final class LegacyRelvenVault {
  private static final String OLD_DATABASE = "relven-workouts.db";
  private static final String SNAPSHOT = "legacy-relven-snapshot-v1.json";

  private LegacyRelvenVault() {}

  public static File snapshotFile(Context context) {
    return new File(new File(context.getFilesDir(), "legacy"), SNAPSHOT);
  }

  public static boolean isAvailable(Context context) {
    return context.getDatabasePath(OLD_DATABASE).isFile() || snapshotFile(context).isFile();
  }

  /**
   * Returns whether a validated snapshot is available. Existing snapshots are never overwritten.
   */
  public static synchronized boolean captureIfPresent(Context context) throws IOException {
    File source = context.getDatabasePath(OLD_DATABASE);
    if (!source.isFile()) return false;
    File snapshot = snapshotFile(context);
    if (snapshot.isFile()) return true;
    File directory = snapshot.getParentFile();
    if (!directory.isDirectory() && !directory.mkdirs()) {
      throw new IOException("Unable to create legacy backup directory");
    }
    File pending = new File(directory, SNAPSHOT + ".pending");
    if (pending.exists() && !pending.delete()) {
      throw new IOException("Unable to clear incomplete legacy backup");
    }
    try {
      writeSnapshot(source, pending);
      if (!pending.renameTo(snapshot)) throw new IOException("Unable to finalize legacy backup");
      return true;
    } finally {
      if (pending.exists()) pending.delete();
    }
  }

  /** Writes the complete private snapshot to a user-selected destination. */
  public static void exportTo(Context context, OutputStream output) throws IOException {
    if (!captureIfPresent(context)) throw new IOException("No previous Relven database found");
    try (FileInputStream input = new FileInputStream(snapshotFile(context))) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }
    output.flush();
  }

  private static void writeSnapshot(File source, File output) throws IOException {
    try (SQLiteDatabase database =
            SQLiteDatabase.openDatabase(
                source.getPath(),
                null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        FileOutputStream stream = new FileOutputStream(output);
        OutputStreamWriter text = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        JsonWriter json = new JsonWriter(text)) {
      String integrity = DatabaseUtils.stringForQuery(database, "PRAGMA quick_check", null);
      if (!"ok".equalsIgnoreCase(integrity)) {
        throw new IOException("Old Relven database failed integrity check: " + integrity);
      }
      json.beginObject();
      json.name("schemaVersion").value(1);
      json.name("snapshotType").value("table-row-archive");
      json.name("sourceDatabase").value(OLD_DATABASE);
      json.name("sourceUserVersion").value(database.getVersion());
      json.name("tables").beginArray();
      try (Cursor tables =
          database.rawQuery(
              "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
              null)) {
        while (tables.moveToNext()) {
          String table = tables.getString(0);
          json.beginObject();
          json.name("name").value(table);
          json.name("createSql").value(tables.getString(1));
          try (Cursor rows = database.rawQuery("SELECT * FROM " + quote(table), null)) {
            json.name("columns").beginArray();
            for (String column : rows.getColumnNames()) json.value(column);
            json.endArray();
            json.name("rows").beginArray();
            while (rows.moveToNext()) {
              json.beginArray();
              for (int i = 0; i < rows.getColumnCount(); i++) {
                switch (rows.getType(i)) {
                  case Cursor.FIELD_TYPE_NULL -> json.nullValue();
                  case Cursor.FIELD_TYPE_INTEGER -> json.value(rows.getLong(i));
                  case Cursor.FIELD_TYPE_FLOAT -> json.value(rows.getDouble(i));
                  case Cursor.FIELD_TYPE_STRING -> json.value(rows.getString(i));
                  case Cursor.FIELD_TYPE_BLOB -> {
                    json.beginObject();
                    json.name("type").value("blob");
                    json.name("base64")
                        .value(Base64.encodeToString(rows.getBlob(i), Base64.NO_WRAP));
                    json.endObject();
                  }
                  default -> throw new IOException("Unknown SQLite value type in " + table);
                }
              }
              json.endArray();
            }
            json.endArray();
          }
          json.endObject();
        }
      }
      json.endArray();
      json.endObject();
      json.flush();
      stream.getFD().sync();
    }
  }

  private static String quote(String name) {
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }
}
