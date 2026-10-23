package org.runnerup.relven.exercise

import android.content.Context
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/** Exposes the user's current local exercise collection to the explicit Plan Kit export action. */
object RelvenPlanKitExerciseData {
  @JvmStatic
  fun exportCustomCollection(context: Context): ByteArray =
      ExerciseCatalogRepository(context).exportCustomExercises()

  @JvmStatic
  fun exportCustomInventory(context: Context): ByteArray {
    val collection = JSONObject(String(exportCustomCollection(context), StandardCharsets.UTF_8))
    val exercises = collection.getJSONArray("exercises")
    val records = (0 until exercises.length()).map(exercises::getJSONObject)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.getString("nameSwedish") })
    val activeCount = records.count { !it.optBoolean("archived", false) }
    val archivedCount = records.size - activeCount
    val lines = mutableListOf(
        "# Custom exercise inventory",
        "",
        "${records.size} user-created exercises · $activeCount active · $archivedCount archived",
        "",
        "This index summarizes the canonical `user-data/custom-exercise-collection-v1.json` snapshot. Use that JSON for exact instructions, revisions, category, muscle groups, and equipment.",
        "Only active exercises with an empty equipment array are selectable for no-equipment plans.",
        "",
        "| Svenska | English | Exercise ID | Revision | Status | Category | Movement pattern | Muscle groups | Equipment |",
        "|---|---|---|---:|---|---|---|---|---|")
    for (record in records) {
      val equipment = (0 until record.getJSONArray("equipment").length())
          .map(record.getJSONArray("equipment")::getString)
          .joinToString(", ")
          .ifEmpty { "None" }
      val muscleGroups = (0 until record.getJSONArray("muscleGroups").length())
          .map(record.getJSONArray("muscleGroups")::getString)
          .joinToString(", ")
      lines += "| ${record.getString("nameSwedish").markdownCell()} | ${record.getString("nameEnglish").markdownCell()} | `${record.getString("exerciseId")}` | ${record.getInt("revision")} | ${if (record.optBoolean("archived", false)) "Archived" else "Active"} | ${record.getString("category")} | ${record.getString("movementPattern")} | ${muscleGroups.markdownCell()} | ${equipment.markdownCell()} |"
    }
    if (records.isEmpty()) lines += "\nNo user-created exercises are currently stored on this device."
    return (lines.joinToString("\n") + "\n").toByteArray(StandardCharsets.UTF_8)
  }

  private fun String.markdownCell(): String = replace("|", "\\|").replace("\n", " ").replace("\r", " ")
}
