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

  public interface ConnectionTestCallback {
    void onResult(ConnectionTestResult result);
  }

  public interface Callback {
    void onResult(boolean supported, boolean succeeded, List<String> connectedNodes);
  }

  private WearConnectionProvider() {}

  public static void query(Context context, Callback callback) {
    callback.onResult(false, false, null);
  }

  public static void testConnection(Context context, ConnectionTestCallback callback) {
    callback.onResult(new ConnectionTestResult(HandshakeStatus.UNSUPPORTED, null, -1));
  }
}
