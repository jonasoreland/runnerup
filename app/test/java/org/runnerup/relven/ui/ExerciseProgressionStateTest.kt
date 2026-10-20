package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.runnerup.relven.strength.StrengthSessionHistoryLoad

class ExerciseProgressionStateTest {
  @Test
  fun mapsMissingResultToLoading() {
    assertSame(ExerciseProgressionLoadState.Loading, exerciseProgressionLoadState(null))
  }

  @Test
  fun mapsEmptyValidHistoryToLoadedEmptyState() {
    assertEquals(
        ExerciseProgressionLoadState.Loaded(emptyMap(), 0),
        exerciseProgressionLoadState(Result.success(StrengthSessionHistoryLoad(emptyList(), 0))))
  }

  @Test
  fun preservesPartialHistoryWarningCount() {
    assertEquals(
        ExerciseProgressionLoadState.Loaded(emptyMap(), 2),
        exerciseProgressionLoadState(Result.success(StrengthSessionHistoryLoad(emptyList(), 2))))
  }

  @Test
  fun mapsStorageFailureToUnavailableState() {
    assertSame(
        ExerciseProgressionLoadState.Unavailable,
        exerciseProgressionLoadState(Result.failure(IllegalStateException("unavailable"))))
  }
}
