package org.runnerup.relven.ui

import org.runnerup.common.tracker.TrackerState

/** Separates durable cleanup ownership from a connection request pending in this Activity. */
internal class WorkoutPreparationTrackerConnectionState(
    initiallyOwnedByPreparation: Boolean = false,
    initiallyPending: Boolean = false,
) {
  var initiatedByPreparation: Boolean = initiallyOwnedByPreparation
    private set

  private var requestPending: Boolean = initiallyPending

  fun shouldRequestConnection(state: TrackerState): Boolean =
      WorkoutPreparationTrackerPolicy.shouldInitiateConnection(requestPending, state)

  fun onConnectionRequested() {
    initiatedByPreparation = true
    requestPending = true
  }

  fun onServiceDisconnected() {
    requestPending = false
  }

  fun onTrackerReset() {
    requestPending = false
  }

  fun onReset() {
    initiatedByPreparation = false
    requestPending = false
  }
}
