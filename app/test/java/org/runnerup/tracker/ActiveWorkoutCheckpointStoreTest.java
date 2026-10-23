package org.runnerup.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.runnerup.common.tracker.TrackerState;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ActiveWorkoutCheckpointStoreTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void savesAndReloadsCheckpointAcrossStoreInstances() throws Exception {
    File checkpointFile = new File(temporaryFolder.getRoot(), "active-workout.json");
    ActiveWorkoutCheckpoint checkpoint = checkpoint();

    new ActiveWorkoutCheckpointStore(checkpointFile).save(checkpoint);

    ActiveWorkoutCheckpoint restored = new ActiveWorkoutCheckpointStore(checkpointFile).load();
    assertEquals(checkpoint.getActivityId(), restored.getActivityId());
    assertEquals(checkpoint.getWorkoutRuntimeJson(), restored.getWorkoutRuntimeJson());
    assertEquals(checkpoint.getElapsedTimeNanos(), restored.getElapsedTimeNanos());
  }

  @Test
  public void malformedCheckpointIsNotExposedAsAResumableSession() throws Exception {
    File checkpointFile = new File(temporaryFolder.getRoot(), "active-workout.json");
    Files.writeString(checkpointFile.toPath(), "{broken");

    assertNull(new ActiveWorkoutCheckpointStore(checkpointFile).load());
  }

  @Test
  public void clearRemovesTheDurableCheckpoint() throws Exception {
    File checkpointFile = new File(temporaryFolder.getRoot(), "active-workout.json");
    ActiveWorkoutCheckpointStore store = new ActiveWorkoutCheckpointStore(checkpointFile);
    store.save(checkpoint());

    store.clear();

    assertNull(store.load());
  }

  private static ActiveWorkoutCheckpoint checkpoint() {
    return new ActiveWorkoutCheckpoint(
        42L,
        TrackerState.STARTED,
        2_000_000_000L,
        123.0,
        0.0,
        0L,
        0L,
        "{}",
        "{}",
        4L,
        1_766_000_000_000L,
        9_000_000L,
        1,
        0,
        false,
        false);
  }
}
