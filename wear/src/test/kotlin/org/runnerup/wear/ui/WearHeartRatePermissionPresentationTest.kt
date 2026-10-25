package org.runnerup.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.runnerup.hr.HeartRatePermissionState
import org.runnerup.common.R as CommonR

class WearHeartRatePermissionPresentationTest {
  @Test
  fun preparedWorkoutReadinessUsesActualSensorPermissionStatus() {
    assertEquals(
        CommonR.string.wear_hr_preparation_ready,
        wearHeartRatePreparationLabel(HeartRatePermissionState.State.READY))
    assertEquals(
        CommonR.string.wear_hr_preparation_sensor_unavailable,
        wearHeartRatePreparationLabel(HeartRatePermissionState.State.UNAVAILABLE))
    assertEquals(
        CommonR.string.wear_hr_preparation_permission_required,
        wearHeartRatePreparationLabel(HeartRatePermissionState.State.NEEDS_PERMISSION))
    assertEquals(
        CommonR.string.wear_hr_preparation_background_permission_required,
        wearHeartRatePreparationLabel(HeartRatePermissionState.State.NEEDS_BACKGROUND_PERMISSION))
  }

  @Test
  fun compactLayoutIsSelectedForSmallRoundAndShortDisplays() {
    assertEquals(true, isCompactWearPermissionLayout(192f, 192f))
    assertEquals(true, isCompactWearPermissionLayout(200f, 174f))
    assertEquals(false, isCompactWearPermissionLayout(320f, 320f))
  }

  @Test
  fun unavailableAndReadyStatesHaveNoPermissionAction() {
    val unavailable =
        wearHeartRatePermissionPresentation(HeartRatePermissionState.State.UNAVAILABLE, 36)
    assertEquals(CommonR.string.wear_hr_sensor_unavailable, unavailable.titleResource)
    assertEquals(CommonR.string.wear_hr_sensor_unavailable_details, unavailable.detailsResource)
    assertNull(unavailable.actionResource)

    val ready = wearHeartRatePermissionPresentation(HeartRatePermissionState.State.READY, 36)
    assertEquals(CommonR.string.wear_hr_permission_ready, ready.titleResource)
    assertEquals(CommonR.string.wear_hr_permission_ready_details, ready.detailsResource)
    assertNull(ready.actionResource)
  }

  @Test
  fun missingSensorPermissionOffersTheExistingForegroundPermissionAction() {
    val presentation =
        wearHeartRatePermissionPresentation(HeartRatePermissionState.State.NEEDS_PERMISSION, 35)

    assertEquals(CommonR.string.wear_hr_permission_needed, presentation.titleResource)
    assertEquals(CommonR.string.wear_hr_permission_needed_details, presentation.detailsResource)
    assertEquals(CommonR.string.wear_hr_grant_permission, presentation.actionResource)
  }

  @Test
  fun missingBackgroundPermissionUsesSettingsOnApi35AndRequestOnApi36() {
    val api35 =
        wearHeartRatePermissionPresentation(
            HeartRatePermissionState.State.NEEDS_BACKGROUND_PERMISSION, 35)
    assertEquals(
        CommonR.string.wear_hr_background_permission_settings_details, api35.detailsResource)
    assertEquals(CommonR.string.wear_hr_open_permission_settings, api35.actionResource)

    val api36 =
        wearHeartRatePermissionPresentation(
            HeartRatePermissionState.State.NEEDS_BACKGROUND_PERMISSION, 36)
    assertEquals(CommonR.string.wear_hr_background_permission_details, api36.detailsResource)
    assertEquals(CommonR.string.wear_hr_grant_background_permission, api36.actionResource)
  }
}
