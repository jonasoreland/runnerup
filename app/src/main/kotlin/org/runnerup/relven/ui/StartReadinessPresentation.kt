package org.runnerup.relven.ui

/** Immutable display data for the RunnerUp workout setup readiness strip. */
data class StartReadinessItem(
  val iconResource: Int?,
  val contentDescription: String?,
  val message: String,
)

data class StartReadinessPresentation(
  val summaryItems: List<StartReadinessItem>,
  val gpsDetails: StartReadinessItem?,
)

/** Mirrors the existing Tracker, GPS, and advanced-workout guards for the visible start action. */
object StartWorkoutAvailability {
  @JvmStatic
  fun isAvailable(
    gpsStarted: Boolean,
    sportWithoutGps: Boolean,
    gpsLogging: Boolean,
    gpsFixed: Boolean,
    trackerConnected: Boolean,
    advancedSelected: Boolean,
    advancedWorkoutAvailable: Boolean,
  ): Boolean =
    gpsStarted &&
      (sportWithoutGps || (gpsLogging && gpsFixed)) &&
      trackerConnected &&
      (!advancedSelected || advancedWorkoutAvailable)
}

/** Keeps the setup strip's visibility rules separate from Tracker and GPS state collection. */
object StartReadinessPresentationFactory {
  fun create(
    gpsSummary: StartReadinessItem,
    gpsDetails: StartReadinessItem,
    heartRate: StartReadinessItem?,
    watch: StartReadinessItem?,
    detailsExpanded: Boolean,
    noDevicesMessage: String,
  ): StartReadinessPresentation {
    val devices = listOfNotNull(heartRate, watch)
    return StartReadinessPresentation(
      summaryItems =
        if (devices.isEmpty() && detailsExpanded) listOf(noDevicesMessage.asStatus())
        else if (detailsExpanded) devices
        else devices + gpsSummary,
      gpsDetails = if (detailsExpanded) gpsDetails else null,
    )
  }

  private fun String.asStatus() = StartReadinessItem(null, null, this)
}
