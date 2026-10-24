package org.runnerup.relven.health

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25])
class HealthConnectSettingsApiGateTest {
  @Test
  fun unsupportedApiDoesNotLoadHealthConnectPermissionContractOrSdk() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    assertEquals(
        HealthConnectAvailabilityStatus.UNSUPPORTED_ANDROID_VERSION,
        HealthConnectAvailability.status(context))
    assertTrue(HealthConnectAvailability.declaredWritePermissionNames().isEmpty())
    assertNull(HealthConnectSettingsAccess.permissionRequestContract())
    assertTrue(HealthConnectSettingsAccess.grantedPermissions(context).isEmpty())
  }
}
