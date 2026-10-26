package org.runnerup.relven.ui

import android.content.Context
import java.io.StringReader
import org.runnerup.workout.Workout
import org.runnerup.workout.WorkoutBuilder
import org.runnerup.workout.WorkoutSerializer
import org.json.JSONObject

/** Reconstructs the selected RunnerUp workout if Android recreates preparation after process death. */
internal object WorkoutPreparationWorkoutRestorer {
  fun restore(context: Context, json: String, sport: Int, workoutType: Int): Workout {
    require(json.isNotBlank()) { "Workout definition is missing" }
    val relvenDocument = runCatching { JSONObject(json).optInt("schemaVersion") == 3 }.getOrDefault(false)
    val workout =
        if (relvenDocument) {
          require(sport == org.runnerup.common.util.Constants.DB.ACTIVITY.SPORT_RUNNING) {
            "Relven workout documents currently support running workouts only"
          }
          org.runnerup.workout.RelvenWorkoutImporter.parse(json)
        } else {
          WorkoutSerializer.readJSON(StringReader(json)).apply {
            this.sport = sport
            setWorkoutType(workoutType)
          }
        }
    WorkoutBuilder.prepareForRecovery(context, workout)
    return workout
  }
}
