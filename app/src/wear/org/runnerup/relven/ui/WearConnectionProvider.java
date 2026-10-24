package org.runnerup.relven.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.runnerup.common.util.Constants;
import org.runnerup.common.wear.WearHandshakeAttempt;
import org.runnerup.common.wear.WearSessionProtocol;
import org.runnerup.common.wear.WorkoutPreparationAttempt;
import org.runnerup.common.wear.WorkoutPreparationProtocol;

/** Reads only currently connected Wear OS nodes through the existing Wear Data Layer. */
public final class WearConnectionProvider {
  private static final long HANDSHAKE_TIMEOUT_MS = 5000;

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
    public final String verifiedNodeId;

    private ConnectionTestResult(
        HandshakeStatus status,
        List<String> connectedNodes,
        long roundTripTimeMillis,
        String verifiedNodeId) {
      this.status = status;
      this.connectedNodes = connectedNodes;
      this.roundTripTimeMillis = roundTripTimeMillis;
      this.verifiedNodeId = verifiedNodeId;
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

  public interface WorkoutPreparationCallback {
    void onResult(WorkoutPreparationResult result);
  }

  public interface Callback {
    void onResult(boolean supported, boolean succeeded, List<String> connectedNodes);
  }

  private WearConnectionProvider() {}

  public static void query(Context context, Callback callback) {
    try {
      Wearable.getNodeClient(context.getApplicationContext())
          .getConnectedNodes()
          .addOnCompleteListener(
              task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                  callback.onResult(true, false, null);
                  return;
                }
                List<String> names = new ArrayList<>();
                for (Node node : task.getResult()) {
                  String name = node.getDisplayName();
                  names.add(name == null || name.trim().isEmpty() ? "Wear OS-klocka" : name);
                }
                callback.onResult(true, true, names);
              });
    } catch (RuntimeException exception) {
      callback.onResult(true, false, null);
    }
  }

  /** Verifies that the installed Relven companion handles a correlated protocol request. */
  public static void testConnection(Context context, ConnectionTestCallback callback) {
    Context appContext = context.getApplicationContext();
    try {
      Wearable.getNodeClient(appContext)
          .getConnectedNodes()
          .addOnCompleteListener(
              task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                  callback.onResult(
                      new ConnectionTestResult(HandshakeStatus.TRANSPORT_ERROR, null, -1, null));
                  return;
                }
                List<Node> nodes = task.getResult();
                List<String> names = new ArrayList<>();
                for (Node node : nodes) names.add(displayName(node));
                if (nodes.isEmpty()) {
                  callback.onResult(
                      new ConnectionTestResult(HandshakeStatus.NO_WATCH, names, -1, null));
                  return;
                }
                pingNode(appContext, nodes.get(0), names, callback);
              });
    } catch (RuntimeException exception) {
      callback.onResult(new ConnectionTestResult(HandshakeStatus.TRANSPORT_ERROR, null, -1, null));
    }
  }

  private static void pingNode(
      Context context, Node node, List<String> names, ConnectionTestCallback callback) {
    final String requestId = WearHandshakeAttempt.createRequestId();
    final AtomicLong sentAt = new AtomicLong(-1);
    final MessageClient client = Wearable.getMessageClient(context);
    final Handler handler = new Handler(Looper.getMainLooper());
    final WearHandshakeAttempt attempt = new WearHandshakeAttempt(node.getId(), requestId);
    final MessageClient.OnMessageReceivedListener[] listenerRef =
        new MessageClient.OnMessageReceivedListener[1];
    listenerRef[0] =
        event -> {
          try {
            WearSessionProtocol.Message pong = WearSessionProtocol.decode(event.getData());
            WearHandshakeAttempt.Result result =
                attempt.onMessage(event.getSourceNodeId(), event.getPath(), pong);
            if (result != WearHandshakeAttempt.Result.IGNORE) {
              finishAttempt(
                  result, node.getId(), names, sentAt, handler, listenerRef[0], client, callback);
            }
          } catch (java.io.IOException malformedResponse) {
            // Ignore malformed or unrelated payloads and let the bounded timeout report failure.
          }
        };
    Runnable timeout =
        () -> {
          finishAttempt(
              attempt.onTimeout(),
              node.getId(),
              names,
              sentAt,
              handler,
              listenerRef[0],
              client,
              callback);
        };
    handler.postAtTime(
        timeout, listenerRef[0], android.os.SystemClock.uptimeMillis() + HANDSHAKE_TIMEOUT_MS);
    client
        .addListener(listenerRef[0])
        .addOnSuccessListener(
            ignored -> {
              if (attempt.isCompleted()) return;
              try {
                byte[] request =
                    WearSessionProtocol.encodePing(requestId, WearSessionProtocol.CURRENT_VERSION);
                sentAt.set(android.os.SystemClock.elapsedRealtime());
                client
                    .sendMessage(node.getId(), Constants.Wear.Path.MSG_SESSION_PING, request)
                    .addOnFailureListener(
                        error -> {
                          finishAttempt(
                              attempt.onTransportError(),
                              node.getId(),
                              names,
                              sentAt,
                              handler,
                              listenerRef[0],
                              client,
                              callback);
                        });
              } catch (java.io.IOException error) {
                finishAttempt(
                    attempt.onTransportError(),
                    node.getId(),
                    names,
                    sentAt,
                    handler,
                    listenerRef[0],
                    client,
                    callback);
              }
            })
        .addOnFailureListener(
            error -> {
              finishAttempt(
                  attempt.onTransportError(),
                  node.getId(),
                  names,
                  sentAt,
                  handler,
                  listenerRef[0],
                  client,
                  callback);
            });
  }

  private static void finishAttempt(
      WearHandshakeAttempt.Result result,
      String nodeId,
      List<String> names,
      AtomicLong sentAt,
      Handler handler,
      MessageClient.OnMessageReceivedListener listener,
      MessageClient client,
      ConnectionTestCallback callback) {
    if (result == WearHandshakeAttempt.Result.IGNORE) return;
    handler.removeCallbacksAndMessages(listener);
    client.removeListener(listener);
    HandshakeStatus status;
    switch (result) {
      case VERIFIED:
        status = HandshakeStatus.VERIFIED;
        break;
      case PROTOCOL_MISMATCH:
        status = HandshakeStatus.PROTOCOL_MISMATCH;
        break;
      case TIMEOUT:
        status = HandshakeStatus.TIMEOUT;
        break;
      default:
        status = HandshakeStatus.TRANSPORT_ERROR;
        break;
    }
    long start = sentAt.get();
    long roundTripTime =
        result == WearHandshakeAttempt.Result.VERIFIED && start >= 0
            ? android.os.SystemClock.elapsedRealtime() - start
            : -1;
    callback.onResult(
        new ConnectionTestResult(
            status,
            names,
            roundTripTime,
            result == WearHandshakeAttempt.Result.VERIFIED ? nodeId : null));
  }

  /** Transfers a workout only after the companion has passed the application-level handshake. */
  public static void prepareWorkout(
      Context context,
      String sessionId,
      String workoutName,
      String upcomingStep,
      String workoutJson,
      WorkoutPreparationCallback callback) {
    testConnection(
        context,
        connection -> {
          if (connection.status != HandshakeStatus.VERIFIED || connection.verifiedNodeId == null) {
            callback.onResult(
                new WorkoutPreparationResult(
                    preparationFailure(connection.status), sessionId, connection.connectedNodes));
            return;
          }
          sendPreparedWorkout(
              context.getApplicationContext(),
              connection.verifiedNodeId,
              connection.connectedNodes,
              sessionId,
              workoutName,
              upcomingStep,
              workoutJson,
              callback);
        });
  }

  /** Best-effort cancellation keyed by session identity; the watch rejects late retries. */
  public static void cancelWorkoutPreparation(Context context, String sessionId) {
    final byte[] payload;
    try {
      payload = WorkoutPreparationProtocol.encodeCancellation(sessionId);
    } catch (java.io.IOException invalidSession) {
      return;
    }
    Context appContext = context.getApplicationContext();
    try {
      Wearable.getNodeClient(appContext)
          .getConnectedNodes()
          .addOnSuccessListener(
              nodes -> {
                MessageClient client = Wearable.getMessageClient(appContext);
                for (Node node : nodes) {
                  client
                      .sendMessage(node.getId(), Constants.Wear.Path.MSG_WORKOUT_CANCEL, payload)
                      .addOnFailureListener(
                          error ->
                              android.util.Log.w("WearConnection", "Workout cancel failed", error));
                }
              });
    } catch (RuntimeException transportUnavailable) {
      android.util.Log.w("WearConnection", "Workout cancel unavailable", transportUnavailable);
    }
  }

  private static PreparationStatus preparationFailure(HandshakeStatus status) {
    switch (status) {
      case NO_WATCH:
        return PreparationStatus.NO_WATCH;
      case TIMEOUT:
        return PreparationStatus.TIMEOUT;
      case PROTOCOL_MISMATCH:
        return PreparationStatus.PROTOCOL_MISMATCH;
      case UNSUPPORTED:
        return PreparationStatus.UNSUPPORTED;
      default:
        return PreparationStatus.TRANSPORT_ERROR;
    }
  }

  private static void sendPreparedWorkout(
      Context context,
      String nodeId,
      List<String> names,
      String sessionId,
      String workoutName,
      String upcomingStep,
      String workoutJson,
      WorkoutPreparationCallback callback) {
    final String requestId = WearHandshakeAttempt.createRequestId();
    final WorkoutPreparationAttempt attempt =
        new WorkoutPreparationAttempt(nodeId, sessionId, requestId);
    final MessageClient client = Wearable.getMessageClient(context);
    final Handler handler = new Handler(Looper.getMainLooper());
    final MessageClient.OnMessageReceivedListener[] listenerRef =
        new MessageClient.OnMessageReceivedListener[1];
    listenerRef[0] =
        event -> {
          WorkoutPreparationAttempt.Result result =
              attempt.onMessage(event.getSourceNodeId(), event.getPath(), event.getData());
          if (result != WorkoutPreparationAttempt.Result.IGNORE) {
            finishPreparation(result, sessionId, names, handler, listenerRef[0], client, callback);
          }
        };
    handler.postAtTime(
        () ->
            finishPreparation(
                attempt.onTimeout(), sessionId, names, handler, listenerRef[0], client, callback),
        listenerRef[0],
        android.os.SystemClock.uptimeMillis() + HANDSHAKE_TIMEOUT_MS);
    client
        .addListener(listenerRef[0])
        .addOnSuccessListener(
            ignored -> {
              if (attempt.isCompleted()) return;
              try {
                byte[] payload =
                    WorkoutPreparationProtocol.encodePrepare(
                        sessionId, requestId, workoutName, upcomingStep, workoutJson);
                client
                    .sendMessage(nodeId, Constants.Wear.Path.MSG_WORKOUT_PREPARE, payload)
                    .addOnFailureListener(
                        error ->
                            finishPreparation(
                                attempt.onTransportError(),
                                sessionId,
                                names,
                                handler,
                                listenerRef[0],
                                client,
                                callback));
              } catch (java.io.IOException invalidWorkout) {
                finishPreparation(
                    attempt.onTransportError(),
                    sessionId,
                    names,
                    handler,
                    listenerRef[0],
                    client,
                    callback);
              }
            })
        .addOnFailureListener(
            error ->
                finishPreparation(
                    attempt.onTransportError(),
                    sessionId,
                    names,
                    handler,
                    listenerRef[0],
                    client,
                    callback));
  }

  private static void finishPreparation(
      WorkoutPreparationAttempt.Result result,
      String sessionId,
      List<String> names,
      Handler handler,
      MessageClient.OnMessageReceivedListener listener,
      MessageClient client,
      WorkoutPreparationCallback callback) {
    if (result == WorkoutPreparationAttempt.Result.IGNORE) return;
    handler.removeCallbacksAndMessages(listener);
    client.removeListener(listener);
    PreparationStatus status;
    switch (result) {
      case PREPARED:
        status = PreparationStatus.PREPARED;
        break;
      case REJECTED:
        status = PreparationStatus.REJECTED;
        break;
      case TIMEOUT:
        status = PreparationStatus.TIMEOUT;
        break;
      default:
        status = PreparationStatus.TRANSPORT_ERROR;
        break;
    }
    callback.onResult(new WorkoutPreparationResult(status, sessionId, names));
  }

  private static String displayName(Node node) {
    String name = node.getDisplayName();
    return name == null || name.trim().isEmpty() ? "Wear OS-klocka" : name;
  }
}
