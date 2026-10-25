package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsReadinessPublisherTest {
  @Test
  fun publishesFirstStateAndOnlyMeaningfulAccuracyChanges() {
    val publisher = GpsReadinessPublisher()

    val first = publisher.next(state(GpsPreparationStatus.ACCURACY_INSUFFICIENT, 21.2f), 100L)
    val same = publisher.next(state(GpsPreparationStatus.ACCURACY_INSUFFICIENT, 21.4f), 200L)
    val smallAccuracyChange =
      publisher.next(state(GpsPreparationStatus.ACCURACY_INSUFFICIENT, 24.1f), 300L)
    val meaningfulAccuracyChange =
      publisher.next(state(GpsPreparationStatus.ACCURACY_INSUFFICIENT, 26.3f), 400L)

    assertEquals(100L, first?.revision)
    assertEquals(21f, first?.accuracyMeters)
    assertNull(same)
    assertNull(smallAccuracyChange)
    assertEquals(26f, meaningfulAccuracyChange?.accuracyMeters)
  }

  @Test
  fun publishesStateTransitionsAndUsesIncreasingRevisions() {
    val publisher = GpsReadinessPublisher()

    val searching = publisher.next(state(GpsPreparationStatus.SEARCHING), 500L)
    val ready = publisher.next(state(GpsPreparationStatus.READY, 8f), 450L)
    val accuracyLost = publisher.next(state(GpsPreparationStatus.READY), 450L)

    assertEquals(500L, searching?.revision)
    assertEquals(501L, ready?.revision)
    assertEquals(502L, accuracyLost?.revision)
  }

  @Test
  fun omitsOutOfRangeAccuracyInsteadOfPublishingAFabricatedMeasurement() {
    val publisher = GpsReadinessPublisher()

    val update = publisher.next(state(GpsPreparationStatus.ACCURACY_INSUFFICIENT, 12_500f), 100L)

    assertEquals(null, update?.accuracyMeters)
  }

  private fun state(status: GpsPreparationStatus, accuracy: Float? = null) =
    GpsPreparationUiState(status, accuracy)
}
