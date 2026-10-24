package org.runnerup.relven.ui

import android.content.SharedPreferences
import androidx.core.content.edit
import org.runnerup.common.tracker.TrackerState
import org.runnerup.tracker.Tracker

private const val GPS_WARMUP_DEFAULT_MIGRATION_KEY = "relven.gps_warmup_default_enabled_v1"

/** Enables the new foreground GPS warm-up once, while preserving later user changes. */
internal fun migrateGpsWarmupPreference(preferences: SharedPreferences, autoStartKey: String) {
  if (preferences.getBoolean(GPS_WARMUP_DEFAULT_MIGRATION_KEY, false)) return
  preferences.edit {
    if (!preferences.contains(autoStartKey)) putBoolean(autoStartKey, true)
    putBoolean(GPS_WARMUP_DEFAULT_MIGRATION_KEY, true)
  }
}

/** Keeps app-launch GPS preparation separate from starting or preserving a workout. */
internal fun shouldWarmUpGps(
    state: TrackerState?,
    autoStartEnabled: Boolean,
    fineLocationGranted: Boolean,
    locationProviderEnabled: Boolean,
    sportWithoutGps: Boolean
): Boolean =
    autoStartEnabled &&
        fineLocationGranted &&
        locationProviderEnabled &&
        !sportWithoutGps &&
        (state == TrackerState.INIT ||
            state == TrackerState.INITIALIZED ||
            state == TrackerState.CLEANUP)

internal fun shouldReleaseGpsWarmup(state: TrackerState?): Boolean =
    state == TrackerState.INITIALIZING ||
        state == TrackerState.INITIALIZED ||
        state == TrackerState.CONNECTING ||
        state == TrackerState.CONNECTED

/** Releases an idle warm-up even if the user disabled future warm-ups after it connected. */
internal fun releaseIdleGpsWarmup(tracker: Tracker) {
  if (shouldReleaseGpsWarmup(tracker.state)) tracker.reset()
}
