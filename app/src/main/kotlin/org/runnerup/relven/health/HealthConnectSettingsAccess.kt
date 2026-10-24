package org.runnerup.relven.health

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RequiresApi
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal data class HealthConnectSessionStatus(
    val sessionId: String,
    val workoutName: String?,
    val status: HealthConnectExportStatus,
    val updatedAtMillis: Long,
    val lastError: String?,
    val omittedPermissions: Set<String>)

internal data class HealthConnectSettingsSnapshot(
    val availability: HealthConnectAvailabilityStatus,
    val exportEnabled: Boolean,
    val grantedPermissions: Set<String>,
    val eligibleBackfillCount: Int,
    val queuedCount: Int,
    val exportedCount: Int,
    val sessions: List<HealthConnectSessionStatus>,
    val retryableCount: Int = 0) {
  companion object {
    fun unavailable(context: Context) = HealthConnectSettingsSnapshot(
        availability = HealthConnectAvailability.status(context),
        exportEnabled = HealthConnectExportPreferences.isEnabled(context),
        grantedPermissions = emptySet(),
        eligibleBackfillCount = 0,
        queuedCount = 0,
        exportedCount = 0,
        sessions = emptyList())
  }
}

internal data class HealthConnectBackfillResult(
    val queued: Int,
    val alreadyPublished: Int,
    val failed: Int)

/** API-safe settings facade; Health Connect SDK classes stay inside the API 26 bridge. */
internal object HealthConnectSettingsAccess {
  private const val BRIDGE = "org.runnerup.relven.health.Api26HealthConnectSettingsBridge"

  fun permissionRequestContract(): ActivityResultContract<Set<String>, Set<String>>? =
      invokeBridgeInferred("createPermissionRequestContract") as? ActivityResultContract<Set<String>, Set<String>>

  suspend fun grantedPermissions(context: Context): Set<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptySet()
    return suspendCoroutine { continuation ->
      try {
        val bridge = Class.forName(BRIDGE)
        val method = bridge.getMethod("grantedPermissions", Context::class.java, Continuation::class.java)
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(null, context.applicationContext, continuation)
        if (result !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
          continuation.resume(result as Set<String>)
        }
      } catch (error: ReflectiveOperationException) {
        continuation.resumeWithException(error.cause ?: error)
      } catch (error: LinkageError) {
        continuation.resumeWithException(error)
      }
    }
  }

  fun snapshot(context: Context, database: SQLiteDatabase, granted: Set<String>): HealthConnectSettingsSnapshot {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return HealthConnectSettingsSnapshot.unavailable(context)
    return invokeBridgeTyped("snapshot", Context::class.java, SQLiteDatabase::class.java, Set::class.java,
        context.applicationContext, database, granted) as? HealthConnectSettingsSnapshot
        ?: HealthConnectSettingsSnapshot.unavailable(context)
  }

  fun queueBackfill(context: Context, database: SQLiteDatabase): HealthConnectBackfillResult {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return HealthConnectBackfillResult(0, 0, 0)
    return invokeBridgeTyped("queueBackfill", Context::class.java, SQLiteDatabase::class.java,
        context.applicationContext, database) as? HealthConnectBackfillResult
        ?: HealthConnectBackfillResult(0, 0, 1)
  }

  fun syncPending(context: Context, grantedPermissions: Set<String>): Int =
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) 0 else
          (invokeBridgeTyped("syncPending", Context::class.java, Set::class.java,
              context.applicationContext, grantedPermissions) as? Int ?: 0)

  fun retry(context: Context, sessionId: String, grantedPermissions: Set<String>): Boolean =
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) false else
          (invokeBridgeTyped("retry", Context::class.java, String::class.java, Set::class.java,
              context.applicationContext, sessionId, grantedPermissions) as? Boolean ?: false)

  fun settingsIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) null else
      invokeBridgeTyped("settingsIntent", Context::class.java, context.applicationContext) as? Intent

  private fun invokeBridgeInferred(method: String, vararg arguments: Any?): Any? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    return try {
      val parameterTypes = arguments.filterNotNull().map { it.javaClass }.toTypedArray()
      Class.forName(BRIDGE).getMethod(method, *parameterTypes).invoke(null, *arguments)
    } catch (_: ReflectiveOperationException) {
      null
    } catch (_: LinkageError) {
      null
    }
  }

  private fun invokeBridgeTyped(method: String, vararg signatureAndArguments: Any): Any? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val signature = signatureAndArguments.filterIsInstance<Class<*>>().toTypedArray()
    val arguments = signatureAndArguments.filterNot { it is Class<*> }.toTypedArray()
    return try {
      Class.forName(BRIDGE).getMethod(method, *signature).invoke(null, *arguments)
    } catch (_: ReflectiveOperationException) {
      null
    } catch (_: LinkageError) {
      null
    }
  }
}

internal object HealthConnectBackfillPolicy {
  private val published = setOf(
      HealthConnectExportStatus.EXPORTED,
      HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS,
      HealthConnectExportStatus.DELETED)

  fun eligibleSessionIds(
      activityIds: List<String>,
      entries: List<HealthConnectExportEntry>): List<String> {
    val byId = entries.associateBy(HealthConnectExportEntry::sessionId)
    return activityIds.distinct().filter { id ->
      val existing = byId[id]
      existing == null ||
          (existing.operation == HealthConnectExportOperation.UPSERT &&
              existing.status !in published)
    }
  }

  fun omissionCanRetry(entry: HealthConnectExportEntry, grantedPermissions: Set<String>): Boolean =
      entry.operation == HealthConnectExportOperation.DONE &&
          entry.status == HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS &&
          entry.omittedPermissions.any(grantedPermissions::contains)

  fun databaseActivityId(sessionId: String): Long? =
      sessionId.takeIf { it.startsWith("activity-") }
          ?.removePrefix("activity-")
          ?.toLongOrNull()
}

@RequiresApi(Build.VERSION_CODES.O)
internal object Api26HealthConnectSettingsBridge {
  @JvmStatic
  fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
      androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()

  @JvmStatic
  suspend fun grantedPermissions(context: Context): Set<String> =
      androidx.health.connect.client.HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

  @JvmStatic
  fun snapshot(
      context: Context,
      database: SQLiteDatabase,
      granted: Set<String>): HealthConnectSettingsSnapshot {
    val activities = readCompletedActivityIds(database)
    val entries = HealthConnectExportStore.all(context)
    val eligible = HealthConnectBackfillPolicy.eligibleSessionIds(activities, entries)
    val sessions = entries.map { entry ->
      HealthConnectSessionStatus(
          entry.sessionId,
          entry.payload?.let { payload ->
            runCatching { HealthConnectWorkoutCodec.decode(payload).title }.getOrNull()
          },
          entry.status,
          entry.updatedAtMillis,
          entry.lastError,
          entry.omittedPermissions)
    }
    return HealthConnectSettingsSnapshot(
        availability = HealthConnectAvailability.status(context),
        exportEnabled = HealthConnectExportPreferences.isEnabled(context),
        grantedPermissions = granted,
        eligibleBackfillCount = eligible.size,
        queuedCount = entries.count { it.operation != HealthConnectExportOperation.DONE },
        exportedCount = entries.count { it.status in setOf(
            HealthConnectExportStatus.EXPORTED, HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS) },
        sessions = sessions,
        retryableCount = entries.count { HealthConnectBackfillPolicy.omissionCanRetry(it, granted) })
  }

  @JvmStatic
  fun queueBackfill(context: Context, database: SQLiteDatabase): HealthConnectBackfillResult {
    if (!HealthConnectExportPreferences.isEnabled(context)) return HealthConnectBackfillResult(0, 0, 0)
    val entries = HealthConnectExportStore.all(context)
    val existing = entries.associateBy(HealthConnectExportEntry::sessionId)
    val ids = readCompletedActivityIds(database)
    val eligible = HealthConnectBackfillPolicy.eligibleSessionIds(ids, entries)
    var queued = 0
    val alreadyPublished = ids.count { id ->
      existing[id]?.status in setOf(
          HealthConnectExportStatus.EXPORTED, HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS)
    }
    var failed = 0
    for (activityId in eligible) {
      try {
        val sessionId = activityId
        val databaseId = HealthConnectBackfillPolicy.databaseActivityId(sessionId)
        if (databaseId == null) {
          failed++
          continue
        }
        val entry = existing[sessionId]?.let { HealthConnectExportStore.retry(context, sessionId, System.currentTimeMillis()) }
            ?: readRecordedHealthConnectWorkout(context, database, databaseId)?.let {
              HealthConnectExportStore.enqueueUpsert(context, it, System.currentTimeMillis())
            }
        if (entry == null) {
          failed++
        } else {
          HealthConnectExportScheduler.enqueue(context, entry.sessionId)
          queued++
        }
      } catch (_: IOException) {
        failed++
      } catch (_: RuntimeException) {
        failed++
      }
    }
    return HealthConnectBackfillResult(queued, alreadyPublished, failed)
  }

  @JvmStatic
  fun syncPending(context: Context, grantedPermissions: Set<String>): Int {
    val pending = HealthConnectExportStore.pending(context)
    var scheduled = 0
    pending.forEach { entry ->
      if (entry.operation == HealthConnectExportOperation.DELETE || HealthConnectExportPreferences.isEnabled(context)) {
        HealthConnectExportScheduler.enqueue(context, entry.sessionId)
        scheduled++
      }
    }
    if (HealthConnectExportPreferences.isEnabled(context)) {
      HealthConnectExportStore.all(context)
          .filter { HealthConnectBackfillPolicy.omissionCanRetry(it, grantedPermissions) }
          .forEach { entry ->
            val retried = HealthConnectExportStore.retryOmitted(
                context, entry.sessionId, System.currentTimeMillis())
            if (retried != null) {
              HealthConnectExportScheduler.enqueue(context, retried.sessionId)
              scheduled++
            }
          }
    }
    return scheduled
  }

  @JvmStatic
  fun retry(context: Context, sessionId: String, grantedPermissions: Set<String>): Boolean {
    val current = HealthConnectExportStore.get(context, sessionId) ?: return false
    val entry = (if (current.operation != HealthConnectExportOperation.DONE) {
      HealthConnectExportStore.retry(context, sessionId, System.currentTimeMillis())
    } else if (HealthConnectBackfillPolicy.omissionCanRetry(current, grantedPermissions)) {
      HealthConnectExportStore.retryOmitted(context, sessionId, System.currentTimeMillis())
    } else {
      null
    }) ?: return false
    HealthConnectExportScheduler.enqueue(context, entry.sessionId)
    return true
  }

  @JvmStatic
  fun settingsIntent(context: Context): Intent =
      androidx.health.connect.client.HealthConnectClient.getHealthConnectManageDataIntent(context)

  private fun readCompletedActivityIds(database: SQLiteDatabase): List<String> = database.query(
      org.runnerup.common.util.Constants.DB.ACTIVITY.TABLE,
      arrayOf("_id"),
      "${org.runnerup.common.util.Constants.DB.ACTIVITY.DELETED} = 0 AND ${org.runnerup.common.util.Constants.DB.ACTIVITY.TIME} > 0",
      null, null, null,
      "${org.runnerup.common.util.Constants.DB.ACTIVITY.START_TIME} DESC").use { cursor ->
    buildList { while (cursor.moveToNext()) add("activity-${cursor.getLong(0)}") }
  }
}

private const val HEALTH_CONNECT_WRITE_EXERCISE = "android.permission.health.WRITE_EXERCISE"

internal fun canEnableHealthConnectExport(grantedPermissions: Set<String>): Boolean =
    HEALTH_CONNECT_WRITE_EXERCISE in grantedPermissions
