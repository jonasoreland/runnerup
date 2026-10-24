package org.runnerup.relven.ui

import android.content.Context
import java.io.StringReader
import org.runnerup.workout.Workout
import org.runnerup.workout.WorkoutBuilder
import org.runnerup.workout.WorkoutSerializer

/** Reconstructs the selected RunnerUp workout if Android recreates preparation after process death. */
internal object WorkoutPreparationWorkoutRestorer {
  fun restore(context: Context, json: String, sport: Int, workoutType: Int): Workout {
    require(json.isNotBlank()) { "Workout definition is missing" }
    val workout = WorkoutSerializer.readJSON(StringReader(json))
    workout.sport = sport
    workout.setWorkoutType(workoutType)
    WorkoutBuilder.prepareForRecovery(context, workout)
    return workout
  }
}
