package org.runnerup.relven.ui

internal enum class ActivityReviewMode {
  UNFINISHED,
  SAVED
}

internal data class ActivityReviewActions(
    val canResume: Boolean,
    val canSave: Boolean,
    val canDiscard: Boolean,
    val canShare: Boolean,
    val canEdit: Boolean)

internal fun activityReviewActions(mode: ActivityReviewMode): ActivityReviewActions =
    when (mode) {
      ActivityReviewMode.UNFINISHED ->
          ActivityReviewActions(
              canResume = true,
              canSave = true,
              canDiscard = true,
              canShare = false,
              canEdit = false)
      ActivityReviewMode.SAVED ->
          ActivityReviewActions(
              canResume = false,
              canSave = false,
              canDiscard = false,
              canShare = true,
              canEdit = true)
    }

internal data class KilometerSplit(
    val number: Int,
    val durationSeconds: Double,
    val paceSecondsPerKm: Double)

internal data class ActivityChartPoint(
    val distanceKm: Double,
    val value: Double,
    val segment: Int)

internal fun activityChartMetrics(route: List<RoutePoint>): List<RouteMetric> =
    listOf(
            RouteMetric.PACE,
            RouteMetric.HEART_RATE,
            RouteMetric.CADENCE,
            RouteMetric.SPEED,
            RouteMetric.ELEVATION)
        .filter { it.isAvailable(route) }

internal fun activityChartPoints(route: List<RoutePoint>, metric: RouteMetric): List<ActivityChartPoint> =
    route.indices.mapNotNull { index ->
      val point = route[index]
      val value = metric.value(point, route.getOrNull(index - 1)) ?: return@mapNotNull null
      if (!metric.isPlausibleForDisplay(value) ||
          !point.distanceMeters.isFinite() ||
          point.distanceMeters < 0.0) {
        return@mapNotNull null
      }
      ActivityChartPoint(point.distanceMeters / 1_000.0, value, point.segment)
    }

/** Derives complete kilometer splits from chronological, same-segment accepted route points. */
internal fun kilometerSplits(
    route: List<RoutePoint>,
    splitDistanceMeters: Double = 1_000.0
): List<KilometerSplit> {
  if (!splitDistanceMeters.isFinite() || splitDistanceMeters <= 0.0 || route.size < 2) return emptyList()
  val points = route.filter(RoutePoint::hasValidCoordinates)
  if (points.size < 2) return emptyList()

  val result = mutableListOf<KilometerSplit>()
  var segment = points.first().segment
  var nextSplitDistance = points.first().distanceMeters + splitDistanceMeters
  var splitStartElapsed = points.first().elapsedSeconds
  for (index in 1 until points.size) {
    val before = points[index - 1]
    val after = points[index]
    if (before.segment != after.segment) {
      segment = after.segment
      if (after.distanceMeters.isFinite() && after.elapsedSeconds.isFinite()) {
        nextSplitDistance = after.distanceMeters + splitDistanceMeters
        splitStartElapsed = after.elapsedSeconds
      }
      continue
    }
    if (before.segment != segment ||
        !before.distanceMeters.isFinite() ||
        !after.distanceMeters.isFinite() ||
        !before.elapsedSeconds.isFinite() ||
        !after.elapsedSeconds.isFinite() ||
        after.distanceMeters <= before.distanceMeters ||
        after.elapsedSeconds <= before.elapsedSeconds) {
      continue
    }

    while (nextSplitDistance <= after.distanceMeters) {
      val fraction = (nextSplitDistance - before.distanceMeters) / (after.distanceMeters - before.distanceMeters)
      val splitElapsed = before.elapsedSeconds + (after.elapsedSeconds - before.elapsedSeconds) * fraction
      val duration = splitElapsed - splitStartElapsed
      if (duration.isFinite() && duration > 0.0) {
        result += KilometerSplit(result.size + 1, duration, duration / (splitDistanceMeters / 1_000.0))
      }
      splitStartElapsed = splitElapsed
      nextSplitDistance += splitDistanceMeters
    }
  }
  return result
}
