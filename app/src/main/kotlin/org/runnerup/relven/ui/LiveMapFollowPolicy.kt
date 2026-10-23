package org.runnerup.relven.ui

internal enum class LiveMapFollowAction {
  INITIAL_CENTER,
  ANIMATE,
  NONE
}

/** Camera-only gate that prevents GPS jitter from repeatedly snapping the live map. */
internal class LiveMapFollowPolicy(private val minimumDisplacementMeters: Double = 12.0) {
  private var cameraCenter: RoutePoint? = null

  fun setCameraCenter(point: RoutePoint) {
    if (point.hasValidCoordinates()) cameraCenter = point
  }

  fun nextAction(point: RoutePoint): LiveMapFollowAction {
    if (!point.hasValidCoordinates()) return LiveMapFollowAction.NONE
    val center = cameraCenter
    if (center == null) {
      cameraCenter = point
      return LiveMapFollowAction.INITIAL_CENTER
    }
    val distanceMeters = distanceMeters(center, point)
    if (!distanceMeters.isFinite() || distanceMeters < minimumDisplacementMeters) {
      return LiveMapFollowAction.NONE
    }
    cameraCenter = point
    return LiveMapFollowAction.ANIMATE
  }

  private fun distanceMeters(first: RoutePoint, second: RoutePoint): Double {
    val firstLatitude = Math.toRadians(first.latitude)
    val secondLatitude = Math.toRadians(second.latitude)
    val latitudeDelta = secondLatitude - firstLatitude
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val latitudeTerm = kotlin.math.sin(latitudeDelta / 2)
    val longitudeTerm = kotlin.math.sin(longitudeDelta / 2)
    val haversine =
        latitudeTerm * latitudeTerm +
            kotlin.math.cos(firstLatitude) * kotlin.math.cos(secondLatitude) *
                longitudeTerm * longitudeTerm
    val boundedHaversine = haversine.coerceIn(0.0, 1.0)
    val centralAngle =
        2.0 * kotlin.math.atan2(kotlin.math.sqrt(boundedHaversine), kotlin.math.sqrt(1.0 - boundedHaversine))
    return 6_371_000.0 * centralAngle
  }
}
