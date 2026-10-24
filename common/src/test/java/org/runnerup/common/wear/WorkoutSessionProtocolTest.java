package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.UUID;
import org.junit.Test;

public class WorkoutSessionProtocolTest {
  @Test
  public void commandRoundTripPreservesIdentitySequenceActionAndMonotonicDeadline()
      throws Exception {
    WorkoutSessionProtocol.Command command =
        new WorkoutSessionProtocol.Command(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            4,
            WorkoutSessionProtocol.Actor.WATCH,
            WorkoutSessionProtocol.Action.START,
            10_000,
            13_000);

    WorkoutSessionProtocol.Command decoded =
        WorkoutSessionProtocol.decodeCommand(WorkoutSessionProtocol.encodeCommand(command));

    assertEquals(command.getSessionId(), decoded.getSessionId());
    assertEquals(command.getCommandId(), decoded.getCommandId());
    assertEquals(command.getSequence(), decoded.getSequence());
    assertEquals(command.getActor(), decoded.getActor());
    assertEquals(command.getAction(), decoded.getAction());
    assertEquals(command.getIssuedAtElapsedRealtimeMs(), decoded.getIssuedAtElapsedRealtimeMs());
    assertEquals(
        command.getPlannedStartElapsedRealtimeMs(), decoded.getPlannedStartElapsedRealtimeMs());
  }

  @Test
  public void delayedStartProjectionIsRejectedAfterItsMonotonicDeadline() {
    String sessionId = UUID.randomUUID().toString();
    WorkoutSessionProtocol.Command start =
        new WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            1L,
            WorkoutSessionProtocol.Actor.WATCH,
            WorkoutSessionProtocol.Action.START,
            10_000L,
            13_000L);

    assertTrue(WorkoutSessionProtocol.isStartDeadlineInFuture(start, 12_999L));
    assertFalse(WorkoutSessionProtocol.isStartDeadlineInFuture(start, 13_000L));
    assertFalse(WorkoutSessionProtocol.isStartDeadlineInFuture(start, 13_001L));
  }

  @Test
  public void clockProjectionPreservesWatchAuthorityActionAndSequence() {
    String sessionId = UUID.randomUUID().toString();
    for (WorkoutSessionProtocol.Action action : WorkoutSessionProtocol.Action.values()) {
      WorkoutSessionProtocol.Command accepted =
          new WorkoutSessionProtocol.Command(
              sessionId,
              UUID.randomUUID().toString(),
              9,
              WorkoutSessionProtocol.Actor.WATCH,
              action,
              10_000,
              action == WorkoutSessionProtocol.Action.START ? 13_000 : 0);

      WorkoutSessionProtocol.Command projection =
          WorkoutSessionProtocol.rebaseCommandClock(accepted, 20_000, 23_000);

      assertEquals(WorkoutSessionProtocol.Actor.WATCH, projection.getActor());
      assertEquals(action, projection.getAction());
      assertEquals(accepted.getSequence(), projection.getSequence());
      assertEquals(accepted.getCommandId(), projection.getCommandId());
      assertEquals(20_000, projection.getIssuedAtElapsedRealtimeMs());
      assertEquals(23_000, projection.getPlannedStartElapsedRealtimeMs());
    }
  }

  @Test
  public void watchCanAcceptPhoneRequestsButPhoneCanOnlyApplyWatchProjections() {
    String sessionId = UUID.randomUUID().toString();
    WorkoutSessionProtocol.SessionState watchState =
        WorkoutSessionProtocol.SessionState.ready(sessionId, WorkoutSessionProtocol.Actor.WATCH);
    WorkoutSessionProtocol.Command phoneRequest =
        new WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            1,
            WorkoutSessionProtocol.Actor.PHONE,
            WorkoutSessionProtocol.Action.START,
            1_000,
            4_000);

    WorkoutSessionProtocol.Transition accepted =
        WorkoutSessionProtocol.applyAtAuthority(
            watchState, WorkoutSessionProtocol.Actor.WATCH, phoneRequest);
    assertEquals(WorkoutSessionProtocol.Result.ACCEPTED, accepted.getResult());
    assertEquals(WorkoutSessionProtocol.Actor.WATCH, accepted.getState().getAuthority());
    assertEquals(
        WorkoutSessionProtocol.Result.REJECTED,
        WorkoutSessionProtocol.applyAtAuthority(
                watchState, WorkoutSessionProtocol.Actor.PHONE, phoneRequest)
            .getResult());

    WorkoutSessionProtocol.Command acceptedWatchEvent =
        new WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            2,
            WorkoutSessionProtocol.Actor.WATCH,
            WorkoutSessionProtocol.Action.PAUSE,
            4_100,
            0);
    WorkoutSessionProtocol.SessionState activeState =
        WorkoutSessionProtocol.advance(accepted.getState(), 4_000).getState();
    assertEquals(
        WorkoutSessionProtocol.Result.ACCEPTED,
        WorkoutSessionProtocol.applyProjection(activeState, acceptedWatchEvent).getResult());
    assertEquals(
        WorkoutSessionProtocol.Result.REJECTED,
        WorkoutSessionProtocol.applyProjection(
                activeState,
                new WorkoutSessionProtocol.Command(
                    sessionId,
                    UUID.randomUUID().toString(),
                    2,
                    WorkoutSessionProtocol.Actor.PHONE,
                    WorkoutSessionProtocol.Action.PAUSE,
                    4_100,
                    0))
            .getResult());
  }

  @Test
  public void rejectsMalformedIdsEnumsAndTrailingData() throws Exception {
    try {
      new WorkoutSessionProtocol.Command(
          "bad",
          UUID.randomUUID().toString(),
          1,
          WorkoutSessionProtocol.Actor.PHONE,
          WorkoutSessionProtocol.Action.START,
          1,
          3001);
      throw new AssertionError("invalid session ID should be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("session"));
    }
    byte[] valid =
        WorkoutSessionProtocol.encodeCommand(
            new WorkoutSessionProtocol.Command(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                1,
                WorkoutSessionProtocol.Actor.PHONE,
                WorkoutSessionProtocol.Action.START,
                100,
                3100));
    byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
    try {
      WorkoutSessionProtocol.decodeCommand(trailing);
      throw new AssertionError("trailing bytes should be rejected");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("Trailing"));
    }
  }

  @Test
  public void clockOffsetUsesFourTimestampEstimateAndRejectsSlowOrInvalidSamples() {
    WorkoutSessionProtocol.ClockOffset offset =
        WorkoutSessionProtocol.estimateClockOffset(1000, 1510, 1520, 1030, 250);

    assertTrue(offset.isValid());
    assertEquals(500, offset.getWatchMinusPhoneOffsetMs());
    assertEquals(20, offset.getRoundTripTimeMs());
    assertEquals(2000, offset.toWatchElapsedRealtime(1500));
    assertEquals(1500, offset.toPhoneElapsedRealtime(2000));

    WorkoutSessionProtocol.ClockOffset watchInitiatedOffset =
        WorkoutSessionProtocol.estimateClockOffset(500, 1000, 1000, 500, 250);
    assertTrue(watchInitiatedOffset.isValid());
    assertEquals(500, watchInitiatedOffset.getWatchMinusPhoneOffsetMs());

    assertFalse(WorkoutSessionProtocol.estimateClockOffset(1000, 1510, 1520, 1030, 10).isValid());
    assertFalse(WorkoutSessionProtocol.estimateClockOffset(1000, 1500, 1520, 1400, 250).isValid());
  }

  @Test
  public void sessionReducerMakesStartIdempotentAndRejectsStaleOrIllegalCommands() {
    String sessionId = UUID.randomUUID().toString();
    WorkoutSessionProtocol.SessionState ready =
        WorkoutSessionProtocol.SessionState.ready(sessionId, WorkoutSessionProtocol.Actor.WATCH);
    WorkoutSessionProtocol.Command firstStart =
        command(sessionId, 1, WorkoutSessionProtocol.Action.START, 1000, 4000);
    WorkoutSessionProtocol.Transition started = WorkoutSessionProtocol.apply(ready, firstStart);

    assertEquals(WorkoutSessionProtocol.Result.ACCEPTED, started.getResult());
    assertEquals(WorkoutSessionProtocol.Phase.COUNTDOWN, started.getState().getPhase());
    assertEquals(
        WorkoutSessionProtocol.Result.DUPLICATE,
        WorkoutSessionProtocol.apply(started.getState(), firstStart).getResult());
    assertEquals(
        WorkoutSessionProtocol.Result.REJECTED,
        WorkoutSessionProtocol.apply(
                started.getState(),
                command(sessionId, 2, WorkoutSessionProtocol.Action.START, 1100, 4100))
            .getResult());
    assertEquals(
        WorkoutSessionProtocol.Phase.COUNTDOWN,
        WorkoutSessionProtocol.advance(started.getState(), 3999).getState().getPhase());
    assertEquals(
        WorkoutSessionProtocol.Phase.ACTIVE,
        WorkoutSessionProtocol.advance(started.getState(), 4000).getState().getPhase());
  }

  @Test
  public void cancelReturnsCountdownToReadyWithoutAcceptingASecondStartDuringCountdown() {
    String sessionId = UUID.randomUUID().toString();
    WorkoutSessionProtocol.SessionState ready =
        WorkoutSessionProtocol.SessionState.ready(sessionId, WorkoutSessionProtocol.Actor.PHONE);
    WorkoutSessionProtocol.Command start =
        command(sessionId, 1, WorkoutSessionProtocol.Action.START, 1000, 4000);
    WorkoutSessionProtocol.SessionState countdown =
        WorkoutSessionProtocol.apply(ready, start).getState();
    assertEquals(
        WorkoutSessionProtocol.Result.REJECTED,
        WorkoutSessionProtocol.apply(
                countdown, command(sessionId, 2, WorkoutSessionProtocol.Action.START, 1100, 4100))
            .getResult());
    WorkoutSessionProtocol.Transition cancelled =
        WorkoutSessionProtocol.apply(
            countdown, command(sessionId, 2, WorkoutSessionProtocol.Action.CANCEL, 2000, 0));
    assertEquals(WorkoutSessionProtocol.Result.ACCEPTED, cancelled.getResult());
    assertEquals(WorkoutSessionProtocol.Phase.READY, cancelled.getState().getPhase());
    assertEquals(2L, cancelled.getState().getLastSequence());
    assertEquals(
        WorkoutSessionProtocol.Result.REJECTED,
        WorkoutSessionProtocol.apply(
                cancelled.getState(),
                command(sessionId, 1, WorkoutSessionProtocol.Action.START, 3000, 6000))
            .getResult());
  }

  @Test
  public void onlyTheConfiguredAuthorityCanCommitEitherDevicesStartRequest() {
    String sessionId = UUID.randomUUID().toString();
    WorkoutSessionProtocol.SessionState ready =
        WorkoutSessionProtocol.SessionState.ready(sessionId, WorkoutSessionProtocol.Actor.PHONE);
    WorkoutSessionProtocol.Command watchRequest =
        command(sessionId, 1, WorkoutSessionProtocol.Action.START, 1000, 4000);

    assertEquals(
        WorkoutSessionProtocol.Result.REJECTED,
        WorkoutSessionProtocol.applyAtAuthority(
                ready, WorkoutSessionProtocol.Actor.WATCH, watchRequest)
            .getResult());
    WorkoutSessionProtocol.Transition accepted =
        WorkoutSessionProtocol.applyAtAuthority(
            ready, WorkoutSessionProtocol.Actor.PHONE, watchRequest);
    assertEquals(WorkoutSessionProtocol.Result.ACCEPTED, accepted.getResult());
    assertEquals(WorkoutSessionProtocol.Actor.PHONE, accepted.getState().getAuthority());
    assertEquals(WorkoutSessionProtocol.Actor.WATCH, watchRequest.getActor());
    WorkoutSessionProtocol.Command simultaneousPhoneRequest =
        new WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            1,
            WorkoutSessionProtocol.Actor.PHONE,
            WorkoutSessionProtocol.Action.START,
            1001,
            4001);
    WorkoutSessionProtocol.Transition winnerRemainsScheduled =
        WorkoutSessionProtocol.applyAtAuthority(
            accepted.getState(), WorkoutSessionProtocol.Actor.PHONE, simultaneousPhoneRequest);
    assertEquals(WorkoutSessionProtocol.Result.REJECTED, winnerRemainsScheduled.getResult());
    assertEquals(4000L, winnerRemainsScheduled.getState().getPlannedStartElapsedRealtimeMs());
    assertEquals(
        WorkoutSessionProtocol.Result.REJECTED,
        WorkoutSessionProtocol.applyAtAuthority(
                ready,
                WorkoutSessionProtocol.Actor.PHONE,
                command(
                    UUID.randomUUID().toString(),
                    1,
                    WorkoutSessionProtocol.Action.START,
                    1000,
                    4000))
            .getResult());
    assertEquals(
        WorkoutSessionProtocol.Result.ACCEPTED,
        WorkoutSessionProtocol.applyAtAuthority(
                ready,
                WorkoutSessionProtocol.Actor.PHONE,
                command(sessionId, 2, WorkoutSessionProtocol.Action.START, 1000, 4000))
            .getResult());
  }

  @Test
  public void sessionReducerAppliesPauseResumeLapSkipAndFinishInSequence() {
    String sessionId = UUID.randomUUID().toString();
    WorkoutSessionProtocol.SessionState ready =
        WorkoutSessionProtocol.SessionState.ready(sessionId, WorkoutSessionProtocol.Actor.PHONE);
    WorkoutSessionProtocol.SessionState active =
        WorkoutSessionProtocol.advance(
                WorkoutSessionProtocol.apply(
                        ready,
                        command(sessionId, 1, WorkoutSessionProtocol.Action.START, 1000, 4000))
                    .getState(),
                4000)
            .getState();
    assertEquals(
        WorkoutSessionProtocol.Result.REJECTED,
        WorkoutSessionProtocol.apply(
                active, command(sessionId, 3, WorkoutSessionProtocol.Action.LAP, 4050, 0))
            .getResult());
    active =
        WorkoutSessionProtocol.apply(
                active, command(sessionId, 2, WorkoutSessionProtocol.Action.PAUSE, 4100, 0))
            .getState();
    assertEquals(WorkoutSessionProtocol.Phase.PAUSED, active.getPhase());
    active =
        WorkoutSessionProtocol.apply(
                active, command(sessionId, 3, WorkoutSessionProtocol.Action.RESUME, 5000, 0))
            .getState();
    assertEquals(WorkoutSessionProtocol.Phase.ACTIVE, active.getPhase());
    active =
        WorkoutSessionProtocol.apply(
                active, command(sessionId, 4, WorkoutSessionProtocol.Action.LAP, 5100, 0))
            .getState();
    active =
        WorkoutSessionProtocol.apply(
                active, command(sessionId, 5, WorkoutSessionProtocol.Action.SKIP, 5200, 0))
            .getState();
    WorkoutSessionProtocol.SessionState completed =
        WorkoutSessionProtocol.apply(
                active, command(sessionId, 6, WorkoutSessionProtocol.Action.FINISH, 5300, 0))
            .getState();
    assertEquals(WorkoutSessionProtocol.Phase.COMPLETED, completed.getPhase());
    assertEquals(
        WorkoutSessionProtocol.Result.REJECTED,
        WorkoutSessionProtocol.apply(
                completed, command(sessionId, 6, WorkoutSessionProtocol.Action.PAUSE, 5400, 0))
            .getResult());
  }

  @Test
  public void wearClockProbeCorrelatesThePhoneReceiveAndSendTimes() throws Exception {
    String requestId = UUID.randomUUID().toString();
    byte[] requestBytes = WearClockSyncProtocol.encodeRequest(requestId, 1_000);
    WearClockSyncProtocol.Request request = WearClockSyncProtocol.decodeRequest(requestBytes);
    assertEquals(requestId, request.getRequestId());
    assertEquals(1_000L, request.getWatchSentAtElapsedRealtimeMs());

    WearClockSyncProtocol.Reply reply =
        WearClockSyncProtocol.decodeReply(
            WearClockSyncProtocol.encodeReply(requestId, 1_000, 510, 520));
    assertEquals(requestId, reply.getRequestId());
    assertEquals(510L, reply.getPhoneReceivedAtElapsedRealtimeMs());
    assertEquals(520L, reply.getPhoneSentAtElapsedRealtimeMs());
    WorkoutSessionProtocol.ClockOffset offset =
        WorkoutSessionProtocol.estimateWatchInitiatedClockOffset(
            reply.getWatchSentAtElapsedRealtimeMs(),
            reply.getPhoneReceivedAtElapsedRealtimeMs(),
            reply.getPhoneSentAtElapsedRealtimeMs(),
            1_020,
            250);
    assertTrue(offset.isValid());
    assertEquals(495L, offset.getWatchMinusPhoneOffsetMs());
    assertEquals(10L, offset.getRoundTripTimeMs());
    assertFalse(
        WorkoutSessionProtocol.estimateWatchInitiatedClockOffset(1_000, 510, 520, 1_005, 250)
            .isValid());
  }

  private static WorkoutSessionProtocol.Command command(
      String sessionId,
      long sequence,
      WorkoutSessionProtocol.Action action,
      long issuedAt,
      long plannedStart) {
    return new WorkoutSessionProtocol.Command(
        sessionId,
        UUID.randomUUID().toString(),
        sequence,
        WorkoutSessionProtocol.Actor.WATCH,
        action,
        issuedAt,
        plannedStart);
  }
}
