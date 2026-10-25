package org.runnerup.relven.ui

import android.content.Intent
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.runnerup.workout.RelvenPlanImporter
import org.runnerup.workout.WorkoutSerializer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlannedRunningWorkoutLaunchTest {
  @Test
  fun planLaunchCarriesTheSelectedDefinitionAndUsesPreparationScreen() {
    val plan =
        RelvenPlanImporter.parse(
            """
            {"format":"relven.plan","schemaVersion":1,"id":"plan","name":"Plan",
            "timezone":"Europe/Stockholm","workouts":[{"schemaVersion":3,"id":"tempo",
            "name":"Tempo block","sport":"running","steps":[
              {"id":"warmup","kind":"segment","name":"Warm up","phase":"warmup",
               "end":{"type":"time","seconds":300}},
              {"id":"tempo","kind":"segment","name":"Tempo reps","phase":"work",
               "end":{"type":"distance","meters":800}}]}],
            "days":[{"id":"today","date":"2026-10-25","kind":"workout","workoutId":"tempo"}]}
            """.trimIndent())
    val selectedWorkout = requireNotNull(plan.workouts()["tempo"])

    val launch = plannedRunningWorkoutLaunchData(selectedWorkout, "Tempo block")
    val restored = WorkoutSerializer.readJSON(StringReader(launch.workoutJson))
    val reserializedRestored =
        java.io.StringWriter().also { WorkoutSerializer.writeJSON(it, restored) }.toString()
    val intent =
        WorkoutPreparationActivity.intent(
            RuntimeEnvironment.getApplication(),
            "7f98c963-275b-4e9d-9c81-f19c3c3f6c62",
            launch.workoutName,
            launch.upcomingStep,
            launch.workoutJson,
            launch.sport,
            launch.workoutType,
            restoreWorkoutFromIntent = true)

    assertEquals("Tempo block", launch.workoutName)
    assertEquals("Warm up", launch.upcomingStep)
    assertEquals(selectedWorkout.stepList.size, restored.stepList.size)
    assertEquals(launch.workoutJson, reserializedRestored)
    assertEquals(selectedWorkout.sport, launch.sport)
    assertEquals(selectedWorkout.workoutType, launch.workoutType)
    assertEquals(WorkoutPreparationActivity::class.java, intent.component?.let { Class.forName(it.className) })
    assertEquals(true, intent.getBooleanExtra(WorkoutPreparationActivity.EXTRA_RESTORE_WORKOUT_FROM_INTENT, false))
    assertEquals(launch.workoutJson, intent.getStringExtra(WorkoutPreparationActivity.EXTRA_WORKOUT_JSON))
  }
}
