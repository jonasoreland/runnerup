package org.runnerup.relven.exercise

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class CollectionImportResult(val addedCount: Int, val unchangedCount: Int)
internal const val MAX_EXERCISE_COLLECTION_BYTES = 1_048_576

internal data class CustomExerciseInput(
    val nameEnglish: String,
    val nameSwedish: String,
    val category: ExerciseCategory,
    val movementPattern: String,
    val muscleGroups: List<String>,
    val equipment: List<String>,
    val instructionsEnglish: List<String>,
    val instructionsSwedish: List<String>
)

/** Private, offline storage for user-authored exercise definitions. */
internal class CustomExerciseStore(
    private val file: File,
    private val rename: (File, File) -> Boolean = { source, destination -> source.renameTo(destination) }
) {
  @Synchronized
  @Throws(IOException::class, IllegalArgumentException::class)
  fun load(languageTag: String): List<ExerciseEntry> {
    val language = if (languageTag.lowercase(Locale.ROOT).startsWith("sv")) "sv" else "en"
    return readRecords().map { record -> record.toEntry(language) }
  }

  @Synchronized
  @Throws(IOException::class, IllegalArgumentException::class)
  fun create(input: CustomExerciseInput): String {
    validate(input)
    val records = readRecords().toMutableList()
    require(records.size < MAX_EXERCISES) { "Custom exercise limit reached" }
    val id = UUID.randomUUID().toString()
    records.add(Record(id, 1, input))
    writeRecords(records)
    return id
  }

  @Synchronized
  @Throws(IOException::class, IllegalArgumentException::class)
  fun update(exerciseId: String, input: CustomExerciseInput) {
    validate(input)
    val records = readRecords().toMutableList()
    val index = records.indexOfFirst { it.exerciseId == exerciseId }
    require(index >= 0) { "Custom exercise does not exist" }
    val current = records[index]
    require(current.revision < Int.MAX_VALUE) { "Custom exercise revision limit reached" }
    records[index] = current.copy(revision = current.revision + 1, input = input)
    writeRecords(records)
  }

  @Synchronized
  @Throws(IOException::class, IllegalArgumentException::class)
  fun setArchived(exerciseId: String, archived: Boolean) {
    val records = readRecords().toMutableList()
    val index = records.indexOfFirst { it.exerciseId == exerciseId }
    require(index >= 0) { "Custom exercise does not exist" }
    records[index] = records[index].copy(isArchived = archived)
    writeRecords(records)
  }

  @Synchronized
  @Throws(IOException::class, IllegalArgumentException::class)
  fun exportCollection(): ByteArray {
    val exercises = JSONArray()
    readRecords().forEach { exercises.put(it.toJson()) }
    val bytes =
        JSONObject()
            .put("format", COLLECTION_FORMAT)
            .put("schemaVersion", COLLECTION_SCHEMA_VERSION)
            .put("exercises", exercises)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
    if (bytes.size > MAX_EXERCISE_COLLECTION_BYTES) {
      throw IOException("Exercise collection is too large to export")
    }
    return bytes
  }

  @Synchronized
  @Throws(IOException::class, IllegalArgumentException::class)
  fun importCollection(bytes: ByteArray, bundledExerciseIds: Set<String>): CollectionImportResult {
    require(bytes.size <= MAX_EXERCISE_COLLECTION_BYTES) { "Exercise collection is too large" }
    val incoming =
        try {
          val root = JSONObject(String(bytes, StandardCharsets.UTF_8))
          requireKeys(root, COLLECTION_ROOT_KEYS)
          require(root.requireExactString("format") == COLLECTION_FORMAT) {
            "Unsupported exercise collection format"
          }
          require(root.requireExactInt("schemaVersion") == COLLECTION_SCHEMA_VERSION) {
            "Unsupported exercise collection version"
          }
          val array = root.getJSONArray("exercises")
          require(array.length() <= MAX_EXERCISES) { "Exercise collection limit exceeded" }
          val records = (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            require(value.keys().asSequence().toSet() == RECORD_KEYS) {
              "Exercise collection record has missing or unexpected fields"
            }
            value.validateCollectionRecordTypes()
            parseRecord(value)
          }
          require(records.map(Record::exerciseId).toSet().size == records.size) {
            "Exercise collection IDs must be unique"
          }
          records
        } catch (error: JSONException) {
          throw IllegalArgumentException("Exercise collection is invalid", error)
        }

    val merged = readRecords().toMutableList()
    val existingById = merged.associateBy(Record::exerciseId)
    var addedCount = 0
    var unchangedCount = 0
    incoming.forEach { record ->
      require(record.exerciseId !in bundledExerciseIds) {
        "Exercise collection ID conflicts with a bundled exercise"
      }
      val existing = existingById[record.exerciseId]
      if (existing == null) {
        merged.add(record)
        addedCount++
      } else {
        require(existing == record) {
          "Exercise collection conflicts with an existing custom exercise"
        }
        unchangedCount++
      }
    }
    require(merged.size <= MAX_EXERCISES) { "Custom exercise limit exceeded" }
    if (addedCount > 0) writeRecords(merged)
    return CollectionImportResult(addedCount, unchangedCount)
  }

  private fun readRecords(): List<Record> {
    recoverInterruptedReplacement()
    if (!file.exists()) return emptyList()
    if (file.length() > MAX_FILE_BYTES) throw IllegalArgumentException("Custom exercise data is too large")
    val text = AtomicFile(file).openRead().use { it.readBytes().toString(StandardCharsets.UTF_8) }
    return try {
      val root = JSONObject(text)
      requireKeys(root, ROOT_KEYS)
      require(root.getString("format") == FORMAT) { "Unsupported custom exercise data format" }
      require(root.getInt("schemaVersion") == SCHEMA_VERSION) {
        "Unsupported custom exercise data version"
      }
      val array = root.getJSONArray("exercises")
      require(array.length() <= MAX_EXERCISES) { "Custom exercise limit exceeded" }
      val records = (0 until array.length()).map { index -> parseRecord(array.getJSONObject(index)) }
      require(records.map(Record::exerciseId).toSet().size == records.size) {
        "Custom exercise IDs must be unique"
      }
      records
    } catch (error: JSONException) {
      throw IllegalArgumentException("Custom exercise data is invalid", error)
    }
  }

  private fun parseRecord(value: JSONObject): Record {
    val actualKeys = value.keys().asSequence().toSet()
    require(actualKeys == RECORD_KEYS || actualKeys == RECORD_KEYS - "archived") {
      "Custom exercise data has unexpected fields"
    }
    val id = value.getString("exerciseId")
    require(id.matches(UUID_PATTERN)) { "Custom exercise ID must be a lowercase UUID" }
    val revision = value.getInt("revision")
    require(revision > 0) { "Custom exercise revision must be positive" }
    val category = ExerciseCategory.fromKey(value.getString("category"))
        ?: throw IllegalArgumentException("Unknown custom exercise category")
    val input =
        CustomExerciseInput(
            value.getString("nameEnglish"),
            value.getString("nameSwedish"),
            category,
            value.getString("movementPattern"),
            value.stringList("muscleGroups"),
            value.stringList("equipment"),
            value.stringList("instructionsEnglish"),
            value.stringList("instructionsSwedish"))
    validate(input)
    val archived = if (value.has("archived")) value.getBoolean("archived") else false
    return Record(id, revision, input, archived)
  }

  private fun writeRecords(records: List<Record>) {
    val entries = JSONArray()
    records.forEach { entries.put(it.toJson()) }
    val content =
        JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("exercises", entries)
            .toString()
    val bytes = content.toByteArray(StandardCharsets.UTF_8)
    if (bytes.size > MAX_FILE_BYTES) throw IOException("Custom exercise data is too large to save")
    val parent = file.parentFile
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw IOException("Could not create exercise storage")
    }
    val staging = File(parent, "${file.name}.${UUID.randomUUID()}.tmp")
    val atomic = AtomicFile(staging)
    var output: java.io.FileOutputStream? = null
    try {
      output = atomic.startWrite()
      output.write(bytes)
      atomic.finishWrite(output)
      output = null
      val backup = backupFile()
      val hadExistingFile = file.exists()
      if (hadExistingFile && !rename(file, backup)) {
        throw IOException("Could not preserve existing custom exercise data")
      }
      if (!rename(staging, file)) {
        if (hadExistingFile) rename(backup, file)
        throw IOException("Could not replace custom exercise data")
      }
      // The committed file is already durable. A stale backup is removed on the next read;
      // cleanup failure must not report a failed save after the new record was committed.
      if (hadExistingFile) backup.delete()
    } catch (error: IOException) {
      output?.let(atomic::failWrite)
      atomic.delete()
      staging.delete()
      throw error
    }
  }

  private fun recoverInterruptedReplacement() {
    val backup = backupFile()
    if (!file.exists() && backup.exists()) {
      if (!rename(backup, file)) throw IOException("Could not restore custom exercise data")
    } else if (file.exists() && backup.exists()) {
      backup.delete()
    }
  }

  private fun backupFile() = File(file.parentFile, "${file.name}.bak")

  private fun validate(input: CustomExerciseInput) {
    validateText(input.nameEnglish, MAX_NAME_LENGTH, "English name")
    validateText(input.nameSwedish, MAX_NAME_LENGTH, "Swedish name")
    require(input.movementPattern.trim().matches(KEY_PATTERN)) { "Movement pattern is invalid" }
    require(input.muscleGroups.isNotEmpty() && input.muscleGroups.size <= MAX_LIST_ITEMS) {
      "Select between 1 and $MAX_LIST_ITEMS muscle groups"
    }
    require(input.muscleGroups.distinct().size == input.muscleGroups.size) {
      "Muscle groups must be unique"
    }
    require(input.muscleGroups.all { it in MUSCLE_GROUP_KEYS }) { "Unknown muscle group" }
    require(input.equipment.size <= MAX_LIST_ITEMS) { "Too many equipment items" }
    require(input.equipment.distinctBy { it.trim().lowercase(Locale.ROOT) }.size == input.equipment.size) {
      "Equipment items must be unique"
    }
    input.equipment.forEach { validateText(it, MAX_EQUIPMENT_LENGTH, "Equipment") }
    validateInstructionList(input.instructionsEnglish, "English instructions")
    validateInstructionList(input.instructionsSwedish, "Swedish instructions")
  }

  private fun validateInstructionList(values: List<String>, field: String) {
    require(values.size in 1..MAX_LIST_ITEMS) { "$field must contain 1 to $MAX_LIST_ITEMS steps" }
    values.forEach { validateText(it, MAX_INSTRUCTION_LENGTH, field) }
  }

  private fun validateText(value: String, maxLength: Int, field: String) {
    val normalized = value.trim()
    require(normalized.isNotEmpty() && normalized.length <= maxLength) { "$field is required and must be at most $maxLength characters" }
  }

  private fun Record.toEntry(language: String) =
      ExerciseEntry(
          exerciseId,
          revision,
          if (language == "sv") input.nameSwedish.trim() else input.nameEnglish.trim(),
          input.category,
          input.movementPattern.trim(),
          input.muscleGroups.toList(),
          input.equipment.trimmed(),
          (if (language == "sv") input.instructionsSwedish else input.instructionsEnglish).trimmed(),
          isCustom = true,
          customInput = input,
          isArchived = isArchived)

  private fun Record.toJson() =
      JSONObject()
          .put("exerciseId", exerciseId)
          .put("revision", revision)
          .put("archived", isArchived)
          .put("nameEnglish", input.nameEnglish.trim())
          .put("nameSwedish", input.nameSwedish.trim())
          .put("category", input.category.key)
          .put("movementPattern", input.movementPattern.trim())
          .put("muscleGroups", input.muscleGroups.toJsonArray())
          .put("equipment", input.equipment.trimmed().toJsonArray())
          .put("instructionsEnglish", input.instructionsEnglish.trimmed().toJsonArray())
          .put("instructionsSwedish", input.instructionsSwedish.trimmed().toJsonArray())

  private fun JSONObject.stringList(key: String): List<String> {
    val array = getJSONArray(key)
    return (0 until array.length()).map { index -> array.getString(index) }
  }

  private fun JSONObject.validateCollectionRecordTypes() {
    requireCanonicalString("exerciseId")
    requireExactInt("revision")
    require(opt("archived") is Boolean) { "Exercise collection archive state must be boolean" }
    requireCanonicalString("nameEnglish")
    requireCanonicalString("nameSwedish")
    requireCanonicalString("category")
    requireCanonicalString("movementPattern")
    listOf("muscleGroups", "equipment", "instructionsEnglish", "instructionsSwedish").forEach { key ->
      val array = opt(key) as? JSONArray
          ?: throw IllegalArgumentException("Exercise collection $key must be an array")
      require((0 until array.length()).all { array.opt(it) is String }) {
        "Exercise collection $key must contain only strings"
      }
      require((0 until array.length()).all { index ->
        val value = array.getString(index)
        value == value.trim()
      }) { "Exercise collection $key must not contain surrounding whitespace" }
    }
  }

  private fun JSONObject.requireCanonicalString(key: String): String {
    val value = requireExactString(key)
    require(value == value.trim()) { "Exercise collection $key must not contain surrounding whitespace" }
    return value
  }

  private fun JSONObject.requireExactString(key: String): String =
      opt(key) as? String ?: throw IllegalArgumentException("Exercise collection $key must be a string")

  private fun JSONObject.requireExactInt(key: String): Int {
    val value = opt(key)
    require(value is Int || value is Long) { "Exercise collection $key must be an integer" }
    val integer = (value as Number).toLong()
    require(integer in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
      "Exercise collection $key is out of range"
    }
    return integer.toInt()
  }

  private fun List<String>.trimmed() = map(String::trim)
  private fun List<String>.toJsonArray() = JSONArray().also { array -> forEach(array::put) }

  private fun requireKeys(value: JSONObject, expected: Set<String>) {
    require(value.keys().asSequence().toSet() == expected) { "Custom exercise data has unexpected fields" }
  }

  private data class Record(
      val exerciseId: String,
      val revision: Int,
      val input: CustomExerciseInput,
      val isArchived: Boolean = false)

  private companion object {
    const val FORMAT = "relven.custom-exercises"
    const val SCHEMA_VERSION = 1
    const val COLLECTION_FORMAT = "relven.exercise-collection"
    const val COLLECTION_SCHEMA_VERSION = 1
    const val MAX_FILE_BYTES = 1_048_576
    const val MAX_EXERCISES = 200
    const val MAX_NAME_LENGTH = 120
    const val MAX_INSTRUCTION_LENGTH = 500
    const val MAX_EQUIPMENT_LENGTH = 80
    const val MAX_LIST_ITEMS = 12
    val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    val KEY_PATTERN = Regex("^[a-z][a-z0-9_]{0,39}$")
    val ROOT_KEYS = setOf("format", "schemaVersion", "exercises")
    val COLLECTION_ROOT_KEYS = ROOT_KEYS
    val RECORD_KEYS =
        setOf(
            "exerciseId",
            "revision",
            "archived",
            "nameEnglish",
            "nameSwedish",
            "category",
            "movementPattern",
            "muscleGroups",
            "equipment",
            "instructionsEnglish",
            "instructionsSwedish")
    val MUSCLE_GROUP_KEYS =
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
  }
}
