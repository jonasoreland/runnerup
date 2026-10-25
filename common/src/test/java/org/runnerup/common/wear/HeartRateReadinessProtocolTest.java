package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

public class HeartRateReadinessProtocolTest {
  @Test
  public void responseRoundTripsAndCorrelatesToSessionAndRequest() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    HeartRateReadinessProtocol.Request request =
        HeartRateReadinessProtocol.decodeRequest(
            HeartRateReadinessProtocol.encodeRequest(sessionId, requestId));
    HeartRateReadinessProtocol.Response response =
        HeartRateReadinessProtocol.decodeResponse(
            HeartRateReadinessProtocol.encodeResponse(
                sessionId, requestId, HeartRateReadinessProtocol.Status.READY));

    assertEquals(sessionId, request.getSessionId());
    assertEquals(requestId, request.getRequestId());
    assertTrue(response.matches(request));
    assertEquals(HeartRateReadinessProtocol.Status.READY, response.getStatus());
    assertFalse(
        response.matches(
            HeartRateReadinessProtocol.decodeRequest(
                HeartRateReadinessProtocol.encodeRequest(
                    sessionId, UUID.randomUUID().toString()))));
  }

  @Test
  public void everySensorPermissionStateRoundTripsWithoutCollapsingUnknowns() throws Exception {
    for (HeartRateReadinessProtocol.Status status : HeartRateReadinessProtocol.Status.values()) {
      assertEquals(
          status,
          HeartRateReadinessProtocol.decodeResponse(
                  HeartRateReadinessProtocol.encodeResponse(
                      UUID.randomUUID().toString(), UUID.randomUUID().toString(), status))
              .getStatus());
    }
  }

  @Test
  public void responseRejectsUnsupportedProtocolAndStatusCodes() throws Exception {
    byte[] payload =
        HeartRateReadinessProtocol.encodeResponse(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            HeartRateReadinessProtocol.Status.READY);
    byte[] badVersion = payload.clone();
    badVersion[4] = 2;
    assertRejected(badVersion);

    byte[] badStatus = payload.clone();
    badStatus[badStatus.length - 1] = 99;
    assertRejected(badStatus);
  }

  private static void assertRejected(byte[] payload) throws Exception {
    try {
      HeartRateReadinessProtocol.decodeResponse(payload);
      throw new AssertionError("invalid readiness response must be rejected");
    } catch (java.io.IOException expected) {
      assertTrue(expected.getMessage().contains("readiness"));
    }
  }
}
