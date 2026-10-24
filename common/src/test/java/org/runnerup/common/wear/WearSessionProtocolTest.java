package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.UUID;
import org.junit.Test;

public class WearSessionProtocolTest {
  @Test
  public void handshakeAttemptIgnoresWrongSourcePathAndRequestThenVerifiesOnce() throws Exception {
    String requestId = WearHandshakeAttempt.createRequestId();
    WearHandshakeAttempt attempt = new WearHandshakeAttempt("watch-1", requestId);
    WearSessionProtocol.Message pong =
        WearSessionProtocol.decode(
            WearSessionProtocol.encodePong(requestId, 1, WearSessionProtocol.CURRENT_VERSION));

    assertEquals(
        WearHandshakeAttempt.Result.IGNORE,
        attempt.onMessage("watch-2", WearHandshakeAttempt.PONG_PATH, pong));
    assertEquals(
        WearHandshakeAttempt.Result.IGNORE, attempt.onMessage("watch-1", "/wrong/path", pong));
    assertEquals(
        WearHandshakeAttempt.Result.IGNORE,
        attempt.onMessage(
            "watch-1",
            WearHandshakeAttempt.PONG_PATH,
            WearSessionProtocol.decode(WearSessionProtocol.encodePong("other", 1, 1))));
    assertEquals(
        WearHandshakeAttempt.Result.VERIFIED,
        attempt.onMessage("watch-1", WearHandshakeAttempt.PONG_PATH, pong));
    assertEquals(
        WearHandshakeAttempt.Result.IGNORE,
        attempt.onMessage("watch-1", WearHandshakeAttempt.PONG_PATH, pong));
    assertEquals(WearHandshakeAttempt.Result.IGNORE, attempt.onTimeout());
  }

  @Test
  public void timeoutCompletesAnAttemptOnceAndRejectsLatePong() throws Exception {
    String requestId = WearHandshakeAttempt.createRequestId();
    WearHandshakeAttempt attempt = new WearHandshakeAttempt("watch-1", requestId);
    WearSessionProtocol.Message latePong =
        WearSessionProtocol.decode(
            WearSessionProtocol.encodePong(requestId, 1, WearSessionProtocol.CURRENT_VERSION));

    assertEquals(WearHandshakeAttempt.Result.TIMEOUT, attempt.onTimeout());
    assertEquals(WearHandshakeAttempt.Result.IGNORE, attempt.onTimeout());
    assertEquals(
        WearHandshakeAttempt.Result.IGNORE,
        attempt.onMessage("watch-1", WearHandshakeAttempt.PONG_PATH, latePong));
  }

  @Test
  public void matchingRequestWithDifferentProtocolVersionCompletesAsMismatch() throws Exception {
    String requestId = WearHandshakeAttempt.createRequestId();
    WearHandshakeAttempt attempt = new WearHandshakeAttempt("watch-1", requestId);
    WearSessionProtocol.Message mismatch =
        WearSessionProtocol.decode(WearSessionProtocol.encodePong(requestId, 2, 1));

    assertEquals(
        WearHandshakeAttempt.Result.PROTOCOL_MISMATCH,
        attempt.onMessage("watch-1", WearHandshakeAttempt.PONG_PATH, mismatch));
  }

  @Test
  public void eachHandshakeAttemptGetsAUniqueUuidRequestId() {
    String first = WearHandshakeAttempt.createRequestId();
    String second = WearHandshakeAttempt.createRequestId();

    assertNotEquals(first, second);
    assertEquals(UUID.fromString(first).toString(), first);
    assertEquals(UUID.fromString(second).toString(), second);
  }

  @Test
  public void pingAndPongRoundTripAndCorrelateByRequestId() throws Exception {
    WearSessionProtocol.Message ping =
        WearSessionProtocol.decode(
            WearSessionProtocol.encodePing("request-123", WearSessionProtocol.CURRENT_VERSION));
    WearSessionProtocol.Message pong =
        WearSessionProtocol.decode(
            WearSessionProtocol.encodePong(
                "request-123",
                WearSessionProtocol.CURRENT_VERSION,
                WearSessionProtocol.CURRENT_VERSION));

    assertEquals(WearSessionProtocol.PING, ping.getType());
    assertEquals("request-123", ping.getRequestId());
    assertTrue(WearSessionProtocol.isSuccessfulHandshake(ping, pong));
  }

  @Test
  public void pongForDifferentRequestCannotCompleteHandshake() throws Exception {
    WearSessionProtocol.Message ping =
        WearSessionProtocol.decode(WearSessionProtocol.encodePing("request-a", 1));
    WearSessionProtocol.Message pong =
        WearSessionProtocol.decode(WearSessionProtocol.encodePong("request-b", 1, 1));

    assertFalse(WearSessionProtocol.isSuccessfulHandshake(ping, pong));
  }

  @Test
  public void incompatibleProtocolVersionDoesNotCompleteHandshake() throws Exception {
    WearSessionProtocol.Message ping =
        WearSessionProtocol.decode(WearSessionProtocol.encodePing("request-a", 2));
    WearSessionProtocol.Message pong =
        WearSessionProtocol.decode(WearSessionProtocol.encodePong("request-a", 2, 1));

    assertFalse(WearSessionProtocol.isSuccessfulHandshake(ping, pong));
    assertEquals(1, pong.getSupportedVersion());
  }

  @Test
  public void watchCanAcknowledgeSupportedAndIncompatibleRequests() throws Exception {
    WearSessionProtocol.Message currentRequest =
        WearSessionProtocol.decode(WearSessionProtocol.encodePing("current", 1));
    WearSessionProtocol.Message response =
        WearSessionProtocol.decode(
            WearSessionProtocol.respondToPing(WearSessionProtocol.encodePing("current", 1)));
    assertTrue(WearSessionProtocol.isSuccessfulHandshake(currentRequest, response));

    WearSessionProtocol.Message futureRequest =
        WearSessionProtocol.decode(WearSessionProtocol.encodePing("future", 2));
    WearSessionProtocol.Message compatibilityResponse =
        WearSessionProtocol.decode(
            WearSessionProtocol.respondToPing(WearSessionProtocol.encodePing("future", 2)));
    assertFalse(WearSessionProtocol.isSuccessfulHandshake(futureRequest, compatibilityResponse));
    assertEquals(2, compatibilityResponse.getRequestedVersion());
    assertEquals(WearSessionProtocol.CURRENT_VERSION, compatibilityResponse.getSupportedVersion());
  }

  @Test
  public void decoderRejectsUnknownVersionAndTrailingData() throws Exception {
    try {
      WearSessionProtocol.encodePing("request-a", 0);
      fail("unsupported protocol version must be rejected");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("version"));
    }

    byte[] valid = WearSessionProtocol.encodePing("request-a", 1);
    byte[] trailing = new byte[valid.length + 1];
    System.arraycopy(valid, 0, trailing, 0, valid.length);
    try {
      WearSessionProtocol.decode(trailing);
      fail("trailing payload data must be rejected");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("payload"));
    }
  }
}
