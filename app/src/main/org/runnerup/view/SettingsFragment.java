package org.runnerup.view;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.widget.AboutPreference;

public class SettingsFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings, rootKey);

    if (!BuildConfig.MAPBOX_ENABLED) {
      Preference pref = findPreference("map_preferencescreen");
      pref.setEnabled(false);
    }
  }

  @Override
  public void onDisplayPreferenceDialog(@NonNull Preference preference) {
    if (preference instanceof AboutPreference) {
      // The about preference was clicked, show the about dialog
      AboutPreference.AboutDialogFragment aboutDialogFragment =
          AboutPreference.AboutDialogFragment.newInstance(preference.getKey());
      aboutDialogFragment.setTargetFragment(this, 0);
      aboutDialogFragment.show(getParentFragmentManager(), AboutPreference.AboutDialogFragment.TAG);
    } else {
      super.onDisplayPreferenceDialog(preference);
    }
  }
}
