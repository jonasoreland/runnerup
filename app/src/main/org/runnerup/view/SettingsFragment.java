package org.runnerup.view;

import android.app.ProgressDialog;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerPressure;
import org.runnerup.tracker.component.TrackerTemperature;
import org.runnerup.widget.AboutPreference;

import java.util.Locale;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        Resources res = getResources();
        {
            Preference btn = findPreference(res.getString(R.string.pref_exportdb));
            btn.setOnPreferenceClickListener(onExportClick);
        }
        {
            Preference btn = findPreference(res.getString(R.string.pref_importdb));
            btn.setOnPreferenceClickListener(onImportClick);
        }
        {
            Preference btn = findPreference(res.getString(R.string.pref_prunedb));
            btn.setOnPreferenceClickListener(onPruneClick);
        }

        if (BuildConfig.MAPBOX_ENABLED == 0) {
            Preference pref = findPreference("map_preferencescreen");
            pref.setEnabled(false);
        }

        if (!SettingsActivity.hasHR(requireContext())) {
            getPreferenceManager().findPreference(res.getString(R.string.cue_configure_hrzones)).setEnabled(false);
            getPreferenceManager().findPreference(res.getString(R.string.pref_battery_level_low_threshold)).setEnabled(false);
            getPreferenceManager().findPreference(res.getString(R.string.pref_battery_level_high_threshold)).setEnabled(false);
        }
        {
            //Preference pref = findPreference(this.getString(R.string.pref_experimental_features));
            //pref.setSummary(null);
        }

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
        CheckBoxPreference simplifyOnSave = findPreference(getString(R.string.pref_path_simplification_on_save));
        CheckBoxPreference simplifyOnExport = findPreference(getString(R.string.pref_path_simplification_on_export));
        if (simplifyOnSave.isChecked()) {
            simplifyOnExport.setChecked(true);
        }
        simplifyOnSave.setOnPreferenceChangeListener((preference, newValue) -> {
            if ((Boolean) newValue) {
                simplifyOnExport.setChecked(true);
            }
            return true;
        });

        String path = DBHelper.getDefaultBackupPath(requireContext());
        getPreferenceManager()
                .findPreference(res.getString(org.runnerup.common.R.string.Maintenance_explanation_summary))
                .setSummary(String.format(Locale.getDefault(), getResources().getString(org.runnerup.common.R.string.Maintenance_explanation_summary), path));
    }

    private final Preference.OnPreferenceClickListener onExportClick = preference -> {
        // TODO Use picker with ACTION_CREATE_DOCUMENT
        DBHelper.exportDatabase(requireContext(), null);
        return false;
    };

    private final Preference.OnPreferenceClickListener onImportClick = preference -> {
        // TODO Use picker with ACTION_OPEN_DOCUMENT
        DBHelper.importDatabase(requireContext(), null);
        return false;
    };

    private final Preference.OnPreferenceClickListener onPruneClick = preference -> {
        final ProgressDialog dialog = new ProgressDialog(requireContext());
        dialog.setTitle(org.runnerup.common.R.string.Pruning_deleted_activities_from_database);
        dialog.show();
        DBHelper.purgeDeletedActivities(requireContext(), dialog, dialog::dismiss);
        return false;
    };

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof AboutPreference) {
            // The about preference was clicked, show the about dialog
            AboutPreference.AboutDialogFragment aboutDialogFragment =
                    AboutPreference.AboutDialogFragment.newInstance(preference.getKey());
            aboutDialogFragment.setTargetFragment(this, 0);
            aboutDialogFragment.show(getParentFragmentManager(), AboutPreference.AboutDialogFragment.TAG);
        }
        else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
}