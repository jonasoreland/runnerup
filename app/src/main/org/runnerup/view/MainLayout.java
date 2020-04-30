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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.app.TabActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.Toast;

import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.util.FileUtil;
import org.runnerup.util.Formatter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class MainLayout extends TabActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private Drawable myGetDrawable(int resId) {
        return getResources().getDrawable(resId);
    }

    private View getTabView(CharSequence label, int iconResource) {
        @SuppressLint("InflateParams")View tabView = getLayoutInflater().inflate(R.layout.bottom_tab_indicator, null);
        // ((TextView)tabView.findViewById(R.id.title)).setText(label);
        Drawable icon = ContextCompat.getDrawable(this, iconResource);
        ((ImageView)tabView.findViewById(R.id.icon)).setImageDrawable(icon);
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
        //GPS is essential, always nag user if not granted
        requestGpsPermissions(this, tabHost.getCurrentView());

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
            if (requestReadStoragePermissions(MainLayout.this)) {
                Log.i(getClass().getSimpleName(), "Importing database from " + filePath);
                DBHelper.importDatabase(MainLayout.this, filePath);
            } else {
                Toast.makeText(this, "Storage permission not granted in Android settings, db is not imported.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleBundled(AssetManager mgr, String srcBase, String dstBase) {
        String list[] = null;

        try {
            list = mgr.list(srcBase);
        } catch (IOException e) {
            e.printStackTrace();
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

    private final OnClickListener onRateClick = new OnClickListener() {
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

    private void whatsNew() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        @SuppressLint("InflateParams") View view = inflater.inflate(R.layout.whatsnew, null);
        WebView wv = (WebView) view.findViewById(R.id.web_view1);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.Whats_new))
                .setView(view)
                .setPositiveButton(getString(R.string.Rate_RunnerUp), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onRateClick.onClick(null);
            }

        })
                .setNegativeButton(getString(R.string.Dismiss), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
        wv.loadUrl("file:///android_asset/changes.html");
    }

    private static void requestGpsPermissions(final Activity activity, final View view) {
        String[] requiredPerms = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        List<String> defaultPerms = new ArrayList<>();
        List<String> shouldPerms = new ArrayList<>();
        for (final String perm : requiredPerms) {
            if (ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)) {
                    shouldPerms.add(perm);
                } else {
                    defaultPerms.add(perm);
                }
            }
        }
        if (defaultPerms.size() > 0) {
            // No explanation needed, we can request the permission.
            final String[] perms = new String[defaultPerms.size()];
            defaultPerms.toArray(perms);
            ActivityCompat.requestPermissions(activity, perms, REQUEST_LOCATION);
        }

        if (shouldPerms.size() > 0) {
            //Snackbar, no popup
            final String[] perms = new String[shouldPerms.size()];
            shouldPerms.toArray(perms);
            Snackbar.make(view, activity.getResources().getString(R.string.GPS_permission_required),
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.OK, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(activity, perms, REQUEST_LOCATION);
                        }
                    })
                    .show();
        }
    }

    private static boolean requestReadStoragePermissions(final Activity activity) {
        boolean ret = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
                ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ret = false;

            //Request permission (not using shouldShowRequestPermissionRationale())
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE);
            String s = "Requesting read permission";
            Log.i(activity.getClass().getSimpleName(), s);
        }
        return ret;
    }

    /**
     * Id to identify a permission request.
     */
    private static final int REQUEST_LOCATION = 1000;
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

        } else if (requestCode == REQUEST_LOCATION) {
            // Check if the only required permission has been granted (could react on the response)
            if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                String s = "Permission response OK: " + grantResults.length;
                Log.v("MainLayout", s);
                //Toast.makeText(MainLayout.this, s, Toast.LENGTH_SHORT).show();
            } else {
                String s = "Location Permission was not granted: ";
                Log.i("MainLayout", s);
                Toast.makeText(MainLayout.this, s, Toast.LENGTH_SHORT).show();
            }

        } else {
            String s = "Unexpected permission request: " + requestCode;
            Log.w("MainLayout", s);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
