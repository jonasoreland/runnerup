package org.runnerup.wear.ui

import org.runnerup.common.wear.GpsPreparationProtocol
import org.runnerup.common.wear.GpsPreparationProtocol.Readiness

internal object WearGpsReadinessPresentation {
  fun label(readiness: Readiness?): String {
    if (readiness == null) return "Inväntar GPS-status"
    val accuracy = readiness.accuracyMeters?.let { " · ±${it.toInt()} m" }.orEmpty()
    return when (readiness.status) {
      GpsPreparationProtocol.Status.NOT_REQUIRED -> "GPS behövs inte"
      GpsPreparationProtocol.Status.PERMISSION_REQUIRED -> "Platsåtkomst saknas"
      GpsPreparationProtocol.Status.UNAVAILABLE -> "GPS otillgängligt"
      GpsPreparationProtocol.Status.DISABLED -> "Plats är avstängd"
      GpsPreparationProtocol.Status.SEARCHING -> "Söker GPS-signal"
      GpsPreparationProtocol.Status.FIX_ACQUIRED -> "GPS-position mottagen"
      GpsPreparationProtocol.Status.ACCURACY_INSUFFICIENT -> "GPS svag$accuracy"
      GpsPreparationProtocol.Status.READY -> "GPS redo$accuracy"
    }
  }
}
