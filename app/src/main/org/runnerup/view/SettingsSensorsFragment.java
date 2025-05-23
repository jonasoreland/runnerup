package org.runnerup.view;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import org.runnerup.R;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerPressure;
import org.runnerup.tracker.component.TrackerTemperature;

public class SettingsSensorsFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings_sensors, rootKey);
    Resources res = getResources();

    // Preference Category - Heart Rate
    if (!SettingsActivity.hasHR(requireContext())) {
      getPreferenceManager()
          .findPreference(res.getString(R.string.cue_configure_hrzones))
          .setEnabled(false);
      getPreferenceManager()
          .findPreference(res.getString(R.string.pref_battery_level_low_threshold))
          .setEnabled(false);
      getPreferenceManager()
          .findPreference(res.getString(R.string.pref_battery_level_high_threshold))
          .setEnabled(false);
    }
    {
      // Preference pref = findPreference(this.getString(R.string.pref_experimental_features));
      // pref.setSummary(null);
    }

    // Preference Category - Phone Sensors
    if (!TrackerCadence.isAvailable(requireContext())) {
      Preference pref = findPreference(this.getString(R.string.pref_use_cadence_step_sensor));
      pref.setEnabled(false);
    }
    if (!TrackerTemperature.isAvailable(requireContext())) {
      Preference pref = findPreference(this.getString(R.string.pref_use_temperature_sensor));
      pref.setEnabled(false);
    }
    if (!TrackerPressure.isAvailable(requireContext())) {
      Preference pref = findPreference(this.getString(R.string.pref_use_pressure_sensor));
      pref.setEnabled(false);
    }

    // Preference Category - Path Simplification
    CheckBoxPreference simplifyOnSave =
        findPreference(getString(R.string.pref_path_simplification_on_save));
    CheckBoxPreference simplifyOnExport =
        findPreference(getString(R.string.pref_path_simplification_on_export));
    if (simplifyOnSave.isChecked()) {
      simplifyOnExport.setChecked(true);
    }
    simplifyOnSave.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          if ((Boolean) newValue) {
            simplifyOnExport.setChecked(true);
          }
          return true;
        });

    // Preference Category - Auto Pause
    // These preferences should be enabled only if the Auto Pause preference in the
    // Workout Preference is enabled.
    SharedPreferences sharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(requireContext());
    boolean autoPause =
        sharedPreferences.getBoolean(getString(R.string.pref_autopause_active), true);
    Preference autoPauseAfterSeconds =
        findPreference(getString(R.string.pref_autopause_afterseconds));
    autoPauseAfterSeconds.setEnabled(autoPause);
    Preference autoPauseMinPace = findPreference(getString(R.string.pref_autopause_minpace));
    autoPauseMinPace.setEnabled(autoPause);
  }
}
