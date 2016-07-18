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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.app.TabActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
//import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.TabHost;
import android.widget.Toast;

import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.util.FileUtil;
import org.runnerup.util.Formatter;
import org.runnerup.widget.WidgetUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@TargetApi(Build.VERSION_CODES.FROYO)
public class MainLayout extends TabActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private Drawable myGetDrawable(int resId) {
        Drawable d = getResources().getDrawable(resId);
        return d;
    }

    private enum UpgradeState {
        UNKNOWN, NEW, UPGRADE, DOWNGRADE, SAME
    }

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
        boolean km = Formatter.getUseKilometers(getResources(), pref, editor);

        if (upgradeState == UpgradeState.NEW) {
            editor.putString(getResources().getString(R.string.pref_autolap),
                    Double.toString(km ? Formatter.km_meters : Formatter.mi_meters));
        }
        editor.commit();

        // clear basicTargetType between application startup/shutdown
        pref.edit().remove(getString(R.string.pref_basic_target_type)).commit();

        Log.e(getClass().getName(), "app-version: " + versionCode + ", upgradeState: " + upgradeState
                + ", km: " + km);

        PreferenceManager.setDefaultValues(this, R.xml.settings, false);
        PreferenceManager.setDefaultValues(this, R.xml.audio_cue_settings, true);

        TabHost tabHost = getTabHost(); // The activity TabHost

        tabHost.addTab(tabHost.newTabSpec("Start")
                .setIndicator(/*getString(R.string.Start)*/"", myGetDrawable(R.drawable.ic_tab_main))
                .setContent(new Intent(this, StartActivity.class)));

        tabHost.addTab(tabHost.newTabSpec("Feed")
                .setIndicator(/*getString(R.string.feed)*/"", myGetDrawable(R.drawable.ic_tab_feed))
                .setContent(new Intent(this, FeedActivity.class)));

        tabHost.addTab(tabHost.newTabSpec("History")
                .setIndicator(/*getString(R.string.History)*/"", myGetDrawable(R.drawable.ic_tab_history))
                .setContent(new Intent(this, HistoryActivity.class)));

        tabHost.addTab(tabHost.newTabSpec("Settings")
                .setIndicator(/*getString(R.string.Settings)*/"", myGetDrawable(R.drawable.ic_tab_setup))
                .setContent(new Intent(this, SettingsActivity.class)));

        // Set tabs Colors
        //tabHost.setBackgroundColor(Color.BLACK);
        //tabHost.getTabWidget().setBackgroundColor(Color.BLACK);
        tabHost.setCurrentTab(0);
        WidgetUtil.addLegacyOverflowButton(getWindow());

        if (upgradeState == UpgradeState.UPGRADE) {
            whatsNew();
        }
        requestGpsPermissions(this, tabHost.getCurrentView());

        //Will request storage permissions if needed
        handleBundled(this, tabHost.getCurrentView(), getApplicationContext().getAssets(), "bundled", getFilesDir().getPath()
                + "/..");

        // if we were called from an intent-filter because user opened "runnerup.db.export", load it
        final Uri data = getIntent().getData();
        if (data != null) {
            if (SettingsActivity.requestReadStoragePermissions(MainLayout.this)) {
                String filePath = null;
                if ("content".equals(data.getScheme())) {
                    Cursor cursor = this.getContentResolver().query(data, new String[]{android.provider.MediaStore.Images.ImageColumns.DATA}, null, null, null);
                    cursor.moveToFirst();
                    filePath = cursor.getString(0);
                    cursor.close();
                } else {
                    filePath = data.getPath();
                }
                Log.i(getClass().getSimpleName(), "Importing database from " + filePath);
                DBHelper.importDatabase(MainLayout.this, filePath);
            } else {
                Toast.makeText(this, "Storage permission not granted in Android settings, predefined workouts are not imported.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    void handleBundled(final Activity activity, final View view, AssetManager mgr, String src, String dst) {
        String list[] = null;

        try {
            list = mgr.list(src);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (list != null) {
             for (String aList : list) {
                boolean isFile = false;
                String add = aList;

                String key = "install_bundled_" + add;
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
                if (pref.contains(key)) {
                    Log.e(getClass().getName(), "Skip: " + key);
                    continue;
                }

                Log.e(getClass().getName(), "Found: " + dst + ", " + add + ", isFile: " + isFile);
                if (!SettingsActivity.requestReadStoragePermissions(activity) && Build.VERSION.SDK_INT >= 16) {
                    //TODO Defer this handling to when the permission response is received
                    //The "bundled" handling should be moved to when needed, not startup
                    Log.e(getClass().getName(), "No storage permission for "+add);
                    //Only request permissions once, if granted import will be done next
//                    if (view == null){
                        //Toast, not intrusive popup at every start
                        Toast.makeText(activity, "Storage permission not granted in Android settings, predefined workouts are not imported.",
                              Toast.LENGTH_SHORT).show();
//                    } else{
                        //Snackbar is a better option, in android.support.design.widget.Snackbar;
                        //However, requires tabHost.getCurrentView() and a AppCompat Theme for TabHost
                        //Replace TabHost first, a popup is too intrusive
//                        Snackbar.make(view, "Storage permission is required to import predefined workouts",
//                                Snackbar.LENGTH_INDEFINITE)
//                                .setAction(R.string.OK, new View.OnClickListener() {
//                                    @Override
//                                    public void onClick(View view) {
//                                        ActivityCompat.requestPermissions(activity,
//                                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
//                                                REQUEST_LOCATION);
//                                    }
//                                })
//                                .show();
//                    }
                    break;
                }

                try {
                    InputStream is = mgr.open(src + File.separator + add);
                    is.close();
                    isFile = true;
                } catch (Exception ex) {
                }

                if (isFile == false) {
                    File dstDir = new File(dst + File.separator + add);
                    if (!dstDir.mkdir() || !dstDir.isDirectory()) {
                        Log.e(getClass().getName(), "Failed to copy " + add + " as \"" + dst
                                + "\" is not a directory!");
                        continue;
                    }
                    if (dst == null)
                        handleBundled(activity, view, mgr, src + File.separator + add, add);
                    else
                        handleBundled(activity, view, mgr, src + File.separator + add, dst + File.separator + add);
                } else {
                    String tmp = dst + File.separator + add;
                    File dstFile = new File(tmp);
                    if (dstFile.isDirectory() || dstFile.isFile()) {
                        Log.e(getClass().getName(), "Skip: " + tmp +
                                ", isDirectory(): " + dstFile.isDirectory() +
                                ", isFile(): " + dstFile.isFile());
                        continue;
                    }

                    pref.edit().putBoolean(key, true).commit();
                    //Write permissions should be granted already here, then write should be granted automatically
                    //No extra user information
                    if (SettingsActivity.requestWriteStoragePermissions(activity)) {
                        Log.e(getClass().getName(), "Copying: " + tmp);
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
    }

    private void handleHooks(String path, String file) {
        if (file.contains("_audio_cues.xml")) {
            String name = file.substring(0, file.indexOf("_audio_cues.xml"));

            SQLiteDatabase mDB = DBHelper.getWritableDatabase(this);

            ContentValues tmp = new ContentValues();
            tmp.put(DB.AUDIO_SCHEMES.NAME, name);
            tmp.put(DB.AUDIO_SCHEMES.SORT_ORDER, 0);
            mDB.insert(DB.AUDIO_SCHEMES.TABLE, null, tmp);

            DBHelper.closeDB(mDB);
        }
    }

    public final OnClickListener onRateClick = new OnClickListener() {
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
        WebView wv = (WebView) view.findViewById(R.id.web_view1);
        builder.setTitle(getString(R.string.Whats_new));
        builder.setView(view);
        builder.setPositiveButton(getString(R.string.Rate_RunnerUp), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onRateClick.onClick(null);
            }

        });
        builder.setNegativeButton(getString(R.string.Dismiss), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
        wv.loadUrl("file:///android_asset/changes.html");
    }

    public static void requestGpsPermissions(final Activity activity, final View view)
    {
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
//                if (view == null){
                    //Toast, not intrusive popup at every start
                    Toast.makeText(activity, "GPS permission not granted in Android settings",
                            Toast.LENGTH_SHORT).show();
//                } else{
//                    //Snackbar is a better option, in android.support.design.widget.Snackbar;
//                    //However, requires tabHost.getCurrentView() and a AppCompat Theme for TabHost
//                    //Replace TabHost first, a popup is too intrusive
//                    Snackbar.make(view, "GPS permission is required",
//                            Snackbar.LENGTH_INDEFINITE)
//                            .setAction(R.string.OK, new View.OnClickListener() {
//                                @Override
//                                public void onClick(View view) {
//                                    ActivityCompat.requestPermissions(activity,
//                                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
//                                            REQUEST_LOCATION);
//                                }
//                            })
//                            .show();
//                }
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_LOCATION);
            }
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

    /**
     * Id to identify a permission request.
     */
    private static final int REQUEST_LOCATION = 0;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_LOCATION) {
            // Check if the only required permission has been granted (could react on the response)
            if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                String s = "Location permission was not granted";
                Log.i("MainLayout", s);
                Toast.makeText(MainLayout.this, s, Toast.LENGTH_SHORT).show();
            }

        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
