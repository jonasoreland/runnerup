package org.runnerup.common.wear;

import org.runnerup.common.util.Constants;

/** Accepts only one readiness response from the expected watch for the expected request. */
public final class HeartRateReadinessAttempt {
  public enum Result {
    IGNORE,
    READY,
    SENSOR_UNAVAILABLE,
    PERMISSION_REQUIRED,
    BACKGROUND_PERMISSION_REQUIRED
  }

  private final String expectedNodeId;
  private final HeartRateReadinessProtocol.Request request;
  private boolean completed;

  public HeartRateReadinessAttempt(
      String expectedNodeId, HeartRateReadinessProtocol.Request request) {
    if (expectedNodeId == null || expectedNodeId.isEmpty()) {
      throw new IllegalArgumentException("Expected node is required");
    }
    if (request == null) throw new IllegalArgumentException("Readiness request is required");
    this.expectedNodeId = expectedNodeId;
    this.request = request;
  }

  public synchronized Result onMessage(String sourceNodeId, String path, byte[] payload) {
    if (completed
        || !expectedNodeId.equals(sourceNodeId)
        || !Constants.Wear.Path.MSG_HR_READINESS_RESPONSE.equals(path)) return Result.IGNORE;
    final HeartRateReadinessProtocol.Response response;
    try {
      response = HeartRateReadinessProtocol.decodeResponse(payload);
    } catch (java.io.IOException malformed) {
      return Result.IGNORE;
    }
    if (!response.matches(request)) return Result.IGNORE;
    completed = true;
    switch (response.getStatus()) {
      case READY:
        return Result.READY;
      case SENSOR_UNAVAILABLE:
        return Result.SENSOR_UNAVAILABLE;
      case PERMISSION_REQUIRED:
        return Result.PERMISSION_REQUIRED;
      case BACKGROUND_PERMISSION_REQUIRED:
        return Result.BACKGROUND_PERMISSION_REQUIRED;
      default:
        return Result.IGNORE;
    }
  }

  public synchronized boolean onTimeout() {
    if (completed) return false;
    completed = true;
    return true;
  }

  public synchronized boolean isCompleted() {
    return completed;
  }
}
