package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;
import org.runnerup.common.util.Constants;

public class HeartRateReadinessAttemptTest {
  @Test
  public void ignoresWrongSourceAndWrongRequestThenAcceptsOneMatchingReply() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    HeartRateReadinessProtocol.Request request =
        HeartRateReadinessProtocol.decodeRequest(
            HeartRateReadinessProtocol.encodeRequest(sessionId, requestId));
    HeartRateReadinessAttempt attempt = new HeartRateReadinessAttempt("watch", request);

    assertEquals(
        HeartRateReadinessAttempt.Result.IGNORE,
        attempt.onMessage(
            "other-watch",
            Constants.Wear.Path.MSG_HR_READINESS_RESPONSE,
            HeartRateReadinessProtocol.encodeResponse(
                sessionId, requestId, HeartRateReadinessProtocol.Status.READY)));
    assertEquals(
        HeartRateReadinessAttempt.Result.IGNORE,
        attempt.onMessage(
            "watch",
            Constants.Wear.Path.MSG_SESSION_PONG,
            HeartRateReadinessProtocol.encodeResponse(
                sessionId, requestId, HeartRateReadinessProtocol.Status.READY)));
    assertEquals(
        HeartRateReadinessAttempt.Result.IGNORE,
        attempt.onMessage(
            "watch",
            Constants.Wear.Path.MSG_HR_READINESS_RESPONSE,
            HeartRateReadinessProtocol.encodeResponse(
                sessionId, UUID.randomUUID().toString(), HeartRateReadinessProtocol.Status.READY)));
    assertEquals(
        HeartRateReadinessAttempt.Result.READY,
        attempt.onMessage(
            "watch",
            Constants.Wear.Path.MSG_HR_READINESS_RESPONSE,
            HeartRateReadinessProtocol.encodeResponse(
                sessionId, requestId, HeartRateReadinessProtocol.Status.READY)));
    assertFalse(attempt.onTimeout());
  }

  @Test
  public void timeoutCompletesUnknownAndIgnoresLateReadiness() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    HeartRateReadinessAttempt attempt =
        new HeartRateReadinessAttempt(
            "watch",
            HeartRateReadinessProtocol.decodeRequest(
                HeartRateReadinessProtocol.encodeRequest(sessionId, requestId)));

    assertTrue(attempt.onTimeout());
    assertFalse(attempt.onTimeout());
    assertEquals(
        HeartRateReadinessAttempt.Result.IGNORE,
        attempt.onMessage(
            "watch",
            Constants.Wear.Path.MSG_HR_READINESS_RESPONSE,
            HeartRateReadinessProtocol.encodeResponse(
                sessionId, requestId, HeartRateReadinessProtocol.Status.READY)));
  }
}
