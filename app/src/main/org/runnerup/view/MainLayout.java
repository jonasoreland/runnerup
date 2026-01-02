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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.util.FileUtil;
import org.runnerup.util.Formatter;
import org.runnerup.util.GoogleApiHelper;
import org.runnerup.util.ViewUtil;

public class MainLayout extends AppCompatActivity {

  private enum UpgradeState {
    UNKNOWN,
    NEW,
    UPGRADE,
    DOWNGRADE,
    SAME
  }

  private ViewPager2 pager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);
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
      editor.putString(
          getResources().getString(R.string.pref_autolap),
          Double.toString(km ? Formatter.km_meters : Formatter.mi_meters));
    }
    editor.apply();

    // clear basicTargetType between application startup/shutdown
    pref.edit().remove(getString(R.string.pref_basic_target_type)).apply();

    Log.i(
        getClass().getName(),
        "app-version: " + versionCode + ", upgradeState: " + upgradeState + ", km: " + km);

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
    PreferenceManager.setDefaultValues(this, R.xml.settings_controls, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_graph, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_maintenance, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_map, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_sensors, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_units, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_workout, true);

    // Set up the ViewPager2 and associate it with the adapter responsible
    // for managing the lifecycle and displaying the different fragment pages.
    pager = findViewById(R.id.pager);
    BottomNavFragmentStateAdapter adapter = new BottomNavFragmentStateAdapter(this);
    pager.setAdapter(adapter);

    // Allows swiping between tabs
    pager.setUserInputEnabled(true);

    // Attach the TabLayout to the ViewPager2 using a TabLayoutMediator.
    // The mediator synchronizes the selected tab with the displayed page in the ViewPager2,
    // and allows for configuring the appearance of each tab (e.g., setting icons/titles).
    TabLayout tabLayout = findViewById(R.id.tab_layout);
    new TabLayoutMediator(
            tabLayout,
            pager,
            false,
            true, // Uses animation when switching tabs
            (tab, position) -> tab.setIcon(adapter.getIcon(position)))
        .attach();

    if (upgradeState == UpgradeState.UPGRADE) {
      whatsNew();
    }

    // Import workouts/schemes. No permission needed
    handleBundled(getApplicationContext().getAssets(), "bundled", getFilesDir().getPath() + "/..");

    // if we were called from an intent-filter because user opened "runnerup.db.export", load it
    final String filePath;
    final Uri data = getIntent().getData();
    if (data != null) {
      if ("content".equals(data.getScheme())) {
        Cursor cursor =
            this.getContentResolver()
                .query(
                    data,
                    new String[] {android.provider.MediaStore.Images.ImageColumns.DATA},
                    null,
                    null,
                    null);
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

    // Apply system bars insets to avoid UI overlap
    ViewUtil.Insets(findViewById(R.id.main_root), true);

    // Handle back navigation
    getOnBackPressedDispatcher().addCallback(this, onBackPressed);
  }

  /**
   * An {@link OnBackPressedCallback} instance that provides custom handling for back navigation
   * within the activity.
   *
   * <p>When on the first page ({@link StartFragment}), it implements a "press back again to exit"
   * behavior. Otherwise, it navigates back to the first page.
   */
  private final OnBackPressedCallback onBackPressed =
      new OnBackPressedCallback(true /* enabled */) {
        @Override
        public void handleOnBackPressed() {
          // If not on the first page, navigate back to the first page instead of exiting.
          if (pager.getCurrentItem() != 0) {
            pager.setCurrentItem(0);
            return;
          }

          // If on the first page (StartFragment) and GPS logging is active but not auto-started,
          // stop GPS instead of exiting the app.
          Fragment fragment = getCurrentFragment();

          if (fragment instanceof StartFragment startFragment) {
            if (!startFragment.getAutoStartGps() && startFragment.isGpsLogging()) {
              startFragment.stopGps();
              startFragment.updateView();
              return;
            }
          }

          // Temporarily disable this callback to allow the system to handle the next back press
          // for exiting the app.
          this.setEnabled(false);

          // If none of the above conditions were met, show the "press back again to exit" toast,
          // and re-enable the callback after a delay.
          Toast.makeText(
                  MainLayout.this,
                  getString(org.runnerup.common.R.string.Catch_backbuttonpress),
                  Toast.LENGTH_SHORT)
              .show();

          new Handler().postDelayed(() -> this.setEnabled(true), 3 * 1000);
        }
      };

  /**
   * Returns the currently resumed fragment within this activity's fragment manager.
   *
   * @return The Fragment that is currently in the {@link Lifecycle.State#RESUMED RESUMED} state, or
   *     {@code null} if no fragment is in the resumed state.
   */
  private Fragment getCurrentFragment() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    List<Fragment> fragments = fragmentManager.getFragments();

    // There is currently no direct API in ViewPager2 or FragmentStateAdapter to reliably
    // retrieve the fragment at a specific position or the currently active fragment based
    // on its position alone. Instead, we iterate through all fragments managed by the
    // FragmentManager and identify the one that is currently in the RESUMED state,
    // which FragmentStateAdapter ensures is the current page's fragment.
    // See: https://issuetracker.google.com/issues/210202198#comment2
    for (Fragment fragment : fragments) {
      if (fragment != null && fragment.isResumed()) {
        return fragment;
      }
    }

    return null;
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
          // Normal, src is directory for first call
        }

        Log.d(getClass().getName(), "Found: " + src + ", " + dst + ", isFile: " + isFile);

        if (!isFile) {
          // The request is hierarchical, source is still on a directory level
          File dstDir = new File(dstBase);
          //noinspection ResultOfMethodCallIgnored
          dstDir.mkdir();
          if (!dstDir.isDirectory()) {
            Log.i(
                getClass().getName(),
                "Failed to copy " + src + " as \"" + dstBase + "\" is not a directory!");
            continue;
          }
          handleBundled(mgr, src, dst);
        } else {
          // Source is a file, ready to copy
          File dstFile = new File(dst);
          if (dstFile.isDirectory() || dstFile.isFile()) {
            Log.d(
                getClass().getName(),
                "Skip: "
                    + dst
                    + ", isDirectory(): "
                    + dstFile.isDirectory()
                    + ", isFile(): "
                    + dstFile.isFile());
            continue;
          }

          // Only copy if the key do not exist already
          String key = "install_bundled_" + add;
          SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
          if (pref.contains(key)) {
            Log.d(getClass().getName(), "Skip already existing pref: " + key);
            continue;
          }

          pref.edit().putBoolean(key, true).apply();

          Log.d(getClass().getName(), "Copying: " + dst);
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

  private final OnClickListener onRateClick =
      arg0 -> {
        try {
          Uri uri = Uri.parse("market://details?id=" + getPackageName());
          startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      };

  private void whatsNew() {
    LayoutInflater inflater = (LayoutInflater) getSystemService(Service.LAYOUT_INFLATER_SERVICE);
    @SuppressLint("InflateParams")
    View view = inflater.inflate(R.layout.whatsnew, null);
    WebView wv = view.findViewById(R.id.web_view1);
    AlertDialog.Builder builder =
        new AlertDialog.Builder(this)
            .setTitle(org.runnerup.common.R.string.Whats_new)
            .setView(view)
            .setNegativeButton(
                org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss());
    if (GoogleApiHelper.isGooglePlayServicesAvailable(this)) {
      builder.setPositiveButton(
          org.runnerup.common.R.string.Rate_RunnerUp, (dialog, which) -> onRateClick.onClick(null));
    }
    builder.show();
    wv.loadUrl("file:///android_asset/changes.html");
  }
}
