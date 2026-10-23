package org.runnerup.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;
import org.runnerup.common.tracker.TrackerState;

public class ActiveWorkoutCheckpointTest {
  @Test
  public void roundTripPreservesAuthoritativeTrackerAndWorkoutRecoveryState() throws Exception {
    ActiveWorkoutCheckpoint checkpoint =
        new ActiveWorkoutCheckpoint(
            81L,
            TrackerState.PAUSED,
            123_000_000_000L,
            1_450.5,
            2.4,
            40_000_000_000L,
            182L,
            "{\"sport\":1}",
            "{\"step\":2,\"repeat\":1}",
            7L,
            1_766_000_000_000L,
            8_000_000L,
            1,
            2,
            true,
            false);

    ActiveWorkoutCheckpoint restored = ActiveWorkoutCheckpoint.fromJson(checkpoint.toJson());

    assertNotNull(restored);
    assertEquals(checkpoint.getActivityId(), restored.getActivityId());
    assertEquals(checkpoint.getTrackerState(), restored.getTrackerState());
    assertEquals(checkpoint.getElapsedTimeNanos(), restored.getElapsedTimeNanos());
    assertEquals(checkpoint.getDistanceMeters(), restored.getDistanceMeters(), 0.0);
    assertEquals(checkpoint.getHeartbeats(), restored.getHeartbeats(), 0.0);
    assertEquals(checkpoint.getHeartbeatNanos(), restored.getHeartbeatNanos());
    assertEquals(checkpoint.getMaxHeartRate(), restored.getMaxHeartRate());
    assertEquals(checkpoint.getWorkoutDefinitionJson(), restored.getWorkoutDefinitionJson());
    assertEquals(checkpoint.getWorkoutRuntimeJson(), restored.getWorkoutRuntimeJson());
    assertEquals(checkpoint.getLapId(), restored.getLapId());
    assertEquals(checkpoint.getStartedAtEpochMillis(), restored.getStartedAtEpochMillis());
    assertEquals(
        checkpoint.getCheckpointElapsedRealtimeNanos(),
        restored.getCheckpointElapsedRealtimeNanos());
    assertEquals(checkpoint.getSport(), restored.getSport());
    assertEquals(checkpoint.getWorkoutType(), restored.getWorkoutType());
    assertEquals(checkpoint.isMute(), restored.isMute());
    assertEquals(checkpoint.isWithoutGps(), restored.isWithoutGps());
  }

  @Test
  public void rejectsUnsupportedOrInvalidCheckpointInsteadOfStartingAnEmptyWorkout()
      throws Exception {
    JSONObject unsupported = new JSONObject().put("version", 99);
    JSONObject negativeDistance =
        new JSONObject()
            .put("version", ActiveWorkoutCheckpoint.VERSION)
            .put("activityId", 4)
            .put("trackerState", TrackerState.PAUSED.name())
            .put("elapsedTimeNanos", 10)
            .put("distanceMeters", -1.0)
            .put("heartbeats", 0)
            .put("heartbeatNanos", 0)
            .put("maxHeartRate", 0)
            .put("workoutDefinitionJson", "{}")
            .put("workoutRuntimeJson", "{}")
            .put("lapId", 0)
            .put("startedAtEpochMillis", 1)
            .put("checkpointElapsedRealtimeNanos", 1)
            .put("sport", 1)
            .put("workoutType", 0)
            .put("mute", false)
            .put("withoutGps", false);

    assertNull(ActiveWorkoutCheckpoint.fromJson(unsupported));
    assertNull(ActiveWorkoutCheckpoint.fromJson(negativeDistance));
  }
}
