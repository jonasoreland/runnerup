package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.ValueModel
import org.runnerup.tracker.Tracker

class HomeSessionStatusTest {
  @Test
  fun startedAndPausedSessionsHaveDistinctStatuses() {
    assertEquals(HomeSessionStatus.ACTIVE, homeSessionStatus(TrackerState.STARTED))
    assertEquals(HomeSessionStatus.PAUSED, homeSessionStatus(TrackerState.PAUSED))
  }

  @Test
  fun setupIdleStoppedAndErrorStatesAreNotPresentedAsSessions() {
    assertNull(homeSessionStatus(null))
    assertNull(homeSessionStatus(TrackerState.INIT))
    assertNull(homeSessionStatus(TrackerState.INITIALIZING))
    assertNull(homeSessionStatus(TrackerState.INITIALIZED))
    assertNull(homeSessionStatus(TrackerState.CONNECTING))
    assertNull(homeSessionStatus(TrackerState.CONNECTED))
    assertNull(homeSessionStatus(TrackerState.STOPPED))
    assertNull(homeSessionStatus(TrackerState.CLEANUP))
    assertNull(homeSessionStatus(TrackerState.ERROR))
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun observerPublishesCurrentAndChangedStateThenUnregistersOnClose() {
    val tracker = Mockito.mock(Tracker::class.java)
    Mockito.`when`(tracker.state).thenReturn(TrackerState.INIT)
    val statuses = mutableListOf<HomeSessionStatus?>()

    val observer = HomeTrackerSessionObserver(tracker, statuses::add)
    val listenerCaptor =
        ArgumentCaptor.forClass(ValueModel.ChangeListener::class.java)
            as ArgumentCaptor<ValueModel.ChangeListener<TrackerState>>

    Mockito.verify(tracker).registerTrackerStateListener(listenerCaptor.capture())
    assertEquals(listOf(null), statuses)

    val model = Mockito.mock(ValueModel::class.java) as ValueModel<TrackerState>
    listenerCaptor.value.onValueChanged(model, TrackerState.INIT, TrackerState.STARTED)
    assertEquals(listOf(null, HomeSessionStatus.ACTIVE), statuses)

    observer.close()
    observer.close()
    Mockito.verify(tracker).unregisterTrackerStateListener(listenerCaptor.value)

    listenerCaptor.value.onValueChanged(model, TrackerState.STARTED, TrackerState.PAUSED)
    assertEquals(listOf(null, HomeSessionStatus.ACTIVE), statuses)
  }
}
