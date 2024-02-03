package org.runnerup.view;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import org.runnerup.R;

public class SettingsWorkoutFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_workout, rootKey);
    }
}