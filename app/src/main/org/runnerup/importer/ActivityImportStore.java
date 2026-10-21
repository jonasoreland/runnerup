package org.runnerup.importer;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.db.entities.LocationEntity;

/** Stores imported GPX/TCX activities in RunnerUp's existing local history tables. */
public final class ActivityImportStore {
  private static final String IMPORT_LEDGER = "relven_activity_import_v1";

  public enum Result {
    IMPORTED,
    ALREADY_IMPORTED
  }

  private ActivityImportStore() {}

  public static String sha256(byte[] source) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(source);
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value));
      return hex.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  public static Result importActivity(
      android.content.Context context, ImportedActivity activity, String sourceSha256) {
    return importActivity(DBHelper.getWritableDatabase(context), activity, sourceSha256);
  }

  static Result importActivity(
      SQLiteDatabase database, ImportedActivity activity, String sourceSha256) {
    if (activity == null) throw new IllegalArgumentException("Missing imported activity");
    if (sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid source file hash");
    }
    List<ActivityTrackPoint> points = activity.getTrackPoints();
    if (points.size() < 2)
      throw new IllegalArgumentException("At least two route points are required");
    if (activity.getDurationSeconds() < 0
        || !Double.isFinite(activity.getDistanceMeters())
        || activity.getDistanceMeters() < 0) {
      throw new IllegalArgumentException("Invalid activity summary");
    }

    database.beginTransaction();
    try {
      database.execSQL(
          "CREATE TABLE IF NOT EXISTS "
              + IMPORT_LEDGER
              + " (source_sha256 TEXT PRIMARY KEY NOT NULL, activity_id INTEGER NOT NULL)");
      if (wasImported(database, sourceSha256)) {
        database.setTransactionSuccessful();
        return Result.ALREADY_IMPORTED;
      }

      ActivityEntity entity = new ActivityEntity();
      entity.setStartTime(new java.util.Date(activity.getStartTimeSeconds() * 1000L));
      entity.setTime(activity.getDurationSeconds());
      entity.setDistance(activity.getDistanceMeters());
      entity.setSport(activity.getSport().getDbValue());
      entity.setAvgHr(activity.getAverageHeartRate());
      entity.setMaxHr(activity.getMaximumHeartRate());
      long activityId = entity.insert(database);
      if (activityId < 0) throw new IllegalStateException("Unable to save imported activity");

      ActivityTrackPoint previous = null;
      for (int index = 0; index < points.size(); index++) {
        ActivityTrackPoint source = points.get(index);
        LocationEntity point = new LocationEntity();
        point.setActivityId(activityId);
        point.setLap(0);
        point.setType(
            index == 0
                ? Constants.DB.LOCATION.TYPE_START
                : index == points.size() - 1
                    ? Constants.DB.LOCATION.TYPE_END
                    : Constants.DB.LOCATION.TYPE_GPS);
        point.setTime(source.getTimeSeconds() * 1000L);
        point.setLatitude(source.getLatitude());
        point.setLongitude(source.getLongitude());
        point.setAltitude(source.getAltitudeMeters());
        point.setHr(source.getHeartRate());
        if (previous != null) {
          long elapsedSeconds = source.getTimeSeconds() - previous.getTimeSeconds();
          if (elapsedSeconds > 0) {
            float[] meters = new float[1];
            Location.distanceBetween(
                previous.getLatitude(),
                previous.getLongitude(),
                source.getLatitude(),
                source.getLongitude(),
                meters);
            point.setSpeed((double) meters[0] / elapsedSeconds);
          }
        }
        if (point.insert(database) < 0)
          throw new IllegalStateException("Unable to save route point");
        previous = source;
      }

      ContentValues ledger = new ContentValues();
      ledger.put("source_sha256", sourceSha256);
      ledger.put("activity_id", activityId);
      database.insertOrThrow(IMPORT_LEDGER, null, ledger);
      database.setTransactionSuccessful();
      return Result.IMPORTED;
    } finally {
      database.endTransaction();
    }
  }

  private static boolean wasImported(SQLiteDatabase database, String sourceSha256) {
    try (Cursor cursor =
        database.rawQuery(
            "SELECT 1 FROM " + IMPORT_LEDGER + " WHERE source_sha256 = ?",
            new String[] {sourceSha256})) {
      return cursor.moveToFirst();
    }
  }
}
