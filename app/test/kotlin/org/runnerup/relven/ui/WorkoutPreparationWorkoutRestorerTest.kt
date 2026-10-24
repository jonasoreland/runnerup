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
