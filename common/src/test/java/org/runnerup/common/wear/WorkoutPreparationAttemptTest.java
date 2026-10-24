package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;

import java.util.UUID;
import org.junit.Test;

public class WorkoutPreparationAttemptTest {
  @Test
  public void ignoresUnrelatedAcknowledgementsAndCompletesOnceOnMatchingReply() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    WorkoutPreparationAttempt attempt =
        new WorkoutPreparationAttempt("watch-1", sessionId, requestId);
    byte[] accepted = WorkoutPreparationProtocol.encodeAcknowledgement(sessionId, requestId, true);

    assertEquals(
        WorkoutPreparationAttempt.Result.IGNORE,
        attempt.onMessage("watch-2", WorkoutPreparationAttempt.ACK_PATH, accepted));
    assertEquals(
        WorkoutPreparationAttempt.Result.IGNORE,
        attempt.onMessage("watch-1", "/wrong/path", accepted));
    assertEquals(
        WorkoutPreparationAttempt.Result.IGNORE,
        attempt.onMessage(
            "watch-1",
            WorkoutPreparationAttempt.ACK_PATH,
            WorkoutPreparationProtocol.encodeAcknowledgement(
                sessionId, UUID.randomUUID().toString(), true)));
    assertEquals(
        WorkoutPreparationAttempt.Result.PREPARED,
        attempt.onMessage("watch-1", WorkoutPreparationAttempt.ACK_PATH, accepted));
    assertEquals(
        WorkoutPreparationAttempt.Result.IGNORE,
        attempt.onMessage("watch-1", WorkoutPreparationAttempt.ACK_PATH, accepted));
  }

  @Test
  public void matchingNegativeAcknowledgementAndTimeoutRemainDistinct() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    WorkoutPreparationAttempt rejected =
        new WorkoutPreparationAttempt("watch-1", sessionId, requestId);
    assertEquals(
        WorkoutPreparationAttempt.Result.REJECTED,
        rejected.onMessage(
            "watch-1",
            WorkoutPreparationAttempt.ACK_PATH,
            WorkoutPreparationProtocol.encodeAcknowledgement(sessionId, requestId, false)));

    WorkoutPreparationAttempt timedOut =
        new WorkoutPreparationAttempt(
            "watch-1", UUID.randomUUID().toString(), UUID.randomUUID().toString());
    assertEquals(WorkoutPreparationAttempt.Result.TIMEOUT, timedOut.onTimeout());
    assertEquals(WorkoutPreparationAttempt.Result.IGNORE, timedOut.onTimeout());
  }
}
