package org.runnerup.relven.ui

import androidx.preference.PreferenceManager
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.runnerup.common.util.Constants
import org.runnerup.workout.Dimension
import org.runnerup.workout.WorkoutBuilder
import org.runnerup.workout.WorkoutSerializer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkoutPreparationWorkoutRestorerTest {
  @Test
  fun plannedRelvenDocumentRestoresItsSelectedStepsInsteadOfLegacyDefault() {
    val context = RuntimeEnvironment.getApplication()
    val planWorkout =
        """{"schemaVersion":3,"id":"plan-run","name":"MalinEwerlov pass","sport":"running","steps":[{"id":"tempo","kind":"segment","name":"Tempo från plan","phase":"work","end":{"type":"time","seconds":420},"target":{"type":"pace","minSecondsPerKm":300,"maxSecondsPerKm":330}}]}"""
    val imported = org.runnerup.workout.RelvenWorkoutImporter.parse(planWorkout)

    val launch = plannedRunningWorkoutLaunchData(imported, "Planerat tempopass", planWorkout)
    val restored =
        WorkoutPreparationWorkoutRestorer.restore(
            context,
            launch.workoutJson,
            launch.sport,
            launch.workoutType)

    assertEquals("Planerat tempopass", launch.workoutName)
    assertEquals(planWorkout, launch.workoutJson)
    assertEquals(1, restored.steps.size)
    assertEquals("Tempo från plan", restored.steps[0].name)
    assertEquals(420.0, restored.steps[0].durationValue, 0.0)
    assertEquals(org.runnerup.workout.Dimension.PACE, restored.steps[0].targetType)
    assertEquals(0.30, restored.steps[0].targetValue.minValue, 0.000001)
    assertEquals(0.33, restored.steps[0].targetValue.maxValue, 0.000001)
  }

  @Test
  fun processRecreationRestoresSelectedWorkoutAndExecutionMetadata() {
    val context = RuntimeEnvironment.getApplication()
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val original =
        WorkoutBuilder.createDefaultWorkout(context.resources, preferences, Dimension.TIME).apply {
          setWorkoutType(Constants.WORKOUT_TYPE.BASIC)
        }
    WorkoutBuilder.prepareWorkout(context.resources, preferences, original)
    val json = StringWriter().also { WorkoutSerializer.writeJSON(it, original) }.toString()

    val restored =
        WorkoutPreparationWorkoutRestorer.restore(
            context,
            json,
            Constants.DB.ACTIVITY.SPORT_RUNNING,
            Constants.WORKOUT_TYPE.BASIC)

    assertFalse(restored.stepList.isEmpty())
    assertEquals(original.stepList.size, restored.stepList.size)
    assertEquals(Constants.DB.ACTIVITY.SPORT_RUNNING, restored.sport)
    assertEquals(Constants.WORKOUT_TYPE.BASIC, restored.workoutType)
  }
}
