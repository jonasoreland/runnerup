package org.runnerup.relven.exercise

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Stores the user's locally favorited exercises by their stable catalog IDs. */
internal class ExerciseFavoritesStore(private val preferences: SharedPreferences) {
  constructor(context: Context) : this(
      context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

  fun read(): Set<String> =
      preferences.getStringSet(FAVORITE_IDS_KEY, emptySet()).orEmpty().filterTo(mutableSetOf()) {
        EXERCISE_ID_PATTERN.matches(it)
      }

  fun setFavorite(exerciseId: String, favorite: Boolean) {
    require(EXERCISE_ID_PATTERN.matches(exerciseId)) { "Exercise ID must be a UUID" }
    val updated = read().toMutableSet().apply {
      if (favorite) add(exerciseId) else remove(exerciseId)
    }
    preferences.edit { putStringSet(FAVORITE_IDS_KEY, updated) }
  }

  private companion object {
    const val PREFERENCES_NAME = "relven.exercise-favorites"
    const val FAVORITE_IDS_KEY = "favorite_ids"
    val EXERCISE_ID_PATTERN =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
  }
}
