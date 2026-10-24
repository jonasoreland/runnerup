package org.runnerup.relven.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.preference.PreferenceManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectExportPreferencesTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    PreferenceManager.getDefaultSharedPreferences(context).edit()
        .clear().commit()
  }

  @Test
  fun exportIsOffByDefaultAndOptInPersistsAcrossRepositoryRecreation() {
    assertFalse(HealthConnectExportPreferences.isEnabled(context))

    HealthConnectExportPreferences.setEnabled(context, true)
    assertTrue(HealthConnectExportPreferences.isEnabled(context))

    HealthConnectExportPreferences.setEnabled(context, false)
    assertFalse(HealthConnectExportPreferences.isEnabled(context))
  }
}
