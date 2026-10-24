package org.runnerup.wear.ui

import org.runnerup.common.wear.WorkoutPreparationProtocol

internal data class PreparedWorkoutPresentation(val workoutName: String, val upcomingStep: String)

internal fun preparedWorkoutPresentation(payload: ByteArray?): PreparedWorkoutPresentation? {
  if (payload == null) return null
  return runCatching {
    WorkoutPreparationProtocol.decodePrepare(payload).let {
      PreparedWorkoutPresentation(it.workoutName, it.upcomingStep)
    }
  }.getOrNull()
}
