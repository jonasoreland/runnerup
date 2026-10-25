package org.runnerup.relven.ui

import org.runnerup.common.tracker.TrackerState

/** Connects RunnerUp's existing engine during preparation without starting workout recording. */
internal object WorkoutPreparationTrackerPolicy {
  fun shouldConnect(state: TrackerState): Boolean =
      state == TrackerState.INIT ||
          state == TrackerState.INITIALIZING ||
          state == TrackerState.INITIALIZED

  fun shouldRetryAfterCleanup(state: TrackerState): Boolean = state == TrackerState.INIT

  fun shouldRestoreSelectedWorkout(restorePending: Boolean, state: TrackerState): Boolean =
      restorePending && state == TrackerState.CONNECTED

  fun shouldInitiateConnection(connectionAttemptPending: Boolean, state: TrackerState): Boolean =
      !connectionAttemptPending && shouldConnect(state)

  fun shouldResetAfterCancellation(
      connectionInitiatedByPreparation: Boolean, state: TrackerState): Boolean =
      connectionInitiatedByPreparation &&
          (state == TrackerState.INITIALIZING ||
              state == TrackerState.INITIALIZED ||
              state == TrackerState.CONNECTING ||
              state == TrackerState.CONNECTED ||
              state == TrackerState.ERROR)

  fun shouldResetAfterRejectedStart(
      cancelWasRequested: Boolean,
      connectionInitiatedByPreparation: Boolean,
      state: TrackerState): Boolean =
      cancelWasRequested && shouldResetAfterCancellation(connectionInitiatedByPreparation, state)
}
