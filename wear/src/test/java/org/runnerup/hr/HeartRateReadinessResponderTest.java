package org.runnerup.hr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.UUID;
import org.junit.Test;
import org.runnerup.common.wear.HeartRateReadinessProtocol;

public class HeartRateReadinessResponderTest {
  @Test
  public void respondsOnlyForTheCurrentlyPreparedSession() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    byte[] request = HeartRateReadinessProtocol.encodeRequest(sessionId, requestId);

    byte[] response =
        HeartRateReadinessResponder.respond(
            request, sessionId, HeartRateReadinessProtocol.Status.BACKGROUND_PERMISSION_REQUIRED);

    assertNotNull(response);
    HeartRateReadinessProtocol.Response decoded =
        HeartRateReadinessProtocol.decodeResponse(response);
    assertEquals(sessionId, decoded.getSessionId());
    assertEquals(requestId, decoded.getRequestId());
    assertEquals(
        HeartRateReadinessProtocol.Status.BACKGROUND_PERMISSION_REQUIRED, decoded.getStatus());
  }

  @Test
  public void refusesUnpreparedOrMalformedRequests() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    byte[] request =
        HeartRateReadinessProtocol.encodeRequest(sessionId, UUID.randomUUID().toString());

    assertNull(
        HeartRateReadinessResponder.respond(
            request, UUID.randomUUID().toString(), HeartRateReadinessProtocol.Status.READY));
    assertNull(
        HeartRateReadinessResponder.respond(
            new byte[] {1}, sessionId, HeartRateReadinessProtocol.Status.READY));
  }
}
