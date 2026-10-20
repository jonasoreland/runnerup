package org.runnerup.relven.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.runnerup.workout.RelvenWorkoutImporter;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutSerializer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LegacyRelvenMigrationStoreTest {
  private static final String WORKOUT =
      "{\"schemaVersion\":3,\"id\":\"run-1\",\"name\":\"Easy run\",\"sport\":\"running\",\"steps\":[{\"id\":\"easy\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"time\",\"seconds\":600}}]}";

  private Context context;
  private File workoutDirectory;
  private final List<String> linkFailures = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    context = RuntimeEnvironment.getApplication();
    linkFailures.clear();
    File migrationDirectory = new File(context.getFilesDir(), "legacy-migration");
    deleteTree(migrationDirectory);
    File snapshot = LegacyRelvenVault.snapshotFile(context);
    if (snapshot.exists()) assertTrue(snapshot.delete());
    workoutDirectory = context.getDir(WorkoutSerializer.WORKOUTS_DIR, Context.MODE_PRIVATE);
    for (File file : safeFiles(workoutDirectory)) assertTrue(file.delete());
    assertTrue(workoutDirectory.isDirectory());
  }

  @Test
  public void importKeepsDistinctCompositeIdentities() throws Exception {
    LegacyRelvenMigrationStore store = testStore();
    Workout workout = RelvenWorkoutImporter.parse(WORKOUT);
    List<LegacyRelvenMigrationStore.StagedWorkout> staged = new ArrayList<>();
    staged.add(store.stage(context, identity("origin-a", "owner-a", "run-1", 1), workout));
    staged.add(store.stage(context, identity("origin-a", "owner-a", "run-1", 2), workout));
    staged.add(store.stage(context, identity("origin-b", "owner-a", "run-1", 1), workout));
    assertEquals(3, staged.stream().map(item -> item.getStagedFile().getName()).distinct().count());
    for (LegacyRelvenMigrationStore.StagedWorkout item : staged) {
      assertTrue(item.getStagedFile().isFile());
      assertFalse(item.getDestinationFile().exists());
    }
    assertEquals(
        3,
        new JSONObject(Files.readString(store.manifestFile().toPath()))
            .getJSONArray("records")
            .length());

    LegacyRelvenMigrationStore.StoreResult result = store.publish(staged);

    StringBuilder details =
        new StringBuilder("conflicts=")
            .append(result.getConflictingCount())
            .append(", failed=")
            .append(result.getFailedCount());
    for (LegacyRelvenMigrationStore.StagedWorkout item : staged) {
      details
          .append("; ")
          .append(item.getDestinationFile().getName())
          .append(" dest=")
          .append(item.getDestinationFile().exists())
          .append(" stage=")
          .append(item.getStagedFile().exists());
    }
    details.append("; workoutDir=").append(workoutDirectory.getAbsolutePath());
    details.append("; linkFailures=").append(linkFailures);
    for (File file : safeFiles(workoutDirectory))
      details.append("; actual=").append(file.getName());
    assertEquals(details.toString(), 3, result.getImportedCount());
    assertEquals(0, result.getConflictingCount());
    File[] files = safeFiles(workoutDirectory);
    assertEquals(3, files.length);
    assertNotEquals(files[0].getName(), files[1].getName());
    JSONArray entries =
        new JSONObject(Files.readString(store.manifestFile().toPath())).getJSONArray("records");
    assertEquals(3, entries.length());
    assertEquals(
        "origin-a", entries.getJSONObject(0).getJSONObject("identity").getString("serverOrigin"));
    assertEquals(
        "owner-a", entries.getJSONObject(0).getJSONObject("identity").getString("ownerUserId"));
  }

  @Test
  public void importIsIdempotent() throws Exception {
    LegacyRelvenMigrationStore store = testStore();
    LegacyRelvenMigrationStore.SourceIdentity identity =
        identity("origin-a", "owner-a", "run-1", 1);
    Workout workout = RelvenWorkoutImporter.parse(WORKOUT);
    store.publish(List.of(store.stage(context, identity, workout)));
    JSONArray storedRecords =
        new JSONObject(Files.readString(store.manifestFile().toPath())).getJSONArray("records");
    assertEquals("imported", storedRecords.getJSONObject(0).getString("status"));

    LegacyRelvenMigrationStore.StoreResult repeated =
        store.publish(List.of(store.stage(context, identity, workout)));

    assertEquals(0, repeated.getImportedCount());
    assertEquals(1, repeated.getAlreadyImportedCount());
    assertEquals(1, safeFiles(workoutDirectory).length);
  }

  @Test
  public void importDoesNotOverwriteChangedOrUserFiles() throws Exception {
    LegacyRelvenMigrationStore store = testStore();
    Workout workout = RelvenWorkoutImporter.parse(WORKOUT);
    LegacyRelvenMigrationStore.SourceIdentity first =
        identity("origin-a", "owner-a", "user-file", 1);
    File userFile = WorkoutSerializer.getFile(context, store.destinationName(first));
    Files.writeString(userFile.toPath(), "user-authored", StandardCharsets.UTF_8);

    LegacyRelvenMigrationStore.StoreResult userConflict =
        store.publish(List.of(store.stage(context, first, workout)));
    assertEquals(1, userConflict.getConflictingCount());
    assertEquals("user-authored", Files.readString(userFile.toPath()));

    LegacyRelvenMigrationStore.SourceIdentity second =
        identity("origin-a", "owner-a", "changed-file", 1);
    store.publish(List.of(store.stage(context, second, workout)));
    File changedFile = WorkoutSerializer.getFile(context, store.destinationName(second));
    Files.writeString(changedFile.toPath(), "changed-by-user", StandardCharsets.UTF_8);
    LegacyRelvenMigrationStore.StoreResult changedConflict =
        store.publish(List.of(store.stage(context, second, workout)));

    assertEquals(1, changedConflict.getConflictingCount());
    assertEquals("changed-by-user", Files.readString(changedFile.toPath()));
  }

  @Test
  public void importResumesAfterInterruptedPublish() throws Exception {
    LegacyRelvenMigrationStore store = testStore();
    LegacyRelvenMigrationStore.StagedWorkout staged =
        store.stage(
            context,
            identity("origin-a", "owner-a", "run-1", 1),
            RelvenWorkoutImporter.parse(WORKOUT));
    assertFalse(staged.getDestinationFile().exists());
    assertTrue(staged.getStagedFile().renameTo(staged.getDestinationFile()));

    LegacyRelvenMigrationStore.StoreResult recovered = store.publish(List.of(staged));

    assertEquals(1, recovered.getImportedCount());
    assertEquals(0, recovered.getFailedCount());
    assertEquals(1, safeFiles(workoutDirectory).length);
  }

  @Test
  public void importStorageFailureDoesNotPublishAnyWorkout() throws Exception {
    writeLegacyArchive();
    File root = new File(context.getFilesDir(), "legacy-migration");
    assertTrue(root.mkdirs());
    File stagePath = new File(root, "workout-staging");
    Files.writeString(stagePath.toPath(), "not-a-directory", StandardCharsets.UTF_8);

    LegacyRelvenMigration.WorkoutImportResult result =
        LegacyRelvenMigration.importWorkouts(context);

    assertEquals(0, result.getImportedCount());
    assertEquals(1, result.getFailedCount());
    assertEquals(0, safeFiles(workoutDirectory).length);
  }

  @Test
  public void importFailureLeavesLegacyArchiveAvailable() throws Exception {
    writeLegacyArchive();
    byte[] before = Files.readAllBytes(LegacyRelvenVault.snapshotFile(context).toPath());
    File root = new File(context.getFilesDir(), "legacy-migration");
    assertTrue(root.mkdirs());
    Files.writeString(
        new File(root, "workout-staging").toPath(), "not-a-directory", StandardCharsets.UTF_8);

    LegacyRelvenMigration.WorkoutImportResult result =
        LegacyRelvenMigration.importWorkouts(context);

    assertEquals(1, result.getFailedCount());
    assertTrue(LegacyRelvenVault.snapshotFile(context).isFile());
    assertEquals(
        new String(before, StandardCharsets.UTF_8),
        Files.readString(LegacyRelvenVault.snapshotFile(context).toPath()));
  }

  private void writeLegacyArchive() throws Exception {
    JSONObject table =
        new JSONObject()
            .put("name", "cached_workout")
            .put("createSql", "")
            .put(
                "columns",
                new JSONArray()
                    .put("serverOrigin")
                    .put("ownerUserId")
                    .put("workoutId")
                    .put("name")
                    .put("revision")
                    .put("documentJson"))
            .put(
                "rows",
                new JSONArray()
                    .put(
                        new JSONArray()
                            .put("origin-a")
                            .put("owner-a")
                            .put("run-1")
                            .put("Easy run")
                            .put(1)
                            .put(WORKOUT)));
    JSONObject archive =
        new JSONObject()
            .put("schemaVersion", 1)
            .put("snapshotType", "table-row-archive")
            .put("sourceDatabase", "relven-workouts.db")
            .put("sourceUserVersion", 18)
            .put("tables", new JSONArray().put(table));
    File snapshot = LegacyRelvenVault.snapshotFile(context);
    Files.createDirectories(snapshot.getParentFile().toPath());
    Files.writeString(snapshot.toPath(), archive.toString(), StandardCharsets.UTF_8);
  }

  private static LegacyRelvenMigrationStore.SourceIdentity identity(
      String origin, String owner, String workoutId, int revision) {
    return new LegacyRelvenMigrationStore.SourceIdentity(
        origin, owner, workoutId, revision, "Easy run");
  }

  private LegacyRelvenMigrationStore testStore() {
    return new LegacyRelvenMigrationStore(
        context,
        (source, destination) -> {
          try {
            Files.createLink(destination.toPath(), source.toPath());
          } catch (java.io.IOException e) {
            linkFailures.add(e.toString());
            throw e;
          }
        },
        (destination, contents) -> {
          File temporary =
              new File(destination.getParentFile(), destination.getName() + ".test-new");
          try (java.io.FileOutputStream output = new java.io.FileOutputStream(temporary)) {
            output.write(contents);
            output.getFD().sync();
          }
          Files.move(
              temporary.toPath(),
              destination.toPath(),
              java.nio.file.StandardCopyOption.REPLACE_EXISTING,
              java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        });
  }

  private static File[] safeFiles(File directory) {
    File[] files = directory.listFiles();
    return files == null ? new File[0] : files;
  }

  private static void deleteTree(File target) throws Exception {
    if (!target.exists()) return;
    if (target.isDirectory()) {
      File[] children = target.listFiles();
      if (children != null) for (File child : children) deleteTree(child);
    }
    assertTrue(target.delete());
  }
}
