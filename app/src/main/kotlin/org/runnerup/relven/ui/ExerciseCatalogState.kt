package org.runnerup.relven.ui

import org.runnerup.relven.exercise.ExerciseCatalog
import org.runnerup.relven.exercise.ExerciseCategory
import org.runnerup.relven.exercise.ExerciseEntry
import org.runnerup.relven.exercise.CustomExerciseInput
import java.io.IOException
import java.util.Locale

internal data class ExerciseCatalogViewState(
    val query: String = "",
    val category: ExerciseCategory? = null,
    val selectedExerciseId: String? = null,
    val favoriteExerciseIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val showArchived: Boolean = false
) {
  fun visibleEntries(catalog: ExerciseCatalog): List<ExerciseEntry> =
      catalog.filter(query, category).filter { it.isArchived == showArchived }.let { matches ->
        if (favoritesOnly) matches.filter { it.exerciseId in favoriteExerciseIds } else matches
      }

  fun selectedExercise(catalog: ExerciseCatalog): ExerciseEntry? =
      selectedExerciseId?.let { id -> catalog.exercises.firstOrNull { it.exerciseId == id } }
}

internal data class CustomExerciseFormState(
    val nameEnglish: String = "",
    val nameSwedish: String = "",
    val category: ExerciseCategory = ExerciseCategory.LOWER_BODY,
    val movementPattern: String = "",
    val muscleGroups: Set<String> = emptySet(),
    val equipment: String = "",
    val instructionsEnglish: String = "",
    val instructionsSwedish: String = "",
    val error: String? = null
) {
  fun toInput(): CustomExerciseInput? {
    val englishSteps = instructionsEnglish.lines().map(String::trim).filter(String::isNotEmpty)
    val swedishSteps = instructionsSwedish.lines().map(String::trim).filter(String::isNotEmpty)
    val equipmentItems = equipment.split(',').map(String::trim).filter(String::isNotEmpty)
    if (nameEnglish.trim().length !in 1..120 || nameSwedish.trim().length !in 1..120 ||
        !movementPattern.trim().matches(Regex("^[a-z][a-z0-9_]{0,39}$")) ||
        muscleGroups.isEmpty() || muscleGroups.size > 12 ||
        muscleGroups.any { it !in CUSTOM_MUSCLE_GROUPS } ||
        englishSteps.size !in 1..12 || swedishSteps.size !in 1..12 ||
        englishSteps.any { it.length > 500 } || swedishSteps.any { it.length > 500 } ||
        equipmentItems.size > 12 || equipmentItems.any { it.length > 80 } ||
        equipmentItems.map { it.lowercase(Locale.ROOT) }.distinct().size != equipmentItems.size) return null
    return CustomExerciseInput(
        nameEnglish.trim(), nameSwedish.trim(), category, movementPattern.trim(), muscleGroups.toList(),
        equipmentItems, englishSteps, swedishSteps)
  }

  private companion object {
    val CUSTOM_MUSCLE_GROUPS = setOf("abdominals", "back", "calves", "chest", "glutes", "hamstrings", "hips", "obliques", "quadriceps", "shoulders", "triceps")
  }
}

internal fun loadExerciseCatalogState(
    load: () -> ExerciseCatalog,
    onFailure: (Exception) -> Unit = {}
): ExerciseCatalogLoadState =
    try {
      ExerciseCatalogLoadState.Loaded(load())
    } catch (error: IOException) {
      onFailure(error)
      ExerciseCatalogLoadState.Unavailable
    } catch (error: IllegalArgumentException) {
      onFailure(error)
      ExerciseCatalogLoadState.Unavailable
    }
