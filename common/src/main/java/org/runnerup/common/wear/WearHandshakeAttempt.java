package org.runnerup.common.wear;

import java.util.UUID;
import org.runnerup.common.util.Constants;

/** Correlates one application-level Wear ping and makes its terminal result single-use. */
public final class WearHandshakeAttempt {
  public static final String PONG_PATH = Constants.Wear.Path.MSG_SESSION_PONG;

  public enum Result {
    IGNORE,
    VERIFIED,
    PROTOCOL_MISMATCH,
    TIMEOUT,
    TRANSPORT_ERROR
  }

  private final String expectedNodeId;
  private final String requestId;
  private boolean completed;

  public static String createRequestId() {
    return UUID.randomUUID().toString();
  }

  public WearHandshakeAttempt(String expectedNodeId, String requestId) {
    if (expectedNodeId == null || expectedNodeId.trim().isEmpty()) {
      throw new IllegalArgumentException("Expected node id is required");
    }
    if (requestId == null || requestId.trim().isEmpty()) {
      throw new IllegalArgumentException("Request id is required");
    }
    this.expectedNodeId = expectedNodeId;
    this.requestId = requestId;
  }

  public synchronized Result onMessage(
      String sourceNodeId, String path, WearSessionProtocol.Message message) {
    if (completed
        || !expectedNodeId.equals(sourceNodeId)
        || !PONG_PATH.equals(path)
        || message == null
        || message.getType() != WearSessionProtocol.PONG
        || !requestId.equals(message.getRequestId())) {
      return Result.IGNORE;
    }
    completed = true;
    return message.getRequestedVersion() == WearSessionProtocol.CURRENT_VERSION
            && message.getSupportedVersion() == WearSessionProtocol.CURRENT_VERSION
        ? Result.VERIFIED
        : Result.PROTOCOL_MISMATCH;
  }

  public synchronized Result onTimeout() {
    return complete(Result.TIMEOUT);
  }

  public synchronized Result onTransportError() {
    return complete(Result.TRANSPORT_ERROR);
  }

  public synchronized boolean isCompleted() {
    return completed;
  }

  private Result complete(Result result) {
    if (completed) return Result.IGNORE;
    completed = true;
    return result;
  }
}
