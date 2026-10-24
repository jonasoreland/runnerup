package org.runnerup.relven.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.ValueModel
import org.runnerup.tracker.Tracker

class HomeTrackerSessionBindingTest {
  @Test
  @Suppress("UNCHECKED_CAST")
  fun bindsOnceObservesCurrentSessionAndUnbindsOnStop() {
    val context = Mockito.mock(Context::class.java)
    Mockito.`when`(
            context.bindService(
                Mockito.any(Intent::class.java),
                Mockito.any(ServiceConnection::class.java),
                Mockito.eq(Context.BIND_AUTO_CREATE)))
        .thenReturn(true)
    val statuses = mutableListOf<HomeSessionStatus?>()
    val binding = HomeTrackerSessionBinding(context, statuses::add)

    binding.start()
    binding.start()

    val connectionCaptor = ArgumentCaptor.forClass(ServiceConnection::class.java)
    Mockito.verify(context)
        .bindService(
            Mockito.any(Intent::class.java),
            connectionCaptor.capture(),
            Mockito.eq(Context.BIND_AUTO_CREATE))

    val tracker = Mockito.mock(Tracker::class.java)
    Mockito.`when`(tracker.state).thenReturn(TrackerState.PAUSED)
    val binder = Mockito.mock(Tracker.LocalBinder::class.java)
    Mockito.`when`(binder.service).thenReturn(tracker)
    connectionCaptor.value.onServiceConnected(Mockito.mock(ComponentName::class.java), binder)

    assertEquals(listOf(HomeSessionStatus.PAUSED), statuses)

    val listenerCaptor =
        ArgumentCaptor.forClass(ValueModel.ChangeListener::class.java)
            as ArgumentCaptor<ValueModel.ChangeListener<TrackerState>>
    Mockito.verify(tracker).registerTrackerStateListener(listenerCaptor.capture())

    binding.stop()
    binding.stop()

    Mockito.verify(tracker).unregisterTrackerStateListener(listenerCaptor.value)
    Mockito.verify(context).unbindService(connectionCaptor.value)
    assertEquals(listOf(HomeSessionStatus.PAUSED, null), statuses)

    connectionCaptor.value.onServiceConnected(Mockito.mock(ComponentName::class.java), binder)
    Mockito.verify(tracker, Mockito.times(1)).registerTrackerStateListener(Mockito.any())
    assertEquals(listOf(HomeSessionStatus.PAUSED, null), statuses)
  }

  @Test
  fun doesNotUnbindWhenServiceBindingFails() {
    val context = Mockito.mock(Context::class.java)
    Mockito.`when`(
            context.bindService(
                Mockito.any(Intent::class.java),
                Mockito.any(ServiceConnection::class.java),
                Mockito.eq(Context.BIND_AUTO_CREATE)))
        .thenReturn(false)
    val binding = HomeTrackerSessionBinding(context) {}

    binding.start()
    binding.stop()

    Mockito.verify(context, Mockito.never()).unbindService(Mockito.any(ServiceConnection::class.java))
  }

  @Test
  fun preparesBoundTrackerAndReleasesItOnceWhenBindingStops() {
    val context = Mockito.mock(Context::class.java)
    Mockito.`when`(
            context.bindService(
                Mockito.any(Intent::class.java),
                Mockito.any(ServiceConnection::class.java),
                Mockito.eq(Context.BIND_AUTO_CREATE)))
        .thenReturn(true)
    val prepared = mutableListOf<Tracker>()
    val released = mutableListOf<Tracker>()
    val binding =
        HomeTrackerSessionBinding(
            context = context,
            onStatusChanged = {},
            onTrackerBound = { tracker -> prepared += tracker },
            onTrackerUnbound = released::add)

    binding.start()
    val connectionCaptor = ArgumentCaptor.forClass(ServiceConnection::class.java)
    Mockito.verify(context)
        .bindService(
            Mockito.any(Intent::class.java),
            connectionCaptor.capture(),
            Mockito.eq(Context.BIND_AUTO_CREATE))
    val tracker = Mockito.mock(Tracker::class.java)
    Mockito.`when`(tracker.state).thenReturn(TrackerState.CONNECTED)
    val binder = Mockito.mock(Tracker.LocalBinder::class.java)
    Mockito.`when`(binder.service).thenReturn(tracker)

    connectionCaptor.value.onServiceConnected(Mockito.mock(ComponentName::class.java), binder)
    binding.stop()
    binding.stop()

    assertEquals(listOf(tracker), prepared)
    assertEquals(listOf(tracker), released)
  }
}
