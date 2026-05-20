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
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.ValueModel;
import org.runnerup.db.DBHelper;
import org.runnerup.hr.HRProvider;
import org.runnerup.hr.MockHRProvider;
import org.runnerup.notification.GpsBoundState;
import org.runnerup.notification.GpsSearchingState;
import org.runnerup.notification.NotificationManagerDisplayStrategy;
import org.runnerup.notification.NotificationStateManager;
import org.runnerup.tracker.GpsInformation;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.tracker.component.TrackerWear;
import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.util.TickListener;
import org.runnerup.widget.ClassicSpinner;
import org.runnerup.widget.SpinnerInterface.OnCloseDialogListener;
import org.runnerup.widget.SpinnerInterface.OnSetValueListener;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Sport;
import org.runnerup.workout.Workout;
import org.runnerup.workout.Workout.StepListEntry;
import org.runnerup.workout.WorkoutBuilder;
import org.runnerup.workout.WorkoutSerializer;

public class StartFragment extends Fragment implements TickListener, GpsInformation {

  private enum GpsLevel {
    NOT_FIXED,
    POOR,
    ACCEPTABLE,
    GOOD
  }

  private static final String TAB_BASIC = "basic";
  private static final String TAB_INTERVAL = "interval";
  static final String TAB_ADVANCED = "advanced";

  private boolean statusDetailsShown = false;

  // StartFragment normally stop GPS in onDestroy (or onStop)
  // but if the fragment stop as it has started a RunActivity
  // it should not!
  // TODO Figure out a way to do this prettier
  private boolean runActivityPending = false;
  private Tracker mTracker = null;
  private org.runnerup.tracker.GpsStatus mGpsStatus = null;

  private TabHost tabHost = null;
  private View startButton = null;

  private ImageView expandIcon = null;
  private TextView noDevicesConnected = null;

  private Button gpsEnable = null;
  private ImageView gpsIndicator = null;
  private TextView gpsMessage = null;
  private LinearLayout gpsDetailRow = null;
  private ImageView gpsDetailIndicator = null;
  private TextView gpsDetailMessage = null;

  private View hrIndicator = null;
  private TextView hrMessage = null;

  private View wearOsIndicator = null;
  private TextView wearOsMessage = null;
  private TrackerWear.WearNotifier mWearNotifier = null;

  boolean sportWithoutGps = false;
  boolean batteryLevelMessageShown = false;

  TitleSpinner simpleTargetType = null;
  TitleSpinner simpleTargetPaceValue = null;
  TitleSpinner simpleTargetHrz = null;
  AudioSchemeListAdapter simpleAudioListAdapter = null;
  HRZonesListAdapter hrZonesAdapter = null;

  TitleSpinner intervalType = null;
  TitleSpinner intervalTime = null;
  TitleSpinner intervalDistance = null;
  TitleSpinner intervalRestType = null;
  TitleSpinner intervalRestTime = null;
  TitleSpinner intervalRestDistance = null;
  AudioSchemeListAdapter intervalAudioListAdapter = null;

  TitleSpinner advancedWorkoutSpinner = null;
  WorkoutListAdapter advancedWorkoutListAdapter = null;
  Workout advancedWorkout = null;
  ListView advancedStepList = null;
  final WorkoutStepsAdapter advancedWorkoutStepsAdapter = new WorkoutStepsAdapter();
  AudioSchemeListAdapter advancedAudioListAdapter = null;

  SQLiteDatabase mDB = null;

  Formatter formatter = null;
  private NotificationStateManager notificationStateManager;
  private GpsSearchingState gpsSearchingState;
  private GpsBoundState gpsBoundState;
  // Id to identify a permission request.
  // Note that the result is not used, the user is dropped back to initial view when a request is
  // done.
  private static final int REQUEST_LOCATION = 3000;
  private static final int START_ACTIVITY = 112;

  public StartFragment() {
    super(R.layout.start);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    Context context = requireContext();
    mDB = DBHelper.getWritableDatabase(context);
    formatter = new Formatter(context);

    bindGpsTracker();
    mGpsStatus = new org.runnerup.tracker.GpsStatus(context);
    NotificationManager notificationManager =
        ContextCompat.getSystemService(context, NotificationManager.class);
    notificationStateManager =
        new NotificationStateManager(new NotificationManagerDisplayStrategy(notificationManager));
    gpsSearchingState = new GpsSearchingState(context, this);
    gpsBoundState = new GpsBoundState(context);

    ClassicSpinner sportSpinner = view.findViewById(R.id.sport_spinner);
    ArrayAdapter<CharSequence> adapter =
        new ArrayAdapter<>(
            context, R.layout.actionbar_spinner, Sport.getStringArray(getResources()));
    adapter.setDropDownViewResource(R.layout.actionbar_dropdown_spinner);
    sportSpinner.setAdapter(adapter);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    sportSpinner.setViewSelection(
        prefs.getInt(getResources().getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING));

    startButton = view.findViewById(R.id.start_button);
    startButton.setOnClickListener(startButtonClick);

    expandIcon = view.findViewById(R.id.expand_icon);
    noDevicesConnected = view.findViewById(R.id.device_status);

    gpsIndicator = view.findViewById(R.id.gps_indicator);
    gpsMessage = view.findViewById(R.id.gps_message);
    gpsDetailRow = view.findViewById(R.id.gps_detail_row);
    gpsDetailIndicator = view.findViewById(R.id.gps_detail_indicator);
    gpsDetailMessage = view.findViewById(R.id.gps_detail_message);

    gpsEnable = view.findViewById(R.id.gps_enable_button);
    gpsEnable.setOnClickListener(gpsEnableClick);

    hrMessage = view.findViewById(R.id.hr_message);
    hrIndicator = view.findViewById(R.id.hr_indicator);

    wearOsIndicator = view.findViewById(R.id.wearos_indicator);
    wearOsMessage = view.findViewById(R.id.wearos_message);

    view.findViewById(R.id.status_layout).setOnClickListener(v -> toggleStatusDetails());

    // TODO: Replace TabHost with ViewPager2 and TabLayout
    tabHost = view.findViewById(R.id.tabhost_start);
    tabHost.setup();
    TabSpec tabSpec = tabHost.newTabSpec(TAB_BASIC);
    tabSpec.setIndicator(
        WidgetUtil.createHoloTabIndicator(context, getString(org.runnerup.common.R.string.Basic)));
    tabSpec.setContent(R.id.start_basic_tab);
    tabHost.addTab(tabSpec);

    tabSpec = tabHost.newTabSpec(TAB_INTERVAL);
    tabSpec.setIndicator(
        WidgetUtil.createHoloTabIndicator(
            context, getString(org.runnerup.common.R.string.Interval)));
    tabSpec.setContent(R.id.start_interval_tab);
    tabHost.addTab(tabSpec);

    tabSpec = tabHost.newTabSpec(TAB_ADVANCED);
    tabSpec.setIndicator(
        WidgetUtil.createHoloTabIndicator(
            context, getString(org.runnerup.common.R.string.Advanced)));
    tabSpec.setContent(R.id.start_advanced_tab);
    tabHost.addTab(tabSpec);

    tabHost.setOnTabChangedListener(onTabChangeListener);
    // tabHost.getTabWidget().setBackgroundColor(Color.DKGRAY);

    LayoutInflater inflater = getLayoutInflater();
    simpleAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
    simpleAudioListAdapter.reload();
    TitleSpinner simpleAudioSpinner = view.findViewById(R.id.basic_audio_cue_spinner);
    simpleAudioSpinner.setAdapter(simpleAudioListAdapter);
    simpleAudioSpinner.setOnSetValueListener(new OnConfigureAudioListener(simpleAudioListAdapter));
    simpleTargetType = view.findViewById(R.id.tab_basic_target_type);
    simpleTargetPaceValue = view.findViewById(R.id.tab_basic_target_pace_max);
    hrZonesAdapter = new HRZonesListAdapter(context, inflater);
    simpleTargetHrz = view.findViewById(R.id.tab_basic_target_hrz);
    simpleTargetHrz.setAdapter(hrZonesAdapter);
    simpleTargetType.setOnCloseDialogListener(simpleTargetTypeClick);

    intervalType = view.findViewById(R.id.interval_type);
    intervalTime = view.findViewById(R.id.start_interval_time);
    intervalTime.setOnSetValueListener(onSetTimeValidator);
    intervalDistance = view.findViewById(R.id.interval_distance);
    intervalType.setOnSetValueListener(intervalTypeSetValue);
    intervalRestType = view.findViewById(R.id.interval_rest_type);
    intervalRestTime = view.findViewById(R.id.interval_rest_time);
    intervalRestTime.setOnSetValueListener(onSetTimeValidator);
    intervalRestDistance = view.findViewById(R.id.interval_rest_distance);
    intervalRestType.setOnSetValueListener(intervalRestTypeSetValue);
    intervalAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
    intervalAudioListAdapter.reload();
    TitleSpinner intervalAudioSpinner = view.findViewById(R.id.interval_audio_cue_spinner);
    intervalAudioSpinner.setAdapter(intervalAudioListAdapter);
    intervalAudioSpinner.setOnSetValueListener(
        new OnConfigureAudioListener(intervalAudioListAdapter));

    advancedAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
    advancedAudioListAdapter.reload();
    TitleSpinner advancedAudioSpinner = view.findViewById(R.id.advanced_audio_cue_spinner);
    advancedAudioSpinner.setAdapter(advancedAudioListAdapter);
    advancedAudioSpinner.setOnSetValueListener(
        new OnConfigureAudioListener(advancedAudioListAdapter));

    advancedWorkoutSpinner = view.findViewById(R.id.advanced_workout_spinner);
    advancedWorkoutListAdapter = new WorkoutListAdapter(inflater);
    advancedWorkoutListAdapter.reload();
    advancedWorkoutSpinner.setAdapter(advancedWorkoutListAdapter);
    advancedWorkoutSpinner.setOnSetValueListener(
        new OnConfigureWorkoutsListener(advancedWorkoutListAdapter));
    advancedStepList = view.findViewById(R.id.advanced_step_list);
    advancedStepList.setDividerHeight(0);
    advancedStepList.setAdapter(advancedWorkoutStepsAdapter);

    Intent i = requireActivity().getIntent();
    if (i != null) {
      if (i.hasExtra("mode")) {
        if (Objects.equals(i.getStringExtra("mode"), TAB_ADVANCED)) {
          tabHost.setCurrentTab(2);
          i.removeExtra("mode");
        }
      }
    }

    updateTargetView();

    mWearNotifier = new TrackerWear.WearNotifier(requireActivity().getApplicationContext());
    mWearNotifier.onViewCreated();

    var listener = sportSpinner.getViewOnItemSelectedListener();
    sportSpinner.setViewOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (listener != null) {
              listener.onItemSelected(parent, view, position, id);
            }
            setGpsNotRequired(Sport.isWithoutGps((int) id));
            StartFragment.this.updateView();
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {
            if (listener != null) {
              listener.onNothingSelected(parent);
            }
          }
        });
  }

  private void setGpsNotRequired(boolean val) {
    if (sportWithoutGps == val) {
      return;
    }

    sportWithoutGps = val;
    if (sportWithoutGps) {
      // Turning GPS off
      if (mTracker != null) {
        mTracker.setWithoutGps(true);
      }
    } else {
      // Toggling GPS on
      Log.e(getClass().getName(), "mTracker.reset()");
      if (mTracker != null) {
        mTracker.setWithoutGps(false);
        mTracker.reset();
        if (mGpsStatus.isStarted()) {
          mTracker.setup();
          startGps();
        }
      }
    }
  }

  private class OnConfigureAudioListener implements OnSetValueListener {
    final AudioSchemeListAdapter adapter;

    OnConfigureAudioListener(AudioSchemeListAdapter adapter) {
      this.adapter = adapter;
    }

    @Override
    public String preSetValue(String newValue) throws IllegalArgumentException {
      if (newValue != null
          && newValue.contentEquals((String) adapter.getItem(adapter.getCount() - 1))) {
        Intent i = new Intent(requireContext(), AudioCueSettingsActivity.class);
        startActivity(i);
        throw new IllegalArgumentException();
      }
      return newValue;
    }

    @Override
    public int preSetValue(int newValueId) throws IllegalArgumentException {
      return newValueId;
    }
  }

  private class OnConfigureWorkoutsListener implements OnSetValueListener {
    final WorkoutListAdapter adapter;

    OnConfigureWorkoutsListener(WorkoutListAdapter adapter) {
      this.adapter = adapter;
    }

    @Override
    public String preSetValue(String newValue) throws IllegalArgumentException {
      if (newValue != null
          && newValue.contentEquals((String) adapter.getItem(adapter.getCount() - 1))) {
        Intent i = new Intent(requireContext(), ManageWorkoutsActivity.class);
        startActivity(i);
        throw new IllegalArgumentException();
      }
      loadAdvanced(newValue);
      return newValue;
    }

    @Override
    public int preSetValue(int newValueId) throws IllegalArgumentException {
      loadAdvanced(null);
      return newValueId;
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    registerStartEventListener();
  }

  @Override
  public void onResume() {
    super.onResume();
    simpleAudioListAdapter.reload();
    intervalAudioListAdapter.reload();
    advancedAudioListAdapter.reload();
    advancedWorkoutListAdapter.reload();
    hrZonesAdapter.reload();
    simpleTargetHrz.setAdapter(hrZonesAdapter);
    if (!hrZonesAdapter.hrZones.isConfigured()) {
      simpleTargetType.addDisabledValue(DB.DIMENSION.HRZ);
    } else {
      simpleTargetType.clearDisabled();
    }

    if (Objects.requireNonNull(tabHost.getCurrentTabTag()).contentEquals(TAB_ADVANCED)) {
      loadAdvanced(null);
    }

    if (!mIsBound || mTracker == null) {
      bindGpsTracker();
    } else {
      onGpsTrackerBound();
    }
    this.updateView();
    mWearNotifier.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();

    if (getAutoStartGps()) {
      // If autoStartGps, then stop it during pause
      stopGps();
    } else {
      if (mTracker != null
          && ((mTracker.getState() == TrackerState.INITIALIZED)
              || (mTracker.getState() == TrackerState.INITIALIZING))) {
        Log.i(getClass().getName(), "mTracker.reset()");
        mTracker.reset();
      }
    }
    mWearNotifier.onPause();
  }

  @Override
  public void onStop() {
    super.onStop();
    unregisterStartEventListener();
  }

  @Override
  public void onDestroy() {
    stopGps();
    unbindGpsTracker();
    mGpsStatus = null;

    DBHelper.closeDB(mDB);
    super.onDestroy();
    mWearNotifier.onDestroy();
  }

  private final BroadcastReceiver startEventBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          requireActivity()
              .runOnUiThread(
                  () -> {
                    if (mTracker == null || startButton.getVisibility() != View.VISIBLE) {
                      return;
                    }

                    if (mTracker.getState() == TrackerState.INIT /* this will start gps */
                        || mTracker.getState() == TrackerState.INITIALIZED /* ...start a workout*/
                        || mTracker.getState() == TrackerState.CONNECTED) {
                      startButton.performClick();
                    }
                  });
        }
      };

  private void registerStartEventListener() {
    IntentFilter intentFilter = new IntentFilter();
    // START_WORKOUT is used by Wear when GPS is captured
    // START_ACTIVITY should also start GPS if not done
    intentFilter.addAction(Constants.Intents.START_ACTIVITY);
    intentFilter.addAction(Constants.Intents.START_WORKOUT);
    ContextCompat.registerReceiver(
        requireActivity(),
        startEventBroadcastReceiver,
        intentFilter,
        ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  private void unregisterStartEventListener() {
    try {
      requireActivity().unregisterReceiver(startEventBroadcastReceiver);
    } catch (Exception ignored) {
    }
  }

  private void onGpsTrackerBound() {
    // check and request permissions at startup
    boolean missingEssentialPermission = checkPermissions(false);
    mTracker.setWithoutGps(sportWithoutGps);
    if (!missingEssentialPermission && getAutoStartGps()) {
      startGps();
    } else {
      Log.d(getClass().getName(), "onGpsTrackerBound state: " + mTracker.getState());
      switch (mTracker.getState()) {
        case INIT:
        case CLEANUP:
          mTracker.setup();
          break;
        case INITIALIZING:
        case INITIALIZED:
          break;
        case CONNECTING:
        case CONNECTED:
        case STARTED:
        case PAUSED:
          break;
        case ERROR:
          break;
      }
    }
    updateView();
  }

  public boolean getAutoStartGps() {
    Context ctx = requireActivity().getApplicationContext();
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
    return pref.getBoolean(getString(R.string.pref_startgps), false);
  }

  private void startGps() {
    Log.d(getClass().getName(), "StartFragment.startGps()");
    if (!sportWithoutGps) {
      if (!mGpsStatus.isEnabled()) {
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
      }
      notificationStateManager.displayNotificationState(gpsSearchingState);
    }

    if (mGpsStatus != null && !mGpsStatus.isStarted()) {
      mGpsStatus.start(this);
    }

    if (mTracker != null) {
      mTracker.setWithoutGps(sportWithoutGps);
      mTracker.connect();
    }
  }

  public void stopGps() {
    Log.d(getClass().getName(), "StartFragment.stopGps() skipStop: " + this.runActivityPending);
    if (runActivityPending) {
      return;
    }

    if (mGpsStatus != null) {
      mGpsStatus.stop(this);
    }

    if (mTracker != null) {
      mTracker.reset();
    }

    notificationStateManager.cancelNotification();
  }

  public boolean isGpsLogging() {
    if (mGpsStatus == null) {
      // If mGpsStatus is null, GPS logging is not possible.
      return false;
    }
    return mGpsStatus.isLogging();
  }

  private void notificationBatteryLevel(int batteryLevel) {
    if ((batteryLevel < 0) || (batteryLevel > 100)) {
      return;
    }

    Context context = requireContext();
    final String pref_key = getString(R.string.pref_battery_level_low_notification_discard);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

    int batteryLevelHighThreshold =
        SafeParse.parseInt(
            prefs.getString(getString(R.string.pref_battery_level_high_threshold), "75"), 75);
    if ((batteryLevel > batteryLevelHighThreshold) && (prefs.contains(pref_key))) {
      prefs.edit().remove(pref_key).apply();
      return;
    }

    int batteryLevelLowThreshold =
        SafeParse.parseInt(
            prefs.getString(getString(R.string.pref_battery_level_low_threshold), "15"), 15);
    if (batteryLevel > batteryLevelLowThreshold) {
      return;
    }

    if (prefs.getBoolean(pref_key, false)) {
      return;
    }

    final CheckBox dontShowAgain = new CheckBox(context);
    dontShowAgain.setText(getResources().getText(org.runnerup.common.R.string.Do_not_show_again));

    new AlertDialog.Builder(context)
        .setView(dontShowAgain)
        .setCancelable(false)
        .setTitle(org.runnerup.common.R.string.Warning)
        .setMessage(
            getResources().getText(org.runnerup.common.R.string.Low_HRM_battery_level)
                + "\n"
                + getResources().getText(org.runnerup.common.R.string.Battery_level)
                + ": "
                + batteryLevel
                + "%")
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, which) -> {
              if (dontShowAgain.isChecked()) {
                prefs.edit().putBoolean(pref_key, true).apply();
              }
            })
        .show();
  }

  private final OnTabChangeListener onTabChangeListener =
      tabId -> {
        if (tabId.contentEquals(TAB_ADVANCED)) {
          loadAdvanced(null);
        }
        updateView();
      };

  private Workout prepareWorkout() {
    Context ctx = requireActivity().getApplicationContext();
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
    SharedPreferences audioPref;
    Workout w;

    if (Objects.requireNonNull(tabHost.getCurrentTabTag()).contentEquals(TAB_BASIC)) {
      audioPref =
          WorkoutBuilder.getAudioCuePreferences(ctx, pref, getString(R.string.pref_basic_audio));
      Dimension target = Dimension.valueOf(simpleTargetType.getValueInt());
      w = WorkoutBuilder.createDefaultWorkout(getResources(), pref, target);
    } else if (tabHost.getCurrentTabTag().contentEquals(TAB_INTERVAL)) {
      audioPref =
          WorkoutBuilder.getAudioCuePreferences(ctx, pref, getString(R.string.pref_interval_audio));
      w = WorkoutBuilder.createDefaultIntervalWorkout(getResources(), pref);
    } else if (tabHost.getCurrentTabTag().contentEquals(TAB_ADVANCED)) {
      audioPref =
          WorkoutBuilder.getAudioCuePreferences(ctx, pref, getString(R.string.pref_advanced_audio));
      w = advancedWorkout;
    } else {
      w = null;
      audioPref = null;
    }
    if (w != null) {
      WorkoutBuilder.prepareWorkout(getResources(), pref, w);
      WorkoutBuilder.addAudioCuesToWorkout(getResources(), w, audioPref, pref);
    }
    return w;
  }

  private void startWorkout() {
    mGpsStatus.stop(StartFragment.this);

    // unregister receivers
    unregisterStartEventListener();

    // This will start the advancedWorkoutSpinner!
    mTracker.setWorkout(prepareWorkout());
    mTracker.start();

    runActivityPending = true;
    Intent intent = new Intent(requireContext(), RunActivity.class);
    // TODO: Use the Activity Result API
    StartFragment.this.startActivityForResult(intent, START_ACTIVITY);
    notificationStateManager.cancelNotification(); // will be added by RunActivity
  }

  private final OnClickListener startButtonClick =
      v -> {
        if (mTracker.getState() == TrackerState.CONNECTED) {
          startWorkout();
          return;
        }
        updateView();
      };

  private final OnClickListener gpsEnableClick =
      v -> {
        if (checkPermissions(true)) {
          // Handle view update etc in permission callback
          return;
        }

        if (mTracker.getState() != TrackerState.CONNECTED) {
          startGps();
        }
        updateView();
      };

  private List<String> getPermissions() {
    List<String> requiredPerms = new ArrayList<>();
    requiredPerms.add(Manifest.permission.ACCESS_FINE_LOCATION);
    requiredPerms.add(Manifest.permission.ACCESS_COARSE_LOCATION);

    Context ctx = requireContext();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      requiredPerms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);

      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
      boolean enabled =
          prefs.getBoolean(
              this.getString(org.runnerup.R.string.pref_use_cadence_step_sensor), true);
      if (enabled && TrackerCadence.isAvailable(ctx)) {
        requiredPerms.add(Manifest.permission.ACTIVITY_RECOGNITION);
      }
    }

    PackageManager packageManager = requireContext().getPackageManager();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S /* Android12, sdk31*/
        && (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            || packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))) {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
      final String btDeviceName = prefs.getString(getString(R.string.pref_bt_name), null);
      if (btDeviceName != null && !btDeviceName.isEmpty()) {
        requiredPerms.add(Manifest.permission.BLUETOOTH_CONNECT);
        requiredPerms.add(Manifest.permission.BLUETOOTH_SCAN);
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requiredPerms.add(Manifest.permission.POST_NOTIFICATIONS);
    }

    return requiredPerms;
  }

  /**
   * Check that required permissions are allowed
   *
   * @param popup
   * @return
   */
  private boolean checkPermissions(boolean popup) {
    boolean missingEssentialPermission = false;
    boolean missingAnyPermission = false;
    List<String> requiredPerms = getPermissions();
    List<String> requestPerms = new ArrayList<>();
    Context ctx = requireContext();

    for (final String perm : requiredPerms) {
      if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
        missingAnyPermission = true;
        // Filter non essential permissions for result
        boolean nonEssential =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && perm.equals(Manifest.permission.ACTIVITY_RECOGNITION)
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && (perm.equals(Manifest.permission.BLUETOOTH_CONNECT)
                        || perm.equals(Manifest.permission.BLUETOOTH_SCAN))
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && perm.equals(Manifest.permission.POST_NOTIFICATIONS));
        missingEssentialPermission = missingEssentialPermission || !nonEssential;
        if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), perm)) {
          // A denied permission, show motivation in a popup
          String s = "Permission " + perm + " is explicitly denied";
          Log.i(getClass().getName(), s);
        } else {
          requestPerms.add(perm);
        }
      }
    }

    if (missingAnyPermission) {
      final String[] permissions = new String[requestPerms.size()];
      requestPerms.toArray(permissions);

      if (popup && missingEssentialPermission || !requestPerms.isEmpty()) {
        // Essential or requestable permissions missing
        String baseMessage =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? getString(org.runnerup.common.R.string.GPS_permission_text_Android12)
                : Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? getString(org.runnerup.common.R.string.GPS_permission_text)
                    : getString(org.runnerup.common.R.string.GPS_permission_text_pre_Android10);

        AlertDialog.Builder builder =
            new AlertDialog.Builder(ctx)
                .setTitle(org.runnerup.common.R.string.GPS_permission_required)
                .setNegativeButton(
                    org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss());
        if (!requestPerms.isEmpty()) {
          // Let Android request the permissions
          builder
              .setPositiveButton(
                  org.runnerup.common.R.string.OK,
                  (dialog, id) ->
                      ActivityCompat.requestPermissions(
                          requireActivity(), permissions, REQUEST_LOCATION))
              .setMessage(
                  baseMessage
                      + "\n"
                      + getString(org.runnerup.common.R.string.Request_permission_text));
        } else {
          // Open settings for the app (no direct shortcut to permissions)
          Intent intent =
              new Intent()
                  .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                  .setData(Uri.fromParts("package", ctx.getPackageName(), null));
          builder
              .setPositiveButton(
                  org.runnerup.common.R.string.OK, (dialog, id) -> startActivity(intent))
              .setMessage(
                  baseMessage
                      + "\n\n"
                      + getString(org.runnerup.common.R.string.Request_permission_text));
        }
        builder.show();
      }
    }

    // https://developer.android.com/training/monitoring-device-state/doze-standby#support_for_other_use_cases
    // Permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS requires special approval in Play
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
    final Resources res = this.getResources();
    final boolean suppressOptimizeBatteryPopup =
        prefs.getBoolean(res.getString(R.string.pref_suppress_battery_optimization_popup), false);
    PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
    if ((popup || getAutoStartGps())
        && !suppressOptimizeBatteryPopup
        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        && !pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) {
      Intent intent;
      int msgId;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Around Android 9 the Battery usage setting is available in the app setting too, which is
        // easier to
        // change than in the system settings
        intent =
            new Intent()
                .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", ctx.getPackageName(), null));
        msgId = org.runnerup.common.R.string.Battery_optimization_check_text_Android9;
      } else {
        intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        msgId = org.runnerup.common.R.string.Battery_optimization_check_text;
      }

      new AlertDialog.Builder(ctx)
          .setTitle(org.runnerup.common.R.string.Battery_optimization_check)
          .setMessage(msgId)
          .setPositiveButton(
              org.runnerup.common.R.string.OK, (dialog, which) -> this.startActivity(intent))
          .setNeutralButton(
              org.runnerup.common.R.string.Do_not_show_again,
              (dialog, which) ->
                  prefs
                      .edit()
                      .putBoolean(
                          res.getString(R.string.pref_suppress_battery_optimization_popup), true)
                      .apply())
          .setNegativeButton(
              org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss())
          .show();
    }

    return missingEssentialPermission;
  }

  private void toggleStatusDetails() {
    statusDetailsShown = !statusDetailsShown;
    float bottomMargin;

    if (statusDetailsShown) {
      expandIcon.setImageResource(R.drawable.ic_expand_down_white_24dp);
      bottomMargin = getResources().getDimension(R.dimen.fab_margin_68row);
    } else {
      expandIcon.setImageResource(R.drawable.ic_expand_up_white_24dp);
      bottomMargin = getResources().getDimension(R.dimen.fab_margin_44row);
    }

    ViewGroup.MarginLayoutParams params =
        (ViewGroup.MarginLayoutParams) startButton.getLayoutParams();
    params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, (int) bottomMargin);
    startButton.setLayoutParams(params);

    updateView();
  }

  private GpsLevel getGpsLevel(double gpsAccuracyMeters, int sats) {
    if (!mGpsStatus.isFixed()) {
      return GpsLevel.NOT_FIXED;
    }
    if (gpsAccuracyMeters <= 7 && sats > 7) {
      return GpsLevel.GOOD;
    } else if (gpsAccuracyMeters <= 15 && sats > 4) {
      return GpsLevel.ACCEPTABLE;
    } else {
      return GpsLevel.POOR;
    }
  }

  public void updateView() {
    updateStartGpsButtonView();
    updateStartButtonView();
    updateGPSView();
    boolean hrPresent = updateHRView();
    boolean wearPresent = updateWearOSView();

    if (!hrPresent && !wearPresent && statusDetailsShown) {
      noDevicesConnected.setVisibility(View.VISIBLE);
    } else {
      noDevicesConnected.setVisibility(View.GONE);
    }
  }

  private void updateStartButtonView() {
    do {
      if (!mGpsStatus.isStarted()) {
        break;
      }

      if (!sportWithoutGps) {
        if (!mGpsStatus.isLogging()) {
          break;
        }

        if (!mGpsStatus.isFixed()) {
          break;
        }
      }

      if (mTracker == null || !mIsBound) {
        break;
      }

      if (mTracker.getState() != TrackerState.CONNECTED) {
        break;
      }

      if (Objects.requireNonNull(tabHost.getCurrentTabTag()).contentEquals(TAB_ADVANCED)
          && advancedWorkout == null) {
        break;
      }

      startButton.setVisibility(View.VISIBLE);
      return;
    } while (false);

    startButton.setVisibility(View.GONE);
  }

  private void updateStartGpsButtonView() {
    do {
      if (mGpsStatus.isStarted()) {
        break;
      }

      //
      if (sportWithoutGps) {
        gpsEnable.setText(org.runnerup.common.R.string.Start_tracker);
      } else if (mGpsStatus.isEnabled()) {
        gpsEnable.setText(org.runnerup.common.R.string.Start_GPS);
      } else {
        gpsEnable.setText(org.runnerup.common.R.string.Enable_GPS);
      }
      gpsEnable.setVisibility(View.VISIBLE);
      return;
    } while (false);

    gpsEnable.setVisibility(View.GONE);
  }

  private void updateGPSView() {
    if (!mGpsStatus.isEnabled() || !mGpsStatus.isStarted() || sportWithoutGps) {

      if (statusDetailsShown) {
        gpsDetailMessage.setText(org.runnerup.common.R.string.GPS_indicator_off);
        gpsDetailRow.setVisibility(View.VISIBLE);
        gpsMessage.setVisibility(View.GONE);
      } else {
        gpsMessage.setText(org.runnerup.common.R.string.GPS_indicator_off);
        gpsMessage.setVisibility(View.VISIBLE);
        gpsDetailRow.setVisibility(View.GONE);
      }

      gpsIndicator.setVisibility(View.GONE);
      gpsDetailIndicator.setVisibility(View.GONE);
      return;
    }

    if (statusDetailsShown) {
      gpsIndicator.setVisibility(View.GONE);
      gpsMessage.setVisibility(View.GONE);
      gpsDetailRow.setVisibility(View.VISIBLE);
    } else {
      gpsIndicator.setVisibility(View.VISIBLE);
      gpsMessage.setVisibility(View.VISIBLE);
      gpsDetailRow.setVisibility(View.GONE);
    }

    gpsDetailIndicator.setVisibility(View.VISIBLE);

    int satFixedCount = mGpsStatus.getSatellitesFixed();
    int satAvailCount = mGpsStatus.getSatellitesAvailable();

    // gps accuracy
    float accuracy = getGpsAccuracy();

    // gps details
    String gpsAccuracy = getGpsAccuracyString(accuracy);
    String gpsDetail =
        gpsAccuracy.isEmpty()
            ? String.format(
                getString(org.runnerup.common.R.string.GPS_status_no_accuracy),
                satFixedCount,
                satAvailCount)
            : String.format(
                getString(org.runnerup.common.R.string.GPS_status_accuracy),
                satFixedCount,
                satAvailCount,
                gpsAccuracy);
    gpsDetailMessage.setText(gpsDetail);

    var gpsLevel = getGpsLevel(accuracy, satFixedCount);
    switch (gpsLevel) {
      case NOT_FIXED:
        gpsIndicator.setImageResource(R.drawable.ic_gps_0);
        gpsDetailIndicator.setImageResource(R.drawable.ic_gps_0);
        gpsMessage.setText(org.runnerup.common.R.string.Waiting_for_GPS);
        break;
      case POOR:
        gpsIndicator.setImageResource(R.drawable.ic_gps_1);
        gpsDetailIndicator.setImageResource(R.drawable.ic_gps_1);
        gpsMessage.setText(org.runnerup.common.R.string.GPS_level_poor);
        break;
      case ACCEPTABLE:
        gpsIndicator.setImageResource(R.drawable.ic_gps_2);
        gpsDetailIndicator.setImageResource(R.drawable.ic_gps_2);
        gpsMessage.setText(org.runnerup.common.R.string.GPS_level_acceptable);
        break;
      case GOOD:
        gpsIndicator.setImageResource(R.drawable.ic_gps_3);
        gpsDetailIndicator.setImageResource(R.drawable.ic_gps_3);
        gpsMessage.setText(org.runnerup.common.R.string.GPS_level_good);
        break;
    }
    if (gpsLevel == GpsLevel.NOT_FIXED) {
      notificationStateManager.displayNotificationState(gpsSearchingState);
    } else {
      notificationStateManager.displayNotificationState(gpsBoundState);
    }
  }

  private boolean updateHRView() {
    if (mTracker == null || !mTracker.isComponentConfigured(TrackerHRM.NAME)) {
      hrIndicator.setVisibility(View.GONE);
      hrMessage.setVisibility(View.GONE);
      return false;
    }
    Integer hrVal = null;
    if (mTracker.isComponentConnected(TrackerHRM.NAME)) {
      hrVal = mTracker.getCurrentHRValue();
    }
    if (hrVal != null) {
      if (!batteryLevelMessageShown) {
        batteryLevelMessageShown = true;
        notificationBatteryLevel(mTracker.getCurrentBatteryLevel());
      }
    }

    hrMessage.setText(getHRDetailString());
    hrIndicator.setVisibility(View.VISIBLE);
    if (statusDetailsShown) {
      hrMessage.setVisibility(View.VISIBLE);
    } else {
      hrMessage.setVisibility(View.GONE);
    }

    return true;
  }

  private boolean updateWearOSView() {
    if (mTracker == null || !mTracker.isComponentConfigured(TrackerWear.NAME)) {
      wearOsIndicator.setVisibility(View.GONE);
      wearOsMessage.setVisibility(View.GONE);
      return false;
    }

    wearOsIndicator.setVisibility(View.VISIBLE);

    if (!mTracker.isComponentConnected(TrackerWear.NAME)) {
      wearOsMessage.setVisibility(View.VISIBLE);
      wearOsMessage.setText("?");
    } else if (statusDetailsShown) {
      // wearOsMessage.setText(""); //todo show device name
      wearOsMessage.setVisibility(View.GONE);
    } else {
      wearOsMessage.setVisibility(View.GONE);
    }

    return true;
  }

  @Override
  public float getGpsAccuracy() {
    if (mTracker != null) {
      Location l = mTracker.getLastKnownLocation();

      if (l != null) {
        return l.getAccuracy();
      }
    }
    return -1;
  }

  public String getGpsAccuracyString(float accuracy) {
    String res = "";
    if (accuracy > 0) {
      String accString = formatter.formatElevation(Formatter.Format.TXT_LONG, accuracy);
      if (mTracker.getCurrentElevation() != null) {
        res =
            String.format(
                Locale.getDefault(),
                getString(org.runnerup.common.R.string.GPS_accuracy_elevation),
                accString,
                formatter.formatElevation(
                    Formatter.Format.TXT_LONG, mTracker.getCurrentElevation()));
      } else {
        res =
            String.format(
                Locale.getDefault(),
                getString(org.runnerup.common.R.string.GPS_accuracy_no_elevation),
                accString);
      }
    }
    if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Extra info in debug builds
      if (mTracker != null) {
        Location l = mTracker.getLastKnownLocation();

        if (l != null) {
          res +=
              String.format(
                  Locale.getDefault(),
                  " [%1$s, %2$s/%3$s/s, %4$.1f/%5$.1f deg]",
                  formatter.formatElevation(
                      Formatter.Format.TXT_LONG, l.getVerticalAccuracyMeters()),
                  formatter.formatElevation(Formatter.Format.TXT_SHORT, l.getSpeed()),
                  formatter.formatElevation(
                      Formatter.Format.TXT_LONG, l.getSpeedAccuracyMetersPerSecond()),
                  l.getBearing(),
                  l.getBearingAccuracyDegrees());
        }
      }
    }
    return res;
  }

  private String getHRDetailString() {
    StringBuilder str = new StringBuilder();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    final String btDeviceName = prefs.getString(getString(R.string.pref_bt_name), null);

    if (btDeviceName != null) {
      str.append(btDeviceName);
    } else if (MockHRProvider.NAME.contentEquals(
        prefs.getString(getString(R.string.pref_bt_provider), ""))) {
      str.append("mock: ").append(prefs.getString(getString(R.string.pref_bt_address), "???"));
    }

    if (mTracker.isComponentConnected(TrackerHRM.NAME)) {
      Integer hrVal = mTracker.getCurrentHRValue();
      if (hrVal != null) {
        str.append(" ").append(hrVal);
        Integer batteryLevel = mTracker.getCurrentBatteryLevel();

        if (batteryLevel != HRProvider.BATTERY_LEVEL_UNAVAILABLE) {
          str.append(" ").append(batteryLevel).append("%");
        }
      }
    }
    return str.toString();
  }

  private boolean mIsBound = false;
  private final ServiceConnection mConnection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
          // This is called when the connection with the service has been
          // established, giving us the service object we can use to
          // interact with the service. Because we have bound to a explicit
          // service that we know is running in our own process, we can
          // cast its IBinder to a concrete class and directly access it.
          mTracker = ((Tracker.LocalBinder) service).getService();
          // Tell the user about this for our demo.
          StartFragment.this.onGpsTrackerBound();
          if (mTracker != null) {
            mTracker.registerTrackerStateListener(trackerStateListener);
          }
        }

        public void onServiceDisconnected(ComponentName className) {
          // This is called when the connection with the service has been
          // unexpectedly disconnected -- that is, its process crashed.
          // Because it is running in our same process, we should never
          // see this happen.
          if (mTracker != null) {
            mTracker.unregisterTrackerStateListener(trackerStateListener);
          }
          mTracker = null;
        }
      };

  private void bindGpsTracker() {
    // Establish a connection with the service. We use an explicit
    // class name because we want a specific service implementation that
    // we know will be running in our own process (and thus won't be
    // supporting component replacement by other applications).
    mIsBound =
        requireActivity()
            .getApplicationContext()
            .bindService(
                new Intent(requireContext(), Tracker.class), mConnection, Context.BIND_AUTO_CREATE);
  }

  private void unbindGpsTracker() {
    if (mIsBound) {
      // Detach our existing connection.
      requireActivity().getApplicationContext().unbindService(mConnection);
      mIsBound = false;
    }
    if (mTracker != null) {
      mTracker.unregisterTrackerStateListener(trackerStateListener);
    }
    mTracker = null;
  }

  // TODO: Use Activity Result API
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    registerStartEventListener();

    if (data != null) {
      if (data.getStringExtra("url") != null)
        Log.d(
            getClass().getName(), "data.getStringExtra(\"url\") => " + data.getStringExtra("url"));
      if (data.getStringExtra("ex") != null)
        Log.d(getClass().getName(), "data.getStringExtra(\"ex\") => " + data.getStringExtra("ex"));
      if (data.getStringExtra("obj") != null)
        Log.d(
            getClass().getName(), "data.getStringExtra(\"obj\") => " + data.getStringExtra("obj"));
    }
    if (requestCode == START_ACTIVITY) {
      runActivityPending = false;
      if (!mIsBound || mTracker == null) {
        bindGpsTracker();
      } else {
        onGpsTrackerBound();
      }
    } else {
      advancedWorkoutListAdapter.reload();
    }
    updateView();
  }

  @Override
  public void onTick() {
    updateView();
  }

  private final OnCloseDialogListener simpleTargetTypeClick =
      (spinner, ok) -> {
        if (ok) {
          updateTargetView();
        }
      };

  private void updateTargetView() {
    Dimension dim = Dimension.valueOf(simpleTargetType.getValueInt());
    if (dim == null) {
      simpleTargetPaceValue.setEnabled(false);
      simpleTargetHrz.setEnabled(false);
    } else {
      switch (dim) {
        case PACE:
          simpleTargetPaceValue.setEnabled(true);
          simpleTargetPaceValue.setVisibility(View.VISIBLE);
          simpleTargetHrz.setVisibility(View.GONE);
          break;
        case HRZ:
          simpleTargetPaceValue.setVisibility(View.GONE);
          simpleTargetHrz.setEnabled(true);
          simpleTargetHrz.setVisibility(View.VISIBLE);
      }
    }
  }

  private final OnSetValueListener intervalTypeSetValue =
      new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue) throws IllegalArgumentException {
          return newValue;
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
          boolean time = (newValue == 0);
          intervalTime.setVisibility(time ? View.VISIBLE : View.GONE);
          intervalDistance.setVisibility(time ? View.GONE : View.VISIBLE);
          return newValue;
        }
      };

  private final OnSetValueListener intervalRestTypeSetValue =
      new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue) throws IllegalArgumentException {
          return newValue;
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
          boolean time = (newValue == 0);
          intervalRestTime.setVisibility(time ? View.VISIBLE : View.GONE);
          intervalRestDistance.setVisibility(time ? View.GONE : View.VISIBLE);
          return newValue;
        }
      };

  private void loadAdvanced(String name) {
    Context ctx = requireActivity().getApplicationContext();
    if (name == null) {
      SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
      name = pref.getString(getResources().getString(R.string.pref_advanced_workout), "");
    }
    advancedWorkout = null;
    if ("".contentEquals(name)) return;
    try {
      advancedWorkout = WorkoutSerializer.readFile(ctx, name);
      advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
      advancedWorkoutStepsAdapter.notifyDataSetChanged();
    } catch (Exception ex) {
      ex.printStackTrace();
      new AlertDialog.Builder(requireActivity())
          .setTitle(getString(org.runnerup.common.R.string.Failed_to_load_workout))
          .setMessage(ex.toString())
          .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
          .show();
    }
  }

  @Override
  public int getSatellitesAvailable() {
    return mGpsStatus.getSatellitesAvailable();
  }

  @Override
  public int getSatellitesFixed() {
    return mGpsStatus.getSatellitesFixed();
  }

  final class WorkoutStepsAdapter extends BaseAdapter {

    List<StepListEntry> steps = new ArrayList<>();

    @Override
    public int getCount() {
      return steps.size();
    }

    @Override
    public Object getItem(int position) {
      return steps.get(position);
    }

    @Override
    public long getItemId(int position) {
      return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      StepListEntry entry = steps.get(position);
      StepButton button =
          (convertView instanceof StepButton)
              ? (StepButton) convertView
              : new StepButton(requireContext(), null);
      button.setStep(entry.step());

      float pxToDp = getResources().getDisplayMetrics().density;
      button.setPadding((int) (entry.level() * 8 * pxToDp + 0.5f), 0, 0, 0);
      button.setOnChangedListener(onWorkoutChanged);
      return button;
    }
  }

  private final Runnable onWorkoutChanged =
      () -> {
        String name = advancedWorkoutSpinner.getValue().toString();
        if (advancedWorkout != null) {
          Context ctx = requireActivity().getApplicationContext();
          try {
            WorkoutSerializer.writeFile(ctx, name, advancedWorkout);
          } catch (Exception ex) {
            new AlertDialog.Builder(requireContext())
                .setTitle(org.runnerup.common.R.string.Failed_to_load_workout)
                .setMessage(ex.toString())
                .setPositiveButton(
                    org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
                .show();
          }
        }
      };

  private final OnSetValueListener onSetTimeValidator =
      new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue) throws IllegalArgumentException {

          if (WorkoutBuilder.validateSeconds(newValue)) return newValue;

          throw new IllegalArgumentException("Unable to parse time value: " + newValue);
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
          return newValue;
        }
      };

  private final ValueModel.ChangeListener<TrackerState> trackerStateListener =
      new ValueModel.ChangeListener<>() {
        @Override
        public void onValueChanged(
            ValueModel<TrackerState> instance, TrackerState oldValue, TrackerState newValue) {
          onTick();
        }
      };
}
