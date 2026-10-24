package org.runnerup.relven.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.relven.settings.RelvenSettingsSnapshot
import org.runnerup.relven.health.HealthConnectAvailabilityStatus
import org.runnerup.relven.health.HealthConnectSettingsSnapshot
import org.runnerup.relven.health.HealthConnectExportStatus
import org.runnerup.relven.health.HealthConnectSessionStatus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RelvenSettingsOverviewTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun workoutPreferenceToggleWritesItsRequestedValue() {
    var requestedAutoPause: Boolean? = null
    composeRule.setContent {
      RelvenSettingsOverview(
          route = SettingsRoute.Page(SettingsPage.Workout),
          onRouteChanged = {},
          settings = RelvenSettingsSnapshot(false, true, false, false, false),
          onKeepScreenOnChanged = {},
          onAutoStartGpsChanged = {},
          onAutoPauseChanged = { requestedAutoPause = it },
          onAutoLapChanged = {},
          onAutoLapIntervalsChanged = {},
          healthConnect = healthConnectSnapshot(),
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

    composeRule.onNodeWithText("Automatisk paus").performClick()

    composeRule.runOnIdle { assertTrue(requestedAutoPause == true) }
  }

  @Test
  fun intervalAutoLapToggleRequestsEnabledValue() {
    var requestedValue: Boolean? = null
    composeRule.setContent {
      RelvenSettingsOverview(
          route = SettingsRoute.Page(SettingsPage.Workout),
          onRouteChanged = {},
          settings = RelvenSettingsSnapshot(false, true, false, false, false),
          onKeepScreenOnChanged = {},
          onAutoStartGpsChanged = {},
          onAutoPauseChanged = {},
          onAutoLapChanged = {},
          onAutoLapIntervalsChanged = { requestedValue = it },
          healthConnect = healthConnectSnapshot(),
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

    composeRule.onNodeWithText("Automatiska varv för intervallpass").performScrollTo().performClick()

    composeRule.runOnIdle { assertTrue(requestedValue == true) }
  }

  @Test
  fun healthConnectPageShowsRealCountsAndHistoryStatus() {
    composeRule.setContent {
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
              HealthConnectAvailabilityStatus.AVAILABLE,
              true,
              setOf("android.permission.health.WRITE_EXERCISE",
                  "android.permission.health.WRITE_EXERCISE_ROUTE"),
              1,
              1,
              2,
              listOf(HealthConnectSessionStatus(
                  "activity-42", "Morning run", HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS,
                  1_798_000_000_000, null,
                  setOf("android.permission.health.WRITE_EXERCISE_ROUTE"))),
              retryableCount = 1),
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

    composeRule.onNodeWithText("Exporterade: 2 · Väntande: 1").assertExists()
    composeRule.onNodeWithText("1 exporterade pass kan kompletteras med nya behörigheter.").assertExists()
    composeRule.onNodeWithText("Tidigare pass att exportera: 1").assertExists()
    composeRule.onNodeWithText("Samsung Health").assertExists()
    composeRule.onNodeWithText("Komplettera export").assertExists()
  }

  @Test
  fun healthConnectHistoryDoesNotOfferRetryWhenAutomaticExportIsDisabled() {
    composeRule.setContent {
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
              HealthConnectAvailabilityStatus.AVAILABLE,
              false,
              setOf("android.permission.health.WRITE_EXERCISE"),
              0,
              1,
              0,
              listOf(HealthConnectSessionStatus(
                  "activity-42", "Morning run", HealthConnectExportStatus.RETRYABLE_ERROR,
                  1_798_000_000_000, "temporary failure", emptySet())),
          ),
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

    composeRule.runOnIdle {
      assertEquals(0, composeRule.onAllNodesWithText("Försök igen").fetchSemanticsNodes().size)
    }
  }

  private fun healthConnectSnapshot() = HealthConnectSettingsSnapshot(
      HealthConnectAvailabilityStatus.UNKNOWN_ERROR, false, emptySet(), 0, 0, 0, emptyList())
}
