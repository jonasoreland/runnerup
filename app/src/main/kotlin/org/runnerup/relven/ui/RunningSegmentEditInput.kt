package org.runnerup.relven.ui

import org.json.JSONObject

internal fun runningSegmentEndInput(type: String, rawValue: String): JSONObject? {
  if (type == "manual") return JSONObject().put("type", "manual")
  val value = rawValue.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 && it <= 1_000_000_000.0 } ?: return null
  return when (type) {
    "time" -> JSONObject().put("type", type).put("seconds", value)
    "distance" -> JSONObject().put("type", type).put("meters", value)
    else -> null
  }
}

internal fun runningSegmentTargetInput(
    type: String,
    rawMinimum: String,
    rawMaximum: String
): JSONObject? {
  if (type == "none") return null
  val minimum = rawMinimum.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 && it <= 1_000_000_000.0 } ?: return null
  val maximum = rawMaximum.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 && it <= 1_000_000_000.0 } ?: return null
  if (minimum > maximum) return null
  val keys = when (type) {
    "pace" -> "minSecondsPerKm" to "maxSecondsPerKm"
    "speed" -> "minMetersPerSecond" to "maxMetersPerSecond"
    "heart_rate" -> "minBpm" to "maxBpm"
    else -> return null
  }
  if (type == "heart_rate" && (minimum < 30 || maximum > 240 || minimum % 1.0 != 0.0 || maximum % 1.0 != 0.0)) return null
  return JSONObject().put("type", type).put(keys.first, minimum).put(keys.second, maximum)
}
