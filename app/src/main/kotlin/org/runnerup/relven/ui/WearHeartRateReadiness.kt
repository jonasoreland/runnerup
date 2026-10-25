package org.runnerup.relven.ui

import org.runnerup.common.wear.HeartRateReadinessProtocol

/** Setup readiness only; a live sample is collected after the workout is accepted as started. */
internal enum class WearHeartRateReadiness(val label: String) {
  CHECKING("Puls: kontrollerar klockan"),
  READY("Puls: sensor tillgänglig"),
  SENSOR_UNAVAILABLE("Puls: ingen sensor på klockan"),
  PERMISSION_REQUIRED("Puls: behörighet saknas på klockan"),
  BACKGROUND_PERMISSION_REQUIRED("Puls: tillåt bakgrundsbehörighet på klockan"),
  UNKNOWN("Puls: status kunde inte bekräftas")
}

internal fun heartRateReadiness(status: HeartRateReadinessProtocol.Status): WearHeartRateReadiness =
    when (status) {
      HeartRateReadinessProtocol.Status.READY -> WearHeartRateReadiness.READY
      HeartRateReadinessProtocol.Status.SENSOR_UNAVAILABLE ->
          WearHeartRateReadiness.SENSOR_UNAVAILABLE
      HeartRateReadinessProtocol.Status.PERMISSION_REQUIRED ->
          WearHeartRateReadiness.PERMISSION_REQUIRED
      HeartRateReadinessProtocol.Status.BACKGROUND_PERMISSION_REQUIRED ->
          WearHeartRateReadiness.BACKGROUND_PERMISSION_REQUIRED
    }
