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
  private val cadenceSensorKey = appContext.getString(R.string.pref_use_cadence_step_sensor)
  private val temperatureSensorKey = appContext.getString(R.string.pref_use_temperature_sensor)
  private val pressureSensorKey = appContext.getString(R.string.pref_use_pressure_sensor)
  private val distanceUnitKey = appContext.getString(R.string.pref_unit)
  private val speedUnitKey = appContext.getString(R.string.pref_speedunit)
  private val mutableState = MutableStateFlow(readState())
  val state: StateFlow<RelvenSettingsSnapshot> = mutableState.asStateFlow()

  private val listener =
      SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in observedKeys) mutableState.value = readState()
      }

  private val observedKeys: Set<String>
    get() = setOf(
        keepScreenOnKey,
        autoStartGpsKey,
        autoPauseKey,
        autoLapKey,
        intervalAutoLapKey,
        cadenceSensorKey,
        temperatureSensorKey,
        pressureSensorKey,
        distanceUnitKey,
        speedUnitKey)

  init {
    preferences.registerOnSharedPreferenceChangeListener(listener)
  }

  fun setKeepScreenOn(enabled: Boolean) = update(keepScreenOnKey, enabled)

  fun setAutoStartGps(enabled: Boolean) = update(autoStartGpsKey, enabled)

  fun setAutoPause(enabled: Boolean) = update(autoPauseKey, enabled)

  fun setAutoLap(enabled: Boolean) = update(autoLapKey, enabled)

  fun setAutoLapIntervals(enabled: Boolean) = update(intervalAutoLapKey, enabled)

  fun setUseCadenceSensor(enabled: Boolean) = update(cadenceSensorKey, enabled)

  fun setUseTemperatureSensor(enabled: Boolean) = update(temperatureSensorKey, enabled)

  fun setUsePressureSensor(enabled: Boolean) = update(pressureSensorKey, enabled)

  fun setDistanceUnit(unit: String) {
    if (unit !in DISTANCE_UNITS) return
    update(distanceUnitKey, unit)
  }

  fun setSpeedUnit(unit: String) {
    if (unit !in SPEED_UNITS) return
    update(speedUnitKey, unit)
  }

  private fun update(key: String, value: Boolean) {
    if (preferences.getBoolean(key, defaultBooleanFor(key)) != value) {
      preferences.edit { putBoolean(key, value) }
      mutableState.value = readState()
    }
  }

  private fun update(key: String, value: String) {
    if (!preferences.contains(key) || preferences.getString(key, defaultStringFor(key)) != value) {
      preferences.edit { putString(key, value) }
      mutableState.value = readState()
    }
  }

  private fun readState() =
      RelvenSettingsSnapshot(
          keepScreenOn = preferences.getBoolean(keepScreenOnKey, false),
          autoStartGps = preferences.getBoolean(autoStartGpsKey, true),
          autoPause = preferences.getBoolean(autoPauseKey, false),
          autoLap = preferences.getBoolean(autoLapKey, false),
          autoLapIntervals = preferences.getBoolean(intervalAutoLapKey, false),
          useCadenceSensor = preferences.getBoolean(cadenceSensorKey, true),
          useTemperatureSensor = preferences.getBoolean(temperatureSensorKey, false),
          usePressureSensor = preferences.getBoolean(pressureSensorKey, false),
          distanceUnit = preferences.getString(distanceUnitKey, null)
              ?.takeIf(DISTANCE_UNITS::contains)
              ?: defaultDistanceUnit(),
          speedUnit = preferences.getString(speedUnitKey, null)
              ?.takeIf(SPEED_UNITS::contains)
              ?: "pace")

  private fun defaultBooleanFor(key: String): Boolean = when (key) {
    keepScreenOnKey -> false
    autoStartGpsKey -> true
    cadenceSensorKey -> true
    else -> false
  }

  private fun defaultStringFor(key: String): String = when (key) {
    speedUnitKey -> "pace"
    distanceUnitKey -> defaultDistanceUnit()
    else -> ""
  }

  private fun defaultDistanceUnit(): String =
      if (java.util.Locale.getDefault().country in setOf("US", "GB")) "mi" else "km"

  override fun close() {
    preferences.unregisterOnSharedPreferenceChangeListener(listener)
  }
}

data class RelvenSettingsSnapshot(
    val keepScreenOn: Boolean,
    val autoStartGps: Boolean,
    val autoPause: Boolean,
    val autoLap: Boolean,
    val autoLapIntervals: Boolean,
    val useCadenceSensor: Boolean = true,
    val useTemperatureSensor: Boolean = false,
    val usePressureSensor: Boolean = false,
    val distanceUnit: String = "km",
    val speedUnit: String = "pace")

private val DISTANCE_UNITS = setOf("km", "mi")
private val SPEED_UNITS = setOf("pace", "speed")
