package org.runnerup.relven.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

data class WorkoutManagerEntry(val name: String)

data class WorkoutManagerSource(val name: String, val workouts: List<WorkoutManagerEntry>)

internal fun visibleWorkoutManagerSources(
    sources: List<WorkoutManagerSource>,
    query: String
): List<WorkoutManagerSource> {
  val normalizedQuery = query.trim().lowercase(Locale.ROOT)
  if (normalizedQuery.isEmpty()) return sources.filter { it.workouts.isNotEmpty() }
  return sources.mapNotNull { source ->
    val matchingWorkouts =
        source.workouts.filter { it.name.lowercase(Locale.ROOT).contains(normalizedQuery) }
    matchingWorkouts.takeIf { it.isNotEmpty() }?.let { source.copy(workouts = it) }
  }
}

internal fun workoutManagerSelectionKey(source: String, workout: String): String =
    "$source\u0000$workout"

data class WorkoutManagerPresentation(
    val sources: List<WorkoutManagerSource> = emptyList(),
    val searchQuery: String = "",
    val selectedWorkoutKey: String? = null)

fun interface WorkoutManagerSelectionCallback {
  fun onSelectionChanged(sourceName: String?, workoutName: String?)
}

class WorkoutManagerScreenStateHolder(
    private val selectionCallback: WorkoutManagerSelectionCallback) {
  var presentation by mutableStateOf(WorkoutManagerPresentation())
    private set

  fun update(sources: List<WorkoutManagerSource>, selectedWorkoutKey: String?) {
    presentation = presentation.copy(sources = sources, selectedWorkoutKey = selectedWorkoutKey)
  }

  fun setSearchQuery(query: String) {
    val selectedWorkoutIsVisible =
        presentation.selectedWorkoutKey?.let { selectedKey ->
          visibleWorkoutManagerSources(presentation.sources, query).any { source ->
            source.workouts.any {
              workoutManagerSelectionKey(source.name, it.name) == selectedKey
            }
          }
        } ?: true
    presentation =
        presentation.copy(
            searchQuery = query,
            selectedWorkoutKey =
                presentation.selectedWorkoutKey.takeIf { selectedWorkoutIsVisible })
    if (!selectedWorkoutIsVisible) selectionCallback.onSelectionChanged(null, null)
  }

  fun selectWorkout(sourceName: String, workoutName: String) {
    val key = workoutManagerSelectionKey(sourceName, workoutName)
    val nextKey = if (presentation.selectedWorkoutKey == key) null else key
    presentation = presentation.copy(selectedWorkoutKey = nextKey)
    selectionCallback.onSelectionChanged(sourceName.takeIf { nextKey != null }, workoutName.takeIf { nextKey != null })
  }
}
