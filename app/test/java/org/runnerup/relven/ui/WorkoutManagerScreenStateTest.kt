package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutManagerScreenStateTest {
  @Test
  fun searchMatchesWorkoutNamesWithoutHidingOtherMatchesInTheSameSource() {
    val sources =
        listOf(
            WorkoutManagerSource(
                name = "My phone",
                workouts =
                    listOf(
                        WorkoutManagerEntry("Easy run"),
                        WorkoutManagerEntry("4 × 4 intervals"),
                        WorkoutManagerEntry("Strength A"))),
            WorkoutManagerSource(
                name = "Archived phone",
                workouts = listOf(WorkoutManagerEntry("Old recovery run"))))

    val visible = visibleWorkoutManagerSources(sources, "INTERVAL")

    assertEquals(listOf("My phone"), visible.map { it.name })
    assertEquals(listOf("4 × 4 intervals"), visible.single().workouts.map { it.name })
  }

  @Test
  fun emptySearchKeepsEverySourceAndWorkoutInOriginalOrder() {
    val sources =
        listOf(
            WorkoutManagerSource("My phone", listOf(WorkoutManagerEntry("B"), WorkoutManagerEntry("A"))),
            WorkoutManagerSource("Watch", listOf(WorkoutManagerEntry("C"))))

    assertEquals(sources, visibleWorkoutManagerSources(sources, " "))
  }

  @Test
  fun emptySourcesDoNotHideTheNoSavedWorkoutsState() {
    assertEquals(
        emptyList<WorkoutManagerSource>(),
        visibleWorkoutManagerSources(listOf(WorkoutManagerSource("My phone", emptyList())), ""))
  }

  @Test
  fun selectionIdentityIncludesSourceAndWorkoutName() {
    assertEquals("phone\u0000Intervals", workoutManagerSelectionKey("phone", "Intervals"))
    assertEquals("watch\u0000Intervals", workoutManagerSelectionKey("watch", "Intervals"))
  }

  @Test
  fun selectingTheCurrentWorkoutTogglesSelectionOffAndNotifiesTheHost() {
    val selections = mutableListOf<Pair<String?, String?>>()
    val state =
        WorkoutManagerScreenStateHolder(
            WorkoutManagerSelectionCallback { source, workout -> selections.add(source to workout) })
    state.update(listOf(WorkoutManagerSource("My phone", listOf(WorkoutManagerEntry("Intervals")))), null)

    state.selectWorkout("My phone", "Intervals")
    assertEquals(workoutManagerSelectionKey("My phone", "Intervals"), state.presentation.selectedWorkoutKey)
    state.selectWorkout("My phone", "Intervals")

    assertEquals(null, state.presentation.selectedWorkoutKey)
    assertEquals(listOf("My phone" to "Intervals", null to null), selections)
  }

  @Test
  fun searchingAwayFromTheSelectedWorkoutClearsSelectionAndActions() {
    val selections = mutableListOf<Pair<String?, String?>>()
    val sources =
        listOf(
            WorkoutManagerSource(
                "My phone",
                listOf(WorkoutManagerEntry("Easy run"), WorkoutManagerEntry("Intervals"))))
    val selectedKey = workoutManagerSelectionKey("My phone", "Intervals")
    val state =
        WorkoutManagerScreenStateHolder(
            WorkoutManagerSelectionCallback { source, workout -> selections.add(source to workout) })
    state.update(sources, selectedKey)

    state.setSearchQuery("easy")

    assertEquals(null, state.presentation.selectedWorkoutKey)
    assertEquals(listOf(null to null), selections)
  }
}
