package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.workout.RelvenExerciseReference
import org.runnerup.workout.RelvenStrengthWorkoutParser

class GymWorkoutDraftBuilderTest {
  private val id = "5de0b4b9-f6ae-4f76-96c9-33bc64d547cc"
  private val reference = RelvenExerciseReference(2, 1, "Bodyweight squat", "Stand tall.\nSquat with control.", emptyList())
  private val references = mapOf(id to reference)

  @Test
  fun createsValidatedWorkoutWithExactCanonicalSnapshot() {
    val generated = generateSequence(0) { it + 1 }.map { "gym-step-$it" }.iterator()
    val json = GymWorkoutDraftBuilder.build(
        "  Monday strength  ", listOf(GymExerciseDraft(id, "Knäböj", sets = "4", repetitions = "10", restSeconds = "75")),
        references, nextId = { generated.next() })

    val parsed = RelvenStrengthWorkoutParser.parse(json, references)
    assertEquals("Monday strength", parsed.name())
    assertEquals("strength", parsed.sport())
    assertEquals(1, parsed.exercises().size)
    assertEquals(id, parsed.exercises().single().exerciseId())
    assertEquals(2, parsed.exercises().single().libraryRevision())
    assertEquals(1, parsed.exercises().single().exerciseRevision())
    assertEquals("Bodyweight squat", parsed.exercises().single().name())
    assertEquals(reference.instructions, parsed.exercises().single().instructions())
    assertEquals(4, parsed.exercises().single().sets())
    assertEquals(10, parsed.exercises().single().minRepetitions())
    assertEquals(10, parsed.exercises().single().maxRepetitions())
    assertEquals(75, parsed.exercises().single().restSeconds())
    assertTrue(json.contains("\"equipment\":[]"))
    assertTrue(json.contains("\"images\":[]"))
  }

  @Test
  fun rejectsEmptyUnknownDuplicateAndOutOfRangeDrafts() {
    assertInvalid { GymWorkoutDraftBuilder.build("Gym", emptyList(), references) }
    assertInvalid { GymWorkoutDraftBuilder.build("Gym", listOf(GymExerciseDraft("unknown", "x")), references) }
    assertInvalid { GymWorkoutDraftBuilder.build("Gym", listOf(GymExerciseDraft(id, "x"), GymExerciseDraft(id, "x")), references) }
    assertInvalid { GymWorkoutDraftBuilder.build("Gym", listOf(GymExerciseDraft(id, "x", sets = "0")), references) }
    assertInvalid { GymWorkoutDraftBuilder.build("Gym", listOf(GymExerciseDraft(id, "x", restSeconds = "")), references) }
    assertInvalid { GymWorkoutDraftBuilder.build(" ", listOf(GymExerciseDraft(id, "x")), references) }
  }

  private fun assertInvalid(block: () -> Unit) {
    try {
      block()
      throw AssertionError("Expected invalid draft to be rejected")
    } catch (_: IllegalArgumentException) {}
  }
}
