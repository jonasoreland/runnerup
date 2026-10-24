package org.runnerup.relven.health

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/** Preference key shared with the opt-in Settings page added in the next integration slice. */
internal object HealthConnectExportPreferences {
  const val EXPORT_ENABLED_KEY = "relven_health_connect_export_enabled"

  fun isEnabled(context: Context): Boolean =
      PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
          .getBoolean(EXPORT_ENABLED_KEY, false)

  fun setEnabled(context: Context, enabled: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        .edit { putBoolean(EXPORT_ENABLED_KEY, enabled) }
  }
}
