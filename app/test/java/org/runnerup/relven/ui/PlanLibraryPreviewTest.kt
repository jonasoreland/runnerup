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

  @Test
  fun planRevisionsAreGroupedWithActiveRevisionPromotedAndHistoryPreserved() {
    val original = plan("root", "Running plan")
    val revisionOne = org.runnerup.workout.RelvenPlanRevision.rename(original, "Renamed", "rev-1")
    val revisionTwo = org.runnerup.workout.RelvenPlanRevision.rename(revisionOne, "Latest", "rev-2")
    val other = plan("other", "Other plan")

    val groups = groupSavedPlans(
        listOf(original, revisionOne, revisionTwo, other),
        activePlanId = revisionOne.id(),
        archivedRootPlanIds = setOf(other.id()))
    val rootGroup = groups.first { it.rootPlanId == original.id() }
    val archivedGroup = groups.first { it.rootPlanId == other.id() }

    assertEquals(revisionOne.id(), rootGroup.displayPlan.id())
    assertEquals(listOf(2, 1, 0), rootGroup.revisions.map { it.revisionNumber() })
    assertEquals(true, rootGroup.isActive)
    assertEquals(false, rootGroup.archived)
    assertEquals(true, archivedGroup.archived)
  }

  @Test
  fun latestRevisionIsTheDisplayWhenNoRevisionIsActive() {
    val original = plan("root", "Running plan")
    val revision = org.runnerup.workout.RelvenPlanRevision.rename(original, "Updated", "rev-1")

    val group = groupSavedPlans(listOf(original, revision), null, emptySet()).single()

    assertEquals(revision.id(), group.displayPlan.id())
    assertEquals(false, group.isActive)
  }
}
