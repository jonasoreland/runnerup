package org.runnerup.content;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.workout.Sport;

/** Writes a compact activity summary for offline sharing with an external analysis tool. */
final class ActivityJsonExporter {
  private ActivityJsonExporter() {}

  static void export(SQLiteDatabase database, long activityId, Writer writer)
      throws JSONException, IOException {
    JSONObject root = new JSONObject();
    JSONObject activity = new JSONObject();
    JSONObject summary = new JSONObject();
    JSONObject dataQuality = new JSONObject();
    Cursor cursor =
        database.query(
            DB.ACTIVITY.TABLE,
            new String[] {
              "_id",
              DB.ACTIVITY.START_TIME,
              DB.ACTIVITY.TIME,
              DB.ACTIVITY.DISTANCE,
              DB.ACTIVITY.SPORT,
              DB.ACTIVITY.AVG_HR,
              DB.ACTIVITY.MAX_HR,
              DB.ACTIVITY.AVG_CADENCE,
              DB.ACTIVITY.COMMENT
            },
            "_id = ? AND " + DB.ACTIVITY.DELETED + " = 0",
            new String[] {Long.toString(activityId)},
            null,
            null,
            null);
    try {
      if (!cursor.moveToFirst()) {
        throw new FileNotFoundException("Activity not found: " + activityId);
      }
      if (cursor.getLong(1) <= 0L) {
        throw new FileNotFoundException("Activity has no valid start timestamp: " + activityId);
      }
      activity.put("id", "activity:" + cursor.getLong(0));
      activity.put("startedAtEpochSeconds", cursor.getLong(1));
      activity.put("sport", Sport.textOf(cursor.getInt(4)));
      activity.put("status", cursor.isNull(2) || cursor.getLong(2) < 0L ? "partial" : "completed");
      putNonNegativeLong(summary, "durationSeconds", cursor, 2);
      putPositiveDouble(summary, "distanceMeters", cursor, 3);
      if (!cursor.isNull(2) && !cursor.isNull(3)) {
        long duration = cursor.getLong(2);
        double distance = cursor.getDouble(3);
        if (duration > 0 && Double.isFinite(distance) && distance > 0) {
          summary.put("averagePaceSecondsPerKm", duration * 1000.0 / distance);
        }
      }
      putPositiveInt(summary, "averageHeartRateBpm", cursor, 5);
      putPositiveInt(summary, "maximumHeartRateBpm", cursor, 6);
      int sport = cursor.getInt(4);
      if (sport == DB.ACTIVITY.SPORT_BIKING || sport == DB.ACTIVITY.SPORT_STATIONARY_BIKE) {
        putPositiveDouble(summary, "averagePedalCadenceRpm", cursor, 7);
      } else if (sport == DB.ACTIVITY.SPORT_RUNNING
          || sport == DB.ACTIVITY.SPORT_TREADMILL
          || sport == DB.ACTIVITY.SPORT_WALKING
          || sport == DB.ACTIVITY.SPORT_ORIENTEERING) {
        putPositiveDouble(summary, "averageCadenceStepsPerMinute", cursor, 7);
      }
      if (summary.length() > 0) activity.put("summary", summary);
      if (!cursor.isNull(8) && !cursor.getString(8).trim().isEmpty()) {
        activity.put("notes", cursor.getString(8));
      }
    } finally {
      cursor.close();
    }

    JSONArray splits = new JSONArray();
    Cursor lapCursor =
        database.query(
            DB.LAP.TABLE,
            new String[] {
              DB.LAP.LAP,
              DB.LAP.TIME,
              DB.LAP.DISTANCE,
              DB.LAP.AVG_HR,
              DB.LAP.PLANNED_TIME,
              DB.LAP.PLANNED_DISTANCE
            },
            DB.LAP.ACTIVITY + " = ?",
            new String[] {Long.toString(activityId)},
            null,
            null,
            DB.LAP.LAP + " ASC");
    try {
      while (lapCursor.moveToNext()) {
        JSONObject split = new JSONObject();
        split.put("number", Math.max(0, lapCursor.getInt(0)) + 1);
        putNonNegativeLong(split, "durationSeconds", lapCursor, 1);
        putPositiveDouble(split, "distanceMeters", lapCursor, 2);
        putPositiveInt(split, "averageHeartRateBpm", lapCursor, 3);
        JSONObject planned = new JSONObject();
        putNonNegativeLong(planned, "durationSeconds", lapCursor, 4);
        putPositiveDouble(planned, "distanceMeters", lapCursor, 5);
        if (planned.length() > 0) split.put("planned", planned);
        splits.put(split);
      }
    } finally {
      lapCursor.close();
    }
    activity.put("splits", splits);

    Cursor gpsCursor =
        database.query(
            DB.LOCATION.TABLE,
            new String[] {"_id"},
            DB.LOCATION.ACTIVITY + " = ? AND " + DB.LOCATION.TYPE + " = ?",
            new String[] {Long.toString(activityId), Integer.toString(DB.LOCATION.TYPE_GPS)},
            null,
            null,
            null);
    try {
      dataQuality.put("gpsSampleCount", gpsCursor.getCount());
    } finally {
      gpsCursor.close();
    }
    dataQuality.put("rawRouteIncluded", false);
    root.put("format", "relven.activity");
    root.put("schemaVersion", 1);
    root.put("activity", activity);
    root.put("dataQuality", dataQuality);
    writer.write(root.toString(2));
    writer.flush();
  }

  private static void putNonNegativeLong(JSONObject target, String name, Cursor cursor, int column)
      throws JSONException {
    if (!cursor.isNull(column) && cursor.getLong(column) >= 0L) {
      target.put(name, cursor.getLong(column));
    }
  }

  private static void putPositiveInt(JSONObject target, String name, Cursor cursor, int column)
      throws JSONException {
    if (!cursor.isNull(column) && cursor.getInt(column) > 0) {
      target.put(name, cursor.getInt(column));
    }
  }

  private static void putPositiveDouble(JSONObject target, String name, Cursor cursor, int column)
      throws JSONException {
    if (!cursor.isNull(column)) {
      double value = cursor.getDouble(column);
      if (Double.isFinite(value) && value > 0) target.put(name, value);
    }
  }
}
