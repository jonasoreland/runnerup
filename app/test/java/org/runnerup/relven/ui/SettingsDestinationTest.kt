package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDestinationTest {
  @Test
  fun settingsPagesAreGroupedIntoTheExpectedSections() {
    assertEquals(
        listOf(
            SettingsPage.Watch,
            SettingsPage.HealthConnect,
            SettingsPage.Workout,
            SettingsPage.Voice,
            SettingsPage.GpsSensors,
            SettingsPage.Appearance,
            SettingsPage.LanguageUnits,
            SettingsPage.Notifications,
            SettingsPage.DataExport,
            SettingsPage.About),
        SettingsPage.entries)
    assertEquals(SettingsGroup.Devices, SettingsPage.Watch.group)
    assertEquals(SettingsGroup.Devices, SettingsPage.HealthConnect.group)
    assertEquals(SettingsGroup.Training, SettingsPage.Workout.group)
    assertEquals(SettingsGroup.Training, SettingsPage.Voice.group)
    assertEquals(SettingsGroup.Training, SettingsPage.GpsSensors.group)
    assertEquals(SettingsGroup.App, SettingsPage.Appearance.group)
    assertEquals(SettingsGroup.App, SettingsPage.LanguageUnits.group)
    assertEquals(SettingsGroup.App, SettingsPage.Notifications.group)
    assertEquals(SettingsGroup.App, SettingsPage.DataExport.group)
    assertEquals(SettingsGroup.About, SettingsPage.About.group)
  }

  @Test
  fun savedPageFallsBackToOverviewForMissingOrUnknownValues() {
    assertEquals(SettingsRoute.Overview, settingsRouteFromSaved(null))
    assertEquals(SettingsRoute.Overview, settingsRouteFromSaved("unknown"))
    assertEquals(SettingsRoute.Page(SettingsPage.Voice), settingsRouteFromSaved("Voice"))
    assertTrue(SettingsPage.entries.all { settingsRouteFromSaved(it.name) == SettingsRoute.Page(it) })
  }

  @Test
  fun wearConnectionMappingDistinguishesDisconnectedUnsupportedAndFailedQueries() {
    assertEquals(WearConnectionState.Unavailable, wearConnectionState(false, false, null))
    assertEquals(WearConnectionState.Error, wearConnectionState(true, false, null))
    assertEquals(WearConnectionState.Disconnected, wearConnectionState(true, true, emptyList()))
    assertEquals(WearConnectionState.Connected, wearConnectionState(true, true, listOf("Watch")))
  }
}
