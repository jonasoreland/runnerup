package org.runnerup.relven.ui;

import android.content.Context;
import java.util.List;

/** Keeps the settings UI honest in builds without the Wear OS Data Layer dependency. */
public final class WearConnectionProvider {
  public enum HandshakeStatus {
    VERIFIED,
    NO_WATCH,
    TIMEOUT,
    PROTOCOL_MISMATCH,
    TRANSPORT_ERROR,
    UNSUPPORTED
  }

  public static final class ConnectionTestResult {
    public final HandshakeStatus status;
    public final List<String> connectedNodes;
    public final long roundTripTimeMillis;

    private ConnectionTestResult(
        HandshakeStatus status, List<String> connectedNodes, long roundTripTimeMillis) {
      this.status = status;
      this.connectedNodes = connectedNodes;
      this.roundTripTimeMillis = roundTripTimeMillis;
    }
  }

  public enum PreparationStatus {
    PREPARED,
    NO_WATCH,
    TIMEOUT,
    REJECTED,
    PROTOCOL_MISMATCH,
    TRANSPORT_ERROR,
    UNSUPPORTED
  }

  public static final class WorkoutPreparationResult {
    public final PreparationStatus status;
    public final String sessionId;
    public final List<String> connectedNodes;

    private WorkoutPreparationResult(
        PreparationStatus status, String sessionId, List<String> connectedNodes) {
      this.status = status;
      this.sessionId = sessionId;
      this.connectedNodes = connectedNodes;
    }
  }

  public interface ConnectionTestCallback {
    void onResult(ConnectionTestResult result);
  }

  public interface Callback {
    void onResult(boolean supported, boolean succeeded, List<String> connectedNodes);
  }

  public interface WorkoutPreparationCallback {
    void onResult(WorkoutPreparationResult result);

    default void onHeartRateReadiness(HeartRateReadinessStatus status) {}
  }

  public enum HeartRateReadinessStatus {
    READY,
    SENSOR_UNAVAILABLE,
    PERMISSION_REQUIRED,
    BACKGROUND_PERMISSION_REQUIRED,
    UNKNOWN
  }

  private WearConnectionProvider() {}

  public static boolean supportsStrengthResultSync() {
    return false;
  }

  public static boolean enqueueStrengthResult(Context context, byte[] encodedResult) {
    return false;
  }

  public static void sendPendingStrengthResult(Context context, String nodeId) {}

  public static void query(Context context, Callback callback) {
    callback.onResult(false, false, null);
  }

  public static void testConnection(Context context, ConnectionTestCallback callback) {
    callback.onResult(new ConnectionTestResult(HandshakeStatus.UNSUPPORTED, null, -1));
  }

  public static void prepareWorkout(
      Context context,
      String sessionId,
      String workoutName,
      String upcomingStep,
      String workoutJson,
      boolean skipSupported,
      WorkoutPreparationCallback callback) {
    callback.onResult(new WorkoutPreparationResult(PreparationStatus.UNSUPPORTED, sessionId, null));
  }

  public static void cancelWorkoutPreparation(Context context, String sessionId) {}
}
