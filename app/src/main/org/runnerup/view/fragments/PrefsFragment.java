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

package org.runnerup.view.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerPressure;
import org.runnerup.tracker.component.TrackerTemperature;
import org.runnerup.view.SettingsActivity;

import java.util.Objects;

public class PrefsFragment extends PreferenceFragmentCompat
   implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Resources res = getResources();
        super.onCreate(savedInstanceState);
        setPreferencesFromResource(R.xml.settings, rootKey);
/*xxx         setContentView(R.layout.settings_wrapper);
        RelativeLayout content = (RelativeLayout) rootView.findViewById(R.id.settings_wrapper);
        content.addView(aboutPage);
    */
        {
            Preference btn = this.getPreferenceScreen().findPreference(res.getString(R.string.pref_exportdb));
            btn.setOnPreferenceClickListener(onExportClick);
        }
        {
            Preference btn = this.getPreferenceScreen().findPreference(res.getString(R.string.pref_importdb));
            btn.setOnPreferenceClickListener(onImportClick);
        }
        {
            Preference btn = this.getPreferenceScreen().findPreference(res.getString(R.string.pref_prunedb));
            btn.setOnPreferenceClickListener(onPruneClick);
        }


        if (!SettingsActivity.hasHR(Objects.requireNonNull(this.getContext()))) {
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
        if (!TrackerCadence.isAvailable(Objects.requireNonNull(this.getContext()))) {
            Preference pref = findPreference(this.getString(R.string.pref_use_cadence_step_sensor));
            pref.setEnabled(false);
        }
        if (!TrackerTemperature.isAvailable(Objects.requireNonNull(this.getContext()))) {
            Preference pref = findPreference(this.getString(R.string.pref_use_temperature_sensor));
            pref.setEnabled(false);
        }
        if (!TrackerPressure.isAvailable(Objects.requireNonNull(this.getContext()))) {
            Preference pref = findPreference(this.getString(R.string.pref_use_pressure_sensor));
            pref.setEnabled(false);
        }
        CheckBoxPreference simplifyOnSave = (CheckBoxPreference) findPreference("pref_path_simplification_on_save");
        CheckBoxPreference simplifyOnExport = (CheckBoxPreference) findPreference("pref_path_simplification_on_export");
        if (simplifyOnSave.isChecked()) {
            simplifyOnExport.setChecked(true);
        }
        simplifyOnSave.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue){
                if ((Boolean) newValue) {
                    simplifyOnExport.setChecked(true);
                };
                return true;
            }
        });
    }

    @SuppressLint("InlinedApi")
    public static boolean requestReadStoragePermissions(final AppCompatActivity activity) {
        boolean ret = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
                ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ret = false;

            //Request permission - not working from Settings.Activity
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE);
            String s = "Requesting read permission";
            Log.i(activity.getClass().getSimpleName(), s);
        }
        return ret;
    }

    private static boolean requestWriteStoragePermissions(final AppCompatActivity activity) {
        boolean ret = true;
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ret = false;

            //Request permission (not using shouldShowRequestPermissionRationale())
            // not working from Settings.Activity
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
            String s = "Requesting write permission";
            Log.i(activity.getClass().getSimpleName(), s);
        }
        return ret;
    }

    /**
     * Id to identify a permission request.
     */
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 2000;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 2001;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE || requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            // Check if the only required permission has been granted (could react on the response)
            //noinspection StatementWithEmptyBody
            if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //OK, could redo request here
            } else {
                String s = (requestCode == REQUEST_READ_EXTERNAL_STORAGE ? "READ" : "WRITE")
                        + " permission was NOT granted";
                if (grantResults.length >= 1) {
                    s += grantResults[0];
                }

                Log.i(getClass().getSimpleName(), s);
                //Toast.makeText(SettingsActivity.this, s, Toast.LENGTH_SHORT).show();
            }

        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private final Preference.OnPreferenceClickListener onExportClick = new Preference.OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (requestWriteStoragePermissions((AppCompatActivity) getActivity())) {
                String dstdir = Environment.getExternalStorageDirectory().getPath();
                String to = dstdir + "/runnerup.db.export";
                DBHelper.exportDatabase(getActivity(), to);

            } else {
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }

                };
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setTitle("Export runnerup.db")
                        .setMessage("Storage permission not granted in Android settings")
                        .setNegativeButton(getString(R.string.Darn), listener);
                builder.show();
            }
            return false;
        }
    };

    private final Preference.OnPreferenceClickListener onImportClick = new Preference.OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (requestReadStoragePermissions((AppCompatActivity) getActivity())) {
                String srcdir = Environment.getExternalStorageDirectory().getPath();
                String from = srcdir + "/runnerup.db.export";
                DBHelper.importDatabase(getActivity(), from);
            } else {
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }

                };
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setTitle("Import runnerup.db")
                        .setMessage("Storage permission not granted in Android settings")
                        .setNegativeButton(getString(R.string.Darn), listener);
                builder.show();
            }
            return false;
        }
    };

    private final Preference.OnPreferenceClickListener onPruneClick = new Preference.OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            final ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setTitle(R.string.Pruning_deleted_activities_from_database);
            dialog.show();
            DBHelper.purgeDeletedActivities(getActivity(), dialog, new Runnable() {
                @Override
                public void run() {
                    dialog.dismiss();
                }
            });
            return false;
        }
    };

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        return false;
    }
}
