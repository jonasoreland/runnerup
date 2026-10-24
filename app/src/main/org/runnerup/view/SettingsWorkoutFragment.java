package org.runnerup.view;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import org.runnerup.R;

public class SettingsWorkoutFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings_workout, rootKey);

    // Preference Category - Auto Pause
    // These preferences should be enabled only if the Auto Pause preference is enabled.
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
