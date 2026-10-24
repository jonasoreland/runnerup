package org.runnerup.relven.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import kotlin.reflect.KClass

/** Health Connect SDK calls live here so they are reached only after the Android API 26 gate. */
internal object Api26HealthConnectAvailability {
  private const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

  @JvmStatic
  fun status(context: Context): HealthConnectAvailabilityStatus =
      when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PROVIDER_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailabilityStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
          HealthConnectAvailabilityStatus.PROVIDER_UPDATE_REQUIRED
        else -> HealthConnectAvailabilityStatus.PROVIDER_UNAVAILABLE
      }

  @JvmStatic
  fun writePermissionNames(): Set<String> =
      setOf(
          writePermission(ExerciseSessionRecord::class),
          HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
          writePermission(DistanceRecord::class),
          writePermission(HeartRateRecord::class),
          writePermission(SpeedRecord::class),
          writePermission(StepsCadenceRecord::class))

  private fun writePermission(record: KClass<out androidx.health.connect.client.records.Record>) =
      HealthPermission.getWritePermission(record)
}
