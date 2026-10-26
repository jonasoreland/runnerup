package org.runnerup.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashSet;
import java.util.Set;
import org.runnerup.common.util.Constants;

/**
 * Keeps late Wear gap-fill points in captured-time order only when the phone route is monotonic.
 */
public final class ActivityRouteOrder {
  private static final String ROUTE_RECEIPT_TABLE = "relven_wear_telemetry_route_receipt";
  private static final String SESSION_TABLE = "relven_wear_telemetry_session";

  private ActivityRouteOrder() {}

  public static String ascending(SQLiteDatabase database, long activityId) {
    return canUseCapturedTime(database, activityId) ? "time ASC, _id ASC" : "_id ASC";
  }

  public static String descending(SQLiteDatabase database, long activityId) {
    return canUseCapturedTime(database, activityId) ? "time DESC, _id DESC" : "_id DESC";
  }

  public static Set<Long> wearRouteLocationIds(SQLiteDatabase database, long activityId) {
    Set<Long> locationIds = new HashSet<>();
    try (Cursor cursor =
        database.rawQuery(
            "SELECT r.location_id FROM "
                + ROUTE_RECEIPT_TABLE
                + " r JOIN "
                + SESSION_TABLE
                + " s ON s.session_id = r.session_id WHERE s.activity_id = ? AND r.location_id IS NOT NULL",
            new String[] {Long.toString(activityId)})) {
      while (cursor.moveToNext()) locationIds.add(cursor.getLong(0));
    } catch (RuntimeException ignored) {
      // Existing/imported databases can be queried before the additive inbox schema is opened.
      locationIds.clear();
    }
    return locationIds;
  }

  private static boolean hasWearRoutePoints(SQLiteDatabase database, long activityId) {
    try (android.database.Cursor cursor =
        database.rawQuery(
            "SELECT EXISTS(SELECT 1 FROM "
                + ROUTE_RECEIPT_TABLE
                + " r JOIN "
                + SESSION_TABLE
                + " s ON s.session_id = r.session_id WHERE s.activity_id = ? AND r.location_id IS NOT NULL)",
            new String[] {Long.toString(activityId)})) {
      return cursor.moveToFirst() && cursor.getInt(0) != 0;
    } catch (RuntimeException ignored) {
      // Existing/imported databases can be queried before the additive inbox schema is opened.
      return false;
    }
  }

  private static boolean canUseCapturedTime(SQLiteDatabase database, long activityId) {
    if (!hasWearRoutePoints(database, activityId) || !hasPhoneGpsPoints(database, activityId)) {
      return false;
    }
    try (Cursor cursor =
        database.query(
            Constants.DB.LOCATION.TABLE,
            new String[] {Constants.DB.LOCATION.TIME},
            Constants.DB.LOCATION.ACTIVITY
                + " = ? AND "
                + Constants.DB.LOCATION.TYPE
                + " = ? AND _id NOT IN (SELECT r.location_id FROM "
                + ROUTE_RECEIPT_TABLE
                + " r JOIN "
                + SESSION_TABLE
                + " s ON s.session_id = r.session_id WHERE s.activity_id = ? AND r.location_id IS NOT NULL)",
            new String[] {
              Long.toString(activityId),
              Integer.toString(Constants.DB.LOCATION.TYPE_GPS),
              Long.toString(activityId)
            },
            null,
            null,
            "_id ASC")) {
      Long previousTime = null;
      while (cursor.moveToNext()) {
        long time = cursor.getLong(0);
        if (previousTime != null && time < previousTime) return false;
        previousTime = time;
      }
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private static boolean hasPhoneGpsPoints(SQLiteDatabase database, long activityId) {
    try (Cursor cursor =
        database.rawQuery(
            "SELECT EXISTS(SELECT 1 FROM "
                + Constants.DB.LOCATION.TABLE
                + " l WHERE l."
                + Constants.DB.LOCATION.ACTIVITY
                + " = ? AND l."
                + Constants.DB.LOCATION.TYPE
                + " = ? AND l._id NOT IN (SELECT r.location_id FROM "
                + ROUTE_RECEIPT_TABLE
                + " r JOIN "
                + SESSION_TABLE
                + " s ON s.session_id = r.session_id WHERE s.activity_id = ? AND r.location_id IS NOT NULL))",
            new String[] {
              Long.toString(activityId),
              Integer.toString(Constants.DB.LOCATION.TYPE_GPS),
              Long.toString(activityId)
            })) {
      return cursor.moveToFirst() && cursor.getInt(0) != 0;
    } catch (RuntimeException ignored) {
      return false;
    }
  }
}
