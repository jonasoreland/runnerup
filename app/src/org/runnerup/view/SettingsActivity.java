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

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.util.FileUtil;

import java.io.IOException;

@TargetApi(Build.VERSION_CODES.FROYO)
public class SettingsActivity extends PreferenceActivity {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        setContentView(R.layout.settings_wrapper);

        {
            Preference btn = (Preference) findPreference("exportdb");
            btn.setOnPreferenceClickListener(onExportClick);
        }
        {
            Preference btn = (Preference) findPreference("importdb");
            btn.setOnPreferenceClickListener(onImportClick);
        }
        {
            Preference btn = (Preference) findPreference("prunedb");
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
            builder.show();
            return false;
        }
    };

    final OnPreferenceClickListener onImportClick = new OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            String srcdir = Environment.getExternalStorageDirectory().getPath();
            String from = srcdir + "/runnerup.db.export";
            DBHelper.importDatabase(SettingsActivity.this, from);
            return false;
        }
    };
    final OnPreferenceClickListener onPruneClick = new OnPreferenceClickListener() {

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
