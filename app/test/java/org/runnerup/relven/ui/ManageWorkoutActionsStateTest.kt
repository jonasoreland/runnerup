package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ManageWorkoutActionsStateTest {
  @Test
  fun selectedLocalWorkoutEnablesModificationActionsAndRemoteSelectionDisablesThem() {
    val state = ManageWorkoutActionsStateHolder(RecordingWorkoutManagerActions())

    assertEquals(ManageWorkoutActionsPresentation(canModifySelected = false), state.presentation)

    state.update(canModifySelected = true)
    assertEquals(ManageWorkoutActionsPresentation(canModifySelected = true), state.presentation)

    state.update(canModifySelected = false)
    assertEquals(ManageWorkoutActionsPresentation(canModifySelected = false), state.presentation)
  }

  @Test
  fun everyActionForwardsToRunnerUpCallback() {
    val actions = RecordingWorkoutManagerActions()
    val state = ManageWorkoutActionsStateHolder(actions)

    state.importWorkout()
    state.importPlan()
    state.showPlanKit()
    state.viewPlans()
    state.share()
    state.edit()
    state.create()
    state.delete()

    assertEquals(8, actions.invocations)
  }

  private class RecordingWorkoutManagerActions : WorkoutManagerActionCallbacks {
    var invocations = 0
      private set

    override fun importWorkout() { invocations++ }
    override fun importPlan() { invocations++ }
    override fun showPlanKit() { invocations++ }
    override fun viewPlans() { invocations++ }
    override fun share() { invocations++ }
    override fun edit() { invocations++ }
    override fun create() { invocations++ }
    override fun delete() { invocations++ }
  }
}
