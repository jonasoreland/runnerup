package org.runnerup.wear.ui

import org.runnerup.common.R
import org.runnerup.hr.HeartRatePermissionState

internal data class WearHeartRatePermissionPresentation(
    val titleResource: Int,
    val detailsResource: Int,
    val actionResource: Int?)

internal fun isCompactWearPermissionLayout(widthDp: Float, heightDp: Float): Boolean =
    widthDp < 200f || heightDp < 200f

internal fun wearHeartRatePermissionPresentation(
    state: HeartRatePermissionState.State, sdkInt: Int): WearHeartRatePermissionPresentation =
    when (state) {
      HeartRatePermissionState.State.UNAVAILABLE ->
          WearHeartRatePermissionPresentation(
              R.string.wear_hr_sensor_unavailable,
              R.string.wear_hr_sensor_unavailable_details,
              null)
      HeartRatePermissionState.State.NEEDS_PERMISSION ->
          WearHeartRatePermissionPresentation(
              R.string.wear_hr_permission_needed,
              R.string.wear_hr_permission_needed_details,
              R.string.wear_hr_grant_permission)
      HeartRatePermissionState.State.NEEDS_BACKGROUND_PERMISSION ->
          WearHeartRatePermissionPresentation(
              R.string.wear_hr_background_permission_needed,
              if (sdkInt < 36) {
                R.string.wear_hr_background_permission_settings_details
              } else {
                R.string.wear_hr_background_permission_details
              },
              if (sdkInt < 36) {
                R.string.wear_hr_open_permission_settings
              } else {
                R.string.wear_hr_grant_background_permission
              })
      HeartRatePermissionState.State.READY ->
          WearHeartRatePermissionPresentation(
              R.string.wear_hr_permission_ready, R.string.wear_hr_permission_ready_details, null)
    }
