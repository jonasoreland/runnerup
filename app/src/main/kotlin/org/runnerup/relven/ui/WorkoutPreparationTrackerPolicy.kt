package org.runnerup.relven.ui

import org.runnerup.common.tracker.TrackerState

/** Connects RunnerUp's existing engine during preparation without starting workout recording. */
internal object WorkoutPreparationTrackerPolicy {
  fun shouldConnect(state: TrackerState): Boolean =
      state == TrackerState.INIT ||
          state == TrackerState.INITIALIZING ||
          state == TrackerState.INITIALIZED ||
          state == TrackerState.CLEANUP
}
