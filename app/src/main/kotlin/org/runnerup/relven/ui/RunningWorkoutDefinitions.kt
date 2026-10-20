package org.runnerup.relven.ui

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.runnerup.workout.RelvenPlanImporter

internal data class RunningSegmentEnd(val type: String, val value: Double?)

internal data class RunningSegmentTarget(val type: String, val minimum: Double, val maximum: Double)

internal data class RunningSegmentSummary(
    val id: String,
    val name: String?,
    val phase: String,
    val end: RunningSegmentEnd,
    val target: RunningSegmentTarget?,
    val repeatCount: Int
)

internal fun runningWorkoutSegments(
    plan: RelvenPlanImporter.Plan,
    workoutId: String
): List<RunningSegmentSummary> {
  try {
    val workouts = JSONObject(plan.sourceJson()).getJSONArray("workouts")
    val workout =
        (0 until workouts.length())
            .map(workouts::getJSONObject)
            .firstOrNull { it.getString("id") == workoutId && it.getString("sport") == "running" }
            ?: return emptyList()
    val segments = mutableListOf<RunningSegmentSummary>()
    collectRunningSegments(workout.getJSONArray("steps"), 1L, segments)
    return segments
  } catch (error: JSONException) {
    throw IllegalArgumentException("Saved running workout definition is invalid", error)
  }
}

private fun collectRunningSegments(
    nodes: JSONArray,
    repeatCount: Long,
    segments: MutableList<RunningSegmentSummary>
) {
  for (index in 0 until nodes.length()) {
    val node = nodes.getJSONObject(index)
    when (node.getString("kind")) {
      "segment" -> {
        val end = node.getJSONObject("end")
        val endType = end.getString("type")
        val endValue =
            when (endType) {
              "time" -> end.getDouble("seconds")
              "distance" -> end.getDouble("meters")
              "manual" -> null
              else -> throw IllegalArgumentException("Unsupported running segment end")
            }
        val targetObject = node.optJSONObject("target")?.takeUnless { node.isNull("target") }
        val target =
            targetObject?.let {
              val type = it.getString("type")
              val keys =
                  when (type) {
                    "pace" -> "minSecondsPerKm" to "maxSecondsPerKm"
                    "speed" -> "minMetersPerSecond" to "maxMetersPerSecond"
                    "heart_rate" -> "minBpm" to "maxBpm"
                    else -> throw IllegalArgumentException("Unsupported running segment target")
                  }
              RunningSegmentTarget(type, it.getDouble(keys.first), it.getDouble(keys.second))
            }
        segments +=
            RunningSegmentSummary(
                node.getString("id"),
                node.optString("name").takeIf { node.has("name") && it.isNotBlank() },
                node.getString("phase"),
                RunningSegmentEnd(endType, endValue),
                target,
                Math.toIntExact(repeatCount))
      }
      "sequence" -> collectRunningSegments(node.getJSONArray("steps"), repeatCount, segments)
      "repeat" -> {
        val expanded = Math.multiplyExact(repeatCount, node.getInt("count").toLong())
        if (expanded > 10_000L) throw IllegalArgumentException("Running workout repeat limit")
        collectRunningSegments(node.getJSONArray("steps"), expanded, segments)
      }
      else -> throw IllegalArgumentException("Unsupported running workout step")
    }
  }
}
