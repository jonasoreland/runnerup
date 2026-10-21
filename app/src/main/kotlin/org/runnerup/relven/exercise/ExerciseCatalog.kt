package org.runnerup.relven.exercise

import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal enum class ExerciseCategory(val key: String) {
  LOWER_BODY("lower_body"),
  UPPER_BODY("upper_body"),
  CORE("core"),
  WARMUP_MOBILITY("warmup_mobility");

  companion object {
    fun fromKey(value: String): ExerciseCategory? = entries.firstOrNull { it.key == value }
  }
}

internal data class ExerciseEntry(
    val exerciseId: String,
    val revision: Int,
    val name: String,
    val category: ExerciseCategory,
    val movementPattern: String,
    val muscleGroups: List<String>,
    val equipment: List<String>,
    val instructions: List<String>,
    val isCustom: Boolean = false,
    val customInput: CustomExerciseInput? = null,
    val isArchived: Boolean = false
)

internal data class ExerciseCatalog(
    val libraryRevision: Int,
    val language: String,
    val exercises: List<ExerciseEntry>
) {
  fun withCustomExercises(custom: List<ExerciseEntry>): ExerciseCatalog {
    require(custom.all(ExerciseEntry::isCustom)) { "Only custom exercises can be merged" }
    val bundledIds = exercises.mapTo(HashSet(), ExerciseEntry::exerciseId)
    val customIds = custom.map(ExerciseEntry::exerciseId)
    require(customIds.toSet().size == customIds.size) { "Custom exercise IDs must be unique" }
    require(custom.none { it.exerciseId in bundledIds }) {
      "Custom exercise ID conflicts with the bundled library"
    }
    return copy(exercises = exercises + custom)
  }

  fun filter(query: String, category: ExerciseCategory?): List<ExerciseEntry> {
    val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank)
    return exercises.filter { exercise ->
      (category == null || exercise.category == category) &&
          terms.all { term ->
            exercise.name.lowercase(Locale.ROOT).contains(term) ||
                exercise.instructions.any { it.lowercase(Locale.ROOT).contains(term) } ||
                exercise.muscleGroups.any { it.replace('_', ' ').contains(term) } ||
                exercise.movementPattern.replace('_', ' ').contains(term)
          }
    }
  }

  companion object {
    private const val FORMAT = "relven.exercise-library"
    private const val SCHEMA_VERSION = 1
    private const val MAX_JSON_CHARACTERS = 1_048_576
    private const val MAX_EXERCISES = 256
    private const val ID_PATTERN =
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    private val keyPattern = Regex("^[a-z][a-z0-9_]{0,39}$")
    private val muscleGroupKeys =
        setOf(
            "abdominals",
            "back",
            "calves",
            "chest",
            "glutes",
            "hamstrings",
            "hips",
            "obliques",
            "quadriceps",
            "shoulders",
            "triceps")

    fun parse(json: String, languageTag: String = "en"): ExerciseCatalog {
      require(json.length <= MAX_JSON_CHARACTERS) { "Exercise library is too large" }
      try {
        val root = JSONObject(json)
        requireKeys(root, setOf("format", "schemaVersion", "libraryRevision", "exercises"))
        require(root.requiredString("format") == FORMAT) { "Unsupported exercise library format" }
        require(root.requiredInt("schemaVersion") == SCHEMA_VERSION) {
          "Unsupported exercise library version"
        }
        val libraryRevision = root.requiredInt("libraryRevision")
        require(libraryRevision > 0) { "Exercise library revision must be positive" }
        val sourceEntries = root.requiredArray("exercises")
        require(sourceEntries.length() in 1..MAX_EXERCISES) {
          "Exercise library must contain between 1 and $MAX_EXERCISES entries"
        }
        val language = if (languageTag.lowercase(Locale.ROOT).startsWith("sv")) "sv" else "en"
        val ids = HashSet<String>()
        val exercises = ArrayList<ExerciseEntry>(sourceEntries.length())
        for (index in 0 until sourceEntries.length()) {
          val source = sourceEntries.opt(index) as? JSONObject
              ?: throw IllegalArgumentException("Exercise entry $index must be an object")
          val entry = parseEntry(source, language)
          require(ids.add(entry.exerciseId)) { "Exercise IDs must be unique" }
          exercises.add(entry)
        }
        return ExerciseCatalog(libraryRevision, language, exercises.toList())
      } catch (error: JSONException) {
        throw IllegalArgumentException("Exercise library is not valid JSON", error)
      }
    }

    private fun parseEntry(source: JSONObject, language: String): ExerciseEntry {
      requireKeys(
          source,
          setOf(
              "exerciseId",
              "revision",
              "name",
              "category",
              "movementPattern",
              "muscleGroups",
              "equipment",
              "instructions"))
      val id = source.requiredString("exerciseId")
      require(id.matches(Regex(ID_PATTERN))) { "Exercise ID must be a lowercase UUID" }
      val revision = source.requiredInt("revision")
      require(revision > 0) { "Exercise revision must be positive" }
      val name = source.requiredLocalizedString("name", language, maxLength = 120)
      val categoryKey = source.requiredString("category")
      val category = ExerciseCategory.fromKey(categoryKey)
          ?: throw IllegalArgumentException("Unknown exercise category")
      val movementPattern = source.requiredString("movementPattern")
      require(movementPattern.matches(keyPattern)) { "Invalid movement pattern" }
      val muscleGroups = source.requiredStringList("muscleGroups", 1, 12, 40)
      muscleGroups.forEach { require(it in muscleGroupKeys) { "Unknown muscle group key" } }
      val equipment = source.requiredStringList("equipment", 0, 12, 80)
      val instructions = source.requiredLocalizedStringList("instructions", language, 1, 12, 500)
      return ExerciseEntry(
          id, revision, name, category, movementPattern, muscleGroups, equipment, instructions)
    }

    private fun requireKeys(value: JSONObject, expected: Set<String>) {
      val actual = value.keys().asSequence().toSet()
      require(actual == expected) { "Unexpected or missing exercise library fields" }
    }

    private fun JSONObject.requiredString(key: String): String {
      val value = opt(key) as? String ?: throw IllegalArgumentException("Missing string: $key")
      require(value.isNotBlank()) { "Blank string: $key" }
      return value.trim()
    }

    private fun JSONObject.requiredInt(key: String): Int {
      val value = opt(key)
      require(value is Int || value is Long) { "Missing integer: $key" }
      val longValue = (value as Number).toLong()
      require(longValue in 0..Int.MAX_VALUE.toLong()) { "Integer is out of range: $key" }
      return longValue.toInt()
    }

    private fun JSONObject.requiredArray(key: String): JSONArray =
        opt(key) as? JSONArray ?: throw IllegalArgumentException("Missing array: $key")

    private fun JSONObject.requiredLocalizedString(
        key: String, language: String, maxLength: Int): String {
      val values = opt(key) as? JSONObject
          ?: throw IllegalArgumentException("Missing localized string: $key")
      requireKeys(values, setOf("en", "sv"))
      val english = values.requiredString("en")
      val swedish = values.requiredString("sv")
      require(english.length <= maxLength && swedish.length <= maxLength) {
        "Localized string is too long: $key"
      }
      return if (language == "sv") swedish else english
    }

    private fun JSONObject.requiredLocalizedStringList(
        key: String, language: String, min: Int, max: Int, maxLength: Int): List<String> {
      val values = opt(key) as? JSONObject
          ?: throw IllegalArgumentException("Missing localized instructions")
      requireKeys(values, setOf("en", "sv"))
      val english = values.requiredStringList("en", min, max, maxLength)
      val swedish = values.requiredStringList("sv", min, max, maxLength)
      return if (language == "sv") swedish else english
    }

    private fun JSONObject.requiredStringList(
        key: String, min: Int, max: Int, maxLength: Int): List<String> {
      val array = requiredArray(key)
      require(array.length() in min..max) { "Invalid list size: $key" }
      return (0 until array.length()).map { index ->
        val value = array.opt(index) as? String
            ?: throw IllegalArgumentException("Invalid string in $key")
        val normalized = value.trim()
        require(normalized.isNotEmpty() && normalized.length <= maxLength) {
          "Invalid string length in $key"
        }
        normalized
      }
    }
  }
}
