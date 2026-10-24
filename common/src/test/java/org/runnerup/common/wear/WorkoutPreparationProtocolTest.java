package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.UUID;
import org.junit.Test;

public class WorkoutPreparationProtocolTest {
  private static final String WORKOUT_JSON =
      "{\"com.garmin.connect.workout.json.UserWorkoutJson\":{\"workoutSteps\":[]}}";

  @Test
  public void preparePayloadRoundTripsSessionWorkoutAndUpcomingStep() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    String workoutJson =
        "{\"com.garmin.connect.workout.json.UserWorkoutJson\":{\"workoutSteps\":[{\"name\":\"Uppvärmning\"}]}}";

    WorkoutPreparationProtocol.PrepareMessage decoded =
        WorkoutPreparationProtocol.decodePrepare(
            WorkoutPreparationProtocol.encodePrepare(
                sessionId, requestId, "Intervaller", "Uppvärmning", workoutJson));

    assertEquals(sessionId, decoded.getSessionId());
    assertEquals(requestId, decoded.getRequestId());
    assertEquals("Intervaller", decoded.getWorkoutName());
    assertEquals("Uppvärmning", decoded.getUpcomingStep());
    assertEquals(workoutJson, decoded.getWorkoutJson());
    assertFalse(decoded.isSkipSupported());
  }

  @Test
  public void preparedIntervalAdvertisesSkipWithoutChangingBasicWorkoutPayloads() throws Exception {
    WorkoutPreparationProtocol.PrepareMessage decoded =
        WorkoutPreparationProtocol.decodePrepare(
            WorkoutPreparationProtocol.encodePrepare(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "Intervaller",
                "Uppvärmning",
                WORKOUT_JSON,
                true));

    assertTrue(decoded.isSkipSupported());
    assertTrue(decoded.getWorkoutJson().contains("\"relvenSkipSupported\":true"));
  }

  @Test
  public void acknowledgementCorrelatesToSessionAndRequest() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    WorkoutPreparationProtocol.PrepareMessage request =
        WorkoutPreparationProtocol.decodePrepare(
            WorkoutPreparationProtocol.encodePrepare(
                sessionId, requestId, "Löpning", "Uppvärmning", WORKOUT_JSON));
    WorkoutPreparationProtocol.Acknowledgement acknowledgement =
        WorkoutPreparationProtocol.decodeAcknowledgement(
            WorkoutPreparationProtocol.encodeAcknowledgement(sessionId, requestId, true));

    assertTrue(acknowledgement.matches(request));
    assertTrue(acknowledgement.isAccepted());
    assertFalse(
        WorkoutPreparationProtocol.decodeAcknowledgement(
                WorkoutPreparationProtocol.encodeAcknowledgement(
                    UUID.randomUUID().toString(), requestId, true))
            .matches(request));
  }

  @Test
  public void preparePayloadRejectsOversizedWorkoutAndMalformedEnvelope() throws Exception {
    String tooLarge =
        "{\"com.garmin.connect.workout.json.UserWorkoutJson\":{\"workoutSteps\":[{\"data\":\""
            + "x".repeat(WorkoutPreparationProtocol.MAX_WORKOUT_BYTES + 1)
            + "\"}]}}";
    try {
      WorkoutPreparationProtocol.encodePrepare(
          UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Run", "Warmup", tooLarge);
      throw new AssertionError("oversized workout must be rejected");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("large"));
    }

    try {
      WorkoutPreparationProtocol.decodePrepare(new byte[] {1, 2, 3});
      throw new AssertionError("malformed payload must be rejected");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("payload"));
    }
  }

  @Test
  public void preparePayloadRejectsJsonWithoutTheCanonicalWorkoutDocument() throws Exception {
    try {
      WorkoutPreparationProtocol.encodePrepare(
          UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Run", "Warm-up", "{}");
      throw new AssertionError("non-workout JSON must be rejected");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("workout"));
    }
  }

  @Test
  public void duplicateSessionIsIdempotentButConflictingIdentityIsRejected() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    WorkoutPreparationProtocol.PrepareMessage first =
        WorkoutPreparationProtocol.decodePrepare(
            WorkoutPreparationProtocol.encodePrepare(
                sessionId,
                UUID.randomUUID().toString(),
                "Intervaller",
                "Uppvärmning",
                WORKOUT_JSON));
    WorkoutPreparationProtocol.PrepareMessage retry =
        WorkoutPreparationProtocol.decodePrepare(
            WorkoutPreparationProtocol.encodePrepare(
                sessionId,
                UUID.randomUUID().toString(),
                "Intervaller",
                "Uppvärmning",
                WORKOUT_JSON));
    WorkoutPreparationProtocol.PrepareMessage conflict =
        WorkoutPreparationProtocol.decodePrepare(
            WorkoutPreparationProtocol.encodePrepare(
                sessionId, UUID.randomUUID().toString(), "Long run", "Warmup", WORKOUT_JSON));
    WorkoutPreparationProtocol.PrepareMessage replacement =
        WorkoutPreparationProtocol.decodePrepare(
            WorkoutPreparationProtocol.encodePrepare(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "Long run",
                "Warmup",
                WORKOUT_JSON));

    assertEquals(
        WorkoutPreparationProtocol.StorageDecision.DUPLICATE,
        WorkoutPreparationProtocol.assessReplacement(first, retry));
    assertEquals(
        WorkoutPreparationProtocol.StorageDecision.CONFLICT,
        WorkoutPreparationProtocol.assessReplacement(first, conflict));
    assertEquals(
        WorkoutPreparationProtocol.StorageDecision.REPLACE,
        WorkoutPreparationProtocol.assessReplacement(first, replacement));
  }

  @Test
  public void cancellationRoundTripsItsStableSessionIdentity() throws Exception {
    String sessionId = UUID.randomUUID().toString();

    WorkoutPreparationProtocol.Cancellation cancellation =
        WorkoutPreparationProtocol.decodeCancellation(
            WorkoutPreparationProtocol.encodeCancellation(sessionId));

    assertEquals(sessionId, cancellation.getSessionId());
  }
}
