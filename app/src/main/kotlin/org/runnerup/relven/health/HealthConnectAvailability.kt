package org.runnerup.relven.health

import android.content.Context
import android.os.Build

enum class HealthConnectAvailabilityStatus {
  AVAILABLE,
  PROVIDER_UNAVAILABLE,
  PROVIDER_UPDATE_REQUIRED,
  UNSUPPORTED_ANDROID_VERSION,
  UNKNOWN_ERROR
}

/** Keeps the API 26+ Health Connect SDK implementation out of the API 24/25 class-loading path. */
object HealthConnectAvailability {
  private const val API_26_IMPLEMENTATION =
      "org.runnerup.relven.health.Api26HealthConnectAvailability"

  fun status(context: Context): HealthConnectAvailabilityStatus {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return HealthConnectAvailabilityStatus.UNSUPPORTED_ANDROID_VERSION
    }
    return try {
      val implementation = Class.forName(API_26_IMPLEMENTATION)
      implementation.getMethod("status", Context::class.java)
          .invoke(null, context.applicationContext) as HealthConnectAvailabilityStatus
    } catch (_: ReflectiveOperationException) {
      HealthConnectAvailabilityStatus.UNKNOWN_ERROR
    } catch (_: LinkageError) {
      HealthConnectAvailabilityStatus.UNKNOWN_ERROR
    }
  }

  /** A write-only permission set used by the future user-initiated permission flow. */
  fun declaredWritePermissionNames(): Set<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptySet()
    return try {
      val implementation = Class.forName(API_26_IMPLEMENTATION)
      @Suppress("UNCHECKED_CAST")
      implementation.getMethod("writePermissionNames").invoke(null) as Set<String>
    } catch (_: ReflectiveOperationException) {
      emptySet()
    } catch (_: LinkageError) {
      emptySet()
    }
  }
}
