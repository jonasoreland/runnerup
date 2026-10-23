package org.runnerup.relven.ui

import android.location.Location
import org.runnerup.common.util.Constants

/** Builds the visible live route from accepted samples emitted by the authoritative Tracker. */
internal class ActiveWorkoutRouteState {
  private val mutablePoints = mutableListOf<RoutePoint>()
  private var lastElapsedRealtimeNanos: Long? = null
  private var currentSegment = 0

  val points: List<RoutePoint>
    get() = mutablePoints.toList()

  fun onLocation(
      location: Location,
      type: Int,
      elapsedSeconds: Double,
      distanceMeters: Double,
      heartRate: Double? = null,
      speedMetersPerSecond: Double? = null,
      elevationMeters: Double? = null) {
    when (type) {
      Constants.DB.LOCATION.TYPE_PAUSE -> {
        onPause()
        return
      }
      Constants.DB.LOCATION.TYPE_RESUME -> onResume()
      Constants.DB.LOCATION.TYPE_START, Constants.DB.LOCATION.TYPE_GPS -> Unit
      else -> return
    }

    val timestamp = location.elapsedRealtimeNanos
    if (timestamp <= 0 || timestamp <= (lastElapsedRealtimeNanos ?: 0L)) return
    if (!location.latitude.isFinite() || location.latitude !in -90.0..90.0) return
    if (!location.longitude.isFinite() || location.longitude !in -180.0..180.0) return
    if (!elapsedSeconds.isFinite() || elapsedSeconds < 0.0) return
    if (!distanceMeters.isFinite() || distanceMeters < 0.0) return

    mutablePoints +=
        RoutePoint(
            latitude = location.latitude,
            longitude = location.longitude,
            lap = 0,
            elapsedSeconds = elapsedSeconds,
            distanceMeters = distanceMeters,
            heartRate = heartRate?.takeIf { it.isFinite() && it > 0.0 },
            speedMetersPerSecond = speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0.0 },
            elevationMeters = elevationMeters?.takeIf(Double::isFinite),
            segment = currentSegment)
    lastElapsedRealtimeNanos = timestamp
  }

  fun onPause() {
    currentSegment++
  }

  fun onResume() {
    currentSegment++
  }

  fun restore(points: List<RoutePoint>, lastElapsedRealtimeNanos: Long?) {
    mutablePoints.clear()
    mutablePoints.addAll(points.filter(RoutePoint::hasValidCoordinates))
    currentSegment = mutablePoints.lastOrNull()?.segment ?: 0
    this.lastElapsedRealtimeNanos = lastElapsedRealtimeNanos
  }
}

internal fun mergePersistedAndLiveRoute(
    persisted: List<RoutePoint>,
    live: List<RoutePoint>,
): List<RoutePoint> {
  if (persisted.isEmpty()) return live
  val merged = persisted.toMutableList()
  for (point in live) {
    val last = merged.lastOrNull()
    val isDuplicate =
        last != null &&
            last.latitude == point.latitude &&
            last.longitude == point.longitude &&
            kotlin.math.abs(last.distanceMeters - point.distanceMeters) < 0.1
    if (isDuplicate) continue
    if (last != null &&
        point.elapsedSeconds <= last.elapsedSeconds &&
        point.distanceMeters <= last.distanceMeters) continue
    merged += point.copy(segment = last?.segment ?: point.segment)
  }
  return merged
}
