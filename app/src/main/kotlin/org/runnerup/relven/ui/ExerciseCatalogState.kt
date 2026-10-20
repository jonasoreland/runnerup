package org.runnerup.relven.ui

import org.runnerup.relven.exercise.ExerciseCatalog
import org.runnerup.relven.exercise.ExerciseCategory
import org.runnerup.relven.exercise.ExerciseEntry
import java.io.IOException

internal data class ExerciseCatalogViewState(
    val query: String = "",
    val category: ExerciseCategory? = null,
    val selectedExerciseId: String? = null
) {
  fun visibleEntries(catalog: ExerciseCatalog): List<ExerciseEntry> = catalog.filter(query, category)

  fun selectedExercise(catalog: ExerciseCatalog): ExerciseEntry? =
      selectedExerciseId?.let { id -> catalog.exercises.firstOrNull { it.exerciseId == id } }
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
