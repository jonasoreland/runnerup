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
    records.forEach { record ->
      val input = record.input
      entries.put(
          JSONObject()
              .put("exerciseId", record.exerciseId)
              .put("revision", record.revision)
              .put("archived", record.isArchived)
              .put("nameEnglish", input.nameEnglish.trim())
              .put("nameSwedish", input.nameSwedish.trim())
              .put("category", input.category.key)
              .put("movementPattern", input.movementPattern.trim())
              .put("muscleGroups", input.muscleGroups.toJsonArray())
              .put("equipment", input.equipment.trimmed().toJsonArray())
              .put("instructionsEnglish", input.instructionsEnglish.trimmed().toJsonArray())
              .put("instructionsSwedish", input.instructionsSwedish.trimmed().toJsonArray()))
    }
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

  private fun JSONObject.stringList(key: String): List<String> {
    val array = getJSONArray(key)
    return (0 until array.length()).map { index -> array.getString(index) }
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
    const val MAX_FILE_BYTES = 1_048_576
    const val MAX_EXERCISES = 200
    const val MAX_NAME_LENGTH = 120
    const val MAX_INSTRUCTION_LENGTH = 500
    const val MAX_EQUIPMENT_LENGTH = 80
    const val MAX_LIST_ITEMS = 12
    val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    val KEY_PATTERN = Regex("^[a-z][a-z0-9_]{0,39}$")
    val ROOT_KEYS = setOf("format", "schemaVersion", "exercises")
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
