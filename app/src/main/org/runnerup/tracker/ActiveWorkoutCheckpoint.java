package org.runnerup.tracker;

import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.tracker.TrackerState;

/** Versioned, validated state needed to restore the authoritative RunnerUp workout tracker. */
public final class ActiveWorkoutCheckpoint {
  public static final int VERSION = 1;

  private final long activityId;
  private final TrackerState trackerState;
  private final long elapsedTimeNanos;
  private final double distanceMeters;
  private final double heartbeats;
  private final long heartbeatNanos;
  private final long maxHeartRate;
  private final String workoutDefinitionJson;
  private final String workoutRuntimeJson;
  private final long lapId;
  private final long startedAtEpochMillis;
  private final long checkpointElapsedRealtimeNanos;
  private final int sport;
  private final int workoutType;
  private final boolean mute;
  private final boolean withoutGps;

  public ActiveWorkoutCheckpoint(
      long activityId,
      TrackerState trackerState,
      long elapsedTimeNanos,
      double distanceMeters,
      double heartbeats,
      long heartbeatNanos,
      long maxHeartRate,
      String workoutDefinitionJson,
      String workoutRuntimeJson,
      long lapId,
      long startedAtEpochMillis,
      long checkpointElapsedRealtimeNanos,
      int sport,
      int workoutType,
      boolean mute,
      boolean withoutGps) {
    this.activityId = activityId;
    this.trackerState = Objects.requireNonNull(trackerState);
    this.elapsedTimeNanos = elapsedTimeNanos;
    this.distanceMeters = distanceMeters;
    this.heartbeats = heartbeats;
    this.heartbeatNanos = heartbeatNanos;
    this.maxHeartRate = maxHeartRate;
    this.workoutDefinitionJson = Objects.requireNonNull(workoutDefinitionJson);
    this.workoutRuntimeJson = Objects.requireNonNull(workoutRuntimeJson);
    this.lapId = lapId;
    this.startedAtEpochMillis = startedAtEpochMillis;
    this.checkpointElapsedRealtimeNanos = checkpointElapsedRealtimeNanos;
    this.sport = sport;
    this.workoutType = workoutType;
    this.mute = mute;
    this.withoutGps = withoutGps;
    if (!isValid()) throw new IllegalArgumentException("Invalid active workout checkpoint");
  }

  public long getActivityId() {
    return activityId;
  }

  public TrackerState getTrackerState() {
    return trackerState;
  }

  public long getElapsedTimeNanos() {
    return elapsedTimeNanos;
  }

  public double getDistanceMeters() {
    return distanceMeters;
  }

  public double getHeartbeats() {
    return heartbeats;
  }

  public long getHeartbeatNanos() {
    return heartbeatNanos;
  }

  public long getMaxHeartRate() {
    return maxHeartRate;
  }

  public String getWorkoutDefinitionJson() {
    return workoutDefinitionJson;
  }

  public String getWorkoutRuntimeJson() {
    return workoutRuntimeJson;
  }

  public long getLapId() {
    return lapId;
  }

  public long getStartedAtEpochMillis() {
    return startedAtEpochMillis;
  }

  public long getCheckpointElapsedRealtimeNanos() {
    return checkpointElapsedRealtimeNanos;
  }

  public int getSport() {
    return sport;
  }

  public int getWorkoutType() {
    return workoutType;
  }

  public boolean isMute() {
    return mute;
  }

  public boolean isWithoutGps() {
    return withoutGps;
  }

  public JSONObject toJson() throws JSONException {
    return new JSONObject()
        .put("version", VERSION)
        .put("activityId", activityId)
        .put("trackerState", trackerState.name())
        .put("elapsedTimeNanos", elapsedTimeNanos)
        .put("distanceMeters", distanceMeters)
        .put("heartbeats", heartbeats)
        .put("heartbeatNanos", heartbeatNanos)
        .put("maxHeartRate", maxHeartRate)
        .put("workoutDefinitionJson", workoutDefinitionJson)
        .put("workoutRuntimeJson", workoutRuntimeJson)
        .put("lapId", lapId)
        .put("startedAtEpochMillis", startedAtEpochMillis)
        .put("checkpointElapsedRealtimeNanos", checkpointElapsedRealtimeNanos)
        .put("sport", sport)
        .put("workoutType", workoutType)
        .put("mute", mute)
        .put("withoutGps", withoutGps);
  }

  /** Returns null for unsupported, malformed, or unsafe checkpoints. */
  public static ActiveWorkoutCheckpoint fromJson(JSONObject json) {
    if (json == null || json.optInt("version", -1) != VERSION) return null;
    try {
      return new ActiveWorkoutCheckpoint(
          json.getLong("activityId"),
          TrackerState.valueOf(json.getString("trackerState")),
          json.getLong("elapsedTimeNanos"),
          json.getDouble("distanceMeters"),
          json.getDouble("heartbeats"),
          json.getLong("heartbeatNanos"),
          json.getLong("maxHeartRate"),
          json.getString("workoutDefinitionJson"),
          json.getString("workoutRuntimeJson"),
          json.getLong("lapId"),
          json.getLong("startedAtEpochMillis"),
          json.getLong("checkpointElapsedRealtimeNanos"),
          json.getInt("sport"),
          json.getInt("workoutType"),
          json.getBoolean("mute"),
          json.getBoolean("withoutGps"));
    } catch (JSONException | IllegalArgumentException exception) {
      return null;
    }
  }

  private boolean isValid() {
    return activityId > 0
        && (trackerState == TrackerState.STARTED || trackerState == TrackerState.PAUSED)
        && elapsedTimeNanos >= 0
        && Double.isFinite(distanceMeters)
        && distanceMeters >= 0
        && Double.isFinite(heartbeats)
        && heartbeats >= 0
        && heartbeatNanos >= 0
        && maxHeartRate >= 0
        && maxHeartRate <= 300
        && isJsonObject(workoutDefinitionJson)
        && isJsonObject(workoutRuntimeJson)
        && lapId >= 0
        && startedAtEpochMillis > 0
        && checkpointElapsedRealtimeNanos > 0
        && sport >= 0
        && workoutType >= 0
        && workoutType <= 2;
  }

  private static boolean isJsonObject(String value) {
    try {
      new JSONObject(value);
      return true;
    } catch (JSONException exception) {
      return false;
    }
  }
}
