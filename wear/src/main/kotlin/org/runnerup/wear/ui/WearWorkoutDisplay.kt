package org.runnerup.wear.ui

internal data class WearMetric(val label: String, val value: String)

internal data class WearWorkoutDisplay(
    val phaseLabel: String?,
    val metrics: List<WearMetric>,
    val isPaused: Boolean)

fun replaceWearHeaderSnapshot(
    previous: MutableMap<String, String>,
    snapshot: Map<String, String>
): Map<String, String> {
  previous.clear()
  previous.putAll(snapshot)
  return previous
}

internal fun wearWorkoutDisplay(
    headers: Map<String, String>,
    data: Map<String, String>,
    screen: Int,
    rowCount: Int,
    isPaused: Boolean
): WearWorkoutDisplay {
  require(screen >= 0) { "Screen index must not be negative" }
  require(rowCount >= 0) { "Row count must not be negative" }
  val metrics =
      (0 until rowCount).mapNotNull { row ->
        val key = "$screen.$row"
        val label = headers["HEADER/$key"]?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        WearMetric(label, data["DATA/$key"]?.takeIf(String::isNotBlank) ?: "—")
      }
  return WearWorkoutDisplay(
      phaseLabel = headers["CURRENT_STEP"]?.takeIf(String::isNotBlank),
      metrics = metrics,
      isPaused = isPaused)
}
