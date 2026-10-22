package org.runnerup.relven.ui

import java.util.ArrayDeque
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.runnerup.workout.RelvenPlanImporter

class PlanLibraryPreviewTest {
  private fun plan(id: String, name: String): RelvenPlanImporter.Plan {
    val ids = ArrayDeque(listOf("$id", "$id-workout", "$id-step", "$id-day"))
    val source = PlanDraftBuilder.create(
        name = name,
        startDate = "2026-06-01",
        weeks = 1,
        sessions = listOf(PlanDraftSession(Calendar.MONDAY, PlanWorkoutType.Easy)),
        timezone = "Europe/Stockholm",
        nextId = { ids.removeFirst() })
    return RelvenPlanImporter.parse(source)
  }

  @Test
  fun explicitlySelectedPlanWinsOverTheActivePlan() {
    val active = plan("active", "Active")
    val selected = plan("selected", "Selected")

    assertEquals(selected.id(), libraryPreviewPlan(listOf(active, selected), selected.id(), active.id())?.id())
  }

  @Test
  fun activePlanIsTheDefaultPreview() {
    val saved = plan("saved", "Saved")
    val active = plan("active", "Active")

    assertEquals(active.id(), libraryPreviewPlan(listOf(saved, active), null, active.id())?.id())
  }

  @Test
  fun firstSavedPlanIsTheFallbackWhenNoPlanIsActive() {
    val first = plan("first", "First")
    val second = plan("second", "Second")

    assertEquals(first.id(), libraryPreviewPlan(listOf(first, second), null, null)?.id())
  }

  @Test
  fun noPlanIsPreviewedWhenTheLibraryIsEmpty() {
    assertNull(libraryPreviewPlan(emptyList(), null, null))
  }
}
