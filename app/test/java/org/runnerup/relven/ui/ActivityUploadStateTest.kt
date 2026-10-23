package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityUploadStateTest {
  @Test
  fun uploadedServiceCannotBeSelectedAgain() {
    val uploaded = ActivityUploadServiceState(
        name = "Strava",
        uploaded = true,
        selected = true,
        externalId = "activity-42",
        externalUrl = "https://example.test/activity-42")

    assertFalse(canSelectActivityUpload(uploaded))
    assertEquals(uploaded, updateActivityUploadSelection(uploaded, selected = false))
  }

  @Test
  fun pendingSelectionChangesAndOnlySelectedUnsentServicesAreUploaded() {
    val initial = listOf(
        ActivityUploadServiceState("Strava", selected = true),
        ActivityUploadServiceState("Runalyze", selected = false),
        ActivityUploadServiceState("File", uploaded = true, selected = true, externalId = "done"))

    val updated = updateActivityUploadSelection(initial, "Runalyze", selected = true)

    assertEquals(setOf("Strava", "Runalyze"), selectedActivityUploadNames(updated))
    assertFalse(canSelectActivityUpload(updated.last()))
  }
}
