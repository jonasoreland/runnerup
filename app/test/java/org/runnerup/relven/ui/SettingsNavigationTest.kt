package org.runnerup.relven.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.relven.health.HealthConnectAvailabilityStatus
import org.runnerup.relven.health.HealthConnectSettingsSnapshot
import org.runnerup.relven.settings.RelvenSettingsSnapshot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsNavigationTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun eachSettingsDestinationShowsItsFunctionalControlsOrActualStatus() {
    val route = mutableStateOf<SettingsRoute>(SettingsRoute.Overview)
    composeRule.setContent {
      RelvenSettingsOverview(
          route = route.value,
          onRouteChanged = { route.value = it },
          settings = RelvenSettingsSnapshot(false, true, false, false, false),
          onKeepScreenOnChanged = {},
          onAutoStartGpsChanged = {},
          onAutoPauseChanged = {},
          onAutoLapChanged = {},
          onAutoLapIntervalsChanged = {},
          healthConnect = HealthConnectSettingsSnapshot(
              HealthConnectAvailabilityStatus.UNKNOWN_ERROR, false, emptySet(), 0, 0, 0, emptyList()),
          healthConnectBusy = false,
          healthConnectMessage = null,
          onEnableHealthConnectExport = {},
          onDisableHealthConnectExport = {},
          onRequestHealthConnectPermissions = {},
          onManageHealthConnectPermissions = {},
          onExportHistoricalWorkouts = {},
          onSyncHealthConnectNow = {},
          onRefreshHealthConnectStatus = {},
          onRetryHealthConnectSession = {},
          deviceState = RelvenSettingsDeviceState(
              locationPermissionGranted = false,
              gpsProviderEnabled = false,
              cadenceSensorAvailable = true,
              temperatureSensorAvailable = false,
              pressureSensorAvailable = false,
              activityRecognitionPermissionGranted = false,
              notificationsEnabled = false,
              localeDisplayName = "Svenska",
              appVersion = "2.0.0",
              wearState = WearConnectionState.Disconnected),
          pageActions = SettingsPageActions())
    }

    val expectedContent = mapOf(
        SettingsPage.Watch to "Ingen ansluten Wear OS-klocka hittades",
        SettingsPage.HealthConnect to "Anslutning",
        SettingsPage.Workout to "Automatisk paus",
        SettingsPage.Voice to "Öppna röstinställningar",
        SettingsPage.GpsSensors to "Platstillstånd",
        SettingsPage.Appearance to "Relven använder mörkt tema",
        SettingsPage.LanguageUnits to "Kilometer",
        SettingsPage.Notifications to "Notiser är avstängda i Android",
        SettingsPage.DataExport to "Öppna träningshistorik",
        SettingsPage.About to "2.0.0")

    composeRule.onNodeWithText("Klocka").performClick()
    composeRule.onNodeWithText("Testa Relven på klockan").fetchSemanticsNode()
    composeRule.onNodeWithText("Inställningar").performClick()
    composeRule.onNodeWithText("Hantera Relven och dina träningsinställningar").fetchSemanticsNode()

    for ((page, expected) in expectedContent) {
      composeRule.runOnIdle { route.value = SettingsRoute.Page(page) }
      composeRule.waitForIdle()
      composeRule.runOnIdle {
        org.junit.Assert.assertTrue(
            "${page.name} should show $expected",
            runCatching {
              composeRule.onNodeWithText(expected, substring = true).fetchSemanticsNode()
            }.isSuccess)
      }
      if (page == SettingsPage.GpsSensors) {
        composeRule.onNodeWithText("Behörighet för rörelsedata saknas", substring = true).fetchSemanticsNode()
      }
    }

  }

  @Test
  fun unitChipsSendOnlyTheirSelectedLegacyUnitValues() {
    var selectedDistanceUnit: String? = null
    var selectedSpeedUnit: String? = null
    composeRule.setContent {
      RelvenSettingsPageContent(
          page = SettingsPage.LanguageUnits,
          settings = RelvenSettingsSnapshot(false, true, false, false, false),
          deviceState = RelvenSettingsDeviceState(localeDisplayName = "Svenska"),
          onKeepScreenOnChanged = {},
          onAutoStartGpsChanged = {},
          onAutoPauseChanged = {},
          onAutoLapChanged = {},
          onAutoLapIntervalsChanged = {},
          actions = SettingsPageActions(
              onDistanceUnitChanged = { selectedDistanceUnit = it },
              onSpeedUnitChanged = { selectedSpeedUnit = it }))
    }

    composeRule.onNodeWithTag("settings-distance-mi").performClick()
    composeRule.onNodeWithTag("settings-speed-speed").performClick()

    composeRule.runOnIdle {
      assertEquals("mi", selectedDistanceUnit)
      assertEquals("speed", selectedSpeedUnit)
    }
  }

  @Test
  fun finalHealthConnectSettingsStayScrollableAboveFloatingNavigation() {
    composeRule.setContent {
      HomeNavigationScaffold(
          modifier = Modifier.height(560.dp),
          navigationBar = {
            Box(Modifier.fillMaxWidth().height(104.dp).testTag("settings-navigation"))
          }) {
        RelvenSettingsOverview(
            route = SettingsRoute.Page(SettingsPage.HealthConnect),
            onRouteChanged = {},
            settings = RelvenSettingsSnapshot(false, true, false, false, false),
            onKeepScreenOnChanged = {},
            onAutoStartGpsChanged = {},
            onAutoPauseChanged = {},
            onAutoLapChanged = {},
            onAutoLapIntervalsChanged = {},
            healthConnect = HealthConnectSettingsSnapshot(
                HealthConnectAvailabilityStatus.UNKNOWN_ERROR, false, emptySet(), 0, 0, 0, emptyList()),
            healthConnectBusy = false,
            healthConnectMessage = null,
            onEnableHealthConnectExport = {},
            onDisableHealthConnectExport = {},
            onRequestHealthConnectPermissions = {},
            onManageHealthConnectPermissions = {},
            onExportHistoricalWorkouts = {},
            onSyncHealthConnectNow = {},
            onRefreshHealthConnectStatus = {},
            onRetryHealthConnectSession = {})
      }
    }

    val finalInstruction = composeRule.onNodeWithText("Tillåt Samsung Health att läsa träningspass", substring = true)
    finalInstruction.performScrollTo()
    val contentBounds = finalInstruction.fetchSemanticsNode().boundsInRoot
    val navigationBounds = composeRule.onNodeWithTag("settings-navigation").fetchSemanticsNode().boundsInRoot

    assertTrue(
        "Final settings content must end above the floating navigation: $contentBounds vs $navigationBounds",
        contentBounds.bottom <= navigationBounds.top)
  }
}
