package org.runnerup.relven.ui

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.runnerup.workout.RelvenExerciseReference
import org.runnerup.workout.RelvenStrengthWorkoutParser

/** Builds a one-off canonical strength Workout v3 document from the offline exercise catalog. */
internal object GymWorkoutDraftBuilder {
  @JvmOverloads
  fun build(
      name: String,
      exercises: List<GymExerciseDraft>,
      references: Map<String, RelvenExerciseReference>,
      nextId: () -> String = { UUID.randomUUID().toString() },
  ): String {
    val title = name.trim()
    require(title.isNotEmpty() && title.length <= 80) { "Workout name must contain 1–80 characters" }
    require(exercises.isNotEmpty()) { "Add at least one exercise" }
    require(exercises.size <= 100) { "A workout can contain at most 100 exercises" }
    require(exercises.map(GymExerciseDraft::exerciseId).distinct().size == exercises.size) {
      "Choose each exercise at most once"
    }
    val ids = mutableSetOf<String>()
    fun uniqueId(): String = nextId().also {
      require(it.isNotBlank() && it.length <= 160 && ids.add(it)) { "Generated identifiers must be unique" }
    }

    val steps = JSONArray()
    exercises.forEach { draft ->
      val sets = draft.sets.toIntOrNull()
      val repetitions = draft.repetitions.toIntOrNull()
      val restSeconds = draft.restSeconds.toIntOrNull()
      require(sets != null && sets in 1..100 &&
          repetitions != null && repetitions in 1..500 &&
          restSeconds != null && restSeconds in 0..3600) {
        "Exercise sets, repetitions, or rest are outside supported limits"
      }
      val reference = references[draft.exerciseId]
          ?: throw IllegalArgumentException("Unknown exercise ID")
      require(reference.libraryRevision > 0 && reference.exerciseRevision > 0 &&
          reference.name.isNotBlank() && reference.instructions.isNotBlank() && reference.equipment.isEmpty()) {
        "Exercise must have a valid equipment-free canonical reference"
      }
      steps.put(
          JSONObject()
              .put("id", uniqueId())
              .put("kind", "segment")
              .put("name", reference.name)
              .put("phase", "work")
              .put("end", JSONObject().put("type", "manual"))
              .put(
                  "exercise",
                  JSONObject()
                      .put("exerciseId", draft.exerciseId)
                      .put("libraryRevision", reference.libraryRevision)
                      .put("exerciseRevision", reference.exerciseRevision)
                      .put(
                          "snapshot",
                          JSONObject()
                              .put("name", reference.name)
                              .put("instructions", reference.instructions)
                              .put("equipment", JSONArray(reference.equipment))
                              .put("images", JSONArray()))
                      .put("sets", sets)
                      .put("minRepetitions", repetitions)
                      .put("maxRepetitions", repetitions)
                      .put("restSeconds", restSeconds)))
    }
    val document =
        JSONObject()
            .put("schemaVersion", 3)
            .put("id", uniqueId())
            .put("name", title)
            .put("sport", "strength")
            .put("steps", steps)
            .toString()
    RelvenStrengthWorkoutParser.parse(document, references)
    return document
  }
}
