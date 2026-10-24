package org.runnerup.relven.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.preference.PreferenceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelvenSettingsRepositoryTest {
  private lateinit var context: Context
  private lateinit var preferences: android.content.SharedPreferences

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    preferences = PreferenceManager.getDefaultSharedPreferences(context)
    preferences.edit().clear().commit()
  }

  @After
  fun tearDown() {
    preferences.edit().clear().commit()
  }

  @Test
  fun readsExistingRunnerUpKeysAndDefaultsWithoutRewritingThem() {
    preferences.edit()
        .putBoolean(context.getString(R.string.pref_keep_screen_on), true)
        .putBoolean(context.getString(R.string.pref_startgps), false)
        .putBoolean(context.getString(R.string.pref_autopause_active), true)
        .commit()

    val repository = RelvenSettingsRepository(context)

    assertTrue(repository.state.value.keepScreenOn)
    assertFalse(repository.state.value.autoStartGps)
    assertTrue(repository.state.value.autoPause)
    assertFalse(repository.state.value.autoLap)
    assertFalse(repository.state.value.autoLapIntervals)
    assertTrue(preferences.getBoolean(context.getString(R.string.pref_keep_screen_on), false))
  }

  @Test
  fun updatesWriteTheSameKeysUsedByLegacyWorkoutCode() {
    val repository = RelvenSettingsRepository(context)

    repository.setKeepScreenOn(true)
    repository.setAutoStartGps(false)
    repository.setAutoPause(true)
    repository.setAutoLap(true)
    repository.setAutoLapIntervals(true)

    assertTrue(preferences.getBoolean(context.getString(R.string.pref_keep_screen_on), false))
    assertFalse(preferences.getBoolean(context.getString(R.string.pref_startgps), true))
    assertTrue(preferences.getBoolean(context.getString(R.string.pref_autopause_active), false))
    assertTrue(preferences.getBoolean(context.getString(R.string.pref_autolap_active), false))
    assertTrue(preferences.getBoolean(context.getString(R.string.pref_step_autolap_active), false))
    assertEquals(true, repository.state.value.keepScreenOn)
    assertEquals(false, repository.state.value.autoStartGps)
    assertEquals(true, repository.state.value.autoPause)
    assertEquals(true, repository.state.value.autoLap)
    assertEquals(true, repository.state.value.autoLapIntervals)
  }

  @Test
  fun reflectsChangesMadeByExistingPreferenceScreens() {
    val repository = RelvenSettingsRepository(context)

    preferences.edit().putBoolean(context.getString(R.string.pref_startgps), false).commit()

    assertFalse(repository.state.value.autoStartGps)
  }

  @Test
  fun sensorAndUnitSettingsUseExistingRunnerUpPreferenceKeys() {
    val repository = RelvenSettingsRepository(context)

    repository.setUseCadenceSensor(false)
    repository.setUseTemperatureSensor(true)
    repository.setUsePressureSensor(true)
    repository.setDistanceUnit("mi")
    repository.setSpeedUnit("speed")

    assertFalse(preferences.getBoolean(context.getString(R.string.pref_use_cadence_step_sensor), true))
    assertTrue(preferences.getBoolean(context.getString(R.string.pref_use_temperature_sensor), false))
    assertTrue(preferences.getBoolean(context.getString(R.string.pref_use_pressure_sensor), false))
    assertEquals("mi", preferences.getString(context.getString(R.string.pref_unit), null))
    assertEquals("speed", preferences.getString(context.getString(R.string.pref_speedunit), null))
    assertEquals(false, repository.state.value.useCadenceSensor)
    assertEquals(true, repository.state.value.useTemperatureSensor)
    assertEquals(true, repository.state.value.usePressureSensor)
    assertEquals("mi", repository.state.value.distanceUnit)
    assertEquals("speed", repository.state.value.speedUnit)
  }

  @Test
  fun invalidUnitValuesAreIgnoredAndUnitDefaultsFollowExistingFormatterRules() {
    val distanceKey = context.getString(R.string.pref_unit)
    val speedKey = context.getString(R.string.pref_speedunit)
    preferences.edit().putString(distanceKey, "yards").putString(speedKey, "knots").commit()
    val repository = RelvenSettingsRepository(context)

    repository.setDistanceUnit("yards")
    repository.setSpeedUnit("knots")

    val expectedDistanceUnit = if (java.util.Locale.getDefault().country in setOf("US", "GB")) "mi" else "km"
    assertEquals(expectedDistanceUnit, repository.state.value.distanceUnit)
    assertEquals("pace", repository.state.value.speedUnit)
    assertEquals("yards", preferences.getString(distanceKey, null))
    assertEquals("knots", preferences.getString(speedKey, null))
  }

  @Test
  fun unitChangesFromLegacyPreferencesAndRepositoryAreVisibleAfterReopen() {
    val repository = RelvenSettingsRepository(context)
    val unitKey = context.getString(R.string.pref_unit)
    val speedKey = context.getString(R.string.pref_speedunit)

    preferences.edit().putString(unitKey, "mi").putString(speedKey, "speed").commit()

    assertEquals("mi", repository.state.value.distanceUnit)
    assertEquals("speed", repository.state.value.speedUnit)
    repository.close()

    val reopened = RelvenSettingsRepository(context)
    assertEquals("mi", reopened.state.value.distanceUnit)
    assertEquals("speed", reopened.state.value.speedUnit)
    reopened.close()
  }
}
