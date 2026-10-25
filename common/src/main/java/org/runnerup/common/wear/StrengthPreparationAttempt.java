package org.runnerup.common.wear;

import java.io.IOException;
import java.util.UUID;
import org.runnerup.common.util.Constants;

/** Correlates one strength preparation response to its verified Wear node and request. */
public final class StrengthPreparationAttempt {
  public static final String ACK_PATH = Constants.Wear.Path.MSG_STRENGTH_PREPARED;

  public enum Result {
    IGNORE,
    PREPARED,
    REJECTED,
    TIMEOUT,
    TRANSPORT_ERROR
  }

  private final String expectedNodeId;
  private final String expectedSessionId;
  private final String expectedRequestId;
  private boolean completed;

  public StrengthPreparationAttempt(String expectedNodeId, String sessionId, String requestId) {
    if (expectedNodeId == null || expectedNodeId.trim().isEmpty()) {
      throw new IllegalArgumentException("Expected node id is required");
    }
    UUID.fromString(sessionId);
    UUID.fromString(requestId);
    this.expectedNodeId = expectedNodeId;
    this.expectedSessionId = sessionId;
    this.expectedRequestId = requestId;
  }

  public synchronized Result onMessage(String sourceNodeId, String path, byte[] payload) {
    if (completed || !expectedNodeId.equals(sourceNodeId) || !ACK_PATH.equals(path)) {
      return Result.IGNORE;
    }
    try {
      StrengthExecutionProtocol.Acknowledgement ack =
          StrengthExecutionProtocol.decodeAcknowledgement(payload);
      if (!expectedSessionId.equals(ack.getSessionId())
          || !expectedRequestId.equals(ack.getRequestId())) return Result.IGNORE;
      completed = true;
      return ack.isAccepted() ? Result.PREPARED : Result.REJECTED;
    } catch (IOException malformed) {
      return Result.IGNORE;
    }
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
