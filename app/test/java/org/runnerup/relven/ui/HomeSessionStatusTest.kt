package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.ValueModel
import org.runnerup.tracker.Tracker
import org.runnerup.view.RunActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeSessionStatusTest {
  @Test
  fun startedAndPausedSessionsHaveDistinctStatuses() {
    assertEquals(HomeSessionStatus.ACTIVE, homeSessionStatus(TrackerState.STARTED))
    assertEquals(HomeSessionStatus.PAUSED, homeSessionStatus(TrackerState.PAUSED))
  }

  @Test
  fun runActivityIntentReturnsToRunnerUpSessionScreen() {
    val intent = runActivityIntent(RuntimeEnvironment.getApplication())
    assertNotNull(intent.component)
    assertEquals(RunActivity::class.java.name, intent.component?.className)
    assertEquals(
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT, intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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
