package org.runnerup.relven.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import java.io.IOException;
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
public class LegacyRelvenMigrationTest {
  private static final String WORKOUT =
      "{\"schemaVersion\":3,\"id\":\"run-1\",\"name\":\"Easy run\",\"sport\":\"running\",\"steps\":[{\"id\":\"easy\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"time\",\"seconds\":600}}]}";

  private Context context;

  @Before
  public void setUp() throws Exception {
    context = RuntimeEnvironment.getApplication();
    context.deleteDatabase("relven-workouts.db");
    var snapshot = LegacyRelvenVault.snapshotFile(context);
    if (snapshot.exists()) assertTrue(snapshot.delete());
  }

  @Test
  public void archivePreviewHandlesOlderSourceVersionAndMissingOrUnknownTables() throws Exception {
    JSONObject archive = archive(17);
    archive
        .getJSONArray("tables")
        .put(
            table(
                "cached_workout",
                new String[] {
                  "serverOrigin", "ownerUserId", "workoutId", "revision", "documentJson"
                },
                new JSONArray()
                    .put(
                        new JSONArray()
                            .put("https://relven.example")
                            .put("owner-1")
                            .put("run-1")
                            .put(2)
                            .put(WORKOUT))
                    .put(
                        new JSONArray()
                            .put("https://relven.example")
                            .put("owner-1")
                            .put("bad-run")
                            .put(0)
                            .put("{}"))));
    archive
        .getJSONArray("tables")
        .put(
            table(
                "cached_plan",
                new String[] {"scheduleMovesJson", "scheduleSkipsJson"},
                new JSONArray()
                    .put(new JSONArray().put("[{\"from\":\"2025-01-01\"}]").put("[{}]"))
                    .put(new JSONArray().put("[]").put("[]"))));
    archive
        .getJSONArray("tables")
        .put(
            table(
                "active_workout",
                new String[] {"slot"},
                new JSONArray().put(new JSONArray().put(0))));
    archive
        .getJSONArray("tables")
        .put(
            table(
                "plan_mutation",
                new String[] {"mutationId", "state"},
                new JSONArray().put(new JSONArray().put("mutation-1").put("pending"))));
    archive
        .getJSONArray("tables")
        .put(
            table(
                "plan_schedule_move_outbox",
                new String[] {"operationId", "state"},
                new JSONArray().put(new JSONArray().put("move-1").put("pending"))));
    archive
        .getJSONArray("tables")
        .put(
            table(
                "plan_schedule_skip_outbox",
                new String[] {"operationId", "state"},
                new JSONArray().put(new JSONArray().put("skip-1").put("pending"))));
    archive
        .getJSONArray("tables")
        .put(
            table(
                "activity_lifecycle_outbox",
                new String[] {"operationId", "state"},
                new JSONArray().put(new JSONArray().put("lifecycle-1").put("pending"))));
    archive
        .getJSONArray("tables")
        .put(
            table(
                "future_table",
                new String[] {"value"},
                new JSONArray().put(new JSONArray().put("keep-readable"))));
    writeArchive(archive);

    LegacyRelvenMigration.Preview preview = LegacyRelvenMigration.preview(context);

    assertEquals(1, preview.getSupportedWorkoutCount());
    assertEquals(1, preview.getUnsupportedWorkoutCount());
    assertEquals(0, preview.getActivityCount());
    assertEquals(0, preview.getSupportedActivityCount());
    assertEquals(2, preview.getPlanCount());
    assertEquals(2, preview.getScheduleChangeCount());
    assertEquals(1, preview.getActiveSessionCount());
    assertEquals(4, preview.getPendingOperationCount());
  }

  @Test
  public void archivePreviewStreamsActivityRows() throws Exception {
    String eligible = activity("completed", "running", true);
    String unsupported = activity("aborted", "running", true);
    JSONObject archive = archive(18);
    archive
        .getJSONArray("tables")
        .put(
            table(
                "cached_activity",
                new String[] {
                  "serverOrigin",
                  "ownerUserId",
                  "serverActivityId",
                  "clientActivityId",
                  "activityJson"
                },
                new JSONArray()
                    .put(
                        new JSONArray()
                            .put("https://relven.example")
                            .put("owner-1")
                            .put("server-1")
                            .put("client-1")
                            .put(eligible))
                    .put(
                        new JSONArray()
                            .put("https://relven.example")
                            .put("owner-1")
                            .put("server-2")
                            .put("client-2")
                            .put(unsupported))));
    archive
        .getJSONArray("tables")
        .put(
            table(
                "activity_outbox",
                new String[] {
                  "clientActivityId",
                  "documentJson",
                  "serverOrigin",
                  "ownerUserId",
                  "serverActivityId",
                  "syncState"
                },
                new JSONArray()
                    .put(
                        new JSONArray()
                            .put("client-1")
                            .put(eligible)
                            .put("https://relven.example")
                            .put("owner-1")
                            .put("server-1")
                            .put("pending"))
                    .put(
                        new JSONArray()
                            .put("client-2")
                            .put(unsupported)
                            .put("https://relven.example")
                            .put("owner-1")
                            .put("server-2")
                            .put("synced"))));
    writeArchive(archive);

    LegacyRelvenMigration.Preview preview = LegacyRelvenMigration.preview(context);

    assertEquals(2, preview.getActivityCount());
    assertEquals(1, preview.getSupportedActivityCount());
    assertEquals(1, preview.getUnsupportedActivityCount());
    assertEquals(1, preview.getPendingOperationCount());
    assertFalse(preview.toString().contains("private-route-payload"));
  }

  @Test
  public void archivePreviewUsesRowClientIdentityWhenDocumentOmitsIt() throws Exception {
    JSONObject activityDocument = new JSONObject(activity("completed", "running", true));
    activityDocument.remove("clientActivityId");
    JSONObject archive = archive(18);
    archive
        .getJSONArray("tables")
        .put(
            table(
                "activity_outbox",
                new String[] {"clientActivityId", "documentJson", "syncState"},
                new JSONArray()
                    .put(
                        new JSONArray()
                            .put("row-client-1")
                            .put(activityDocument.toString())
                            .put("pending"))));
    writeArchive(archive);

    LegacyRelvenMigration.Preview preview = LegacyRelvenMigration.preview(context);

    assertEquals(1, preview.getSupportedActivityCount());
    assertEquals(1, preview.getActivityCount());
  }

  @Test
  public void archivePreviewRequiresImportableActivityIdentityAndRejectsMismatches()
      throws Exception {
    JSONObject documentWithoutId = new JSONObject(activity("completed", "running", true));
    documentWithoutId.remove("clientActivityId");
    JSONObject archive = archive(18);
    archive
        .getJSONArray("tables")
        .put(
            table(
                "cached_activity",
                new String[] {
                  "serverOrigin",
                  "ownerUserId",
                  "serverActivityId",
                  "clientActivityId",
                  "activityJson"
                },
                new JSONArray()
                    .put(
                        new JSONArray()
                            .put(null)
                            .put(null)
                            .put(null)
                            .put(null)
                            .put(documentWithoutId.toString()))
                    .put(
                        new JSONArray()
                            .put("https://relven.example")
                            .put("owner-1")
                            .put("server-1")
                            .put(null)
                            .put(documentWithoutId.toString()))
                    .put(
                        new JSONArray()
                            .put("https://relven.example")
                            .put("owner-1")
                            .put("server-2")
                            .put("different-client")
                            .put(activity("completed", "running", true)))));
    writeArchive(archive);

    LegacyRelvenMigration.Preview preview = LegacyRelvenMigration.preview(context);

    assertEquals(3, preview.getActivityCount());
    assertEquals(
        "supported="
            + preview.getSupportedActivityCount()
            + " unsupported="
            + preview.getUnsupportedActivityCount(),
        1,
        preview.getSupportedActivityCount());
    assertEquals(2, preview.getUnsupportedActivityCount());
  }

  @Test
  public void archivePreviewRejectsMalformedInputAndUsesColumnNames() throws Exception {
    JSONObject archive = archive(18);
    archive
        .getJSONArray("tables")
        .put(
            table(
                "cached_workout",
                new String[] {
                  "documentJson", "revision", "workoutId", "ownerUserId", "serverOrigin"
                },
                new JSONArray()
                    .put(
                        new JSONArray()
                            .put(WORKOUT)
                            .put(3)
                            .put("run-reordered")
                            .put("owner-2")
                            .put("https://relven.example"))));
    writeArchive(archive);

    LegacyRelvenMigration.Preview preview = LegacyRelvenMigration.preview(context);

    assertEquals(1, preview.getSupportedWorkoutCount());
    assertEquals(0, preview.getUnsupportedWorkoutCount());
    Files.writeString(
        LegacyRelvenVault.snapshotFile(context).toPath(), "{malformed", StandardCharsets.UTF_8);
    assertThrows(IOException.class, () -> LegacyRelvenMigration.preview(context));
  }

  private void writeArchive(JSONObject archive) throws Exception {
    var file = LegacyRelvenVault.snapshotFile(context);
    Files.createDirectories(file.getParentFile().toPath());
    Files.writeString(file.toPath(), archive.toString(), StandardCharsets.UTF_8);
  }

  private static JSONObject archive(int sourceVersion) throws Exception {
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("snapshotType", "table-row-archive")
        .put("sourceDatabase", "relven-workouts.db")
        .put("sourceUserVersion", sourceVersion)
        .put("tables", new JSONArray());
  }

  private static JSONObject table(String name, String[] columns, JSONArray rows) throws Exception {
    JSONArray columnArray = new JSONArray();
    for (String column : columns) columnArray.put(column);
    return new JSONObject()
        .put("name", name)
        .put("createSql", "")
        .put("columns", columnArray)
        .put("rows", rows);
  }

  private static String activity(String outcome, String sport, boolean includeSamples)
      throws Exception {
    JSONObject workout =
        new JSONObject().put("id", "source-workout").put("name", "Easy run").put("sport", sport);
    JSONArray samples = new JSONArray();
    if (includeSamples) {
      samples.put(sample(0, 59.9, 10.7));
      samples.put(sample(30_000, 59.9005, 10.7005));
    }
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("clientActivityId", "client-1")
        .put("workout", workout)
        .put("startedAt", "2025-01-01T10:00:00Z")
        .put("endedAt", "2025-01-01T10:10:00Z")
        .put("activeDurationSeconds", 600)
        .put("elapsedDurationSeconds", 600)
        .put("outcome", outcome)
        .put("deviations", new JSONArray())
        .put("samples", samples)
        .put("private", "private-route-payload")
        .toString();
  }

  private static JSONObject sample(long elapsedMs, double latitude, double longitude)
      throws Exception {
    return new JSONObject()
        .put("schemaVersion", 1)
        .put("type", "location")
        .put("elapsedMilliseconds", elapsedMs)
        .put("data", new JSONObject().put("latitude", latitude).put("longitude", longitude));
  }
}
