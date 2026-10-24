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
}
