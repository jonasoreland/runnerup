package org.runnerup.relven.ui

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import org.runnerup.workout.RelvenStrengthWorkoutParser
import org.runnerup.workout.RelvenWorkoutImporter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class FavoriteWorkout(
    val id: String,
    val name: String,
    val workoutJson: String,
    val sport: String)

/** Device-private, offline copies of workouts saved independently from their source plan. */
internal object FavoriteWorkoutStore {
  private const val VERSION = 1
  private const val MAX_FAVORITES = 100
  private const val MAX_FILE_BYTES = 16_777_216
  private const val PREFERENCES_NAME = "relven.favorite-workouts"
  private const val FAVORITES_KEY = "favorites_v1"

  @Synchronized
  @Throws(IOException::class)
  fun read(context: Context): List<FavoriteWorkout> {
    val json = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getString(FAVORITES_KEY, null) ?: return emptyList()
    if (json.toByteArray(StandardCharsets.UTF_8).size > MAX_FILE_BYTES) {
      throw IOException("Saved favorites are too large")
    }
    return try {
      val root = JSONObject(json)
      require(root.getInt("version") == VERSION) { "Unsupported favorite workout file version" }
      val items = root.getJSONArray("favorites")
      require(items.length() <= MAX_FAVORITES) { "Too many favorite workouts" }
      val seenIds = mutableSetOf<String>()
      (0 until items.length()).map { index ->
        val item = items.getJSONObject(index)
        val id = item.getString("id")
        require(id.isNotBlank() && seenIds.add(id)) { "Invalid favorite workout identity" }
        val name = item.getString("name")
        val workoutJson = item.getString("workoutJson")
        val validated = validateWorkout(name, workoutJson)
        FavoriteWorkout(id, name, validated, JSONObject(validated).getString("sport"))
      }
    } catch (error: Exception) {
      throw IOException("Saved favorite workouts are invalid", error)
    }
  }

  @Synchronized
  @Throws(IOException::class)
  fun save(context: Context, name: String, workoutJson: String): FavoriteWorkout {
    val title = name.trim()
    require(title.isNotEmpty() && title.length <= 80) { "Favorite name must contain 1–80 characters" }
    val canonicalJson = validateWorkout(title, workoutJson)
    val favorites = read(context)
    require(favorites.size < MAX_FAVORITES) { "Favorite workout limit reached" }
    val favorite =
        FavoriteWorkout(UUID.randomUUID().toString(), title, canonicalJson,
            JSONObject(canonicalJson).getString("sport"))
    write(context, favorites + favorite)
    return favorite
  }

  @Synchronized
  @Throws(IOException::class)
  fun remove(context: Context, id: String): Boolean {
    require(id.isNotBlank()) { "Missing favorite workout identity" }
    val favorites = read(context)
    val updated = favorites.filterNot { it.id == id }
    if (updated.size == favorites.size) return false
    write(context, updated)
    return true
  }

  private fun validateWorkout(name: String, json: String): String {
    try {
      val document = JSONObject(json)
      require(document.getInt("schemaVersion") == 3) { "Favorite must contain a Workout v3 document" }
      val renamed = document.put("name", name).toString()
      when (JSONObject(renamed).getString("sport")) {
        "running" -> RelvenWorkoutImporter.parse(renamed)
        "strength", "prehab" -> RelvenStrengthWorkoutParser.parse(renamed)
        else -> throw IllegalArgumentException("Unsupported favorite workout type")
      }
      return renamed
    } catch (error: IllegalArgumentException) {
      throw error
    } catch (error: Exception) {
      throw IllegalArgumentException("Favorite must contain a valid Workout v3 document", error)
    }
  }

  private fun write(context: Context, favorites: List<FavoriteWorkout>) {
    val array = JSONArray()
    favorites.forEach { favorite ->
      array.put(JSONObject()
          .put("id", favorite.id)
          .put("name", favorite.name)
          .put("workoutJson", favorite.workoutJson))
    }
    val content = JSONObject().put("version", VERSION).put("favorites", array)
        .toString().toByteArray(StandardCharsets.UTF_8)
    require(content.size <= MAX_FILE_BYTES) { "Saved favorite workouts file is too large" }
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    preferences.edit(commit = true) {
      putString(FAVORITES_KEY, String(content, StandardCharsets.UTF_8))
    }
  }
}
