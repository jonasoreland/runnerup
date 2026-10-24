package org.runnerup.relven.ui

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.runnerup.common.tracker.TrackerState
import org.runnerup.tracker.Tracker
import org.mockito.Mockito

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GpsWarmupPolicyTest {
  @Test
  fun oneTimeUpgradePreservesAnExistingGpsWarmupOptOut() {
    val preferences =
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("gps-warmup-migration-test", Context.MODE_PRIVATE)
    preferences.edit().clear().putBoolean("pref_startgps", false).commit()

    migrateGpsWarmupPreference(preferences, "pref_startgps")
    assertFalse(preferences.getBoolean("pref_startgps", true))

    preferences.edit().putBoolean("pref_startgps", true).commit()
    migrateGpsWarmupPreference(preferences, "pref_startgps")
    assertTrue(preferences.getBoolean("pref_startgps", false))
  }

  @Test
  fun oneTimeUpgradeEnablesGpsWarmupWhenThePreferenceWasNeverStored() {
    val preferences =
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("gps-warmup-migration-default-test", Context.MODE_PRIVATE)
    preferences.edit().clear().commit()

    migrateGpsWarmupPreference(preferences, "pref_startgps")

    assertTrue(preferences.getBoolean("pref_startgps", false))
  }

  @Test
  fun appStartupWarmsGpsOnlyWhenLocationCanBeUsedForARunningWorkout() {
    assertTrue(
        shouldWarmUpGps(
            TrackerState.INIT,
            autoStartEnabled = true,
            fineLocationGranted = true,
            locationProviderEnabled = true,
            sportWithoutGps = false))

    assertFalse(
        shouldWarmUpGps(
            TrackerState.INIT,
            autoStartEnabled = true,
            fineLocationGranted = false,
            locationProviderEnabled = true,
            sportWithoutGps = false))
    assertFalse(
        shouldWarmUpGps(
            TrackerState.INIT,
            autoStartEnabled = true,
            fineLocationGranted = true,
            locationProviderEnabled = false,
            sportWithoutGps = false))
    assertFalse(
        shouldWarmUpGps(
            TrackerState.INIT,
            autoStartEnabled = true,
            fineLocationGranted = true,
            locationProviderEnabled = true,
            sportWithoutGps = true))
    assertFalse(
        shouldWarmUpGps(
            TrackerState.INIT,
            autoStartEnabled = false,
            fineLocationGranted = true,
            locationProviderEnabled = true,
            sportWithoutGps = false))
  }

  @Test
  fun startupDoesNotReconnectAnExistingTrackerOrInterruptAWorkout() {
    listOf(
            TrackerState.INITIALIZING,
            TrackerState.CONNECTING,
            TrackerState.CONNECTED,
            TrackerState.STARTED,
            TrackerState.PAUSED,
            TrackerState.STOPPED,
            TrackerState.ERROR)
        .forEach { state ->
          assertFalse(
              "must not reconnect from $state",
              shouldWarmUpGps(
                  state,
                  autoStartEnabled = true,
                  fineLocationGranted = true,
                  locationProviderEnabled = true,
                  sportWithoutGps = false))
        }

    assertTrue(
        shouldWarmUpGps(
            TrackerState.CLEANUP,
            autoStartEnabled = true,
            fineLocationGranted = true,
            locationProviderEnabled = true,
            sportWithoutGps = false))
  }

  @Test
  fun idleWarmupCanBeReleasedWhenAppStopsEvenAfterPreferenceChanges() {
    // Release eligibility depends on Tracker state, not the setting that started warm-up.
    assertTrue(shouldReleaseGpsWarmup(TrackerState.CONNECTED))
    assertTrue(shouldReleaseGpsWarmup(TrackerState.CONNECTING))
    assertFalse(shouldReleaseGpsWarmup(TrackerState.STARTED))
    assertFalse(shouldReleaseGpsWarmup(TrackerState.PAUSED))
    assertFalse(shouldReleaseGpsWarmup(TrackerState.STOPPED))
    assertFalse(shouldReleaseGpsWarmup(TrackerState.INIT))
  }

  @Test
  fun releasingIdleWarmupResetsTrackerButKeepsActiveWorkout() {
    val idleTracker = Mockito.mock(Tracker::class.java)
    Mockito.`when`(idleTracker.state).thenReturn(TrackerState.CONNECTED)

    releaseIdleGpsWarmup(idleTracker)

    Mockito.verify(idleTracker).reset()

    val activeTracker = Mockito.mock(Tracker::class.java)
    Mockito.`when`(activeTracker.state).thenReturn(TrackerState.STARTED)

    releaseIdleGpsWarmup(activeTracker)

    Mockito.verify(activeTracker, Mockito.never()).reset()
  }
}
