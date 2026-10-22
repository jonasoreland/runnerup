package org.runnerup.relven.ui

import java.util.Locale

internal data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val lap: Int,
    val elapsedSeconds: Double,
    val distanceMeters: Double,
    val heartRate: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val elevationMeters: Double? = null,
    val segment: Int = lap)

internal enum class RouteMetric {
  PACE,
  HEART_RATE,
  SPEED,
  ELEVATION;

  fun value(point: RoutePoint, previous: RoutePoint? = null): Double? =
      when (this) {
        PACE -> {
          if (previous == null || point.lap != previous.lap || point.segment != previous.segment) null
          else {
            val distance = point.distanceMeters - previous.distanceMeters
            val seconds = point.elapsedSeconds - previous.elapsedSeconds
            if (distance > 0.0 && seconds > 0.0) seconds / distance * (1000.0 / 60.0) else null
          }
        }
        HEART_RATE -> point.heartRate
        SPEED -> point.speedMetersPerSecond?.times(3.6)
        ELEVATION -> point.elevationMeters
      }

  fun isAvailable(route: List<RoutePoint>): Boolean =
      route.indices.count { index ->
        value(route[index], route.getOrNull(index - 1))?.isFinite() == true
      } >= 2
}

internal data class RouteMetricRange(val minimum: Double, val maximum: Double, val count: Int)

internal fun routeMetricRange(route: List<RoutePoint>, metric: RouteMetric): RouteMetricRange? {
  val values = route.indices.mapNotNull { index ->
    metric.value(route[index], route.getOrNull(index - 1))?.takeIf(Double::isFinite)
  }
  if (values.size < 2) return null
  return RouteMetricRange(values.min(), values.max(), values.size)
}

internal fun routeMetricColor(value: Double?, range: RouteMetricRange?): String {
  val colors = listOf("#2E7D32", "#9EAA24", "#FFC107", "#F57C00", "#C62828")
  if (value == null || !value.isFinite() || range == null) return "#808080"
  val index =
      if (range.maximum <= range.minimum) 2
      else (((value - range.minimum) / (range.maximum - range.minimum) * colors.size).toInt())
          .coerceIn(0, colors.lastIndex)
  return colors[index]
}

/** Emits a GeoJSON FeatureCollection with one colored line feature per adjacent same-lap pair. */
internal fun buildRouteGeoJson(route: List<RoutePoint>, metric: RouteMetric): String {
  val range = routeMetricRange(route, metric)
  return buildString {
    append("{\"type\":\"FeatureCollection\",\"features\":[")
    var firstFeature = true
    for (index in 1 until route.size) {
      val before = route[index - 1]
      val after = route[index]
      if (before.lap != after.lap || before.segment != after.segment) continue
      if (!firstFeature) append(',')
      firstFeature = false
      val value = metric.value(after, before)
      append("{\"type\":\"Feature\",\"properties\":{\"color\":\"")
      append(routeMetricColor(value, range))
      append("\"},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[")
      append(number(before.longitude)).append(',').append(number(before.latitude)).append("],[")
      append(number(after.longitude)).append(',').append(number(after.latitude))
      append("]]}}")
    }
    append("]}")
  }
}

private fun number(value: Double): String = String.format(Locale.US, "%.7f", value)
