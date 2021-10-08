/*
 * Copyright (C) 2012 - 2014 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.view;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerPressure;
import org.runnerup.tracker.component.TrackerTemperature;


public class SettingsActivity extends PreferenceActivity {

    public void onCreate(Bundle savedInstanceState) {
        Resources res = getResources();
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        setContentView(R.layout.settings_wrapper);
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

        if (!hasHR(this)) {
            getPreferenceManager().findPreference(res.getString(R.string.cue_configure_hrzones)).setEnabled(false);
            getPreferenceManager().findPreference(res.getString(R.string.pref_battery_level_low_threshold)).setEnabled(false);
            getPreferenceManager().findPreference(res.getString(R.string.pref_battery_level_high_threshold)).setEnabled(false);
        }
        {
            //Preference pref = findPreference(this.getString(R.string.pref_experimental_features));
            //pref.setSummary(null);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Preference pref = findPreference(this.getString(R.string.pref_keystartstop_active));
            pref.setEnabled(false);
        }
        if (!TrackerCadence.isAvailable(this)) {
            Preference pref = findPreference(this.getString(R.string.pref_use_cadence_step_sensor));
            pref.setEnabled(false);
        }
        if (!TrackerTemperature.isAvailable(this)) {
            Preference pref = findPreference(this.getString(R.string.pref_use_temperature_sensor));
            pref.setEnabled(false);
        }
        if (!TrackerPressure.isAvailable(this)) {
            Preference pref = findPreference(this.getString(R.string.pref_use_pressure_sensor));
            pref.setEnabled(false);
        }
        CheckBoxPreference simplifyOnSave = (CheckBoxPreference) findPreference(getString(R.string.pref_path_simplification_on_save));
        CheckBoxPreference simplifyOnExport = (CheckBoxPreference) findPreference(getString(R.string.pref_path_simplification_on_export));
        if (simplifyOnSave.isChecked()) {
            simplifyOnExport.setChecked(true);
        }
        simplifyOnSave.setOnPreferenceChangeListener((preference, newValue) -> {
            if ((Boolean) newValue) {
                simplifyOnExport.setChecked(true);
            }
            return true;
        });
    }

    public static boolean hasHR(Context ctx) {
        Resources res = ctx.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null);
        String btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider), null);
        return btProviderName != null && btAddress != null;
    }

    private final OnPreferenceClickListener onExportClick = preference -> {
        // TODO Use picker with ACTION_CREATE_DOCUMENT
        DBHelper.exportDatabase(SettingsActivity.this, null);
        return false;
    };

    private final OnPreferenceClickListener onImportClick = preference -> {
        // TODO Use picker with ACTION_OPEN_DOCUMENT
        DBHelper.importDatabase(SettingsActivity.this, null);
        return false;
    };

    private final OnPreferenceClickListener onPruneClick = preference -> {
        final ProgressDialog dialog = new ProgressDialog(SettingsActivity.this);
        dialog.setTitle(R.string.Pruning_deleted_activities_from_database);
        dialog.show();
        DBHelper.purgeDeletedActivities(SettingsActivity.this, dialog, dialog::dismiss);
        return false;
    };

    public void onBackPressed() {
        Intent intent = new Intent(this, MainLayout.class);
        startActivity(intent);
        finish();
    }
}
