package org.runnerup.relven.health

import android.content.Context
import android.os.Build
import android.system.Os
import androidx.annotation.RequiresApi
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal enum class HealthConnectExportOperation { UPSERT, DELETE, DONE }

internal enum class HealthConnectExportStatus {
  PENDING,
  EXPORTED,
  EXPORTED_WITH_OMISSIONS,
  WAITING_PERMISSION,
  PROVIDER_UNAVAILABLE,
  UNSUPPORTED,
  RETRYABLE_ERROR,
  FAILED,
  DELETED
}

internal enum class HealthConnectRecordKind(val clientIdSuffix: String) {
  SESSION("session"),
  DISTANCE("distance"),
  HEART_RATE("heart-rate"),
  SPEED("speed"),
  CADENCE("cadence")
}

internal data class HealthConnectExportEntry(
    val sessionId: String,
    val version: Long,
    val operation: HealthConnectExportOperation,
    val status: HealthConnectExportStatus,
    val payload: String?,
    val ownedRecordKinds: Set<HealthConnectRecordKind>,
    val omittedPermissions: Set<String> = emptySet(),
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val updatedAtMillis: Long)

/** Per-session durable outbox records, separate from but stored beside RunnerUp's source DB. */
internal object HealthConnectExportStore {
  private const val DIRECTORY = "relven-health-connect-outbox"
  private const val SCHEMA_VERSION = 1
  private const val MAX_ENTRY_BYTES = 50 * 1024 * 1024

  @RequiresApi(Build.VERSION_CODES.O)
  @Synchronized
  @Throws(IOException::class)
  fun enqueueUpsert(context: Context, workout: RecordedWorkoutForHealthConnect, nowMillis: Long): HealthConnectExportEntry {
    require(workout.sessionId.isNotBlank()) { "Missing Health Connect session identity" }
    val existing = read(context, workout.sessionId)
    if (existing != null && existing.operation != HealthConnectExportOperation.DELETE &&
        existing.status in setOf(HealthConnectExportStatus.PENDING, HealthConnectExportStatus.EXPORTED,
            HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS, HealthConnectExportStatus.WAITING_PERMISSION,
            HealthConnectExportStatus.PROVIDER_UNAVAILABLE, HealthConnectExportStatus.RETRYABLE_ERROR) &&
        existing.payload == HealthConnectWorkoutCodec.encode(workout.copy(version = existing.version))) return existing
    val version = if (existing == null) maxOf(workout.version, 0L) else existing.version + 1L
    val entry = HealthConnectExportEntry(
        sessionId = workout.sessionId,
        version = version,
        operation = HealthConnectExportOperation.UPSERT,
        status = HealthConnectExportStatus.PENDING,
        payload = HealthConnectWorkoutCodec.encode(workout.copy(version = version)),
        ownedRecordKinds = existing?.ownedRecordKinds.orEmpty(),
        updatedAtMillis = nowMillis)
    write(context, entry)
    return entry
  }

  @Synchronized
  @RequiresApi(Build.VERSION_CODES.O)
  @Throws(IOException::class)
  fun enqueueDelete(context: Context, sessionId: String, nowMillis: Long): HealthConnectExportEntry? {
    val existing = read(context, sessionId) ?: return null
    if (existing.operation == HealthConnectExportOperation.DELETE) return existing
    val pendingRecordKinds = if (existing.operation == HealthConnectExportOperation.UPSERT && existing.payload != null) {
      mapWorkoutForHealthConnect(HealthConnectWorkoutCodec.decode(existing.payload)).mapNotNull(::kindFor).toSet()
    } else {
      emptySet()
    }
    val deleteKinds = existing.ownedRecordKinds + pendingRecordKinds
    if (deleteKinds.isEmpty()) {
      deleteFile(context, sessionId)
      return null
    }
    val entry = existing.copy(
        operation = HealthConnectExportOperation.DELETE,
        status = HealthConnectExportStatus.PENDING,
        payload = null,
        ownedRecordKinds = deleteKinds,
        omittedPermissions = emptySet(),
        attemptCount = 0,
        lastError = null,
        updatedAtMillis = nowMillis)
    write(context, entry)
    return entry
  }

  @Synchronized
  @Throws(IOException::class)
  fun get(context: Context, sessionId: String): HealthConnectExportEntry? = read(context, sessionId)

  @Synchronized
  @Throws(IOException::class)
  fun pending(context: Context): List<HealthConnectExportEntry> = entries(context).filter {
    it.operation != HealthConnectExportOperation.DONE
  }

  @Synchronized
  @Throws(IOException::class)
  fun all(context: Context): List<HealthConnectExportEntry> = entries(context).sortedByDescending {
    it.updatedAtMillis
  }

  @Synchronized
  @Throws(IOException::class)
  fun retry(context: Context, sessionId: String, nowMillis: Long): HealthConnectExportEntry? {
    val current = read(context, sessionId) ?: return null
    if (current.operation == HealthConnectExportOperation.DONE) return null
    val updated = current.copy(
        status = HealthConnectExportStatus.PENDING,
        attemptCount = 0,
        lastError = null,
        updatedAtMillis = nowMillis)
    write(context, updated)
    return updated
  }

  @Synchronized
  @RequiresApi(Build.VERSION_CODES.O)
  @Throws(IOException::class)
  fun retryOmitted(context: Context, sessionId: String, nowMillis: Long): HealthConnectExportEntry? {
    val current = read(context, sessionId) ?: return null
    if (current.operation != HealthConnectExportOperation.DONE ||
        current.status != HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS ||
        current.omittedPermissions.isEmpty() || current.payload == null) return null
    val nextVersion = current.version + 1L
    val updated = current.copy(
        version = nextVersion,
        operation = HealthConnectExportOperation.UPSERT,
        status = HealthConnectExportStatus.PENDING,
        payload = HealthConnectWorkoutCodec.encode(
            HealthConnectWorkoutCodec.decode(current.payload).copy(version = nextVersion)),
        omittedPermissions = emptySet(),
        attemptCount = 0,
        lastError = null,
        updatedAtMillis = nowMillis)
    write(context, updated)
    return updated
  }

  @Synchronized
  @Throws(IOException::class)
  fun markExported(
      context: Context,
      sessionId: String,
      expectedVersion: Long,
      recordKinds: Set<HealthConnectRecordKind>,
      omittedPermissions: Set<String>,
      nowMillis: Long): Boolean {
    val current = read(context, sessionId) ?: return false
    if (current.operation != HealthConnectExportOperation.UPSERT || current.version != expectedVersion) return false
    write(context, current.copy(
        operation = HealthConnectExportOperation.DONE,
        status = if (omittedPermissions.isEmpty()) HealthConnectExportStatus.EXPORTED
            else HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS,
        ownedRecordKinds = recordKinds,
        omittedPermissions = omittedPermissions,
        attemptCount = 0,
        lastError = null,
        updatedAtMillis = nowMillis))
    return true
  }

  @Synchronized
  @Throws(IOException::class)
  fun markDeleted(context: Context, sessionId: String, nowMillis: Long): Boolean {
    val current = read(context, sessionId) ?: return false
    if (current.operation != HealthConnectExportOperation.DELETE) return false
    write(context, current.copy(
        operation = HealthConnectExportOperation.DONE,
        status = HealthConnectExportStatus.DELETED,
        payload = null,
        ownedRecordKinds = emptySet(),
        omittedPermissions = emptySet(),
        attemptCount = 0,
        lastError = null,
        updatedAtMillis = nowMillis))
    return true
  }

  @Synchronized
  @Throws(IOException::class)
  fun markStatus(
      context: Context,
      sessionId: String,
      expectedVersion: Long,
      status: HealthConnectExportStatus,
      error: String? = null,
      nowMillis: Long): Boolean {
    val current = read(context, sessionId) ?: return false
    if (current.version != expectedVersion || current.operation == HealthConnectExportOperation.DONE) return false
    write(context, current.copy(
        status = status,
        attemptCount = current.attemptCount + 1,
        lastError = error?.take(500),
        updatedAtMillis = nowMillis))
    return true
  }

  private fun entries(context: Context): List<HealthConnectExportEntry> {
    val directory = directory(context)
    val files = directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }
        ?: if (!directory.exists()) return emptyList() else throw IOException("Could not list Health Connect outbox")
    return files.mapNotNull { file ->
      try {
        readFile(file)
      } catch (error: Exception) {
        throw IOException("Invalid Health Connect outbox entry ${file.name}", error)
      }
    }.sortedBy(HealthConnectExportEntry::updatedAtMillis)
  }

  private fun read(context: Context, sessionId: String): HealthConnectExportEntry? {
    require(sessionId.isNotBlank()) { "Missing Health Connect session identity" }
    val file = file(context, sessionId)
    if (!file.exists()) return null
    return try {
      readFile(file).also { require(it.sessionId == sessionId) { "Outbox filename identity mismatch" } }
    } catch (error: Exception) {
      throw IOException("Invalid Health Connect outbox entry", error)
    }
  }

  private fun readFile(file: File): HealthConnectExportEntry {
    if (file.length() > MAX_ENTRY_BYTES) throw IOException("Health Connect outbox entry is too large")
    val text = file.inputStream().use { it.readBytes().toString(StandardCharsets.UTF_8) }
    val root = JSONObject(text)
    require(root.getInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported Health Connect outbox schema" }
    val sessionId = root.getString("sessionId")
    val version = root.getLong("version")
    val operation = HealthConnectExportOperation.valueOf(root.getString("operation"))
    val status = HealthConnectExportStatus.valueOf(root.getString("status"))
    val payload = if (root.isNull("payload")) null else root.getString("payload")
    val recordKinds = enumSet(root.getJSONArray("ownedRecordKinds"), HealthConnectRecordKind::valueOf)
    val permissions = strings(root.getJSONArray("omittedPermissions"))
    val attemptCount = root.getInt("attemptCount")
    val lastError = if (root.isNull("lastError")) null else root.getString("lastError")
    val updated = root.getLong("updatedAtMillis")
    require(sessionId.isNotBlank() && version >= 0 && attemptCount >= 0 && updated >= 0) { "Invalid outbox metadata" }
    require(operation != HealthConnectExportOperation.UPSERT || payload != null) { "Upsert is missing payload" }
    require(operation != HealthConnectExportOperation.DELETE || payload == null) { "Delete contains a workout payload" }
    require(operation != HealthConnectExportOperation.DELETE || recordKinds.isNotEmpty()) { "Delete without owned record IDs" }
    return HealthConnectExportEntry(sessionId, version, operation, status, payload, recordKinds, permissions,
        attemptCount, lastError, updated)
  }

  private fun write(context: Context, entry: HealthConnectExportEntry) {
    val root = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("sessionId", entry.sessionId)
        .put("version", entry.version)
        .put("operation", entry.operation.name)
        .put("status", entry.status.name)
        .put("payload", entry.payload)
        .put("ownedRecordKinds", JSONArray(entry.ownedRecordKinds.map(HealthConnectRecordKind::name)))
        .put("omittedPermissions", JSONArray(entry.omittedPermissions.sorted()))
        .put("attemptCount", entry.attemptCount)
        .put("lastError", entry.lastError)
        .put("updatedAtMillis", entry.updatedAtMillis)
    val bytes = root.toString().toByteArray(StandardCharsets.UTF_8)
    if (bytes.size > MAX_ENTRY_BYTES) throw IOException("Health Connect outbox entry is too large")
    val target = file(context, entry.sessionId)
    val replacement = File(target.path + ".new")
    if (replacement.exists() && !replacement.delete()) {
      throw IOException("Could not clear incomplete Health Connect outbox write")
    }
    try {
      FileOutputStream(replacement).use { stream ->
        stream.write(bytes)
        stream.fd.sync()
      }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          try {
            Files.move(replacement.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
          } catch (_: AtomicMoveNotSupportedException) {
            Files.move(replacement.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
          }
        } else {
          Os.rename(replacement.absolutePath, target.absolutePath)
        }
      } catch (error: Exception) {
        throw IOException("Could not atomically replace Health Connect outbox entry", error)
      }
    } catch (error: IOException) {
      replacement.delete()
      throw error
    }
  }

  private fun deleteFile(context: Context, sessionId: String) {
    val file = file(context, sessionId)
    if (file.exists() && !file.delete()) throw IOException("Could not remove unexported outbox entry")
  }

  private fun directory(context: Context) = context.getDir(DIRECTORY, Context.MODE_PRIVATE)

  private fun file(context: Context, sessionId: String): File =
      File(directory(context), "${hash(sessionId)}.json")

  private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray(StandardCharsets.UTF_8))
      .joinToString("") { "%02x".format(it) }

  private inline fun <reified T : Enum<T>> enumSet(values: JSONArray, parse: (String) -> T): Set<T> =
      (0 until values.length()).map { parse(values.getString(it)) }.toSet()

  private fun strings(values: JSONArray): Set<String> =
      (0 until values.length()).map(values::getString).toSet()

  private fun kindFor(record: Record): HealthConnectRecordKind = when (record) {
    is ExerciseSessionRecord -> HealthConnectRecordKind.SESSION
    is DistanceRecord -> HealthConnectRecordKind.DISTANCE
    is HeartRateRecord -> HealthConnectRecordKind.HEART_RATE
    is SpeedRecord -> HealthConnectRecordKind.SPEED
    is StepsCadenceRecord -> HealthConnectRecordKind.CADENCE
    else -> error("Unexpected Health Connect record type: ${record::class.java.simpleName}")
  }
}

@RequiresApi(Build.VERSION_CODES.O)
internal object HealthConnectWorkoutCodec {
  fun encode(workout: RecordedWorkoutForHealthConnect): String {
    val route = JSONArray()
    workout.route.forEach { point ->
      val item = JSONObject()
          .put("time", point.time.toEpochMilli())
          .put("latitude", point.latitude)
          .put("longitude", point.longitude)
      point.altitudeMeters?.let { item.put("altitudeMeters", it) }
      point.heartRateBpm?.let { item.put("heartRateBpm", it) }
      point.speedMetersPerSecond?.let { item.put("speedMetersPerSecond", it) }
      point.cadencePerMinute?.let { item.put("cadencePerMinute", it) }
      route.put(item)
    }
    val laps = JSONArray()
    workout.laps.forEach { lap ->
      val item = JSONObject().put("startTime", lap.startTime.toEpochMilli()).put("endTime", lap.endTime.toEpochMilli())
      lap.distanceMeters?.let { item.put("distanceMeters", it) }
      laps.put(item)
    }
    val root = JSONObject()
        .put("schemaVersion", 1)
        .put("sessionId", workout.sessionId)
        .put("version", workout.version)
        .put("activity", workout.activity.name)
        .put("startTime", workout.startTime.toEpochMilli())
        .put("endTime", workout.endTime?.toEpochMilli())
        .put("disposition", workout.disposition.name)
        .put("route", route)
        .put("laps", laps)
    workout.title?.let { root.put("title", it) }
    workout.notes?.let { root.put("notes", it) }
    workout.distanceMeters?.let { root.put("distanceMeters", it) }
    return root.toString()
  }

  fun decode(json: String): RecordedWorkoutForHealthConnect {
    val root = JSONObject(json)
    require(root.getInt("schemaVersion") == 1) { "Unsupported Health Connect workout payload" }
    val routeJson = root.getJSONArray("route")
    require(routeJson.length() <= 200_000) { "Health Connect route payload too large" }
    val route = (0 until routeJson.length()).map { index ->
      val point = routeJson.getJSONObject(index)
      HealthConnectRouteSample(
          java.time.Instant.ofEpochMilli(point.getLong("time")), point.getDouble("latitude"), point.getDouble("longitude"),
          point.optionalDouble("altitudeMeters"), point.optionalDouble("heartRateBpm"),
          point.optionalDouble("speedMetersPerSecond"), point.optionalDouble("cadencePerMinute"))
    }
    val lapsJson = root.getJSONArray("laps")
    require(lapsJson.length() <= 100_000) { "Too many Health Connect laps" }
    val laps = (0 until lapsJson.length()).map { index ->
      val lap = lapsJson.getJSONObject(index)
      HealthConnectLap(java.time.Instant.ofEpochMilli(lap.getLong("startTime")),
          java.time.Instant.ofEpochMilli(lap.getLong("endTime")), lap.optionalDouble("distanceMeters"))
    }
    return RecordedWorkoutForHealthConnect(
        sessionId = root.getString("sessionId"),
        version = root.getLong("version"),
        activity = HealthConnectActivity.valueOf(root.getString("activity")),
        title = root.optionalString("title"),
        notes = root.optionalString("notes"),
        startTime = java.time.Instant.ofEpochMilli(root.getLong("startTime")),
        endTime = root.optionalLong("endTime")?.let(java.time.Instant::ofEpochMilli),
        disposition = HealthConnectSessionDisposition.valueOf(root.getString("disposition")),
        distanceMeters = root.optionalDouble("distanceMeters"), route = route, laps = laps)
  }

  private fun JSONObject.optionalDouble(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
  private fun JSONObject.optionalLong(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
  private fun JSONObject.optionalString(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
