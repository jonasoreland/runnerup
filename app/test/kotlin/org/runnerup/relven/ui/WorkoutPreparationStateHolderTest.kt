package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import org.runnerup.common.tracker.TrackerState

class WorkoutPreparationStateHolderTest {
  private fun newSessionId() = UUID.randomUUID().toString()
  @Test
  fun trackerConnectsDuringPreparationOnlyFromNonRecordingStates() {
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.INIT))
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.INITIALIZING))
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.INITIALIZED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.CLEANUP))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.CONNECTED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.STARTED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.PAUSED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.STOPPED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.ERROR))
  }

  @Test
  fun preparationRetriesConnectionAfterTrackerCleanupSettlesToIdle() {
    assertTrue(WorkoutPreparationTrackerPolicy.shouldRetryAfterCleanup(TrackerState.INIT))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldRetryAfterCleanup(TrackerState.CLEANUP))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldRetryAfterCleanup(TrackerState.CONNECTED))
  }

  @Test
  fun recreatedPreparationReconnectsWhenTrackerRestartedAfterProcessDeath() {
    assertTrue(
        WorkoutPreparationTrackerPolicy.shouldInitiateConnection(
            connectionAttemptPending = false, TrackerState.INIT))
    assertTrue(
        WorkoutPreparationTrackerPolicy.shouldInitiateConnection(
            connectionAttemptPending = false, TrackerState.INITIALIZED))
    assertFalse(
        WorkoutPreparationTrackerPolicy.shouldInitiateConnection(
            connectionAttemptPending = true, TrackerState.INITIALIZED))
    assertFalse(
        WorkoutPreparationTrackerPolicy.shouldInitiateConnection(
            connectionAttemptPending = false, TrackerState.CONNECTED))
  }

  @Test
  fun serviceRebindClearsPendingRequestButRetainsPreparationOwnership() {
    val state =
        WorkoutPreparationTrackerConnectionState(
            initiallyOwnedByPreparation = true, initiallyPending = true)
    assertFalse(state.shouldRequestConnection(TrackerState.INIT))

    state.onServiceDisconnected()

    assertTrue(state.initiatedByPreparation)
    assertTrue(state.shouldRequestConnection(TrackerState.INIT))
  }

  @Test
  fun preparationCancellationResetsOnlyItsOwnPreStartTrackerConnection() {
    assertTrue(
        WorkoutPreparationTrackerPolicy.shouldResetAfterCancellation(
            connectionInitiatedByPreparation = true, TrackerState.CONNECTED))
    assertTrue(
        WorkoutPreparationTrackerPolicy.shouldResetAfterCancellation(
            connectionInitiatedByPreparation = true, TrackerState.CONNECTING))
    assertTrue(
        WorkoutPreparationTrackerPolicy.shouldResetAfterCancellation(
            connectionInitiatedByPreparation = true, TrackerState.INITIALIZING))
    assertFalse(
        WorkoutPreparationTrackerPolicy.shouldResetAfterCancellation(
            connectionInitiatedByPreparation = false, TrackerState.CONNECTED))
    assertFalse(
        WorkoutPreparationTrackerPolicy.shouldResetAfterCancellation(
            connectionInitiatedByPreparation = true, TrackerState.STARTED))
    assertFalse(
        WorkoutPreparationTrackerPolicy.shouldResetAfterCancellation(
            connectionInitiatedByPreparation = true, TrackerState.PAUSED))
  }

  @Test
  fun cancelThenRejectedWatchStartResetsOwnedConnectionButOrdinaryRejectionKeepsIt() {
    assertTrue(
        WorkoutPreparationTrackerPolicy.shouldResetAfterRejectedStart(
            cancelWasRequested = true,
            connectionInitiatedByPreparation = true,
            TrackerState.CONNECTED))
    assertFalse(
        WorkoutPreparationTrackerPolicy.shouldResetAfterRejectedStart(
            cancelWasRequested = false,
            connectionInitiatedByPreparation = true,
            TrackerState.CONNECTED))
    assertFalse(
        WorkoutPreparationTrackerPolicy.shouldResetAfterRejectedStart(
            cancelWasRequested = true,
            connectionInitiatedByPreparation = false,
            TrackerState.CONNECTED))
  }

  @Test
  fun startIsUnavailableUntilPreparationFinishes() {
    val state = WorkoutPreparationStateHolder(newSessionId(), "Intervaller", "Uppvärmning")

    assertEquals(WorkoutPreparationPhase.PREPARING, state.value.phase)
    assertFalse(state.requestStart(nowElapsedRealtimeMs = 0))
  }

  @Test
  fun phoneOnlyFallbackCanStartOnlyOnceAfterPreparation() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = false, status = "Fortsätt på mobilen")

    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
    assertFalse(state.value.watchReady)
    assertTrue(state.requestStart(nowElapsedRealtimeMs = 10_000))
    assertFalse(state.requestStart(nowElapsedRealtimeMs = 10_100))
    assertEquals(WorkoutPreparationPhase.COUNTDOWN, state.value.phase)
  }

  @Test
  fun connectedWorkoutUsesWatchAuthorityWhilePhoneOnlyUsesPhoneAuthority() {
    val connected = WorkoutPreparationStateHolder(newSessionId(), "Intervaller", "Uppvärmning")
    connected.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")

    assertEquals(
        org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
        connected.authority)
    assertFalse(connected.requestStart(nowElapsedRealtimeMs = 10_000))
    assertEquals(WorkoutPreparationPhase.READY, connected.value.phase)

    val phoneOnly = WorkoutPreparationStateHolder(newSessionId(), "Löpning", "Start")
    phoneOnly.onWatchPreparationFinished(watchReady = false, status = "Fortsätt på mobilen")
    assertEquals(
        org.runnerup.common.wear.WorkoutSessionProtocol.Actor.PHONE,
        phoneOnly.authority)
    assertTrue(phoneOnly.requestStart(nowElapsedRealtimeMs = 10_000))
  }

  @Test
  fun connectedPhoneStartIsOnlyARequestUntilWatchProjectionArrives() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")

    val request = state.createStartRequest(nowElapsedRealtimeMs = 10_000)!!
    assertEquals(org.runnerup.common.wear.WorkoutSessionProtocol.Actor.PHONE, request.actor)
    assertTrue(state.markStartRequested(request))
    assertEquals(WorkoutPreparationPhase.START_REQUESTED, state.value.phase)
    assertEquals(0L, state.value.plannedStartElapsedRealtimeMs)

    val accepted = requestAsWatchAuthority(request)
    assertTrue(state.acceptAuthoritativeStart(accepted))
    assertEquals(WorkoutPreparationPhase.COUNTDOWN, state.value.phase)
    assertEquals(accepted.plannedStartElapsedRealtimeMs, state.value.plannedStartElapsedRealtimeMs)
  }

  @Test
  fun unansweredWatchStartRequestReturnsToReadyForRetry() {
    val state = WorkoutPreparationStateHolder(newSessionId(), "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")
    val request = state.createStartRequest(nowElapsedRealtimeMs = 10_000)!!

    assertTrue(state.markStartRequested(request))
    state.onStartRequestFailed("Klockan svarar inte · försök igen")

    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
    assertEquals("Klockan svarar inte · försök igen", state.value.status)
    assertEquals(0L, state.value.plannedStartElapsedRealtimeMs)
  }

  @Test
  fun phoneOnlyAcceptedStartCanBeProjectedIntoThePreparationUi() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Löpning", "Start")
    state.onWatchPreparationFinished(watchReady = false, status = "Fortsätt på mobilen")
    val command = state.createStartCommand(nowElapsedRealtimeMs = 20_000)!!

    assertTrue(state.acceptAuthoritativeStart(command))
    assertEquals(WorkoutPreparationPhase.COUNTDOWN, state.value.phase)
    assertEquals(23_000L, state.value.plannedStartElapsedRealtimeMs)
  }

  @Test
  fun connectedCountdownCancellationWaitsForTheWatchProjection() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")
    val start =
        org.runnerup.common.wear.WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            1L,
            org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
            org.runnerup.common.wear.WorkoutSessionProtocol.Action.START,
            1_000L,
            4_000L)
    assertTrue(state.acceptAuthoritativeStart(start))

    val request = state.cancelCountdown()!!
    assertEquals(org.runnerup.common.wear.WorkoutSessionProtocol.Actor.PHONE, request.actor)
    assertEquals(WorkoutPreparationPhase.CANCEL_REQUESTED, state.value.phase)

    val accepted =
        org.runnerup.common.wear.WorkoutSessionProtocol.Command(
            sessionId,
            request.commandId,
            2L,
            org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
            org.runnerup.common.wear.WorkoutSessionProtocol.Action.CANCEL,
            1_500L,
            0L)
    assertTrue(state.acceptAuthoritativeCancel(accepted))
    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
  }

  @Test
  fun lateWatchCancellationStillClearsThePendingCountdownAfterRequestTimeout() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")
    val start =
        org.runnerup.common.wear.WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            1L,
            org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
            org.runnerup.common.wear.WorkoutSessionProtocol.Action.START,
            1_000L,
            4_000L)
    assertTrue(state.acceptAuthoritativeStart(start))

    val cancelRequest = state.cancelCountdown(nowElapsedRealtimeMs = 1_500L)!!
    state.onCancelRequestFailed()
    assertEquals(WorkoutPreparationPhase.COUNTDOWN, state.value.phase)

    val acceptedCancel =
        org.runnerup.common.wear.WorkoutSessionProtocol.Command(
            sessionId,
            cancelRequest.commandId,
            2L,
            org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
            org.runnerup.common.wear.WorkoutSessionProtocol.Action.CANCEL,
            1_600L,
            0L)
    assertTrue(state.acceptAuthoritativeCancel(acceptedCancel))
    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
  }

  @Test
  fun failedStartCanReturnToReadyWithoutChangingSession() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = false, status = "Fortsätt på mobilen")

    assertTrue(state.requestStart(nowElapsedRealtimeMs = 10_000))
    state.onStartFailed()

    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
    assertEquals(sessionId, state.value.sessionId)
    assertFalse(state.value.watchReady)
  }

  @Test
  fun startEntersThreeSecondMonotonicCountdownBeforeActive() {
    val state = WorkoutPreparationStateHolder(newSessionId(), "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = false, status = "Fortsätt på mobilen")

    assertTrue(state.requestStart(nowElapsedRealtimeMs = 10_000))
    assertEquals(WorkoutPreparationPhase.COUNTDOWN, state.value.phase)
    assertEquals(13_000L, state.value.plannedStartElapsedRealtimeMs)
    assertEquals(3, state.countdownSeconds(10_000))
    assertEquals(1, state.countdownSeconds(12_100))
    assertFalse(state.isStartDue(12_999))
    assertTrue(state.isStartDue(13_000))
    assertFalse(state.requestStart(nowElapsedRealtimeMs = 10_100))
    assertTrue(state.completeStart(nowElapsedRealtimeMs = 13_000))
    assertEquals(WorkoutPreparationPhase.ACTIVE, state.value.phase)
    assertFalse(state.requestStart(nowElapsedRealtimeMs = 14_000))
  }

  @Test
  fun countdownCanBeInterruptedWithoutStartingTracker() {
    val state = WorkoutPreparationStateHolder(newSessionId(), "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = false, status = "Fortsätt på mobilen")
    assertTrue(state.requestStart(nowElapsedRealtimeMs = 1_000))

    val cancel = state.cancelCountdown()

    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
    assertFalse(state.isStartDue(4_000))
    assertEquals(org.runnerup.common.wear.WorkoutSessionProtocol.Action.CANCEL, cancel?.action)
    assertTrue(state.requestStart(nowElapsedRealtimeMs = 4_100))
    assertEquals(3L, state.lastCommand?.sequence)
  }

  private fun requestAsWatchAuthority(
      request: org.runnerup.common.wear.WorkoutSessionProtocol.Command
  ) = org.runnerup.common.wear.WorkoutSessionProtocol.Command(
      request.sessionId,
      request.commandId,
      request.sequence,
      org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
      request.action,
      request.issuedAtElapsedRealtimeMs,
      request.plannedStartElapsedRealtimeMs)

  @Test
  fun authoritativeWatchStartCanSynchronizeASequenceGapToThePhoneProjection() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")
    val command =
        org.runnerup.common.wear.WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            5L,
            org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
            org.runnerup.common.wear.WorkoutSessionProtocol.Action.START,
            10_000L,
            13_000L)

    assertTrue(state.acceptAuthoritativeStart(command))
    assertEquals(WorkoutPreparationPhase.COUNTDOWN, state.value.phase)
    assertEquals(5L, state.lastCommand?.sequence)
    assertEquals(13_000L, state.value.plannedStartElapsedRealtimeMs)
  }

  @Test
  fun rejectedAuthoritativeWatchStartReturnsPhoneProjectionToReady() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")
    val command =
        org.runnerup.common.wear.WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            1L,
            org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
            org.runnerup.common.wear.WorkoutSessionProtocol.Action.START,
            10_000L,
            13_000L)

    assertTrue(state.acceptAuthoritativeStart(command))
    state.resetAfterRejectedAuthoritativeStart("Passet kunde inte starta · försök igen")

    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
    assertEquals(sessionId, state.value.sessionId)
    assertEquals("Passet kunde inte starta · försök igen", state.value.status)
    assertEquals(0L, state.value.plannedStartElapsedRealtimeMs)
    assertFalse(state.isStartDue(13_000L))
    assertNull(state.lastCommand)
  }

  @Test
  fun pendingWatchStartRetainsItsCommandForRetryAndLateAcceptance() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")
    val request = state.createStartRequest(nowElapsedRealtimeMs = 10_000L)!!
    assertTrue(state.markStartRequested(request))

    state.onStartRequestStillPending("Försöker igen")

    assertEquals(WorkoutPreparationPhase.START_REQUESTED, state.value.phase)
    assertEquals(request.commandId, state.lastCommand?.commandId)
    assertEquals("Försöker igen", state.value.status)
  }

  @Test
  fun scheduledWatchStartIsRecognizedAsThePhoneCountdownCommand() {
    val sessionId = newSessionId()
    val scheduled =
        org.runnerup.common.wear.WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            2L,
            org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
            org.runnerup.common.wear.WorkoutSessionProtocol.Action.START,
            10_100L,
            13_100L)

    assertTrue(isScheduledWatchStartForSession(scheduled, sessionId))
    assertFalse(isScheduledWatchStartForSession(scheduled, newSessionId()))

    val invalid =
        org.runnerup.common.wear.WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            3L,
            org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
            org.runnerup.common.wear.WorkoutSessionProtocol.Action.START,
            10_100L,
            10_100L)
    assertFalse(isScheduledWatchStartForSession(invalid, sessionId))
  }
}
