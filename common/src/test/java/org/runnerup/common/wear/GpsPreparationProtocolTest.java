package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.UUID;
import org.junit.Test;

public class GpsPreparationProtocolTest {
  @Test
  public void everyReadinessStateRoundTripsForItsSession() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    for (GpsPreparationProtocol.Status status : GpsPreparationProtocol.Status.values()) {
      byte[] encoded = GpsPreparationProtocol.encode(sessionId, 7L, status, null);

      GpsPreparationProtocol.Readiness decoded = GpsPreparationProtocol.decode(encoded);

      assertEquals(sessionId, decoded.getSessionId());
      assertEquals(7L, decoded.getRevision());
      assertEquals(status, decoded.getStatus());
      assertNull(decoded.getAccuracyMeters());
      assertEquals(
          GpsPreparationProtocol.dataItemPath(sessionId),
          GpsPreparationProtocol.dataItemPath(decoded.getSessionId()));
    }
  }

  @Test
  public void optionalMeasuredAccuracyRoundTripsWithoutInventingValues() throws Exception {
    GpsPreparationProtocol.Readiness decoded =
        GpsPreparationProtocol.decode(
            GpsPreparationProtocol.encode(
                UUID.randomUUID().toString(), 1L, GpsPreparationProtocol.Status.READY, 8.5f));

    assertEquals(Float.valueOf(8.5f), decoded.getAccuracyMeters());
  }

  @Test
  public void rejectsInvalidSessionRevisionAndAccuracy() {
    assertThrows(
        IOException.class,
        () ->
            GpsPreparationProtocol.encode(
                "not-a-uuid", 1L, GpsPreparationProtocol.Status.SEARCHING, null));
    assertThrows(
        IOException.class,
        () ->
            GpsPreparationProtocol.encode(
                UUID.randomUUID().toString(), 0L, GpsPreparationProtocol.Status.SEARCHING, null));
    assertThrows(
        IOException.class,
        () ->
            GpsPreparationProtocol.encode(
                UUID.randomUUID().toString(), 1L, GpsPreparationProtocol.Status.READY, Float.NaN));
    assertThrows(
        IOException.class,
        () ->
            GpsPreparationProtocol.encode(
                UUID.randomUUID().toString(),
                1L,
                GpsPreparationProtocol.Status.READY,
                Float.POSITIVE_INFINITY));
  }

  @Test
  public void rejectsMalformedPayloadAndSessionSpecificPathsAreDistinct() {
    assertThrows(IOException.class, () -> GpsPreparationProtocol.decode(new byte[] {1, 2, 3}));

    String first = UUID.randomUUID().toString();
    String second = UUID.randomUUID().toString();
    assertFalse(
        GpsPreparationProtocol.dataItemPath(first)
            .equals(GpsPreparationProtocol.dataItemPath(second)));
    assertEquals(
        first,
        GpsPreparationProtocol.sessionIdFromDataItemPath(
            GpsPreparationProtocol.dataItemPath(first)));
    assertNull(
        GpsPreparationProtocol.sessionIdFromDataItemPath("/org.runnerup/gps/readiness/nope"));
  }

  @Test
  public void rejectsUnknownWireStatus() throws Exception {
    byte[] payload =
        GpsPreparationProtocol.encode(
            UUID.randomUUID().toString(), 1L, GpsPreparationProtocol.Status.READY, null);
    payload[51] = (byte) 100;

    assertThrows(IOException.class, () -> GpsPreparationProtocol.decode(payload));
  }
}
