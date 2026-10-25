package org.runnerup.relven.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.wear.remote.interactions.RemoteActivityHelper;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.runnerup.common.util.Constants;
import org.runnerup.common.wear.GpsPreparationProtocol;
import org.runnerup.common.wear.HeartRateReadinessAttempt;
import org.runnerup.common.wear.HeartRateReadinessProtocol;
import org.runnerup.common.wear.StrengthExecutionProtocol;
import org.runnerup.common.wear.StrengthPreparationAttempt;
import org.runnerup.common.wear.StrengthResultOutboxStore;
import org.runnerup.common.wear.StrengthResultProtocol;
import org.runnerup.common.wear.WearHandshakeAttempt;
import org.runnerup.common.wear.WearHandshakeDiscovery;
import org.runnerup.common.wear.WearSessionProtocol;
import org.runnerup.common.wear.WorkoutPreparationAttempt;
import org.runnerup.common.wear.WorkoutPreparationProtocol;
import org.runnerup.relven.strength.StrengthSession;

/** Reads only currently connected Wear OS nodes through the existing Wear Data Layer. */
public final class WearConnectionProvider {
  private static final String TRUST_PREFERENCES = "wear_result_sync_trust";
  private static final String TRUSTED_NODE_KEY = "verified_node_id";
  private static final long HANDSHAKE_TIMEOUT_MS = 5000;
  private static final long HEART_RATE_READINESS_TIMEOUT_MS = 2500;

  public enum HandshakeStatus {
    VERIFIED,
    NO_WATCH,
    COMPANION_UNAVAILABLE,
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
    COMPANION_UNAVAILABLE,
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

  public enum StrengthPreparationStatus {
    PREPARED,
    NO_WATCH,
    COMPANION_UNAVAILABLE,
    TIMEOUT,
    REJECTED,
    PROTOCOL_MISMATCH,
    TRANSPORT_ERROR,
    UNSUPPORTED
  }

  public static final class StrengthPreparationResult {
    public final StrengthPreparationStatus status;
    public final String sessionId;
    public final List<String> connectedNodes;

    private StrengthPreparationResult(
        StrengthPreparationStatus status, String sessionId, List<String> connectedNodes) {
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

    default void onHeartRateReadiness(HeartRateReadinessStatus status) {}
  }

  public interface StrengthPreparationCallback {
    void onResult(StrengthPreparationResult result);
  }

  public interface StrengthAuthorityCommandCallback {
    void onResult(boolean transportAccepted);
  }

  private interface HeartRateReadinessCallback {
    void onResult(HeartRateReadinessStatus status);
  }

  public enum HeartRateReadinessStatus {
    READY,
    SENSOR_UNAVAILABLE,
    PERMISSION_REQUIRED,
    BACKGROUND_PERMISSION_REQUIRED,
    UNKNOWN
  }

  public interface Callback {
    void onResult(boolean supported, boolean succeeded, List<String> connectedNodes);
  }

  private WearConnectionProvider() {}

  /** Publishes the phone's current GPS preparation state for the already-prepared session. */
  public static void publishGpsReadiness(Context context, byte[] encodedReadiness) {
    final GpsPreparationProtocol.Readiness readiness;
    try {
      readiness = GpsPreparationProtocol.decode(encodedReadiness);
    } catch (java.io.IOException invalid) {
      android.util.Log.w("WearConnection", "Ignoring invalid GPS readiness", invalid);
      return;
    }
    try {
      PutDataMapRequest request =
          PutDataMapRequest.create(GpsPreparationProtocol.dataItemPath(readiness.getSessionId()));
      request.getDataMap().putByteArray(GpsPreparationProtocol.DATA_KEY, encodedReadiness);
      Wearable.getDataClient(context.getApplicationContext())
          .putDataItem(request.asPutDataRequest().setUrgent())
          .addOnFailureListener(
              error -> android.util.Log.w("WearConnection", "GPS readiness sync failed", error));
    } catch (RuntimeException unavailable) {
      android.util.Log.w("WearConnection", "GPS readiness sync unavailable", unavailable);
    }
  }

  /** Removes the latest-value item only for the preparation that is ending. */
  public static void clearGpsReadiness(Context context, String sessionId) {
    try {
      Uri uri =
          new Uri.Builder()
              .scheme(PutDataRequest.WEAR_URI_SCHEME)
              .path(GpsPreparationProtocol.dataItemPath(sessionId))
              .build();
      Wearable.getDataClient(context.getApplicationContext())
          .deleteDataItems(uri)
          .addOnFailureListener(
              error -> android.util.Log.w("WearConnection", "GPS readiness cleanup failed", error));
    } catch (RuntimeException unavailable) {
      android.util.Log.w("WearConnection", "GPS readiness cleanup unavailable", unavailable);
    }
  }

  public static boolean supportsStrengthResultSync() {
    return true;
  }

  /** Persist each confirmed set before attempting delivery to a connected Relven watch. */
  public static boolean enqueueStrengthResult(Context context, byte[] encodedResult) {
    Context appContext = context.getApplicationContext();
    final StrengthResultOutboxStore.EnqueueResult outcome;
    try {
      outcome =
          new StrengthResultOutboxStore(appContext)
              .enqueue(StrengthResultProtocol.decodeResult(encodedResult));
    } catch (java.io.IOException | RuntimeException invalid) {
      android.util.Log.w("WearConnection", "Invalid strength result outbox event", invalid);
      return false;
    }
    if (outcome == StrengthResultOutboxStore.EnqueueResult.ENQUEUED
        || outcome == StrengthResultOutboxStore.EnqueueResult.DUPLICATE) {
      sendPendingStrengthResult(appContext, null);
    }
    return outcome == StrengthResultOutboxStore.EnqueueResult.ENQUEUED
        || outcome == StrengthResultOutboxStore.EnqueueResult.DUPLICATE;
  }

  /** Retries the oldest durable result to a known connected peer after reconnection or an ACK. */
  public static void sendPendingStrengthResult(Context context, String nodeId) {
    Context appContext = context.getApplicationContext();
    final StrengthResultProtocol.Result next;
    try {
      List<StrengthResultProtocol.Result> pending =
          new StrengthResultOutboxStore(appContext).pending(1);
      if (pending.isEmpty()) return;
      next = pending.get(0);
    } catch (RuntimeException corruptOutbox) {
      android.util.Log.e("WearConnection", "Cannot read pending strength results", corruptOutbox);
      return;
    }
    testConnection(
        appContext,
        connection -> {
          if (connection.status != HandshakeStatus.VERIFIED || connection.verifiedNodeId == null)
            return;
          if (nodeId != null && !nodeId.equals(connection.verifiedNodeId)) return;
          sendStrengthResult(appContext, connection.verifiedNodeId, next);
        });
  }

  /** Sends an idempotent phone request to the watch authority without mutating phone state. */
  public static void requestStrengthAuthorityCommand(
      Context context,
      org.runnerup.common.wear.StrengthSessionAuthorityProtocol.Command command,
      StrengthAuthorityCommandCallback callback) {
    Context appContext = context.getApplicationContext();
    if (!new org.runnerup.relven.strength.StrengthAuthorityCommandStore(appContext)
        .enqueue(command)) {
      callback.onResult(false);
      return;
    }
    sendPendingStrengthAuthorityCommand(appContext, callback);
  }

  /** Retries the pending durable phone request with its original stable request ID. */
  public static void sendPendingStrengthAuthorityCommand(
      Context context, StrengthAuthorityCommandCallback callback) {
    Context appContext = context.getApplicationContext();
    org.runnerup.common.wear.StrengthSessionAuthorityProtocol.Command command =
        new org.runnerup.relven.strength.StrengthAuthorityCommandStore(appContext).pending();
    if (command == null) {
      if (callback != null) callback.onResult(false);
      return;
    }
    testConnection(
        appContext,
        connection -> {
          if (connection.status != HandshakeStatus.VERIFIED || connection.verifiedNodeId == null) {
            if (callback != null) callback.onResult(false);
            return;
          }
          try {
            Wearable.getMessageClient(appContext)
                .sendMessage(
                    connection.verifiedNodeId,
                    Constants.Wear.Path.MSG_STRENGTH_AUTHORITY_COMMAND,
                    org.runnerup.common.wear.StrengthSessionAuthorityProtocol.encodeCommand(
                        command))
                .addOnSuccessListener(
                    ignored -> {
                      if (callback != null) callback.onResult(true);
                    })
                .addOnFailureListener(
                    error -> {
                      android.util.Log.w(
                          "WearConnection", "Strength authority command delivery failed", error);
                      if (callback != null) callback.onResult(false);
                    });
          } catch (RuntimeException error) {
            android.util.Log.w(
                "WearConnection", "Could not deliver strength authority command", error);
            if (callback != null) callback.onResult(false);
          }
        });
  }

  public static boolean isVerifiedResultPeer(Context context, String nodeId) {
    return nodeId != null
        && nodeId.equals(
            context
                .getApplicationContext()
                .getSharedPreferences(TRUST_PREFERENCES, Context.MODE_PRIVATE)
                .getString(TRUSTED_NODE_KEY, null));
  }

  private static void sendStrengthResult(
      Context context, String nodeId, StrengthResultProtocol.Result result) {
    try {
      Wearable.getMessageClient(context)
          .sendMessage(
              nodeId,
              Constants.Wear.Path.MSG_STRENGTH_RESULT,
              StrengthResultProtocol.encodeResult(result))
          .addOnFailureListener(
              error ->
                  android.util.Log.w("WearConnection", "Strength result delivery failed", error));
    } catch (java.io.IOException | RuntimeException error) {
      android.util.Log.w("WearConnection", "Could not encode or deliver strength result", error);
    }
  }

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
                Map<String, Node> nodesById = new LinkedHashMap<>();
                for (Node node : nodes) {
                  names.add(displayName(node));
                  nodesById.putIfAbsent(node.getId(), node);
                }
                if (nodesById.isEmpty()) {
                  callback.onResult(
                      new ConnectionTestResult(HandshakeStatus.NO_WATCH, names, -1, null));
                  return;
                }
                Wearable.getCapabilityClient(appContext)
                    .getCapability(
                        Constants.Wear.Capability.RELVEN_WORKOUT_COMPANION,
                        CapabilityClient.FILTER_REACHABLE)
                    .addOnCompleteListener(
                        capabilityTask -> {
                          if (!capabilityTask.isSuccessful()
                              || capabilityTask.getResult() == null) {
                            callback.onResult(
                                new ConnectionTestResult(
                                    HandshakeStatus.TRANSPORT_ERROR, names, -1, null));
                            return;
                          }
                          Set<String> advertisedNodeIds = new HashSet<>();
                          for (Node companionNode : capabilityTask.getResult().getNodes()) {
                            advertisedNodeIds.add(companionNode.getId());
                          }
                          List<String> companionNodeIds =
                              selectRelvenCompanionNodeIds(
                                  new ArrayList<>(nodesById.keySet()), advertisedNodeIds);
                          if (companionNodeIds.isEmpty()) {
                            callback.onResult(
                                new ConnectionTestResult(
                                    HandshakeStatus.COMPANION_UNAVAILABLE, names, -1, null));
                            return;
                          }
                          discoverRelvenCompanion(
                              appContext, names, nodesById, companionNodeIds, callback);
                        });
              });
    } catch (RuntimeException exception) {
      callback.onResult(new ConnectionTestResult(HandshakeStatus.TRANSPORT_ERROR, null, -1, null));
    }
  }

  static List<String> selectRelvenCompanionNodeIds(
      List<String> connectedNodeIds, Set<String> advertisedNodeIds) {
    List<String> selected = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    if (connectedNodeIds == null || advertisedNodeIds == null) return selected;
    for (String nodeId : connectedNodeIds) {
      if (nodeId != null && advertisedNodeIds.contains(nodeId) && seen.add(nodeId)) {
        selected.add(nodeId);
      }
    }
    return selected;
  }

  private static void discoverRelvenCompanion(
      Context appContext,
      List<String> names,
      Map<String, Node> nodesById,
      List<String> companionNodeIds,
      ConnectionTestCallback callback) {
    WearHandshakeDiscovery.discover(
        companionNodeIds,
        (nodeId, pingCallback) -> {
          Node candidate = nodesById.get(nodeId);
          if (candidate == null) {
            pingCallback.onResult(WearHandshakeDiscovery.Status.TRANSPORT_ERROR, -1L);
            return;
          }
          pingNode(
              appContext,
              candidate,
              names,
              result ->
                  pingCallback.onResult(
                      WearHandshakeDiscovery.Status.valueOf(result.status.name()),
                      result.roundTripTimeMillis));
        },
        result -> {
          String verifiedNodeId =
              result.status == WearHandshakeDiscovery.Status.VERIFIED ? result.nodeId : null;
          if (verifiedNodeId != null) {
            appContext
                .getSharedPreferences(TRUST_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(TRUSTED_NODE_KEY, verifiedNodeId)
                .apply();
          }
          callback.onResult(
              new ConnectionTestResult(
                  HandshakeStatus.valueOf(result.status.name()),
                  names,
                  result.roundTripTimeMillis,
                  verifiedNodeId));
        });
  }

  private static void pingNode(
      Context context, Node node, List<String> names, ConnectionTestCallback callback) {
    final String requestId = WearHandshakeAttempt.createRequestId();
    final AtomicLong sentAt = new AtomicLong(-1);
    final Handler handler = new Handler(Looper.getMainLooper());
    final MessageClient client = Wearable.getMessageClient(context);
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
      boolean skipSupported,
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
              skipSupported,
              callback);
        });
  }

  /** Transfers a durable prepared strength session only after the companion handshake succeeds. */
  public static void prepareStrengthSession(
      Context context, StrengthSession session, StrengthPreparationCallback callback) {
    if (session == null || session.getPhase() != StrengthSession.Phase.PREPARING) {
      callback.onResult(
          new StrengthPreparationResult(
              StrengthPreparationStatus.REJECTED,
              session == null ? null : session.getExecutionId(),
              null));
      return;
    }
    testConnection(
        context,
        connection -> {
          if (connection.status != HandshakeStatus.VERIFIED || connection.verifiedNodeId == null) {
            callback.onResult(
                new StrengthPreparationResult(
                    strengthPreparationFailure(connection.status),
                    session.getExecutionId(),
                    connection.connectedNodes));
            return;
          }
          sendPreparedStrengthSession(
              context.getApplicationContext(),
              connection.verifiedNodeId,
              connection.connectedNodes,
              session,
              callback);
        });
  }

  /** Best-effort cancellation, scoped to the previously verified companion. */
  public static void cancelStrengthPreparation(Context context, String sessionId) {
    final byte[] payload;
    try {
      payload = StrengthExecutionProtocol.encodeCancellation(sessionId);
    } catch (java.io.IOException invalidSession) {
      return;
    }
    Context appContext = context.getApplicationContext();
    testConnection(
        appContext,
        connection -> {
          if (connection.status != HandshakeStatus.VERIFIED || connection.verifiedNodeId == null)
            return;
          try {
            Wearable.getMessageClient(appContext)
                .sendMessage(
                    connection.verifiedNodeId, Constants.Wear.Path.MSG_STRENGTH_CANCEL, payload)
                .addOnFailureListener(
                    error -> android.util.Log.w("WearConnection", "Strength cancel failed", error));
          } catch (RuntimeException error) {
            android.util.Log.w("WearConnection", "Strength cancel unavailable", error);
          }
        });
  }

  private static StrengthPreparationStatus strengthPreparationFailure(HandshakeStatus status) {
    switch (status) {
      case NO_WATCH:
        return StrengthPreparationStatus.NO_WATCH;
      case COMPANION_UNAVAILABLE:
        return StrengthPreparationStatus.COMPANION_UNAVAILABLE;
      case TIMEOUT:
        return StrengthPreparationStatus.TIMEOUT;
      case PROTOCOL_MISMATCH:
        return StrengthPreparationStatus.PROTOCOL_MISMATCH;
      case UNSUPPORTED:
        return StrengthPreparationStatus.UNSUPPORTED;
      default:
        return StrengthPreparationStatus.TRANSPORT_ERROR;
    }
  }

  private static void sendPreparedStrengthSession(
      Context context,
      String nodeId,
      List<String> names,
      StrengthSession session,
      StrengthPreparationCallback callback) {
    final String sessionId = session.getExecutionId();
    final String requestId = WearHandshakeAttempt.createRequestId();
    final byte[] payload;
    try {
      payload = StrengthExecutionProtocol.encodePrepare(session, requestId);
    } catch (java.io.IOException invalidSession) {
      callback.onResult(
          new StrengthPreparationResult(StrengthPreparationStatus.REJECTED, sessionId, names));
      return;
    }
    final StrengthPreparationAttempt attempt =
        new StrengthPreparationAttempt(nodeId, sessionId, requestId);
    final MessageClient client = Wearable.getMessageClient(context);
    final Handler handler = new Handler(Looper.getMainLooper());
    final MessageClient.OnMessageReceivedListener[] listenerRef =
        new MessageClient.OnMessageReceivedListener[1];
    listenerRef[0] =
        event -> {
          StrengthPreparationAttempt.Result result =
              attempt.onMessage(event.getSourceNodeId(), event.getPath(), event.getData());
          finishStrengthPreparationIfTerminal(
              result, context, nodeId, sessionId, names, handler, listenerRef[0], client, callback);
        };
    Runnable timeout =
        () ->
            finishStrengthPreparationIfTerminal(
                attempt.onTimeout(),
                context,
                nodeId,
                sessionId,
                names,
                handler,
                listenerRef[0],
                client,
                callback);
    handler.postAtTime(
        timeout, listenerRef[0], android.os.SystemClock.uptimeMillis() + HANDSHAKE_TIMEOUT_MS);
    client
        .addListener(listenerRef[0])
        .addOnSuccessListener(
            ignored -> {
              if (attempt.isCompleted()) return;
              client
                  .sendMessage(nodeId, Constants.Wear.Path.MSG_STRENGTH_PREPARE, payload)
                  .addOnFailureListener(
                      error ->
                          finishStrengthPreparationIfTerminal(
                              attempt.onTransportError(),
                              context,
                              nodeId,
                              sessionId,
                              names,
                              handler,
                              listenerRef[0],
                              client,
                              callback));
            })
        .addOnFailureListener(
            error ->
                finishStrengthPreparationIfTerminal(
                    attempt.onTransportError(),
                    context,
                    nodeId,
                    sessionId,
                    names,
                    handler,
                    listenerRef[0],
                    client,
                    callback));
  }

  private static void finishStrengthPreparationIfTerminal(
      StrengthPreparationAttempt.Result result,
      Context context,
      String nodeId,
      String sessionId,
      List<String> names,
      Handler handler,
      MessageClient.OnMessageReceivedListener listener,
      MessageClient client,
      StrengthPreparationCallback callback) {
    if (result == StrengthPreparationAttempt.Result.IGNORE) return;
    handler.removeCallbacksAndMessages(listener);
    client.removeListener(listener);
    StrengthPreparationStatus status;
    switch (result) {
      case PREPARED:
        status = StrengthPreparationStatus.PREPARED;
        break;
      case REJECTED:
        status = StrengthPreparationStatus.REJECTED;
        break;
      case TIMEOUT:
        status = StrengthPreparationStatus.TIMEOUT;
        break;
      default:
        status = StrengthPreparationStatus.TRANSPORT_ERROR;
        break;
    }
    callback.onResult(new StrengthPreparationResult(status, sessionId, names));
    PreparedWorkoutUiLauncher.afterStrengthPreparation(
        result,
        nodeId,
        (targetNodeId, targetIntent) ->
            requestPreparedWorkoutUi(context, targetNodeId, targetIntent));
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
      case COMPANION_UNAVAILABLE:
        return PreparationStatus.COMPANION_UNAVAILABLE;
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
      boolean skipSupported,
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
            PreparedWorkoutUiLauncher.afterWorkoutPreparation(
                result,
                nodeId,
                (targetNodeId, targetIntent) ->
                    requestPreparedWorkoutUi(context, targetNodeId, targetIntent));
            if (result == WorkoutPreparationAttempt.Result.PREPARED) {
              queryHeartRateReadiness(context, nodeId, sessionId, callback::onHeartRateReadiness);
            }
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
                        sessionId,
                        requestId,
                        workoutName,
                        upcomingStep,
                        workoutJson,
                        skipSupported);
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

  /**
   * Requests a visible watch activity after the prepared-workout ACK; notification remains
   * fallback.
   */
  private static void requestPreparedWorkoutUi(
      Context context, String nodeId, Intent targetIntent) {
    Handler handler = new Handler(Looper.getMainLooper());
    Executor mainExecutor = command -> handler.post(command);
    try {
      var launchRequest =
          new RemoteActivityHelper(context.getApplicationContext(), mainExecutor)
              .startRemoteActivity(targetIntent, nodeId);
      launchRequest.addListener(
          () -> {
            try {
              launchRequest.get();
              android.util.Log.d(
                  "WearConnection", "Prepared-workout remote launch request was sent");
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
              android.util.Log.w(
                  "WearConnection",
                  "Prepared-workout launch did not complete; watch notification remains available",
                  interrupted);
            } catch (ExecutionException | RuntimeException failure) {
              android.util.Log.w(
                  "WearConnection",
                  "Prepared-workout launch failed; watch notification remains available",
                  failure);
            }
          },
          mainExecutor);
    } catch (RuntimeException failure) {
      android.util.Log.w(
          "WearConnection",
          "Prepared-workout launch could not start; watch notification remains available",
          failure);
    }
  }

  private static void queryHeartRateReadiness(
      Context context, String nodeId, String sessionId, HeartRateReadinessCallback callback) {
    final String requestId = WearHandshakeAttempt.createRequestId();
    final byte[] payload;
    final HeartRateReadinessProtocol.Request request;
    try {
      payload = HeartRateReadinessProtocol.encodeRequest(sessionId, requestId);
      request = HeartRateReadinessProtocol.decodeRequest(payload);
    } catch (java.io.IOException invalidRequest) {
      callback.onResult(HeartRateReadinessStatus.UNKNOWN);
      return;
    }

    final Handler handler = new Handler(Looper.getMainLooper());
    final MessageClient client;
    try {
      client = Wearable.getMessageClient(context);
    } catch (RuntimeException unavailable) {
      handler.post(() -> callback.onResult(HeartRateReadinessStatus.UNKNOWN));
      return;
    }
    final HeartRateReadinessAttempt attempt = new HeartRateReadinessAttempt(nodeId, request);
    final AtomicBoolean finished = new AtomicBoolean();
    final MessageClient.OnMessageReceivedListener[] listenerRef =
        new MessageClient.OnMessageReceivedListener[1];
    Runnable completeUnknown =
        () -> {
          if (attempt.onTimeout()) {
            completeHeartRateReadiness(
                HeartRateReadinessStatus.UNKNOWN,
                handler,
                listenerRef[0],
                client,
                finished,
                callback);
          }
        };
    listenerRef[0] =
        event -> {
          HeartRateReadinessAttempt.Result result =
              attempt.onMessage(event.getSourceNodeId(), event.getPath(), event.getData());
          if (result == HeartRateReadinessAttempt.Result.IGNORE) return;
          completeHeartRateReadiness(
              mapReadiness(result), handler, listenerRef[0], client, finished, callback);
        };
    handler.postAtTime(
        completeUnknown,
        listenerRef[0],
        android.os.SystemClock.uptimeMillis() + HEART_RATE_READINESS_TIMEOUT_MS);
    try {
      client
          .addListener(listenerRef[0])
          .addOnSuccessListener(
              ignored -> {
                if (attempt.isCompleted()) return;
                client
                    .sendMessage(nodeId, Constants.Wear.Path.MSG_HR_READINESS_REQUEST, payload)
                    .addOnFailureListener(error -> completeUnknown.run());
              })
          .addOnFailureListener(error -> completeUnknown.run());
    } catch (RuntimeException unavailable) {
      completeUnknown.run();
    }
  }

  private static HeartRateReadinessStatus mapReadiness(HeartRateReadinessAttempt.Result result) {
    switch (result) {
      case READY:
        return HeartRateReadinessStatus.READY;
      case SENSOR_UNAVAILABLE:
        return HeartRateReadinessStatus.SENSOR_UNAVAILABLE;
      case PERMISSION_REQUIRED:
        return HeartRateReadinessStatus.PERMISSION_REQUIRED;
      case BACKGROUND_PERMISSION_REQUIRED:
        return HeartRateReadinessStatus.BACKGROUND_PERMISSION_REQUIRED;
      default:
        return HeartRateReadinessStatus.UNKNOWN;
    }
  }

  private static void completeHeartRateReadiness(
      HeartRateReadinessStatus status,
      Handler handler,
      MessageClient.OnMessageReceivedListener listener,
      MessageClient client,
      AtomicBoolean finished,
      HeartRateReadinessCallback callback) {
    if (!finished.compareAndSet(false, true)) return;
    if (listener != null) {
      handler.removeCallbacksAndMessages(listener);
      client.removeListener(listener);
    }
    handler.post(() -> callback.onResult(status));
  }

  private static String displayName(Node node) {
    String name = node.getDisplayName();
    return name == null || name.trim().isEmpty() ? "Wear OS-klocka" : name;
  }
}
