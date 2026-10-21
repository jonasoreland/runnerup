package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import org.runnerup.relven.exercise.ExerciseCatalog
import org.runnerup.relven.exercise.ExerciseCategory

class ExerciseCatalogStateTest {
  private val catalog =
      ExerciseCatalog.parse(
          """{"format":"relven.exercise-library","schemaVersion":1,"libraryRevision":1,"exercises":[
          {"exerciseId":"3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0","revision":1,"name":{"en":"Squat","sv":"Knäböj"},"category":"lower_body","movementPattern":"squat","muscleGroups":["quadriceps"],"equipment":[],"instructions":{"en":["Bend your knees."],"sv":["Böj knäna."]}},
          {"exerciseId":"09a2a7b3-0798-4ef8-98a8-02a446068ee0","revision":1,"name":{"en":"Push-up","sv":"Armhävning"},"category":"upper_body","movementPattern":"push","muscleGroups":["chest"],"equipment":[],"instructions":{"en":["Press the floor."],"sv":["Pressa golvet."]}}]}""")

  @Test
  fun combinesSearchAndCategoryThenClearsBothFilters() {
    val state = ExerciseCatalogViewState(query = "quad", category = ExerciseCategory.LOWER_BODY)

    assertEquals(listOf("Squat"), state.visibleEntries(catalog).map { it.name })
    assertEquals(2, state.copy(query = "", category = null).visibleEntries(catalog).size)
  }

  @Test
  fun resolvesTheSelectedDetailAndHandlesStaleIds() {
    val selected = ExerciseCatalogViewState(selectedExerciseId = catalog.exercises.first().exerciseId)

    assertEquals("Squat", selected.selectedExercise(catalog)?.name)
    assertNull(selected.copy(selectedExerciseId = "removed-id").selectedExercise(catalog))
  }

  @Test
  fun mapsSuccessfulAndRecoverableCatalogLoads() {
    assertTrue(loadExerciseCatalogState({ catalog }) is ExerciseCatalogLoadState.Loaded)
    assertEquals(
        ExerciseCatalogLoadState.Unavailable,
        loadExerciseCatalogState({ throw IOException("missing asset") }))
    assertEquals(
        ExerciseCatalogLoadState.Unavailable,
        loadExerciseCatalogState({ throw IllegalArgumentException("invalid catalog") }))
  }

  @Test
  fun customExerciseFormRequiresBilingualContentAndMapsValidInput() {
    assertNull(CustomExerciseFormState().toInput())
    val form =
        CustomExerciseFormState(
            nameEnglish = "  Split squat ",
            nameSwedish = "Utfall",
            category = ExerciseCategory.LOWER_BODY,
            movementPattern = "single_leg_squat",
            muscleGroups = setOf("quadriceps", "glutes"),
            equipment = "Dumbbells, bench",
            instructionsEnglish = "Stand tall.\nLower slowly.",
            instructionsSwedish = "Stå upprätt.\nSänk långsamt.")

    val input = form.toInput()
    assertEquals("Split squat", input?.nameEnglish)
    assertEquals(listOf("quadriceps", "glutes"), input?.muscleGroups)
    assertEquals(listOf("Stand tall.", "Lower slowly."), input?.instructionsEnglish)
    assertEquals(listOf("Dumbbells", "bench"), input?.equipment)
    assertNull(form.copy(nameSwedish = " ").toInput())
    assertNull(form.copy(movementPattern = "Invalid Key").toInput())
    assertNull(form.copy(instructionsEnglish = (1..13).joinToString("\n") { "Step $it" }).toInput())
    assertNull(form.copy(muscleGroups = setOf("unknown")).toInput())
  }
}
