package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoriteWorkoutStoreTest {
  private val runningWorkout =
      """{"schemaVersion":3,"id":"planned-run","name":"Easy run","sport":"running","steps":[{"id":"run","kind":"segment","phase":"work","end":{"type":"time","seconds":600}}]}"""
  private val strengthWorkout =
      """{"schemaVersion":3,"id":"strength-plan","name":"Bodyweight","sport":"strength","steps":[{"id":"squat","kind":"segment","phase":"work","end":{"type":"manual"},"exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Squat","instructions":"Move slowly.","equipment":[],"images":[]},"sets":2,"minRepetitions":8,"maxRepetitions":12,"restSeconds":30}}]}"""

  @Before
  fun clearStoredFavorites() {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("relven.favorite-workouts", 0)
        .edit()
        .clear()
        .commit()
  }

  @Test
  fun savesRenamedSnapshotAcrossReadsAndCanRemoveIt() {
    val context = RuntimeEnvironment.getApplication()

    val favorite = FavoriteWorkoutStore.save(context, "  Lunch run  ", runningWorkout)

    assertEquals("Lunch run", favorite.name)
    assertEquals("Lunch run", org.json.JSONObject(favorite.workoutJson).getString("name"))
    assertEquals("Easy run", org.json.JSONObject(runningWorkout).getString("name"))
    assertEquals(listOf(favorite), FavoriteWorkoutStore.read(context))
    assertTrue(FavoriteWorkoutStore.remove(context, favorite.id))
    assertTrue(FavoriteWorkoutStore.read(context).isEmpty())
  }

  @Test
  fun repeatedSavesReceiveIndependentFavoriteIdentity() {
    val context = RuntimeEnvironment.getApplication()

    val first = FavoriteWorkoutStore.save(context, "A", runningWorkout)
    val second = FavoriteWorkoutStore.save(context, "B", runningWorkout)

    assertNotEquals(first.id, second.id)
    assertEquals(listOf(first, second), FavoriteWorkoutStore.read(context))
  }

  @Test
  fun savesStrengthWorkoutWithItsExerciseSnapshotAndCustomName() {
    val favorite = FavoriteWorkoutStore.save(
        RuntimeEnvironment.getApplication(), "Core day", strengthWorkout)
    val parsed = org.runnerup.workout.RelvenStrengthWorkoutParser.parse(favorite.workoutJson)

    assertEquals("strength", favorite.sport)
    assertEquals("Core day", parsed.name())
    assertEquals("Squat", parsed.exercises().single().name())
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsInvalidWorkoutJson() {
    FavoriteWorkoutStore.save(
        RuntimeEnvironment.getApplication(), "Invalid", "{\"schemaVersion\":3}")
  }
}
