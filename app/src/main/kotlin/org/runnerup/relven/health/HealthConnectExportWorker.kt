package org.runnerup.relven.health

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/** Loads only through the API-26 bridge, so Android 24/25 never resolve Health Connect types. */
internal object HealthConnectExportScheduler {
  private const val BRIDGE_CLASS = "org.runnerup.relven.health.Api26HealthConnectExportScheduler"

  fun enqueue(context: Context, sessionId: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || sessionId.isBlank()) return
    try {
      Class.forName(BRIDGE_CLASS).getMethod("enqueue", Context::class.java, String::class.java)
          .invoke(null, context.applicationContext, sessionId)
    } catch (_: ReflectiveOperationException) {
      // The durable outbox remains available for the next explicit retry/backfill action.
    } catch (_: LinkageError) {
      // Keep recording and activity edits independent from optional Health Connect APIs.
    }
  }
}

@RequiresApi(Build.VERSION_CODES.O)
internal object Api26HealthConnectExportScheduler {
  @JvmStatic
  fun enqueue(context: Context, sessionId: String) {
    val request = OneTimeWorkRequestBuilder<HealthConnectExportWorker>()
        .setInputData(Data.Builder().putString(HealthConnectExportWorker.INPUT_SESSION_ID, sessionId).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    // Keep edits/deletes behind any in-flight Health Connect call for this activity.
    WorkManager.getInstance(context).enqueueUniqueWork(
        "relven-health-connect-${stableName(sessionId)}", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
  }

  private fun stableName(value: String): String = MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray(Charsets.UTF_8))
      .take(16)
      .joinToString("") { "%02x".format(it) }
}

@RequiresApi(Build.VERSION_CODES.O)
class HealthConnectExportWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    val sessionId = inputData.getString(INPUT_SESSION_ID)?.takeIf(String::isNotBlank) ?: return Result.success()
    val now = System.currentTimeMillis()
    val entry = try {
      HealthConnectExportStore.get(applicationContext, sessionId)
    } catch (error: IOException) {
      return Result.retry()
    } ?: return Result.success()
    if (entry.operation == HealthConnectExportOperation.DONE) return Result.success()

    val availability = HealthConnectAvailability.status(applicationContext)
    val client = if (availability == HealthConnectAvailabilityStatus.AVAILABLE) {
      try {
        HealthConnectClient.getOrCreate(applicationContext)
      } catch (error: Exception) {
        HealthConnectExportStore.markStatus(applicationContext, sessionId, entry.version,
            HealthConnectExportStatus.PROVIDER_UNAVAILABLE, error.message, now)
        return Result.retry()
      }
    } else {
      null
    }
    val granted = try {
      client?.permissionController?.getGrantedPermissions().orEmpty()
    } catch (error: SecurityException) {
      HealthConnectExportStore.markStatus(applicationContext, sessionId, entry.version,
          HealthConnectExportStatus.WAITING_PERMISSION, error.message, now)
      return Result.success()
    } catch (error: Exception) {
      HealthConnectExportStore.markStatus(applicationContext, sessionId, entry.version,
          HealthConnectExportStatus.RETRYABLE_ERROR, error.message, now)
      return Result.retry()
    }
    val outcome = HealthConnectExportProcessor.process(
        applicationContext,
        entry,
        granted,
        client?.let(::ClientGateway) ?: UnavailableGateway,
        exportOptedIn = HealthConnectExportPreferences.isEnabled(applicationContext),
        availability = availability,
        nowMillis = now)
    return when (outcome) {
      HealthConnectExportOutcome.COMPLETE,
      HealthConnectExportOutcome.WAITING_FOR_PERMISSION,
      HealthConnectExportOutcome.FAILED -> Result.success()
      HealthConnectExportOutcome.RETRY -> Result.retry()
    }
  }

  companion object {
    const val INPUT_SESSION_ID = "healthConnectSessionId"
  }
}

@RequiresApi(Build.VERSION_CODES.O)
private class ClientGateway(private val client: HealthConnectClient) : HealthConnectExportGateway {
  override suspend fun insertRecords(records: List<Record>) {
    client.insertRecords(records)
  }

  override suspend fun deleteRecords(kind: HealthConnectRecordKind, clientRecordId: String) {
    client.deleteRecords(recordTypeFor(kind), emptyList(), listOf(clientRecordId))
  }

  private fun recordTypeFor(kind: HealthConnectRecordKind): KClass<out Record> = when (kind) {
    HealthConnectRecordKind.SESSION -> ExerciseSessionRecord::class
    HealthConnectRecordKind.DISTANCE -> DistanceRecord::class
    HealthConnectRecordKind.HEART_RATE -> HeartRateRecord::class
    HealthConnectRecordKind.SPEED -> SpeedRecord::class
    HealthConnectRecordKind.CADENCE -> StepsCadenceRecord::class
  }
}

@RequiresApi(Build.VERSION_CODES.O)
private object UnavailableGateway : HealthConnectExportGateway {
  override suspend fun insertRecords(records: List<Record>) = Unit
  override suspend fun deleteRecords(kind: HealthConnectRecordKind, clientRecordId: String) = Unit
}
