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
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.util.FileUtil;

import java.io.IOException;

@TargetApi(Build.VERSION_CODES.FROYO)
public class SettingsActivity extends PreferenceActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback{

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        setContentView(R.layout.settings_wrapper);
        {
            Preference btn = findPreference("exportdb");
            btn.setOnPreferenceClickListener(onExportClick);
        }
        {
            Preference btn = findPreference("importdb");
            btn.setOnPreferenceClickListener(onImportClick);
        }
        {
            Preference btn = findPreference("prunedb");
            btn.setOnPreferenceClickListener(onPruneClick);
        }

        //remove google play notices from froyo since we do not use it
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.FROYO) {
            Preference pref = findPreference("googleplayserviceslegalnotices");
            PreferenceCategory category = (PreferenceCategory)findPreference("aboutcategory");
            category.removePreference(pref);
        }

        if (!hasHR(this)) {
            Preference pref = findPreference("cue_configure_hrzones");
            getPreferenceScreen().removePreference(pref);
        }

    }

    public static boolean hasHR(Context ctx) {
        Resources res = ctx.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null);
        String btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider), null);
        if (btProviderName != null && btAddress != null)
            return true;
        return false;
    }

    @SuppressLint("InlinedApi")
    public static boolean requestReadStoragePermissions(final Activity activity) {
        boolean ret = true;
        if (Build.VERSION.SDK_INT >= 16 &&
                ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ret = false;

            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                //Assume that the caller informs the user, no toast or SnackBar
            } else {
                //Request permission - not working from Settings.Activity
                //If not calling requestPermissions at startup, this part will not be called...
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_READ_EXTERNAL_STORAGE);
            }
        }
        return ret;
    }

    public static boolean requestWriteStoragePermissions(final Activity activity) {
        boolean ret = true;
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ret = false;

            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            } else {
                 //Request permission - not working from Settings.Activity
                 ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        }
        return ret;
    }

    /**
     * Id to identify a permission request.
     */
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 2;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE || requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            // Check if the only required permission has been granted (could react on the response)
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                String s = requestCode == REQUEST_READ_EXTERNAL_STORAGE ? "READ" : "WRITE" + " permission was NOT granted.";
                Log.i(getClass().getSimpleName(), s);
                Toast.makeText(SettingsActivity.this, s, Toast.LENGTH_SHORT).show();
            }

        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    final OnPreferenceClickListener onExportClick = new OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
            String dstdir = Environment.getExternalStorageDirectory().getPath();
            builder.setTitle("Export runnerup.db to " + dstdir);
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }

            };
            if(requestWriteStoragePermissions(SettingsActivity.this)) {
                String from = DBHelper.getDbPath(getApplicationContext());
                String to = dstdir + "/runnerup.db.export";
                try {
                    int cnt = FileUtil.copyFile(to, from);
                    builder.setMessage("Copied " + cnt + " bytes");
                    builder.setPositiveButton(getString(R.string.Great), listener);
                } catch (IOException e) {
                    builder.setMessage("Exception: " + e.toString());
                    builder.setNegativeButton(getString(R.string.Darn), listener);
                }
            } else {
                builder.setMessage("Storage permission not granted in Android settings");
                builder.setNegativeButton(getString(R.string.Darn), listener);
            }
            builder.show();
            return false;
        }
    };

    final OnPreferenceClickListener onImportClick = new OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (requestReadStoragePermissions(SettingsActivity.this)) {
                String srcdir = Environment.getExternalStorageDirectory().getPath();
                String from = srcdir + "/runnerup.db.export";
                DBHelper.importDatabase(SettingsActivity.this, from);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }

                };
                builder.setTitle("Import runnerup.db");
                builder.setMessage("Storage permission not granted in Android settings");
                builder.setNegativeButton(getString(R.string.Darn), listener);
                builder.show();
            }
            return false;
        }
    };
    final OnPreferenceClickListener onPruneClick = new OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (requestWriteStoragePermissions(SettingsActivity.this)) {
                final ProgressDialog dialog = new ProgressDialog(SettingsActivity.this);
                dialog.setTitle(R.string.Pruning_deleted_activities_from_database);
                dialog.show();
                DBHelper.purgeDeletedActivities(SettingsActivity.this, dialog, new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                    }
                });
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }

                };
                builder.setTitle("Prune runnerup.db");
                builder.setMessage("Storage permission not granted in Android settings");
                builder.setNegativeButton(getString(R.string.Darn), listener);
                builder.show();
            }
            return false;
        }
    };
}
