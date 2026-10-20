package org.runnerup.relven.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {
  @Test
  fun parsesLocalizedEntriesAndFallsBackToEnglish() {
    val swedish = ExerciseCatalog.parse(catalogJson(), "sv-SE")
    val englishFallback = ExerciseCatalog.parse(catalogJson(), "fr-FR")

    assertEquals("Knäböj", swedish.exercises.single().name)
    assertEquals(listOf("Stå med fötterna höftbrett."), swedish.exercises.single().instructions)
    assertEquals("Squat", englishFallback.exercises.single().name)
    assertEquals(3, swedish.libraryRevision)
  }

  @Test
  fun rejectsUnknownSchemaVersionAndInvalidExerciseIds() {
    assertIllegalArgument {
      ExerciseCatalog.parse(catalogJson().replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
    }
    assertIllegalArgument {
      ExerciseCatalog.parse(catalogJson().replace(EXERCISE_ID, "not-an-id"))
    }
  }

  @Test
  fun rejectsDuplicateIds() {
    val duplicate = catalogJson().replace("\"exercises\":[", "\"exercises\":[${exerciseJson()},")
    assertIllegalArgument { ExerciseCatalog.parse(duplicate) }
  }

  @Test
  fun rejectsMissingTranslations() {
    assertIllegalArgument {
      ExerciseCatalog.parse(catalogJson().replace("Knäböj", ""))
    }
  }

  @Test
  fun rejectsUnknownCategories() {
    assertIllegalArgument {
      ExerciseCatalog.parse(catalogJson().replace("lower_body", "unknown"))
    }
  }

  @Test
  fun rejectsUnknownMuscleGroupKeys() {
    assertIllegalArgument {
      ExerciseCatalog.parse(catalogJson().replace("quadriceps", "unknown_muscle"))
    }
  }

  @Test
  fun rejectsInstructionListsAboveTheSchemaLimit() {
    val steps = List(13) { "\"Instruction $it\"" }.joinToString(",")
    val overLimit =
        catalogJson().replace(
            "\"en\":[\"Stand with feet hip-width apart.\"]",
            "\"en\":[$steps]")

    assertIllegalArgument { ExerciseCatalog.parse(overLimit) }
  }

  @Test
  fun searchAndCategoryFiltersComposeAndPreserveCatalogOrder() {
    val catalog = ExerciseCatalog.parse(catalogJson(twoExercises = true), "en")

    assertEquals(listOf("Squat"), catalog.filter("quad", ExerciseCategory.LOWER_BODY).map { it.name })
    assertTrue(catalog.filter("missing", null).isEmpty())
    assertEquals(2, catalog.filter("", null).size)
    assertEquals(listOf("Push-up"), catalog.filter("push", null).map { it.name })
  }

  private fun catalogJson(twoExercises: Boolean = false): String {
    val entries = if (twoExercises) "${exerciseJson()},${exerciseJson(PUSH_ID, "Push-up", "upper_body", "chest")}" else exerciseJson()
    return """{"format":"relven.exercise-library","schemaVersion":1,"libraryRevision":3,"exercises":[$entries]}"""
  }

  private fun exerciseJson(
      id: String = EXERCISE_ID,
      name: String = "Squat",
      category: String = "lower_body",
      muscleGroup: String = "quadriceps"
  ): String =
      """{"exerciseId":"$id","revision":1,"name":{"en":"$name","sv":"Knäböj"},"category":"$category","movementPattern":"squat","muscleGroups":["$muscleGroup"],"equipment":[],"instructions":{"en":["Stand with feet hip-width apart."],"sv":["Stå med fötterna höftbrett."]}}"""

  private fun assertIllegalArgument(block: () -> Unit) {
    try {
      block()
      fail("Expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
      // Expected.
    }
  }

  private companion object {
    const val EXERCISE_ID = "3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0"
    const val PUSH_ID = "09a2a7b3-0798-4ef8-98a8-02a446068ee0"
  }
}
