package org.runnerup.relven.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import java.util.UUID
import org.runnerup.common.wear.WorkoutSessionProtocol
import androidx.compose.runtime.setValue

internal enum class WorkoutPreparationPhase {
  PREPARING,
  READY,
  START_REQUESTED,
  CANCEL_REQUESTED,
  COUNTDOWN,
  ACTIVE,
  STARTING
}

internal data class WorkoutPreparationUiState(
    val sessionId: String,
    val workoutName: String,
    val upcomingStep: String,
    val phase: WorkoutPreparationPhase,
    val watchReady: Boolean,
    val status: String,
    val plannedStartElapsedRealtimeMs: Long = 0L,
    val countdownValue: Int = 0
)

internal fun isScheduledWatchStartForSession(
    command: WorkoutSessionProtocol.Command,
    expectedSessionId: String
): Boolean =
    command.sessionId == expectedSessionId &&
        command.actor == WorkoutSessionProtocol.Actor.WATCH &&
        command.action == WorkoutSessionProtocol.Action.START &&
        command.plannedStartElapsedRealtimeMs > command.issuedAtElapsedRealtimeMs &&
        command.plannedStartElapsedRealtimeMs - command.issuedAtElapsedRealtimeMs <= 10_000L

internal class WorkoutPreparationStateHolder(
    sessionId: String,
    workoutName: String,
    upcomingStep: String
) {
  var authority: WorkoutSessionProtocol.Actor = WorkoutSessionProtocol.Actor.PHONE
    private set
  private var sessionState: WorkoutSessionProtocol.SessionState? = null
  private var sequence = 0L
  var lastCommand: WorkoutSessionProtocol.Command? = null
    private set
  var value by
      mutableStateOf(
          WorkoutPreparationUiState(
              sessionId,
              workoutName,
              upcomingStep,
              WorkoutPreparationPhase.PREPARING,
              watchReady = false,
              status = "Ansluter till klockan"))
    private set

  fun onWatchPreparationFinished(watchReady: Boolean, status: String) {
    if (value.phase != WorkoutPreparationPhase.PREPARING) return
    authority = if (watchReady) WorkoutSessionProtocol.Actor.WATCH else WorkoutSessionProtocol.Actor.PHONE
    sessionState = WorkoutSessionProtocol.SessionState.ready(value.sessionId, authority)
    value = value.copy(phase = WorkoutPreparationPhase.READY, watchReady = watchReady, status = status)
  }

  fun requestStart(
      nowElapsedRealtimeMs: Long,
      actor: WorkoutSessionProtocol.Actor = WorkoutSessionProtocol.Actor.PHONE,
      commandId: String = UUID.randomUUID().toString(),
      commandSequence: Long = sequence + 1L
  ): Boolean {
    if (authority != WorkoutSessionProtocol.Actor.PHONE) return false
    val command =
        createStartCommand(nowElapsedRealtimeMs, actor, commandId, commandSequence) ?: return false
    return acceptStart(command)
  }

  /** Creates a phone request for the connected watch; it does not change authoritative state. */
  fun createStartRequest(
      nowElapsedRealtimeMs: Long,
      commandId: String = UUID.randomUUID().toString(),
      commandSequence: Long = (sessionState?.lastSequence ?: sequence) + 1L
  ): WorkoutSessionProtocol.Command? {
    if (authority != WorkoutSessionProtocol.Actor.WATCH || value.phase != WorkoutPreparationPhase.READY) return null
    return WorkoutSessionProtocol.Command(
        value.sessionId,
        commandId,
        commandSequence,
        WorkoutSessionProtocol.Actor.PHONE,
        WorkoutSessionProtocol.Action.START,
        nowElapsedRealtimeMs,
        nowElapsedRealtimeMs + START_COUNTDOWN_MS)
  }

  fun markStartRequested(command: WorkoutSessionProtocol.Command): Boolean {
    if (authority != WorkoutSessionProtocol.Actor.WATCH ||
        value.phase != WorkoutPreparationPhase.READY ||
        command.sessionId != value.sessionId ||
        command.actor != WorkoutSessionProtocol.Actor.PHONE ||
        command.action != WorkoutSessionProtocol.Action.START) return false
    lastCommand = command
    value = value.copy(phase = WorkoutPreparationPhase.START_REQUESTED, status = "Väntar på klockan")
    return true
  }

  fun onStartRequestFailed(status: String = "Klockan kunde inte starta passet") {
    if (value.phase != WorkoutPreparationPhase.START_REQUESTED) return
    lastCommand = null
    value = value.copy(phase = WorkoutPreparationPhase.READY, status = status)
  }

  fun onStartRequestStillPending(status: String) {
    if (value.phase != WorkoutPreparationPhase.START_REQUESTED) return
    value = value.copy(status = status)
  }

  fun resetAfterRejectedAuthoritativeStart(status: String) {
    if (authority != WorkoutSessionProtocol.Actor.WATCH ||
        (value.phase != WorkoutPreparationPhase.READY &&
            value.phase != WorkoutPreparationPhase.START_REQUESTED &&
            value.phase != WorkoutPreparationPhase.COUNTDOWN)) return
    sessionState = WorkoutSessionProtocol.SessionState.ready(value.sessionId, authority)
    sequence = 0L
    lastCommand = null
    value = value.copy(
        phase = WorkoutPreparationPhase.READY,
        status = status,
        plannedStartElapsedRealtimeMs = 0L,
        countdownValue = 0)
  }

  fun createStartCommand(
      nowElapsedRealtimeMs: Long,
      actor: WorkoutSessionProtocol.Actor = WorkoutSessionProtocol.Actor.PHONE,
      commandId: String = UUID.randomUUID().toString(),
      commandSequence: Long = sequence + 1L
  ): WorkoutSessionProtocol.Command? {
    if (value.phase != WorkoutPreparationPhase.READY) return null
    return WorkoutSessionProtocol.Command(
        value.sessionId,
        commandId,
        commandSequence,
        actor,
        WorkoutSessionProtocol.Action.START,
        nowElapsedRealtimeMs,
        nowElapsedRealtimeMs + START_COUNTDOWN_MS)
  }

  fun acceptStart(command: WorkoutSessionProtocol.Command): Boolean {
    if (value.phase != WorkoutPreparationPhase.READY ||
        command.sessionId != value.sessionId ||
        command.action != WorkoutSessionProtocol.Action.START) return false
    val transition =
        WorkoutSessionProtocol.applyAtAuthority(
            sessionState, WorkoutSessionProtocol.Actor.PHONE, command)
    if (transition.result != WorkoutSessionProtocol.Result.ACCEPTED) return false
    sessionState = transition.state
    sequence = command.sequence
    lastCommand = command
    val startAt = transition.state.plannedStartElapsedRealtimeMs
    value = value.copy(
        phase = WorkoutPreparationPhase.COUNTDOWN,
        status = "Passet startar om",
        plannedStartElapsedRealtimeMs = startAt,
        countdownValue = 3)
    return true
  }

  /** Mirrors a start after the phone Tracker coordinator has already validated it. */
  fun acceptAuthoritativeStart(command: WorkoutSessionProtocol.Command): Boolean {
    if ((value.phase != WorkoutPreparationPhase.READY && value.phase != WorkoutPreparationPhase.START_REQUESTED) ||
        command.sessionId != value.sessionId ||
        command.actor != authority ||
        command.action != WorkoutSessionProtocol.Action.START) return false
    sessionState = try {
      if (authority == WorkoutSessionProtocol.Actor.WATCH) {
        val ready = WorkoutSessionProtocol.SessionState.ready(value.sessionId, authority)
        val transition = WorkoutSessionProtocol.applyProjection(ready, command)
        if (transition.result != WorkoutSessionProtocol.Result.ACCEPTED ||
            transition.state.phase != WorkoutSessionProtocol.Phase.COUNTDOWN) return false
        transition.state
      } else {
        WorkoutSessionProtocol.SessionState.projectAcceptedStart(value.sessionId, authority, command)
      }
    } catch (_: IllegalArgumentException) {
      return false
    }
    sequence = command.sequence
    lastCommand = command
    value = value.copy(
        phase = WorkoutPreparationPhase.COUNTDOWN,
        status = "Passet startar om",
        plannedStartElapsedRealtimeMs = command.plannedStartElapsedRealtimeMs,
        countdownValue = 3)
    return true
  }

  fun acceptAuthoritativeCancel(command: WorkoutSessionProtocol.Command): Boolean {
    if (authority != WorkoutSessionProtocol.Actor.WATCH ||
        (value.phase != WorkoutPreparationPhase.CANCEL_REQUESTED &&
            value.phase != WorkoutPreparationPhase.COUNTDOWN &&
            value.phase != WorkoutPreparationPhase.START_REQUESTED) ||
        command.sessionId != value.sessionId ||
        command.actor != authority ||
        command.action != WorkoutSessionProtocol.Action.CANCEL) return false
    val transition = WorkoutSessionProtocol.applyProjection(sessionState, command)
    if (transition.result != WorkoutSessionProtocol.Result.ACCEPTED) return false
    sessionState = transition.state
    sequence = command.sequence
    lastCommand = null
    value = value.copy(
        phase = WorkoutPreparationPhase.READY,
        status = if (value.watchReady) "Klockan redo" else "Fortsätt på mobilen",
        plannedStartElapsedRealtimeMs = 0L,
        countdownValue = 0)
    return true
  }

  fun onStartFailed(): WorkoutSessionProtocol.Command? =
      cancelCountdown("Kunde inte starta passet")

  fun countdownSeconds(nowElapsedRealtimeMs: Long): Int {
    if (value.phase != WorkoutPreparationPhase.COUNTDOWN) return 0
    val remaining = (value.plannedStartElapsedRealtimeMs - nowElapsedRealtimeMs).coerceAtLeast(0L)
    return ((remaining + 999L) / 1000L).toInt()
  }

  fun updateCountdown(nowElapsedRealtimeMs: Long) {
    if (value.phase != WorkoutPreparationPhase.COUNTDOWN) return
    value = value.copy(countdownValue = countdownSeconds(nowElapsedRealtimeMs))
  }

  fun isStartDue(nowElapsedRealtimeMs: Long): Boolean =
      value.phase == WorkoutPreparationPhase.COUNTDOWN &&
          nowElapsedRealtimeMs >= value.plannedStartElapsedRealtimeMs

  fun completeStart(nowElapsedRealtimeMs: Long): Boolean {
    if (!isStartDue(nowElapsedRealtimeMs)) return false
    val transition = WorkoutSessionProtocol.advance(sessionState, nowElapsedRealtimeMs)
    if (transition.result != WorkoutSessionProtocol.Result.ACCEPTED) return false
    sessionState = transition.state
    value = value.copy(
        phase = WorkoutPreparationPhase.ACTIVE,
        status = "Passet pågår",
        countdownValue = 0)
    return true
  }

  fun cancelCountdown(
      status: String = if (value.watchReady) "Klockan redo" else "Fortsätt på mobilen",
      nowElapsedRealtimeMs: Long = value.plannedStartElapsedRealtimeMs
  ): WorkoutSessionProtocol.Command? {
    if (value.phase != WorkoutPreparationPhase.COUNTDOWN) return null
    if (authority == WorkoutSessionProtocol.Actor.WATCH) {
      val request =
          WorkoutSessionProtocol.Command(
              value.sessionId,
              UUID.randomUUID().toString(),
              (sessionState?.lastSequence ?: sequence) + 1L,
              WorkoutSessionProtocol.Actor.PHONE,
              WorkoutSessionProtocol.Action.CANCEL,
              nowElapsedRealtimeMs,
              0L)
      lastCommand = request
      value = value.copy(phase = WorkoutPreparationPhase.CANCEL_REQUESTED, status = "Avbryter nedräkning")
      return request
    }
    val cancel =
        WorkoutSessionProtocol.Command(
            value.sessionId,
            UUID.randomUUID().toString(),
            sequence + 1L,
            WorkoutSessionProtocol.Actor.PHONE,
            WorkoutSessionProtocol.Action.CANCEL,
            lastCommand?.plannedStartElapsedRealtimeMs ?: value.plannedStartElapsedRealtimeMs,
            0L)
    val transition =
        WorkoutSessionProtocol.applyAtAuthority(sessionState, authority, cancel)
    if (transition.result != WorkoutSessionProtocol.Result.ACCEPTED) return null
    sessionState = transition.state
    sequence = cancel.sequence
    lastCommand = null
    value = value.copy(
        phase = WorkoutPreparationPhase.READY,
        status = status,
        plannedStartElapsedRealtimeMs = 0L,
        countdownValue = 0)
    return cancel
  }

  fun onCancelRequestFailed() {
    if (value.phase != WorkoutPreparationPhase.CANCEL_REQUESTED) return
    value = value.copy(phase = WorkoutPreparationPhase.COUNTDOWN, status = "Klockan kunde inte nås")
  }

  fun onCancelRequestStillPending(status: String) {
    if (value.phase != WorkoutPreparationPhase.CANCEL_REQUESTED) return
    value = value.copy(status = status)
  }

  companion object { const val START_COUNTDOWN_MS = 3_000L }
}
