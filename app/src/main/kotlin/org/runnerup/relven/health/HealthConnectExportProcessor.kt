package org.runnerup.relven.health

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface HealthConnectExportGateway {
  suspend fun insertRecords(records: List<Record>)
  suspend fun deleteRecords(kind: HealthConnectRecordKind, clientRecordId: String)
}

internal enum class HealthConnectExportOutcome { COMPLETE, WAITING_FOR_PERMISSION, RETRY, FAILED }

/** Runs the outbox state machine behind a narrow gateway so write/retry behavior is testable. */
@RequiresApi(Build.VERSION_CODES.O)
internal object HealthConnectExportProcessor {
  private val sessionLocks = ConcurrentHashMap<String, Mutex>()
  private const val WRITE_EXERCISE = "android.permission.health.WRITE_EXERCISE"
  private const val WRITE_ROUTE = "android.permission.health.WRITE_EXERCISE_ROUTE"
  private const val WRITE_DISTANCE = "android.permission.health.WRITE_DISTANCE"
  private const val WRITE_HEART_RATE = "android.permission.health.WRITE_HEART_RATE"
  private const val WRITE_SPEED = "android.permission.health.WRITE_SPEED"
  private const val WRITE_STEPS = "android.permission.health.WRITE_STEPS"

  suspend fun process(
      context: android.content.Context,
      entry: HealthConnectExportEntry,
      grantedPermissions: Set<String>,
      gateway: HealthConnectExportGateway,
      exportOptedIn: Boolean,
      availability: HealthConnectAvailabilityStatus,
      nowMillis: Long): HealthConnectExportOutcome =
      sessionLocks.getOrPut(entry.sessionId) { Mutex() }.withLock {
        processLocked(context, entry, grantedPermissions, gateway, exportOptedIn, availability, nowMillis)
      }

  private suspend fun processLocked(
      context: android.content.Context,
      entry: HealthConnectExportEntry,
      grantedPermissions: Set<String>,
      gateway: HealthConnectExportGateway,
      exportOptedIn: Boolean,
      availability: HealthConnectAvailabilityStatus,
      nowMillis: Long): HealthConnectExportOutcome {
    val latest = HealthConnectExportStore.get(context, entry.sessionId)
    if (latest == null || latest.version != entry.version || latest.operation != entry.operation) {
      return HealthConnectExportOutcome.COMPLETE
    }
    if (entry.operation == HealthConnectExportOperation.DONE) return HealthConnectExportOutcome.COMPLETE
    if (entry.operation == HealthConnectExportOperation.UPSERT && !exportOptedIn) {
      return HealthConnectExportOutcome.COMPLETE
    }
    if (availability == HealthConnectAvailabilityStatus.UNSUPPORTED_ANDROID_VERSION) {
      HealthConnectExportStore.markStatus(context, entry.sessionId, entry.version,
          HealthConnectExportStatus.UNSUPPORTED, nowMillis = nowMillis)
      return HealthConnectExportOutcome.FAILED
    }
    if (availability != HealthConnectAvailabilityStatus.AVAILABLE) {
      HealthConnectExportStore.markStatus(context, entry.sessionId, entry.version,
          HealthConnectExportStatus.PROVIDER_UNAVAILABLE, availability.name, nowMillis)
      return HealthConnectExportOutcome.RETRY
    }

    return try {
      if (entry.operation == HealthConnectExportOperation.DELETE) {
        for (kind in entry.ownedRecordKinds) {
          val required = permissionFor(kind)
          if (required !in grantedPermissions) {
            HealthConnectExportStore.markStatus(context, entry.sessionId, entry.version,
                HealthConnectExportStatus.WAITING_PERMISSION, required, nowMillis)
            return HealthConnectExportOutcome.WAITING_FOR_PERMISSION
          }
          gateway.deleteRecords(kind, clientRecordId(entry.sessionId, kind))
        }
        HealthConnectExportStore.markDeleted(context, entry.sessionId, nowMillis)
        HealthConnectExportOutcome.COMPLETE
      } else {
        if (WRITE_EXERCISE !in grantedPermissions) {
          HealthConnectExportStore.markStatus(context, entry.sessionId, entry.version,
              HealthConnectExportStatus.WAITING_PERMISSION, WRITE_EXERCISE, nowMillis)
          return HealthConnectExportOutcome.WAITING_FOR_PERMISSION
        }
        val workout = HealthConnectWorkoutCodec.decode(entry.payload!!)
        val fullRecords = mapWorkoutForHealthConnect(workout)
        val expectedOptionalPermissions = expectedOptionalPermissions(workout, fullRecords)
        val omitted = (expectedOptionalPermissions - grantedPermissions).toMutableSet()
        val capabilities = HealthConnectWriteCapabilities(
            route = WRITE_ROUTE in grantedPermissions,
            distance = WRITE_DISTANCE in grantedPermissions,
            heartRate = WRITE_HEART_RATE in grantedPermissions,
            speed = WRITE_SPEED in grantedPermissions,
            cadence = WRITE_STEPS in grantedPermissions)
        val records = mapWorkoutForHealthConnect(workout, capabilities)
        gateway.insertRecords(records)
        val newKinds = records.mapNotNull(::kindFor).toSet()
        val retainedKinds = mutableSetOf<HealthConnectRecordKind>()
        for (oldKind in entry.ownedRecordKinds - newKinds) {
          val required = permissionFor(oldKind)
          if (required in grantedPermissions) {
            gateway.deleteRecords(oldKind, clientRecordId(entry.sessionId, oldKind))
          } else {
            retainedKinds += oldKind
            omitted += required
          }
        }
        HealthConnectExportStore.markExported(context, entry.sessionId, entry.version,
            newKinds + retainedKinds, omitted, nowMillis)
        HealthConnectExportOutcome.COMPLETE
      }
    } catch (error: SecurityException) {
      HealthConnectExportStore.markStatus(context, entry.sessionId, entry.version,
          HealthConnectExportStatus.WAITING_PERMISSION, "Health Connect permission was revoked", nowMillis)
      HealthConnectExportOutcome.WAITING_FOR_PERMISSION
    } catch (error: IllegalArgumentException) {
      HealthConnectExportStore.markStatus(context, entry.sessionId, entry.version,
          HealthConnectExportStatus.FAILED, error.message, nowMillis)
      HealthConnectExportOutcome.FAILED
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      HealthConnectExportStore.markStatus(context, entry.sessionId, entry.version,
          HealthConnectExportStatus.RETRYABLE_ERROR, error.message ?: error.javaClass.simpleName, nowMillis)
      HealthConnectExportOutcome.RETRY
    }
  }

  private fun kindFor(record: Record): HealthConnectRecordKind = when (record) {
    is ExerciseSessionRecord -> HealthConnectRecordKind.SESSION
    is DistanceRecord -> HealthConnectRecordKind.DISTANCE
    is HeartRateRecord -> HealthConnectRecordKind.HEART_RATE
    is SpeedRecord -> HealthConnectRecordKind.SPEED
    is StepsCadenceRecord -> HealthConnectRecordKind.CADENCE
    else -> error("Unexpected Health Connect record type: ${record::class.java.simpleName}")
  }

  private fun permissionFor(kind: HealthConnectRecordKind): String = when (kind) {
    HealthConnectRecordKind.SESSION -> WRITE_EXERCISE
    HealthConnectRecordKind.DISTANCE -> WRITE_DISTANCE
    HealthConnectRecordKind.HEART_RATE -> WRITE_HEART_RATE
    HealthConnectRecordKind.SPEED -> WRITE_SPEED
    HealthConnectRecordKind.CADENCE -> WRITE_STEPS
  }

  private fun expectedOptionalPermissions(
      workout: RecordedWorkoutForHealthConnect,
      records: List<Record>): Set<String> = buildSet {
    if (records.any { it is ExerciseSessionRecord && it.exerciseRouteResult is androidx.health.connect.client.records.ExerciseRouteResult.Data }) add(WRITE_ROUTE)
    if (records.any { it is DistanceRecord }) add(WRITE_DISTANCE)
    if (records.any { it is HeartRateRecord }) add(WRITE_HEART_RATE)
    if (records.any { it is SpeedRecord }) add(WRITE_SPEED)
    if (records.any { it is StepsCadenceRecord } && workout.activity in setOf(
            HealthConnectActivity.Running, HealthConnectActivity.TreadmillRunning, HealthConnectActivity.Walking)) add(WRITE_STEPS)
  }

  private fun clientRecordId(sessionId: String, kind: HealthConnectRecordKind) =
      "relven-$sessionId-${kind.clientIdSuffix}"
}
