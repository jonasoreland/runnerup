package org.runnerup.relven.analysis

internal data class RecordedActivity(
    val id: String,
    val startedAtMillis: Long,
    val durationSeconds: Long?,
    val distanceMeters: Double?)

internal data class ActivityWindowSummary(
    val sessionCount: Int,
    val durationSeconds: Long,
    val distanceMeters: Double?,
    val distanceSessionCount: Int)

internal data class ProgressSummary(
    val sevenDays: ActivityWindowSummary,
    val twentyEightDays: ActivityWindowSummary)

internal fun summarizeActivities(rows: List<RecordedActivity>, nowMillis: Long): ProgressSummary {
  require(nowMillis > 0) { "Current time must be a positive epoch timestamp" }
  return ProgressSummary(
      summarizeWindow(rows, nowMillis, SEVEN_DAYS_MILLIS),
      summarizeWindow(rows, nowMillis, TWENTY_EIGHT_DAYS_MILLIS))
}

private fun summarizeWindow(
    rows: List<RecordedActivity>,
    nowMillis: Long,
    windowMillis: Long
): ActivityWindowSummary {
  var count = 0
  var duration = 0L
  var distance = 0.0
  var distanceSessionCount = 0
  var distanceOverflowed = false
  val cutoff = nowMillis - windowMillis
  rows.asSequence()
      .filter { it.id.isNotBlank() && it.startedAtMillis > 0 && it.startedAtMillis in cutoff..nowMillis }
      .filter { it.durationSeconds != null && it.durationSeconds >= 0 }
      .distinctBy(RecordedActivity::id)
      .forEach { row ->
        val durationSeconds = requireNotNull(row.durationSeconds)
        count += 1
        duration =
            if (duration > Long.MAX_VALUE - durationSeconds) Long.MAX_VALUE
            else duration + durationSeconds
        row.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }?.let { meters ->
          distanceSessionCount += 1
          val nextDistance = distance + meters
          if (nextDistance.isFinite()) {
            distance = nextDistance
          } else {
            distanceOverflowed = true
          }
        }
      }
  return ActivityWindowSummary(
      count,
      duration,
      distance.takeIf { distanceSessionCount > 0 && !distanceOverflowed },
      distanceSessionCount)
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val SEVEN_DAYS_MILLIS = 7 * DAY_MILLIS
private const val TWENTY_EIGHT_DAYS_MILLIS = 28 * DAY_MILLIS
