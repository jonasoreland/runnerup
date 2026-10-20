package org.runnerup.relven.migration;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.db.entities.LocationEntity;

/** Imports eligible legacy running activities into RunnerUp history with an identity ledger. */
public final class LegacyRelvenActivityImporter {
  private static final String LEDGER = "legacy_activity_import_v1";

  private LegacyRelvenActivityImporter() {}

  public static ActivityImportResult importActivities(Context context) {
    return importActivities(context, DBHelper.getWritableDatabase(context));
  }

  static ActivityImportResult importActivities(Context context, SQLiteDatabase database) {
    ActivityImportResult result = new ActivityImportResult();
    Set<String> seenInArchive = new HashSet<>();
    database.beginTransaction();
    try {
      database.execSQL(
          "CREATE TABLE IF NOT EXISTS "
              + LEDGER
              + " (source_key TEXT PRIMARY KEY NOT NULL, activity_id INTEGER NOT NULL, "
              + "server_origin TEXT, owner_id TEXT, server_id TEXT)");
      LegacyRelvenMigration.forEachArchiveRow(
          context,
          (table, row) -> {
            if (!"cached_activity".equals(table) && !"activity_outbox".equals(table)) return;
            importRow(database, row, result, seenInArchive);
          });
      database.setTransactionSuccessful();
    } catch (IOException | RuntimeException e) {
      result.imported = 0;
      result.alreadyImported = 0;
      result.failed++;
    } finally {
      database.endTransaction();
    }
    return result;
  }

  private static void importRow(
      SQLiteDatabase database,
      Map<String, String> row,
      ActivityImportResult result,
      Set<String> seenInArchive) {
    String documentText = row.get("activityJson");
    if (documentText == null) documentText = row.get("documentJson");
    if (documentText == null) {
      result.unsupported++;
      return;
    }
    LegacyRelvenActivityDocument document;
    try {
      document = LegacyRelvenActivityDocument.parse(documentText);
    } catch (RuntimeException e) {
      result.unsupported++;
      return;
    }
    String rowClientId = row.get("clientActivityId");
    if (present(rowClientId)) {
      if (present(document.getClientActivityId())
          && !rowClientId.equals(document.getClientActivityId())) {
        result.unsupported++;
        return;
      }
    } else {
      rowClientId = document.getClientActivityId();
    }
    String origin = row.get("serverOrigin");
    String owner = row.get("ownerUserId");
    String serverId = row.get("serverActivityId");
    String sourceKey;
    if (present(rowClientId)) {
      sourceKey = "client:" + rowClientId;
    } else if (present(origin) && present(owner) && present(serverId)) {
      sourceKey = "server:" + part(origin) + part(owner) + part(serverId);
    } else {
      result.unsupported++;
      return;
    }
    if (!seenInArchive.add(sourceKey)) return;
    if (ledgerContains(database, sourceKey)) {
      result.alreadyImported++;
      return;
    }
    ActivityEntity activity = new ActivityEntity();
    activity.setStartTime(new java.util.Date(document.getStartedAtEpochMilliseconds()));
    activity.setDistance(document.getRouteDistanceMeters());
    activity.setTime(document.getActiveDurationSeconds());
    activity.setName(document.getWorkoutName());
    activity.setSport(Constants.DB.ACTIVITY.SPORT_RUNNING);
    long activityId = activity.insert(database);
    if (activityId < 0) throw new IllegalStateException("Unable to insert imported activity");
    for (RouteEvent event : routeEvents(document)) {
      LocationEntity point = new LocationEntity();
      point.setActivityId(activityId);
      point.setLap(0);
      point.setType(event.type);
      point.setTime(document.getStartedAtEpochMilliseconds() + event.elapsedMilliseconds);
      point.setLatitude(event.sample.latitude);
      point.setLongitude(event.sample.longitude);
      point.setAltitude(event.sample.altitudeMeters);
      point.setSpeed(event.sample.speedMetersPerSecond);
      if (point.insert(database) < 0)
        throw new IllegalStateException("Unable to insert route point");
    }
    ContentValues provenance = new ContentValues();
    provenance.put("source_key", sourceKey);
    provenance.put("activity_id", activityId);
    provenance.put("server_origin", origin);
    provenance.put("owner_id", owner);
    provenance.put("server_id", serverId);
    if (database.insertOrThrow(LEDGER, null, provenance) < 0) {
      throw new IllegalStateException("Unable to record imported activity identity");
    }
    result.imported++;
  }

  private static List<RouteEvent> routeEvents(LegacyRelvenActivityDocument document) {
    List<LegacyRelvenActivityDocument.RouteSample> samples = document.getSamples();
    List<RouteEvent> events = new ArrayList<>();
    for (int i = 0; i < samples.size(); i++) {
      int type =
          i == 0
              ? Constants.DB.LOCATION.TYPE_START
              : i == samples.size() - 1
                  ? Constants.DB.LOCATION.TYPE_END
                  : Constants.DB.LOCATION.TYPE_GPS;
      events.add(new RouteEvent(samples.get(i).elapsedMilliseconds, samples.get(i), type, 1));
    }
    for (LegacyRelvenActivityDocument.PauseInterval pause : document.getTechnicalPauses()) {
      events.add(
          nearestEvent(
              samples, pause.startElapsedMilliseconds, Constants.DB.LOCATION.TYPE_PAUSE, 0));
      events.add(
          nearestEvent(
              samples, pause.endElapsedMilliseconds, Constants.DB.LOCATION.TYPE_RESUME, 2));
    }
    events.sort(
        Comparator.comparingLong((RouteEvent event) -> event.elapsedMilliseconds)
            .thenComparingInt(event -> event.order));
    return events;
  }

  private static RouteEvent nearestEvent(
      List<LegacyRelvenActivityDocument.RouteSample> samples, long elapsed, int type, int order) {
    LegacyRelvenActivityDocument.RouteSample nearest = samples.get(0);
    for (LegacyRelvenActivityDocument.RouteSample sample : samples) {
      if (Math.abs(sample.elapsedMilliseconds - elapsed)
          < Math.abs(nearest.elapsedMilliseconds - elapsed)) nearest = sample;
    }
    return new RouteEvent(elapsed, nearest, type, order);
  }

  private static boolean ledgerContains(SQLiteDatabase database, String sourceKey) {
    try (Cursor cursor =
        database.rawQuery(
            "SELECT 1 FROM " + LEDGER + " WHERE source_key = ?", new String[] {sourceKey})) {
      return cursor.moveToFirst();
    }
  }

  private static boolean present(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String part(String value) {
    return value.length() + ":" + value;
  }

  private static final class RouteEvent {
    final long elapsedMilliseconds;
    final LegacyRelvenActivityDocument.RouteSample sample;
    final int type;
    final int order;

    RouteEvent(
        long elapsedMilliseconds,
        LegacyRelvenActivityDocument.RouteSample sample,
        int type,
        int order) {
      this.elapsedMilliseconds = elapsedMilliseconds;
      this.sample = sample;
      this.type = type;
      this.order = order;
    }
  }

  public static final class ActivityImportResult {
    private int imported;
    private int alreadyImported;
    private int unsupported;
    private int failed;

    public int getImportedCount() {
      return imported;
    }

    public int getAlreadyImportedCount() {
      return alreadyImported;
    }

    public int getUnsupportedCount() {
      return unsupported;
    }

    public int getFailedCount() {
      return failed;
    }
  }
}
