package org.runnerup.relven.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.runnerup.common.util.Constants;
import org.runnerup.common.wear.WearSessionProtocol;

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

    private ConnectionTestResult(
        HandshakeStatus status, List<String> connectedNodes, long roundTripTimeMillis) {
      this.status = status;
      this.connectedNodes = connectedNodes;
      this.roundTripTimeMillis = roundTripTimeMillis;
    }
  }

  public interface ConnectionTestCallback {
    void onResult(ConnectionTestResult result);
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
                      new ConnectionTestResult(HandshakeStatus.TRANSPORT_ERROR, null, -1));
                  return;
                }
                List<Node> nodes = task.getResult();
                List<String> names = new ArrayList<>();
                for (Node node : nodes) names.add(displayName(node));
                if (nodes.isEmpty()) {
                  callback.onResult(new ConnectionTestResult(HandshakeStatus.NO_WATCH, names, -1));
                  return;
                }
                pingNode(appContext, nodes.get(0), names, callback);
              });
    } catch (RuntimeException exception) {
      callback.onResult(new ConnectionTestResult(HandshakeStatus.TRANSPORT_ERROR, null, -1));
    }
  }

  private static void pingNode(
      Context context, Node node, List<String> names, ConnectionTestCallback callback) {
    final String requestId = UUID.randomUUID().toString();
    final AtomicLong sentAt = new AtomicLong(-1);
    final MessageClient client = Wearable.getMessageClient(context);
    final Handler handler = new Handler(Looper.getMainLooper());
    final AtomicBoolean completed = new AtomicBoolean(false);
    final MessageClient.OnMessageReceivedListener[] listenerRef =
        new MessageClient.OnMessageReceivedListener[1];
    listenerRef[0] =
        event -> {
          if (!node.getId().equals(event.getSourceNodeId())
              || !Constants.Wear.Path.MSG_SESSION_PONG.equals(event.getPath())) return;
          try {
            WearSessionProtocol.Message pong = WearSessionProtocol.decode(event.getData());
            if (pong.getType() != WearSessionProtocol.PONG
                || !requestId.equals(pong.getRequestId())) return;
            HandshakeStatus status =
                pong.getRequestedVersion() == WearSessionProtocol.CURRENT_VERSION
                        && pong.getSupportedVersion() == WearSessionProtocol.CURRENT_VERSION
                    ? HandshakeStatus.VERIFIED
                    : HandshakeStatus.PROTOCOL_MISMATCH;
            if (!completed.compareAndSet(false, true)) return;
            handler.removeCallbacksAndMessages(listenerRef[0]);
            client.removeListener(listenerRef[0]);
            callback.onResult(
                new ConnectionTestResult(
                    status, names, android.os.SystemClock.elapsedRealtime() - sentAt.get()));
          } catch (java.io.IOException malformedResponse) {
            // Ignore malformed or unrelated payloads and let the bounded timeout report failure.
          }
        };
    Runnable timeout =
        () -> {
          if (!completed.compareAndSet(false, true)) return;
          client.removeListener(listenerRef[0]);
          callback.onResult(new ConnectionTestResult(HandshakeStatus.TIMEOUT, names, -1));
        };
    handler.postAtTime(
        timeout, listenerRef[0], android.os.SystemClock.uptimeMillis() + HANDSHAKE_TIMEOUT_MS);
    client
        .addListener(listenerRef[0])
        .addOnSuccessListener(
            ignored -> {
              if (completed.get()) return;
              try {
                byte[] request =
                    WearSessionProtocol.encodePing(requestId, WearSessionProtocol.CURRENT_VERSION);
                sentAt.set(android.os.SystemClock.elapsedRealtime());
                client
                    .sendMessage(node.getId(), Constants.Wear.Path.MSG_SESSION_PING, request)
                    .addOnFailureListener(
                        error -> {
                          if (!completed.compareAndSet(false, true)) return;
                          handler.removeCallbacksAndMessages(listenerRef[0]);
                          client.removeListener(listenerRef[0]);
                          callback.onResult(
                              new ConnectionTestResult(HandshakeStatus.TRANSPORT_ERROR, names, -1));
                        });
              } catch (java.io.IOException error) {
                if (!completed.compareAndSet(false, true)) return;
                handler.removeCallbacksAndMessages(listenerRef[0]);
                client.removeListener(listenerRef[0]);
                callback.onResult(
                    new ConnectionTestResult(HandshakeStatus.TRANSPORT_ERROR, names, -1));
              }
            })
        .addOnFailureListener(
            error -> {
              if (!completed.compareAndSet(false, true)) return;
              handler.removeCallbacksAndMessages(listenerRef[0]);
              client.removeListener(listenerRef[0]);
              callback.onResult(
                  new ConnectionTestResult(HandshakeStatus.TRANSPORT_ERROR, names, -1));
            });
  }

  private static String displayName(Node node) {
    String name = node.getDisplayName();
    return name == null || name.trim().isEmpty() ? "Wear OS-klocka" : name;
  }
}
