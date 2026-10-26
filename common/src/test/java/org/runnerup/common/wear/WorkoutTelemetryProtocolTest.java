package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class WorkoutTelemetryProtocolTest {
  private static final String SESSION = "telemetry-session-1";

  @Test
  public void roundTripsGpsOnlyHeartRateOnlyAndCombinedSamples() throws Exception {
    WorkoutTelemetryProtocol.Sample gps =
        new WorkoutTelemetryProtocol.Sample(
            SESSION, 1, 100, 1_000, 59.3, 18.0, 6.0, null, null, null);
    WorkoutTelemetryProtocol.Sample heartRate =
        new WorkoutTelemetryProtocol.Sample(
            SESSION, 2, 200, 1_100, null, null, null, null, null, 142.0);
    WorkoutTelemetryProtocol.Sample combined =
        new WorkoutTelemetryProtocol.Sample(
            SESSION, 3, 300, 1_200, 59.4, 18.1, 5.0, 12.0, 2.5, 150.0);

    WorkoutTelemetryProtocol.Batch batch =
        new WorkoutTelemetryProtocol.Batch(SESSION, Arrays.asList(gps, heartRate, combined));
    WorkoutTelemetryProtocol.Batch decoded =
        WorkoutTelemetryProtocol.decodeBatch(WorkoutTelemetryProtocol.encodeBatch(batch));

    assertEquals(batch, decoded);
    assertEquals(
        new WorkoutTelemetryProtocol.Ack(SESSION, 2),
        WorkoutTelemetryProtocol.decodeAck(
            WorkoutTelemetryProtocol.encodeAck(new WorkoutTelemetryProtocol.Ack(SESSION, 2))));
  }

  @Test
  public void rejectsEmptyOversizedAndNonContiguousBatches() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkoutTelemetryProtocol.Batch(SESSION, Collections.emptyList()));
    WorkoutTelemetryProtocol.Sample first = sample(1);
    WorkoutTelemetryProtocol.Sample third = sample(3);
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkoutTelemetryProtocol.Batch(SESSION, Arrays.asList(first, third)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkoutTelemetryProtocol.Batch(SESSION, Collections.nCopies(65, first)));
  }

  @Test
  public void rejectsMalformedVersionPayloadAndInvalidMeasurements() throws Exception {
    byte[] encoded =
        WorkoutTelemetryProtocol.encodeBatch(
            new WorkoutTelemetryProtocol.Batch(SESSION, Collections.singletonList(sample(1))));
    encoded[4] = (byte) 99;
    assertThrows(IOException.class, () -> WorkoutTelemetryProtocol.decodeBatch(encoded));
    assertThrows(IOException.class, () -> WorkoutTelemetryProtocol.decodeBatch(new byte[0]));
    assertThrows(
        IOException.class,
        () ->
            WorkoutTelemetryProtocol.decodeBatch(
                new byte[WorkoutTelemetryProtocol.MAX_PAYLOAD_BYTES + 1]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WorkoutTelemetryProtocol.Sample(
                SESSION, 1, 1, 1, 91.0, 18.0, 5.0, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WorkoutTelemetryProtocol.Sample(
                SESSION, 1, 1, 1, null, null, 5.0, null, null, null));
  }

  @Test
  public void sampleIdentityIncludesSessionAndSequence() {
    assertNotEquals(sample(1), sample(2));
    assertEquals(sample(1), sample(1));
  }

  private static WorkoutTelemetryProtocol.Sample sample(long sequence) {
    return new WorkoutTelemetryProtocol.Sample(
        SESSION, sequence, sequence * 100, sequence * 1_000, 59.3, 18.0, 6.0, null, null, null);
  }
}
