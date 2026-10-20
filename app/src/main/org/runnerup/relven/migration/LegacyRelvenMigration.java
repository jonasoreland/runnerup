package org.runnerup.relven.migration;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.runnerup.workout.RelvenWorkoutImporter;

/** Streams and classifies data in the read-only archive of the old Relven database. */
public final class LegacyRelvenMigration {
  private static final String SOURCE_DATABASE = "relven-workouts.db";
  private static final Set<String> WORKOUT_COLUMNS =
      columns("serverOrigin", "ownerUserId", "workoutId", "name", "revision", "documentJson");
  private static final Set<String> ACTIVITY_COLUMNS =
      columns(
          "serverOrigin",
          "ownerUserId",
          "serverActivityId",
          "clientActivityId",
          "activityJson",
          "documentJson",
          "syncState");
  private static final Set<String> PLAN_COLUMNS = columns("scheduleMovesJson", "scheduleSkipsJson");

  private static Set<String> columns(String... values) {
    Set<String> result = new HashSet<>();
    Collections.addAll(result, values);
    return Collections.unmodifiableSet(result);
  }

  private LegacyRelvenMigration() {}

  public static Preview preview(Context context) throws IOException {
    PreviewCounter counter = new PreviewCounter();
    readArchive(context, counter);
    return counter.toPreview();
  }

  public static WorkoutImportResult importWorkouts(Context context) {
    try {
      preview(context); // Validate the complete archive before creating staged files.
    } catch (IOException e) {
      return new WorkoutImportResult(0, 0, 0, 0, 1);
    }
    LegacyRelvenMigrationStore store = new LegacyRelvenMigrationStore(context);
    WorkoutImportAccumulator accumulator = new WorkoutImportAccumulator(context, store);
    try {
      readArchive(context, accumulator);
    } catch (IOException e) {
      accumulator.failed++;
      accumulator.stageFailed = true;
    }
    if (accumulator.stageFailed) {
      return new WorkoutImportResult(0, 0, accumulator.unsupported, 0, accumulator.failed);
    }
    LegacyRelvenMigrationStore.StoreResult published = store.publish(accumulator.staged);
    return new WorkoutImportResult(
        published.getImportedCount(),
        published.getAlreadyImportedCount(),
        accumulator.unsupported,
        published.getConflictingCount(),
        accumulator.failed + published.getFailedCount());
  }

  private static void readArchive(Context context, RowVisitor visitor) throws IOException {
    if (!LegacyRelvenVault.captureIfPresent(context)) {
      throw new IOException("No legacy Relven archive is available");
    }
    File archive = LegacyRelvenVault.snapshotFile(context);
    try (FileInputStream stream = new FileInputStream(archive);
        InputStreamReader input = new InputStreamReader(stream, StandardCharsets.UTF_8);
        JsonReader reader = new JsonReader(input)) {
      reader.beginObject();
      int schemaVersion = -1;
      String snapshotType = null;
      String sourceDatabase = null;
      boolean foundTables = false;
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "schemaVersion" -> schemaVersion = reader.nextInt();
          case "snapshotType" -> snapshotType = readNullableString(reader);
          case "sourceDatabase" -> sourceDatabase = readNullableString(reader);
          case "tables" -> {
            if (foundTables)
              throw new IOException("Legacy archive contains duplicate tables arrays");
            if (schemaVersion != 1
                || !"table-row-archive".equals(snapshotType)
                || !SOURCE_DATABASE.equals(sourceDatabase)) {
              throw new IOException("Legacy archive header is unsupported or incomplete");
            }
            foundTables = true;
            readTables(reader, visitor);
          }
          default -> reader.skipValue();
        }
      }
      reader.endObject();
      if (reader.peek() != JsonToken.END_DOCUMENT) {
        throw new IOException("Legacy archive contains trailing data");
      }
      if (schemaVersion != 1
          || !"table-row-archive".equals(snapshotType)
          || !SOURCE_DATABASE.equals(sourceDatabase)
          || !foundTables) {
        throw new IOException("Legacy archive header is unsupported or incomplete");
      }
    } catch (IllegalStateException e) {
      throw new IOException("Legacy archive is malformed", e);
    }
  }

  static void forEachArchiveRow(Context context, ArchiveRowConsumer consumer) throws IOException {
    readArchive(context, consumer::accept);
  }

  interface ArchiveRowConsumer {
    void accept(String table, Map<String, String> row) throws IOException;
  }

  private static void readTables(JsonReader reader, RowVisitor visitor) throws IOException {
    reader.beginArray();
    while (reader.hasNext()) readTable(reader, visitor);
    reader.endArray();
  }

  private static void readTable(JsonReader reader, RowVisitor visitor) throws IOException {
    reader.beginObject();
    String tableName = null;
    List<String> columns = null;
    while (reader.hasNext()) {
      String property = reader.nextName();
      switch (property) {
        case "name" -> tableName = readNullableString(reader);
        case "columns" -> columns = readStringArray(reader);
        case "rows" -> {
          Set<String> wanted = wantedColumns(tableName);
          if (wanted == null) {
            reader.skipValue();
          } else {
            if (columns == null)
              throw new IOException("Legacy table columns precede rows requirement failed");
            readRows(reader, tableName, columns, wanted, visitor);
          }
        }
        default -> reader.skipValue();
      }
    }
    reader.endObject();
  }

  private static Set<String> wantedColumns(String table) {
    if (table == null) return null;
    return switch (table) {
      case "cached_workout" -> WORKOUT_COLUMNS;
      case "cached_activity", "activity_outbox" -> ACTIVITY_COLUMNS;
      case "cached_plan" -> PLAN_COLUMNS;
      case "active_workout" -> Collections.emptySet();
      case "plan_mutation",
          "plan_schedule_move_outbox",
          "plan_schedule_skip_outbox",
          "activity_lifecycle_outbox" ->
          columns("state");
      default -> null;
    };
  }

  private static List<String> readStringArray(JsonReader reader) throws IOException {
    List<String> values = new ArrayList<>();
    reader.beginArray();
    while (reader.hasNext()) values.add(readNullableString(reader));
    reader.endArray();
    return values;
  }

  private static void readRows(
      JsonReader reader, String table, List<String> columns, Set<String> wanted, RowVisitor visitor)
      throws IOException {
    reader.beginArray();
    while (reader.hasNext()) {
      Map<String, String> values = readRow(reader, columns, wanted);
      visitor.accept(table, values);
    }
    reader.endArray();
  }

  private static Map<String, String> readRow(
      JsonReader reader, List<String> columns, Set<String> wanted) throws IOException {
    Map<String, String> values = new HashMap<>();
    reader.beginArray();
    int index = 0;
    while (reader.hasNext()) {
      String column = index < columns.size() ? columns.get(index) : null;
      if (column != null && wanted.contains(column)) {
        values.put(column, readNullableString(reader));
      } else {
        reader.skipValue();
      }
      index++;
    }
    reader.endArray();
    return values;
  }

  private static String readNullableString(JsonReader reader) throws IOException {
    JsonToken token = reader.peek();
    if (token == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }
    if (token == JsonToken.STRING || token == JsonToken.NUMBER) return reader.nextString();
    reader.skipValue();
    return null;
  }

  private interface RowVisitor {
    void accept(String table, Map<String, String> row) throws IOException;
  }

  private static final class WorkoutImportAccumulator implements RowVisitor {
    private final Context context;
    private final LegacyRelvenMigrationStore store;
    private final List<LegacyRelvenMigrationStore.StagedWorkout> staged = new ArrayList<>();
    private int unsupported;
    private int failed;
    private boolean stageFailed;

    private WorkoutImportAccumulator(Context context, LegacyRelvenMigrationStore store) {
      this.context = context.getApplicationContext();
      this.store = store;
    }

    @Override
    public void accept(String table, Map<String, String> row) {
      if (!"cached_workout".equals(table)) return;
      String origin = row.get("serverOrigin");
      String owner = row.get("ownerUserId");
      String workoutId = row.get("workoutId");
      String revisionText = row.get("revision");
      String document = row.get("documentJson");
      if (isBlank(origin)
          || isBlank(owner)
          || isBlank(workoutId)
          || isBlank(revisionText)
          || isBlank(document)) {
        unsupported++;
        return;
      }
      int revision;
      org.runnerup.workout.Workout workout;
      try {
        revision = Integer.parseInt(revisionText);
        if (revision < 0) throw new IllegalArgumentException("Invalid revision");
        workout = RelvenWorkoutImporter.parse(document);
      } catch (RuntimeException e) {
        unsupported++;
        return;
      }
      if (stageFailed) {
        failed++;
        return;
      }
      LegacyRelvenMigrationStore.SourceIdentity identity;
      try {
        String sourceName = row.get("name");
        identity =
            new LegacyRelvenMigrationStore.SourceIdentity(
                origin, owner, workoutId, revision, sourceName);
        staged.add(store.stage(context, identity, workout));
      } catch (IOException | IllegalArgumentException e) {
        failed++;
        stageFailed = true;
      }
    }
  }

  private static final class PreviewCounter implements RowVisitor {
    private int supportedWorkouts;
    private int unsupportedWorkouts;
    private int supportedActivities;
    private int unsupportedActivities;
    private int plans;
    private int activityRecords;
    private int activeSessions;
    private int scheduleChanges;
    private int pendingOperations;
    private long activityOrdinal;
    private final Set<String> seenActivities = new HashSet<>();

    @Override
    public void accept(String table, Map<String, String> row) {
      switch (table) {
        case "cached_workout" -> countWorkout(row);
        case "cached_activity" -> countActivity(row);
        case "activity_outbox" -> {
          countActivity(row);
          if ("pending".equals(row.get("syncState"))) pendingOperations++;
        }
        case "cached_plan" -> countPlan(row);
        case "active_workout" -> activeSessions++;
        case "plan_mutation",
            "plan_schedule_move_outbox",
            "plan_schedule_skip_outbox",
            "activity_lifecycle_outbox" -> {
          if ("pending".equals(row.get("state"))) pendingOperations++;
        }
      }
    }

    private void countWorkout(Map<String, String> row) {
      String origin = row.get("serverOrigin");
      String owner = row.get("ownerUserId");
      String workoutId = row.get("workoutId");
      String revision = row.get("revision");
      String document = row.get("documentJson");
      if (isBlank(origin)
          || isBlank(owner)
          || isBlank(workoutId)
          || isBlank(revision)
          || isBlank(document)) {
        unsupportedWorkouts++;
        return;
      }
      try {
        if (Integer.parseInt(revision) < 0) throw new IllegalArgumentException("Invalid revision");
        RelvenWorkoutImporter.parse(document);
        supportedWorkouts++;
      } catch (RuntimeException e) {
        unsupportedWorkouts++;
      }
    }

    private void countActivity(Map<String, String> row) {
      String document = row.get("activityJson");
      if (document == null) document = row.get("documentJson");
      String rowClientId = row.get("clientActivityId");
      LegacyRelvenActivityDocument parsed = null;
      if (!isBlank(document)) {
        try {
          parsed = LegacyRelvenActivityDocument.parse(document);
        } catch (RuntimeException ignored) {
          // Unsupported source records are counted and remain intact in the archive.
        }
      }
      String clientId =
          firstNonBlank(rowClientId, parsed == null ? null : parsed.getClientActivityId());
      String serverId = row.get("serverActivityId");
      String origin = row.get("serverOrigin");
      String owner = row.get("ownerUserId");
      boolean clientMismatch =
          !isBlank(rowClientId)
              && parsed != null
              && !isBlank(parsed.getClientActivityId())
              && !rowClientId.equals(parsed.getClientActivityId());
      boolean hasClientIdentity = !isBlank(clientId) && !clientMismatch;
      boolean hasServerIdentity =
          isBlank(clientId) && !isBlank(origin) && !isBlank(owner) && !isBlank(serverId);
      String identity =
          hasClientIdentity
              ? "client:" + clientId
              : hasServerIdentity
                  ? "server:" + part(origin) + part(owner) + part(serverId)
                  : "row:" + (++activityOrdinal);
      if (!seenActivities.add(identity)) return;
      activityRecords++;
      if (parsed != null && (hasClientIdentity || hasServerIdentity)) {
        supportedActivities++;
      } else {
        unsupportedActivities++;
      }
    }

    private void countPlan(Map<String, String> row) {
      plans++;
      scheduleChanges += jsonArrayLength(row.get("scheduleMovesJson"));
      scheduleChanges += jsonArrayLength(row.get("scheduleSkipsJson"));
    }

    Preview toPreview() {
      return new Preview(
          supportedWorkouts,
          unsupportedWorkouts,
          supportedActivities,
          unsupportedActivities,
          plans,
          activityRecords,
          activeSessions,
          scheduleChanges,
          pendingOperations);
    }
  }

  private static int jsonArrayLength(String source) {
    if (isBlank(source)) return 0;
    try {
      return new JSONArray(source).length();
    } catch (JSONException e) {
      return 0;
    }
  }

  private static String firstNonBlank(String first, String second) {
    return !isBlank(first) ? first : second;
  }

  private static String part(String value) {
    return value.length() + ":" + value;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public static final class Preview {
    private final int supportedWorkoutCount;
    private final int unsupportedWorkoutCount;
    private final int supportedActivityCount;
    private final int unsupportedActivityCount;
    private final int planCount;
    private final int activityCount;
    private final int activeSessionCount;
    private final int scheduleChangeCount;
    private final int pendingOperationCount;

    private Preview(
        int supportedWorkoutCount,
        int unsupportedWorkoutCount,
        int supportedActivityCount,
        int unsupportedActivityCount,
        int planCount,
        int activityCount,
        int activeSessionCount,
        int scheduleChangeCount,
        int pendingOperationCount) {
      this.supportedWorkoutCount = supportedWorkoutCount;
      this.unsupportedWorkoutCount = unsupportedWorkoutCount;
      this.supportedActivityCount = supportedActivityCount;
      this.unsupportedActivityCount = unsupportedActivityCount;
      this.planCount = planCount;
      this.activityCount = activityCount;
      this.activeSessionCount = activeSessionCount;
      this.scheduleChangeCount = scheduleChangeCount;
      this.pendingOperationCount = pendingOperationCount;
    }

    public int getSupportedWorkoutCount() {
      return supportedWorkoutCount;
    }

    public int getUnsupportedWorkoutCount() {
      return unsupportedWorkoutCount;
    }

    public int getSupportedActivityCount() {
      return supportedActivityCount;
    }

    public int getUnsupportedActivityCount() {
      return unsupportedActivityCount;
    }

    public int getPlanCount() {
      return planCount;
    }

    public int getActivityCount() {
      return activityCount;
    }

    public int getActiveSessionCount() {
      return activeSessionCount;
    }

    public int getScheduleChangeCount() {
      return scheduleChangeCount;
    }

    public int getPendingOperationCount() {
      return pendingOperationCount;
    }
  }

  public static final class WorkoutImportResult {
    private final int importedCount;
    private final int alreadyImportedCount;
    private final int unsupportedCount;
    private final int conflictingCount;
    private final int failedCount;

    private WorkoutImportResult(
        int importedCount,
        int alreadyImportedCount,
        int unsupportedCount,
        int conflictingCount,
        int failedCount) {
      this.importedCount = importedCount;
      this.alreadyImportedCount = alreadyImportedCount;
      this.unsupportedCount = unsupportedCount;
      this.conflictingCount = conflictingCount;
      this.failedCount = failedCount;
    }

    public int getImportedCount() {
      return importedCount;
    }

    public int getAlreadyImportedCount() {
      return alreadyImportedCount;
    }

    public int getUnsupportedCount() {
      return unsupportedCount;
    }

    public int getConflictingCount() {
      return conflictingCount;
    }

    public int getFailedCount() {
      return failedCount;
    }
  }
}
