package org.runnerup.relven.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HealthConnectAvailabilityTest {
  @Test
  @Config(sdk = [24])
  fun reportsUnsupportedAndroidWithoutLoadingHealthConnectSdkClasses() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    assertEquals(HealthConnectAvailabilityStatus.UNSUPPORTED_ANDROID_VERSION,
        HealthConnectAvailability.status(context))
    assertEquals(emptySet<String>(), HealthConnectAvailability.declaredWritePermissionNames())
  }

  @Test
  @Config(sdk = [35])
  fun reportsProviderStatusThroughTheSdkBoundary() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    assertEquals(HealthConnectAvailabilityStatus.PROVIDER_UNAVAILABLE,
        HealthConnectAvailability.status(context))
  }

  @Test
  @Config(sdk = [35])
  fun onlyExposesTheDeclaredWritePermissions() {
    val permissions = HealthConnectAvailability.declaredWritePermissionNames()

    assertEquals(
        setOf(
            "android.permission.health.WRITE_EXERCISE",
            "android.permission.health.WRITE_EXERCISE_ROUTE",
            "android.permission.health.WRITE_DISTANCE",
            "android.permission.health.WRITE_HEART_RATE",
            "android.permission.health.WRITE_SPEED",
            "android.permission.health.WRITE_STEPS"),
        permissions)
    assertTrue(permissions.none { it.contains("READ_") })
  }
}
