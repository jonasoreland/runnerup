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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerPressure;
import org.runnerup.tracker.component.TrackerTemperature;
import org.runnerup.util.FileUtil;

import java.io.IOException;


public class SettingsActivity extends PreferenceActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback{

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
    }

    public static boolean hasHR(Context ctx) {
        Resources res = ctx.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null);
        String btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider), null);
        return btProviderName != null && btAddress != null;
    }

    @SuppressLint("InlinedApi")
    public static boolean requestReadStoragePermissions(final Activity activity) {
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

    private static boolean requestWriteStoragePermissions(final Activity activity) {
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

    private final OnPreferenceClickListener onExportClick = new OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (requestWriteStoragePermissions(SettingsActivity.this)) {
                String dstdir = Environment.getExternalStorageDirectory().getPath();
                String to = dstdir + "/runnerup.db.export";
                DBHelper.exportDatabase(SettingsActivity.this, to);

            } else {
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }

                };
                AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Export runnerup.db")
                        .setMessage("Storage permission not granted in Android settings")
                        .setNegativeButton(getString(R.string.Darn), listener);
                builder.show();
            }
            return false;
        }
    };

    private final OnPreferenceClickListener onImportClick = new OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (requestReadStoragePermissions(SettingsActivity.this)) {
                String srcdir = Environment.getExternalStorageDirectory().getPath();
                String from = srcdir + "/runnerup.db.export";
                DBHelper.importDatabase(SettingsActivity.this, from);
            } else {
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }

                };
                AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Import runnerup.db")
                        .setMessage("Storage permission not granted in Android settings")
                        .setNegativeButton(getString(R.string.Darn), listener);
                builder.show();
            }
            return false;
        }
    };

    private final OnPreferenceClickListener onPruneClick = new OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            final ProgressDialog dialog = new ProgressDialog(SettingsActivity.this);
            dialog.setTitle(R.string.Pruning_deleted_activities_from_database);
            dialog.show();
            DBHelper.purgeDeletedActivities(SettingsActivity.this, dialog, new Runnable() {
                @Override
                public void run() {
                    dialog.dismiss();
                }
            });
            return false;
        }
    };
}
