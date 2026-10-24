package org.runnerup.relven.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.runnerup.R

/** Typed, observable access to workout preferences that remain shared with legacy RunnerUp code. */
class RelvenSettingsRepository(context: Context) : AutoCloseable {
  private val appContext = context.applicationContext
  private val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
  private val keepScreenOnKey = appContext.getString(R.string.pref_keep_screen_on)
  private val autoStartGpsKey = appContext.getString(R.string.pref_startgps)
  private val autoPauseKey = appContext.getString(R.string.pref_autopause_active)
  private val autoLapKey = appContext.getString(R.string.pref_autolap_active)
  private val intervalAutoLapKey = appContext.getString(R.string.pref_step_autolap_active)
  private val mutableState = MutableStateFlow(readState())
  val state: StateFlow<RelvenSettingsSnapshot> = mutableState.asStateFlow()

  private val listener =
      SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in observedKeys) mutableState.value = readState()
      }

  private val observedKeys: Set<String>
    get() = setOf(keepScreenOnKey, autoStartGpsKey, autoPauseKey, autoLapKey, intervalAutoLapKey)

  init {
    preferences.registerOnSharedPreferenceChangeListener(listener)
  }

  fun setKeepScreenOn(enabled: Boolean) = update(keepScreenOnKey, enabled)

  fun setAutoStartGps(enabled: Boolean) = update(autoStartGpsKey, enabled)

  fun setAutoPause(enabled: Boolean) = update(autoPauseKey, enabled)

  fun setAutoLap(enabled: Boolean) = update(autoLapKey, enabled)

  fun setAutoLapIntervals(enabled: Boolean) = update(intervalAutoLapKey, enabled)

  private fun update(key: String, value: Boolean) {
    if (preferences.getBoolean(key, defaultFor(key)) != value) {
      preferences.edit { putBoolean(key, value) }
      mutableState.value = readState()
    }
  }

  private fun readState() =
      RelvenSettingsSnapshot(
          keepScreenOn = preferences.getBoolean(keepScreenOnKey, false),
          autoStartGps = preferences.getBoolean(autoStartGpsKey, true),
          autoPause = preferences.getBoolean(autoPauseKey, false),
          autoLap = preferences.getBoolean(autoLapKey, false),
          autoLapIntervals = preferences.getBoolean(intervalAutoLapKey, false))

  private fun defaultFor(key: String): Boolean = when (key) {
    keepScreenOnKey -> false
    autoStartGpsKey -> true
    else -> false
  }

  override fun close() {
    preferences.unregisterOnSharedPreferenceChangeListener(listener)
  }
}

data class RelvenSettingsSnapshot(
    val keepScreenOn: Boolean,
    val autoStartGps: Boolean,
    val autoPause: Boolean,
    val autoLap: Boolean,
    val autoLapIntervals: Boolean)
