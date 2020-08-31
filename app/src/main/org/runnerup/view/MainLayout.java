/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

import android.annotation.SuppressLint;
import android.app.Service;
import android.app.TabActivity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TabHost;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;

import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.util.FileUtil;
import org.runnerup.util.Formatter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;


public class MainLayout extends TabActivity {

    private View getTabView(CharSequence label, int iconResource) {
        @SuppressLint("InflateParams")View tabView = getLayoutInflater().inflate(R.layout.bottom_tab_indicator, null);
        ImageView iconView = tabView.findViewById(R.id.icon);
        iconView.setContentDescription(label);
        Drawable icon = AppCompatResources.getDrawable(this, iconResource);
        iconView.setImageDrawable(icon);
        return tabView;
    }

    private enum UpgradeState {
        UNKNOWN, NEW, UPGRADE, DOWNGRADE, SAME
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.main);

        int versionCode = 0;
        UpgradeState upgradeState = UpgradeState.UNKNOWN;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        Editor editor = pref.edit();
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionCode = pInfo.versionCode;
            int version = pref.getInt("app-version", -1);
            if (version == -1) {
                upgradeState = UpgradeState.NEW;
            } else if (versionCode == version) {
                upgradeState = UpgradeState.SAME;
            } else if (versionCode > version) {
                upgradeState = UpgradeState.UPGRADE;
            } else if (versionCode < version) {
                upgradeState = UpgradeState.DOWNGRADE;
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        editor.putInt("app-version", versionCode);
        boolean km = Formatter.getUseMetric(getResources(), pref, editor);

        if (upgradeState == UpgradeState.NEW) {
            editor.putString(getResources().getString(R.string.pref_autolap),
                    Double.toString(km ? Formatter.km_meters : Formatter.mi_meters));
        }
        editor.apply();

        // clear basicTargetType between application startup/shutdown
        pref.edit().remove(getString(R.string.pref_basic_target_type)).apply();

        Log.e(getClass().getName(), "app-version: " + versionCode + ", upgradeState: " + upgradeState
                + ", km: " + km);

        // Migration in 1.56: convert pref_mute to pref_mute_bool
        Resources res = getResources();
        try {
            if (pref.contains(res.getString(R.string.pref_mute))) {
                String v = pref.getString(res.getString(R.string.pref_mute), "no");
                editor.putBoolean(res.getString(R.string.pref_mute_bool), v.equalsIgnoreCase("yes"));
                editor.remove(res.getString(R.string.pref_mute));
                editor.apply();
            }
        } catch (Exception e) {
        }

        PreferenceManager.setDefaultValues(this, R.xml.settings, false);
        PreferenceManager.setDefaultValues(this, R.xml.audio_cue_settings, true);

        TabHost tabHost = getTabHost(); // The activity TabHost

        tabHost.addTab(tabHost.newTabSpec("Start")
                .setIndicator(getTabView(getString(R.string.Start), R.drawable.ic_tab_main_24dp))
                .setContent(new Intent(this, StartActivity.class)));

        tabHost.addTab(tabHost.newTabSpec("History")
                .setIndicator(getTabView(getString(R.string.History), R.drawable.ic_tab_history_24dp))
                .setContent(new Intent(this, HistoryActivity.class)));

        tabHost.addTab(tabHost.newTabSpec("Feed")
                .setIndicator(getTabView(getString(R.string.feed), R.drawable.ic_tab_feed_24dp))
                .setContent(new Intent(this, FeedActivity.class)));

        tabHost.addTab(tabHost.newTabSpec("Settings")
                .setIndicator(getTabView(getString(R.string.Settings), R.drawable.ic_tab_settings_24dp))
                .setContent(new Intent(this, SettingsActivity.class)));

        tabHost.setCurrentTab(0);

        if (upgradeState == UpgradeState.UPGRADE) {
            whatsNew();
        }

        //Import workouts/schemes. No permission needed
        handleBundled(getApplicationContext().getAssets(), "bundled", getFilesDir().getPath() + "/..");

        // if we were called from an intent-filter because user opened "runnerup.db.export", load it
        final String filePath;
        final Uri data = getIntent().getData();
        if (data != null) {
            if ("content".equals(data.getScheme())) {
                Cursor cursor = this.getContentResolver().query(data, new String[]{android.provider.MediaStore.Images.ImageColumns.DATA}, null, null, null);
                cursor.moveToFirst();
                filePath = cursor.getString(0);
                cursor.close();
            } else {
                filePath = data.getPath();
            }
        } else {
            filePath = null;
        }

        if (filePath != null) {
            // No check for permissions or that this is within scooped storage (>=SDK29)
            Log.i(getClass().getSimpleName(), "Importing database from " + filePath);
            DBHelper.importDatabase(MainLayout.this, filePath);
        }
    }

    private void handleBundled(AssetManager mgr, String srcBase, String dstBase) {
        String[] list;

        try {
            list = mgr.list(srcBase);
        } catch (IOException e) {
            e.printStackTrace();
            list = null;
        }
        if (list != null) {
            for (String add : list) {
                boolean isFile = false;

                String src = srcBase + File.separator + add;
                String dst = dstBase + File.separator + add;
                try {
                    InputStream is = mgr.open(src);
                    is.close();
                    isFile = true;
                } catch (Exception ex) {
                    //Normal, src is directory for first call
                }

                Log.v(getClass().getName(), "Found: " + src + ", " + dst + ", isFile: " + isFile);

                if (!isFile) {
                    //The request is hierarchical, source is still on a directory level
                    File dstDir = new File(dstBase);
                    //noinspection ResultOfMethodCallIgnored
                    dstDir.mkdir();
                    if (!dstDir.isDirectory()) {
                        Log.w(getClass().getName(), "Failed to copy " + src + " as \"" + dstBase
                                + "\" is not a directory!");
                        continue;
                    }
                    handleBundled(mgr, src, dst);
                } else {
                    //Source is a file, ready to copy
                    File dstFile = new File(dst);
                    if (dstFile.isDirectory() || dstFile.isFile()) {
                        Log.v(getClass().getName(), "Skip: " + dst +
                                ", isDirectory(): " + dstFile.isDirectory() +
                                ", isFile(): " + dstFile.isFile());
                        continue;
                    }

                    //Only copy if the key do not exist already
                    String key = "install_bundled_" + add;
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
                    if (pref.contains(key)) {
                        Log.v(getClass().getName(), "Skip already existing pref: " + key);
                        continue;
                    }

                    pref.edit().putBoolean(key, true).apply();

                    Log.v(getClass().getName(), "Copying: " + dst);
                    InputStream input = null;
                    try {
                        input = mgr.open(src);
                        FileUtil.copy(input, dst);
                        handleHooks(add);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        FileUtil.close(input);
                    }
                }
            }
        }
    }

    private void handleHooks(String key) {
        if (key.contains("_audio_cues.xml")) {
            String name = key.substring(0, key.indexOf("_audio_cues.xml"));

            SQLiteDatabase mDB = DBHelper.getWritableDatabase(this);

            ContentValues tmp = new ContentValues();
            tmp.put(DB.AUDIO_SCHEMES.NAME, name);
            tmp.put(DB.AUDIO_SCHEMES.SORT_ORDER, 0);
            mDB.insert(DB.AUDIO_SCHEMES.TABLE, null, tmp);

            DBHelper.closeDB(mDB);
        }
    }

    private final OnClickListener onRateClick = arg0 -> {
        try {
            Uri uri = Uri.parse("market://details?id=" + getPackageName());
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    };

    private void whatsNew() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        @SuppressLint("InflateParams") View view = inflater.inflate(R.layout.whatsnew, null);
        WebView wv = view.findViewById(R.id.web_view1);
        new AlertDialog.Builder(this)
                .setTitle(R.string.Whats_new)
                .setView(view)
                .setPositiveButton(R.string.Rate_RunnerUp, (dialog, which) -> onRateClick.onClick(null))
                .setNegativeButton(R.string.OK, (dialog, which) -> dialog.dismiss())
                .show();
        wv.loadUrl("file:///android_asset/changes.html");
    }
}
