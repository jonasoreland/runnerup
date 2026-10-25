package org.runnerup.relven.ui

import java.io.StringWriter
import org.runnerup.common.util.Constants
import org.runnerup.workout.Workout
import org.runnerup.workout.WorkoutSerializer

internal data class PlannedRunningWorkoutLaunchData(
    val workoutName: String,
    val upcomingStep: String,
    val workoutJson: String,
    val sport: Int,
    val workoutType: Int
)

/** Builds the immutable workout payload passed from a plan entry into workout preparation. */
internal fun plannedRunningWorkoutLaunchData(
    workout: Workout,
    workoutName: String
): PlannedRunningWorkoutLaunchData {
  require(workout.sport == Constants.DB.ACTIVITY.SPORT_RUNNING) {
    "A planned running launch requires a running workout"
  }
  val serialized = StringWriter().also { WorkoutSerializer.writeJSON(it, workout) }.toString()
  val firstStep = workout.stepList.firstOrNull()?.step()?.name?.takeIf(String::isNotBlank)
  return PlannedRunningWorkoutLaunchData(
      workoutName = workoutName.trim().ifEmpty { "Löpning" },
      upcomingStep = firstStep ?: "Första steget",
      workoutJson = serialized,
      sport = workout.sport,
      workoutType = workout.workoutType)
}
