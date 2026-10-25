package org.runnerup.relven.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.runnerup.common.wear.GpsPreparationProtocol

/** Coalesces phone GPS updates so ordinary location fixes do not flood the Wear Data Layer. */
internal class GpsReadinessPublisher {
  private var previousStatus: GpsPreparationStatus? = null
  private var previousAccuracyMeters: Float? = null
  private var revision = 0L

  fun next(state: GpsPreparationUiState, elapsedRealtimeMs: Long): GpsReadinessPublication? {
    val accuracyMeters =
      state.accuracyMeters
        ?.takeIf { it.isFinite() && it > 0f && it <= MAX_REPORTED_ACCURACY_METERS }
        ?.roundToInt()
        ?.coerceAtLeast(1)
        ?.toFloat()
    if (previousStatus == state.status && !accuracyChangedMeaningfully(accuracyMeters)) return null

    previousStatus = state.status
    previousAccuracyMeters = accuracyMeters
    revision = max(max(1L, elapsedRealtimeMs), revision + 1L)
    return GpsReadinessPublication(
      revision = revision,
      status = state.status.toProtocolStatus(),
      accuracyMeters = accuracyMeters,
    )
  }

  private fun accuracyChangedMeaningfully(accuracyMeters: Float?): Boolean {
    val previous = previousAccuracyMeters
    return when {
      accuracyMeters == previous -> false
      accuracyMeters == null || previous == null -> true
      else -> abs(accuracyMeters - previous) >= MIN_ACCURACY_CHANGE_METERS
    }
  }

  private fun GpsPreparationStatus.toProtocolStatus(): GpsPreparationProtocol.Status =
    when (this) {
      GpsPreparationStatus.NOT_REQUIRED -> GpsPreparationProtocol.Status.NOT_REQUIRED
      GpsPreparationStatus.PERMISSION_REQUIRED -> GpsPreparationProtocol.Status.PERMISSION_REQUIRED
      GpsPreparationStatus.UNAVAILABLE -> GpsPreparationProtocol.Status.UNAVAILABLE
      GpsPreparationStatus.DISABLED -> GpsPreparationProtocol.Status.DISABLED
      GpsPreparationStatus.SEARCHING -> GpsPreparationProtocol.Status.SEARCHING
      GpsPreparationStatus.FIX_ACQUIRED -> GpsPreparationProtocol.Status.FIX_ACQUIRED
      GpsPreparationStatus.ACCURACY_INSUFFICIENT ->
        GpsPreparationProtocol.Status.ACCURACY_INSUFFICIENT
      GpsPreparationStatus.READY -> GpsPreparationProtocol.Status.READY
    }

  companion object {
    private const val MIN_ACCURACY_CHANGE_METERS = 5f
    private const val MAX_REPORTED_ACCURACY_METERS = 10_000f
  }
}

internal data class GpsReadinessPublication(
  val revision: Long,
  val status: GpsPreparationProtocol.Status,
  val accuracyMeters: Float?,
)
