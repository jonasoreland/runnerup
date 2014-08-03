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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Constants.DB;
import org.runnerup.util.FileUtil;
import org.runnerup.util.Formatter;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Service;
import android.app.TabActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.TabHost;

@TargetApi(Build.VERSION_CODES.FROYO)
public class MainLayout extends TabActivity {

    private Drawable getDrawable(int resId) {
        Drawable d = getResources().getDrawable(resId);
        return d;
    }

    private enum UpgradeState {
        UNKNOWN, NEW, UPGRADE, DOWNGRADE, SAME
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        boolean km = Formatter.getUseKilometers(pref, editor);

        if (upgradeState == UpgradeState.NEW) {
            editor.putString(getResources().getString(R.string.pref_autolap),
                    Double.toString(km ? Formatter.km_meters : Formatter.mi_meters));
        }
        editor.commit();

        // clear basicTargetType between application startup/shutdown
        pref.edit().remove("basicTargetType").commit();

        System.err.println("app-version: " + versionCode + ", upgradeState: " + upgradeState
                + ", km: " + km);

        PreferenceManager.setDefaultValues(this, R.layout.settings, false);
        PreferenceManager.setDefaultValues(this, R.layout.audio_cue_settings, true);

        TabHost tabHost = getTabHost(); // The activity TabHost

        tabHost.addTab(tabHost.newTabSpec("Start")
                .setIndicator("Start", getDrawable(R.drawable.ic_tab_main))
                .setContent(new Intent(this, StartActivity.class)));

        tabHost.addTab(tabHost.newTabSpec("Feed")
                .setIndicator("Feed", getDrawable(R.drawable.ic_tab_feed))
                .setContent(new Intent(this, FeedActivity.class)));

        tabHost.addTab(tabHost.newTabSpec("History")
                .setIndicator("History", getDrawable(R.drawable.ic_tab_history))
                .setContent(new Intent(this, HistoryActivity.class)));

        tabHost.addTab(tabHost.newTabSpec("Settings")
                .setIndicator("Settings", getDrawable(R.drawable.ic_tab_setup))
                .setContent(new Intent(this, SettingsActivity.class)));

        // Set tabs Colors
        tabHost.setBackgroundColor(Color.BLACK);
        tabHost.getTabWidget().setBackgroundColor(Color.BLACK);
        tabHost.setCurrentTab(0);

        if (upgradeState == UpgradeState.UPGRADE) {
            whatsNew();
        }

        handleBundled(getApplicationContext().getAssets(), "bundled", getFilesDir().getPath()
                + "/..");
    }

    void handleBundled(AssetManager mgr, String src, String dst) {
        String list[] = null;
        try {
            list = mgr.list(src);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (list != null) {
            for (int i = 0; i < list.length; ++i) {
                boolean isFile = false;
                String add = list[i];
                try {
                    InputStream is = mgr.open(src + File.separator + add);
                    is.close();
                    isFile = true;
                } catch (Exception ex) {
                }

                System.err.println("Found: " + dst + ", " + add + ", isFile: " + isFile);
                if (isFile == false) {
                    File dstDir = new File(dst + File.separator + add);
                    dstDir.mkdir();
                    if (!dstDir.isDirectory()) {
                        System.err.println("Failed to copy " + add + " as \"" + dst
                                + "\" is not a directory!");
                        continue;
                    }
                    if (dst == null)
                        handleBundled(mgr, src + File.separator + add, add);
                    else
                        handleBundled(mgr, src + File.separator + add, dst + File.separator + add);
                } else {
                    String tmp = dst + File.separator + add;
                    File dstFile = new File(tmp);
                    if (dstFile.isDirectory() || dstFile.isFile()) {
                        System.err.println("Skip: " + tmp +
                                ", isDirectory(): " + dstFile.isDirectory() +
                                ", isFile(): " + dstFile.isFile());
                        continue;
                    }

                    String key = "install_bundled_" + add;
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
                    if (pref.contains(key)) {
                        System.err.println("Skip: " + key);
                        continue;

                    }

                    pref.edit().putBoolean(key, true).commit();
                    System.err.println("Copying: " + tmp);
                    InputStream input = null;
                    try {
                        input = mgr.open(src + File.separator + add);
                        FileUtil.copy(input, tmp);
                        handleHooks(src, add);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        FileUtil.close(input);
                    }
                }
            }
        }
    }

    private void handleHooks(String path, String file) {
        if (file.contains("_audio_cues.xml")) {
            String name = file.substring(0, file.indexOf("_audio_cues.xml"));

            DBHelper mDBHelper = new DBHelper(this);
            SQLiteDatabase mDB = mDBHelper.getWritableDatabase();

            ContentValues tmp = new ContentValues();
            tmp.put(DB.AUDIO_SCHEMES.NAME, name);
            tmp.put(DB.AUDIO_SCHEMES.SORT_ORDER, 0);
            mDB.insert(DB.AUDIO_SCHEMES.TABLE, null, tmp);

            mDB.close();
            mDBHelper.close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i = null;
        switch (item.getItemId()) {
            case R.id.menu_accounts:
                i = new Intent(this, AccountListActivity.class);
                break;
            case R.id.menu_workouts:
                i = new Intent(this, ManageWorkoutsActivity.class);
                break;
            case R.id.menu_audio_cues:
                i = new Intent(this, AudioCueSettingsActivity.class);
                break;
            case R.id.menu_settings:
                getTabHost().setCurrentTab(3);
                return true;
            case R.id.menu_rate:
                onRateClick.onClick(null);
                break;
            case R.id.menu_whatsnew:
                whatsNew();
                break;
        }
        if (i != null) {
            startActivity(i);
        }
        return true;
    }

    public OnClickListener onRateClick = new OnClickListener() {
        @Override
        public void onClick(View arg0) {
            try {
                Uri uri = Uri.parse("market://details?id=" + getPackageName());
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    };

    public void whatsNew() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.whatsnew, null);
        WebView wv = (WebView) view.findViewById(R.id.whats_new_view);
        builder.setTitle("What's new");
        builder.setView(view);
        builder.setPositiveButton("Rate RunnerUp", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onRateClick.onClick(null);
            }

        });
        builder.setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
        wv.loadUrl("file:///android_asset/changes.html");
    }
}
