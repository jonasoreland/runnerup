package org.runnerup.hr;

import java.io.IOException;
import org.runnerup.common.wear.HeartRateReadinessProtocol;

/** Builds a readiness response only for the workout currently stored on the watch. */
public final class HeartRateReadinessResponder {
  private HeartRateReadinessResponder() {}

  public static byte[] respond(
      byte[] payload, String preparedSessionId, HeartRateReadinessProtocol.Status status) {
    try {
      HeartRateReadinessProtocol.Request request =
          HeartRateReadinessProtocol.decodeRequest(payload);
      if (preparedSessionId == null || !preparedSessionId.equals(request.getSessionId()))
        return null;
      return HeartRateReadinessProtocol.encodeResponse(
          request.getSessionId(), request.getRequestId(), status);
    } catch (IOException malformed) {
      return null;
    }
  }
}
