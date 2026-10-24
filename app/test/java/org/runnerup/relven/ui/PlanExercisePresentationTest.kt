package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.runnerup.relven.exercise.ExerciseCatalog

class PlanExercisePresentationTest {
  @Test
  fun usesCurrentSwedishCatalogTextWithoutChangingSnapshotFallback() {
    val presentation =
        planExercisePresentation(EXERCISE_ID, "Bodyweight squat", "English snapshot", swedishCatalog)

    assertEquals("Knäböj", presentation.name)
    assertEquals("Stå med fötterna höftbrett.", presentation.instructions)
  }

  @Test
  fun usesEnglishCatalogTextForEnglishPresentation() {
    val presentation =
        planExercisePresentation(EXERCISE_ID, "Bodyweight squat", "English snapshot", englishCatalog)

    assertEquals("Bodyweight squat", presentation.name)
    assertEquals("Stand with feet hip-width apart.", presentation.instructions)
  }

  @Test
  fun fallsBackToImmutableSnapshotWhenExerciseIsMissingFromCatalog() {
    val presentation =
        planExercisePresentation("00000000-0000-0000-0000-000000000001", "Custom exercise", "Saved instructions", swedishCatalog)

    assertEquals("Custom exercise", presentation.name)
    assertEquals("Saved instructions", presentation.instructions)
  }

  @Test
  fun fallsBackToSnapshotWhenCatalogIsUnavailable() {
    val presentation = planExercisePresentation(EXERCISE_ID, "Snapshot name", "Snapshot instructions", null)

    assertEquals("Snapshot name", presentation.name)
    assertEquals("Snapshot instructions", presentation.instructions)
  }

  private val swedishCatalog = ExerciseCatalog.parse(catalogJson, "sv-SE")
  private val englishCatalog = ExerciseCatalog.parse(catalogJson, "en-US")

  private companion object {
    const val EXERCISE_ID = "3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0"
    val catalogJson =
        """{"format":"relven.exercise-library","schemaVersion":1,"libraryRevision":4,"exercises":[{"exerciseId":"$EXERCISE_ID","revision":1,"name":{"en":"Bodyweight squat","sv":"Knäböj"},"category":"lower_body","movementPattern":"squat","muscleGroups":["quadriceps"],"equipment":[],"instructions":{"en":["Stand with feet hip-width apart."],"sv":["Stå med fötterna höftbrett."]}}]}"""
  }
}
