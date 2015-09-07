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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.hr.MockHRProvider;
import org.runnerup.notification.GpsBoundState;
import org.runnerup.notification.GpsSearchingState;
import org.runnerup.notification.NotificationManagerDisplayStrategy;
import org.runnerup.notification.NotificationStateManager;
import org.runnerup.tracker.GpsInformation;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.tracker.component.TrackerWear;
import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.util.TickListener;
import org.runnerup.widget.DisabledEntriesAdapter;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.TitleSpinner.OnCloseDialogListener;
import org.runnerup.widget.TitleSpinner.OnSetValueListener;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Workout;
import org.runnerup.workout.Workout.StepListEntry;
import org.runnerup.workout.WorkoutBuilder;
import org.runnerup.workout.WorkoutSerializer;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@TargetApi(Build.VERSION_CODES.FROYO)
public class StartActivity extends Activity implements TickListener, GpsInformation {

    final static String TAB_BASIC = "basic";
    final static String TAB_INTERVAL = "interval";
    final static String TAB_ADVANCED = "advanced";
    final static String TAB_MANUAL = "manual";

    boolean skipStopGps = false;
    Tracker mTracker = null;
    org.runnerup.tracker.GpsStatus mGpsStatus = null;

    TabHost tabHost = null;
    Button startButton = null;
    TextView gpsInfoView1 = null;
    TextView gpsInfoView2 = null;
    View gpsInfoLayout = null;
    TextView hrInfo = null;

    ImageButton hrButton = null;
    TextView hrValueText = null;
    FrameLayout hrLayout = null;
    boolean batteryLevelMessageShowed = false;

    ImageButton wearButton = null;
    TextView wearValueText = null;
    FrameLayout wearLayout = null;

    TitleSpinner simpleAudioSpinner = null;
    AudioSchemeListAdapter simpleAudioListAdapter = null;
    TitleSpinner simpleTargetType = null;
    TitleSpinner simpleTargetPaceValue = null;
    TitleSpinner simpleTargetHrz = null;
    HRZonesListAdapter hrZonesAdapter = null;

    TitleSpinner intervalType = null;
    TitleSpinner intervalTime = null;
    TitleSpinner intervalDistance = null;
    TitleSpinner intervalRestType = null;
    TitleSpinner intervalRestTime = null;
    TitleSpinner intervalRestDistance = null;
    TitleSpinner intervalAudioSpinner = null;
    AudioSchemeListAdapter intervalAudioListAdapter = null;

    TitleSpinner advancedWorkoutSpinner = null;
    WorkoutListAdapter advancedWorkoutListAdapter = null;
    TitleSpinner advancedAudioSpinner = null;
    AudioSchemeListAdapter advancedAudioListAdapter = null;
    Button advancedDownloadWorkoutButton = null;
    Workout advancedWorkout = null;
    ListView advancedStepList = null;
    final WorkoutStepsAdapter advancedWorkoutStepsAdapter = new WorkoutStepsAdapter();

    boolean manualSetValue = false;
    TitleSpinner manualSport = null;
    TitleSpinner manualDate = null;
    TitleSpinner manualTime = null;
    TitleSpinner manualDistance = null;
    TitleSpinner manualDuration = null;
    TitleSpinner manualPace = null;
    EditText manualNotes = null;

    DBHelper mDBHelper = null;
    SQLiteDatabase mDB = null;

    Formatter formatter = null;
    private NotificationStateManager notificationStateManager;
    private GpsSearchingState gpsSearchingState;
    private GpsBoundState gpsBoundState;
    private boolean headsetRegistered = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDBHelper = new DBHelper(this);
        mDB = mDBHelper.getWritableDatabase();
        formatter = new Formatter(this);

        bindGpsTracker();
        mGpsStatus = new org.runnerup.tracker.GpsStatus(this);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationStateManager = new NotificationStateManager(new NotificationManagerDisplayStrategy(notificationManager));
        gpsSearchingState = new GpsSearchingState(this, this);
        gpsBoundState = new GpsBoundState(this);

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        setContentView(R.layout.start);
        startButton = (Button) findViewById(R.id.start_button);
        startButton.setOnClickListener(startButtonClick);
        gpsInfoLayout = findViewById(R.id.gpsinfo);
        gpsInfoView1 = (TextView) findViewById(R.id.gps_info1);
        gpsInfoView2 = (TextView) findViewById(R.id.gps_info2);
        hrInfo = (TextView) findViewById(R.id.hr_info);

        hrButton = (ImageButton) findViewById(R.id.hr_button);
        hrButton.setOnClickListener(hrButtonClick);
        hrValueText = (TextView) findViewById(R.id.hr_value_text);
        hrLayout = (FrameLayout) findViewById(R.id.hr_layout);

        wearButton = (ImageButton) findViewById(R.id.wear_button);
        wearValueText = (TextView) findViewById(R.id.wear_value_text);
        wearLayout = (FrameLayout) findViewById(R.id.wear_layout);

        tabHost = (TabHost) findViewById(R.id.tabhost_start);
        tabHost.setup();
        TabSpec tabSpec = tabHost.newTabSpec(TAB_BASIC);
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.Basic)));
        tabSpec.setContent(R.id.tab_basic);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(TAB_INTERVAL);
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.Interval)));
        tabSpec.setContent(R.id.tab_interval);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(TAB_ADVANCED);
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.Advanced)));
        tabSpec.setContent(R.id.tab_advanced);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(TAB_MANUAL);
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.Manual)));
        tabSpec.setContent(R.id.tab_manual);
        tabHost.addTab(tabSpec);

        tabHost.setOnTabChangedListener(onTabChangeListener);
        tabHost.getTabWidget().setBackgroundColor(Color.DKGRAY);

        simpleAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
        simpleAudioListAdapter.reload();
        simpleAudioSpinner = (TitleSpinner) findViewById(R.id.basic_audio_cue_spinner);
        simpleAudioSpinner.setAdapter(simpleAudioListAdapter);
        simpleTargetType = (TitleSpinner) findViewById(R.id.tab_basic_target_type);
        simpleTargetPaceValue = (TitleSpinner) findViewById(R.id.tab_basic_target_pace_max);
        hrZonesAdapter = new HRZonesListAdapter(this, inflater);
        simpleTargetHrz = (TitleSpinner) findViewById(R.id.tab_basic_target_hrz);
        simpleTargetHrz.setAdapter(hrZonesAdapter);
        simpleTargetType.setOnCloseDialogListener(simpleTargetTypeClick);

        intervalType = (TitleSpinner) findViewById(R.id.interval_type);
        intervalTime = (TitleSpinner) findViewById(R.id.interval_time);
        intervalTime.setOnSetValueListener(onSetTimeValidator);
        intervalDistance = (TitleSpinner) findViewById(R.id.interval_distance);
        intervalType.setOnSetValueListener(intervalTypeSetValue);

        intervalRestType = (TitleSpinner) findViewById(R.id.interval_rest_type);
        intervalRestTime = (TitleSpinner) findViewById(R.id.interval_rest_time);
        intervalRestTime.setOnSetValueListener(onSetTimeValidator);
        intervalRestDistance = (TitleSpinner) findViewById(R.id.interval_rest_distance);
        intervalRestType.setOnSetValueListener(intervalRestTypeSetValue);
        intervalAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
        intervalAudioListAdapter.reload();
        intervalAudioSpinner = (TitleSpinner) findViewById(R.id.interval_audio_cue_spinner);
        intervalAudioSpinner.setAdapter(intervalAudioListAdapter);

        advancedAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
        advancedAudioListAdapter.reload();
        advancedAudioSpinner = (TitleSpinner) findViewById(R.id.advanced_audio_cue_spinner);
        advancedAudioSpinner.setAdapter(advancedAudioListAdapter);
        advancedWorkoutSpinner = (TitleSpinner) findViewById(R.id.advanced_workout_spinner);
        advancedWorkoutListAdapter = new WorkoutListAdapter(inflater);
        advancedWorkoutListAdapter.reload();
        advancedWorkoutSpinner.setAdapter(advancedWorkoutListAdapter);
        advancedWorkoutSpinner.setOnSetValueListener(new OnSetValueListener() {
            @Override
            public String preSetValue(String newValue)
                    throws IllegalArgumentException {
                loadAdvanced(newValue);
                return newValue;
            }

            @Override
            public int preSetValue(int newValue)
                    throws IllegalArgumentException {
                loadAdvanced(null);
                return newValue;
            }
        });
        advancedStepList = (ListView) findViewById(R.id.advanced_step_list);
        advancedStepList.setDividerHeight(0);
        advancedStepList.setAdapter(advancedWorkoutStepsAdapter);
        advancedDownloadWorkoutButton = (Button) findViewById(R.id.advanced_download_button);
        advancedDownloadWorkoutButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartActivity.this, ManageWorkoutsActivity.class);
                StartActivity.this.startActivityForResult(intent, 113);
            }
        });

        manualSport = (TitleSpinner) findViewById(R.id.manual_sport);
        manualDate = (TitleSpinner) findViewById(R.id.manual_date);
        manualDate.setOnSetValueListener(onSetValueManual);
        manualTime = (TitleSpinner) findViewById(R.id.manual_time);
        manualTime.setOnSetValueListener(onSetValueManual);
        manualDistance = (TitleSpinner) findViewById(R.id.manual_distance);
        manualDistance.setOnSetValueListener(onSetManualDistance);
        manualDuration = (TitleSpinner) findViewById(R.id.manual_duration);
        manualDuration.setOnSetValueListener(onSetManualDuration);
        manualPace = (TitleSpinner) findViewById(R.id.manual_pace);
        manualPace.setVisibility(View.GONE);
        manualNotes = (EditText) findViewById(R.id.manual_notes);

        if (getParent() != null && getParent().getIntent() != null) {
            Intent i = getParent().getIntent();
            if (i.hasExtra("mode")) {
                if (i.getStringExtra("mode").equals(TAB_ADVANCED)) {
                    tabHost.setCurrentTab(2);
                    i.removeExtra("mode");
                }
            }
        }

        updateTargetView();
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

        if (tabHost.getCurrentTabTag().contentEquals(TAB_ADVANCED)) {
            loadAdvanced(null);
        }

        if (mIsBound == false || mTracker == null) {
            bindGpsTracker();
        } else {
            onGpsTrackerBound();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (getAutoStartGps()) {
            /**
             * If autoStartGps, then stop it during pause
             */
            stopGps();
        } else {
            if (mTracker != null &&
                ((mTracker.getState() == TrackerState.INITIALIZED) ||
                 (mTracker.getState() == TrackerState.INITIALIZING))) {
                Log.e(getClass().getName(), "mTracker.reset()");
                mTracker.reset();
            }
        }
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
        mTracker = null;

        mDB.close();
        mDBHelper.close();
        super.onDestroy();
    }

    private final BroadcastReceiver startEventBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mTracker == null)
                        return;

                    if (!startButton.isEnabled())
                        return;

                    if (mTracker.getState() == TrackerState.INIT /* this will start gps */||
                        mTracker.getState() == TrackerState.INITIALIZED /* ...start a workout*/ ||
                        mTracker.getState() == TrackerState.CONNECTED) {
                        startButton.performClick();
                    }
                }
            });
        }
    };

    private void registerStartEventListener() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.Intents.START_WORKOUT);
        registerReceiver(startEventBroadcastReceiver, intentFilter);

        if (StartActivityHeadsetButtonReceiver.getAllowStartStopFromHeadsetKey(this)) {
            headsetRegistered = true;
            StartActivityHeadsetButtonReceiver.registerHeadsetListener(this);
        }
    }

    private void unregisterStartEventListener() {
        try {
            unregisterReceiver(startEventBroadcastReceiver);
        } catch (Exception e) {
        }
        if (headsetRegistered) {
            headsetRegistered = false;
            StartActivityHeadsetButtonReceiver.unregisterHeadsetListener(this);
        }
    }

    void onGpsTrackerBound() {
        if (getAutoStartGps()) {
            startGps();
        } else {
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
                    assert(false);
                    return;
                case ERROR:
                    break;
            }
        }
        updateView();
    }

    boolean getAutoStartGps() {
        Context ctx = getApplicationContext();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        return pref.getBoolean(getString(R.string.pref_startgps), false);
    }

    private void startGps() {
        Log.e(getClass().getName(), "StartActivity.startGps()");
        if (mGpsStatus != null && !mGpsStatus.isLogging())
            mGpsStatus.start(this);

        if (mTracker != null) {
            mTracker.connect();
        }

        notificationStateManager.displayNotificationState(gpsSearchingState);
    }

    private void stopGps() {
        Log.e(getClass().getName(), "StartActivity.stopGps() skipStop: " + this.skipStopGps);
        if (skipStopGps == true)
            return;

        if (mGpsStatus != null)
            mGpsStatus.stop(this);

        if (mTracker != null)
            mTracker.reset();

        notificationStateManager.cancelNotification();
    }

    protected void notificationBatteryLevel(int batteryLevel) {
        if ((batteryLevel < 0) || (batteryLevel > 100)) {
            return;
        }

        final String pref_key = getString(R.string.pref_battery_level_low_notification_discard);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        int batteryLevelHighThreshold = SafeParse.parseInt(prefs.getString(getString(
                R.string.pref_battery_level_high_threshold), "75"), 75);
        if ((batteryLevel > batteryLevelHighThreshold) && (prefs.contains(pref_key))) {
            prefs.edit().remove(pref_key).commit();
            return;
        }

        int batteryLevelLowThreshold = SafeParse.parseInt(prefs.getString(getString(
                R.string.pref_battery_level_low_threshold), "15"), 15);
        if (batteryLevel > batteryLevelLowThreshold) {
            return;
        }

        if (prefs.getBoolean(pref_key, false)) {
            return;
        }

        AlertDialog.Builder prompt = new AlertDialog.Builder(this);
        final CheckBox dontShowAgain = new CheckBox(this);
        dontShowAgain.setText(getResources().getText(R.string.Do_not_show_again));
        prompt.setView(dontShowAgain);

        prompt.setCancelable(false);
        prompt.setMessage(getResources().getText(R.string.Low_HRM_battery_level)
            + "\n" + getResources().getText(R.string.Battery_level) + ": " + batteryLevel + "%");
        prompt.setTitle(getResources().getText(R.string.Warning));

        prompt.setPositiveButton(getResources().getText(R.string.OK), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (dontShowAgain.isChecked()) {
                    prefs.edit().putBoolean(pref_key, true).commit();
                }
                return;
            }
        });

        prompt.show();
    }

    final OnTabChangeListener onTabChangeListener = new OnTabChangeListener() {

        @Override
        public void onTabChanged(String tabId) {
            if (tabId.contentEquals(TAB_BASIC))
                startButton.setVisibility(View.VISIBLE);
            else if (tabId.contentEquals(TAB_INTERVAL))
                startButton.setVisibility(View.VISIBLE);
            else if (tabId.contentEquals(TAB_ADVANCED)) {
                startButton.setVisibility(View.VISIBLE);
                loadAdvanced(null);
            } else if (tabId.contentEquals(TAB_MANUAL)) {
                startButton.setText(getString(R.string.Save_activity));
            }
            updateView();
        }
    };

    Workout prepareWorkout() {
        Context ctx = getApplicationContext();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences audioPref = null;
        Workout w = null;
        if (tabHost.getCurrentTabTag().contentEquals(TAB_BASIC)) {
            audioPref = WorkoutBuilder.getAudioCuePreferences(ctx, pref,
                    getString(R.string.pref_basic_audio));
            Dimension target = Dimension.valueOf(simpleTargetType.getValueInt());
            w = WorkoutBuilder.createDefaultWorkout(getResources(), pref, target);
        } else if (tabHost.getCurrentTabTag().contentEquals(TAB_INTERVAL)) {
            audioPref = WorkoutBuilder.getAudioCuePreferences(ctx, pref,
                    getString(R.string.pref_interval_audio));
            w = WorkoutBuilder.createDefaultIntervalWorkout(getResources(), pref);
        } else if (tabHost.getCurrentTabTag().contentEquals(TAB_ADVANCED)) {
            audioPref = WorkoutBuilder.getAudioCuePreferences(ctx, pref,
                    getString(R.string.pref_advanced_audio));
            w = advancedWorkout;
        }
        WorkoutBuilder.prepareWorkout(getResources(), pref, w,
                TAB_BASIC.contentEquals(tabHost.getCurrentTabTag()));
        WorkoutBuilder.addAudioCuesToWorkout(getResources(), w, audioPref);
        return w;
    }

    final OnClickListener startButtonClick = new OnClickListener() {
        public void onClick(View v) {
            if (tabHost.getCurrentTabTag().contentEquals(TAB_MANUAL)) {
                manualSaveButtonClick.onClick(v);
                return;
            } else if (mGpsStatus.isEnabled() == false) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } else if (mTracker.getState() != TrackerState.CONNECTED) {
                startGps();
            } else if (mTracker.getState() == TrackerState.CONNECTED) {
                mGpsStatus.stop(StartActivity.this);

                /**
                 * unregister receivers
                 */
                unregisterStartEventListener();

                /**
                 * This will start the advancedWorkoutSpinner!
                 */
                mTracker.setWorkout(prepareWorkout());
                mTracker.start();

                skipStopGps = true;
                Intent intent = new Intent(StartActivity.this,
                        RunActivity.class);
                StartActivity.this.startActivityForResult(intent, 112);
                notificationStateManager.cancelNotification(); // will be added by RunActivity
                return;
            }
            updateView();
        }
    };

    final OnClickListener hrButtonClick = new OnClickListener() {
        @Override
        public void onClick(View arg0) {

        }
    };

    private void updateView() {
        {
            int cnt0 = mGpsStatus.getSatellitesFixed();
            int cnt1 = mGpsStatus.getSatellitesAvailable();
            gpsInfoView1.setText(": " + cnt0 + "/" + cnt1);
        }

        gpsInfoView2.setText(getGpsAccuracy());

        int playIcon = 0;
        if (tabHost.getCurrentTabTag().contentEquals(TAB_MANUAL)) {
            gpsInfoLayout.setVisibility(View.GONE);
            startButton.setEnabled(manualSetValue);
            startButton.setText(getString(R.string.Save_activity));
            return;
        } else if (mGpsStatus.isEnabled() == false) {
            startButton.setEnabled(true);
            startButton.setText(getString(R.string.Enable_GPS));
        } else if (mGpsStatus.isLogging() == false) {
            startButton.setEnabled(true);
            startButton.setText(getString(R.string.Start_GPS));
        } else if (mGpsStatus.isFixed() == false) {
            startButton.setEnabled(false);
            startButton.setText(getString(R.string.Waiting_for_GPS));
            notificationStateManager.displayNotificationState(gpsSearchingState);
        } else {
            playIcon = R.drawable.ic_av_play_arrow;
            startButton.setText(getString(R.string.Start_activity));
            if (!tabHost.getCurrentTabTag().contentEquals(TAB_ADVANCED) || advancedWorkout != null) {
                startButton.setEnabled(true);
            } else {
                startButton.setEnabled(false);
            }
            notificationStateManager.displayNotificationState(gpsBoundState);
        }
        startButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, playIcon, 0);
        gpsInfoLayout.setVisibility(View.VISIBLE);

        {
            Resources res = getResources();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            final String btDeviceName = prefs.getString(res.getString(R.string.pref_bt_name), null);
            if (btDeviceName != null) {
                hrInfo.setText(btDeviceName);
            } else {
                hrInfo.setText("");
                if (MockHRProvider.NAME.contentEquals(prefs.getString(
                        res.getString(R.string.pref_bt_provider), ""))) {
                    final String btAddress = "mock: "
                            + prefs.getString(res.getString(R.string.pref_bt_address), "???");
                    hrInfo.setText(btAddress);
                }
            }
        }

        boolean hideHR = true;
        boolean hideWear = true;
        if (mTracker != null) {
            if (mTracker.isComponentConfigured(TrackerHRM.NAME)) {
                hideHR = false;
                Integer hrVal = null;
                if (mTracker.isComponentConnected(TrackerHRM.NAME)) {
                    hrVal = mTracker.getCurrentHRValue();
                }
                if (hrVal != null) {
                    hrButton.setEnabled(false);
                    hrValueText.setText(Integer.toString(hrVal));

                    if (!batteryLevelMessageShowed) {
                        batteryLevelMessageShowed = true;
                        notificationBatteryLevel(mTracker.getCurrentBatteryLevel());
                    }
                } else {
                    hrButton.setEnabled(true);
                    hrValueText.setText("?");
                }
            }
            if (mTracker.isComponentConfigured(TrackerWear.NAME)) {
                hideWear = false;
                if (mTracker.isComponentConnected(TrackerWear.NAME)) {
                    wearValueText.setVisibility(View.GONE);
                } else {
                    wearValueText.setText("?");
                    wearValueText.setVisibility(View.VISIBLE);
                }
            }
        }
        if (hideHR)
            hrLayout.setVisibility(View.GONE);
        else
            hrLayout.setVisibility(View.VISIBLE);

        if (hideWear)
            wearLayout.setVisibility(View.GONE);
        else
            wearLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public String getGpsAccuracy() {
        if (mTracker != null) {
            Location l = mTracker.getLastKnownLocation();

            if (l != null && l.getAccuracy() > 0) {
                return String.format(", %s m", l.getAccuracy());
            }
        }

        return "";
    }

    private boolean mIsBound = false;
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mTracker = ((Tracker.LocalBinder) service).getService();
            // Tell the user about this for our demo.
            StartActivity.this.onGpsTrackerBound();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mTracker = null;
        }
    };

    void bindGpsTracker() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        getApplicationContext().bindService(new Intent(this, Tracker.class),
                mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void unbindGpsTracker() {
        if (mIsBound) {
            // Detach our existing connection.
            getApplicationContext().unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        registerStartEventListener();

        if (data != null) {
            if (data.getStringExtra("url") != null)
                Log.e(getClass().getName(), "data.getStringExtra(\"url\") => " + data.getStringExtra("url"));
            if (data.getStringExtra("ex") != null)
                Log.e(getClass().getName(), "data.getStringExtra(\"ex\") => " + data.getStringExtra("ex"));
            if (data.getStringExtra("obj") != null)
                Log.e(getClass().getName(), "data.getStringExtra(\"obj\") => " + data.getStringExtra("obj"));
        }
        if (requestCode == 112) {
            skipStopGps = false;
            if (mIsBound == false || mTracker == null) {
                bindGpsTracker();
            } else {
                onGpsTrackerBound();
            }
        } else {
            updateView();
            advancedWorkoutListAdapter.reload();
        }
    }

    @Override
    public void onTick() {
        updateView();
    }

    final OnCloseDialogListener simpleTargetTypeClick = new OnCloseDialogListener() {

        @Override
        public void onClose(TitleSpinner spinner, boolean ok) {
            if (ok) {
                updateTargetView();
            }
        }
    };

    void updateTargetView() {
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

    final OnSetValueListener intervalTypeSetValue = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {
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

    final OnSetValueListener intervalRestTypeSetValue = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {
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

    void loadAdvanced(String name) {
        Context ctx = getApplicationContext();
        if (name == null) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
            name = pref.getString(getResources().getString(R.string.pref_advanced_workout), "");
        }
        advancedWorkout = null;
        if ("".contentEquals(name))
            return;
        try {
            advancedWorkout = WorkoutSerializer.readFile(ctx, name);
            advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
            advancedDownloadWorkoutButton.setVisibility(View.GONE);
        } catch (Exception ex) {
            ex.printStackTrace();
            AlertDialog.Builder builder = new AlertDialog.Builder(StartActivity.this);
            builder.setTitle(getString(R.string.Failed_to_load_workout));
            builder.setMessage("" + ex.toString());
            builder.setPositiveButton(getString(R.string.OK),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            builder.show();
            return;
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

        List<StepListEntry> steps = new ArrayList<StepListEntry>();

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
            StepButton button = null;
            if (convertView != null && convertView instanceof StepButton) {
                button = (StepButton) convertView;
            } else {
                button = new StepButton(StartActivity.this, null);
            }
            button.setStep(entry.step);
            button.setPadding(entry.level * 7, 0, 0, 0);
            button.setOnChangedListener(onWorkoutChanged);
            return button;
        }
    }

    final Runnable onWorkoutChanged = new Runnable() {
        @Override
        public void run() {
            String name = advancedWorkoutSpinner.getValue().toString();
            if (advancedWorkout != null) {
                Context ctx = getApplicationContext();
                try {
                    WorkoutSerializer.writeFile(ctx, name, advancedWorkout);
                } catch (Exception ex) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(StartActivity.this);
                    builder.setTitle(getString(R.string.Failed_to_load_workout));
                    builder.setMessage("" + ex.toString());
                    builder.setPositiveButton(getString(R.string.OK),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    builder.show();
                    return;
                }
            }
        }
    };

    final OnSetValueListener onSetTimeValidator = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {

            if (WorkoutBuilder.validateSeconds(newValue))
                return newValue;

            throw new IllegalArgumentException("Unable to parse time value: " + newValue);
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
            return newValue;
        }

    };

    final OnSetValueListener onSetValueManual = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {
            manualSetValue = true;
            startButton.setEnabled(true);
            return newValue;
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
            manualSetValue = true;
            startButton.setEnabled(true);
            return newValue;
        }
    };

    void setManualPace(String distance, String duration) {
        Log.e(getClass().getName(), "distance: >" + distance + "< duration: >" + duration + "<");
        double dist = SafeParse.parseDouble(distance, 0); // convert to meters
        long seconds = SafeParse.parseSeconds(duration, 0);
        if (dist == 0 || seconds == 0) {
            manualPace.setVisibility(View.GONE);
            return;
        }
        double pace = seconds / dist;
        manualPace.setValue(formatter.formatPace(Formatter.TXT_SHORT, pace));
        manualPace.setVisibility(View.VISIBLE);
        return;
    }

    final OnSetValueListener onSetManualDistance = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {
            setManualPace(newValue, manualDuration.getValue().toString());
            startButton.setEnabled(true);
            return newValue;
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
            startButton.setEnabled(true);
            return newValue;
        }

    };

    final OnSetValueListener onSetManualDuration = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {
            setManualPace(manualDistance.getValue().toString(), newValue);
            startButton.setEnabled(true);
            return newValue;
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
            startButton.setEnabled(true);
            return newValue;
        }
    };

    final OnClickListener manualSaveButtonClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            ContentValues save = new ContentValues();
            int sport = manualSport.getValueInt();
            CharSequence date = manualDate.getValue();
            CharSequence time = manualTime.getValue();
            CharSequence distance = manualDistance.getValue();
            CharSequence duration = manualDuration.getValue();
            String notes = manualNotes.getText().toString().trim();
            long start_time = 0;

            if (notes.length() > 0) {
                save.put(DB.ACTIVITY.COMMENT, notes);
            }
            double dist = 0;
            if (distance.length() > 0) {
                dist = Double.parseDouble(distance.toString()); // convert to
                                                                // meters
                save.put(DB.ACTIVITY.DISTANCE, dist);
            }
            long secs = 0;
            if (duration.length() > 0) {
                secs = SafeParse.parseSeconds(duration.toString(), 0);
                save.put(DB.ACTIVITY.TIME, secs);
            }
            if (date.length() > 0) {
                DateFormat df = android.text.format.DateFormat.getDateFormat(StartActivity.this);
                try {
                    Date d = df.parse(date.toString());
                    start_time += d.getTime() / 1000;
                } catch (ParseException e) {
                }
            }
            if (time.length() > 0) {
                DateFormat df = android.text.format.DateFormat.getTimeFormat(StartActivity.this);
                try {
                    Date d = df.parse(time.toString());
                    start_time += d.getTime() / 1000;
                } catch (ParseException e) {
                }
            }
            save.put(DB.ACTIVITY.START_TIME, start_time);

            save.put(DB.ACTIVITY.SPORT, sport);
            long id = mDB.insert(DB.ACTIVITY.TABLE, null, save);

            ContentValues lap = new ContentValues();
            lap.put(DB.LAP.ACTIVITY, id);
            lap.put(DB.LAP.LAP, 0);
            lap.put(DB.LAP.INTENSITY, DB.INTENSITY.ACTIVE);
            lap.put(DB.LAP.TIME, secs);
            lap.put(DB.LAP.DISTANCE, dist);
            mDB.insert(DB.LAP.TABLE, null, lap);

            Intent intent = new Intent(StartActivity.this, DetailActivity.class);
            intent.putExtra("mode", "save");
            intent.putExtra("ID", id);
            StartActivity.this.startActivityForResult(intent, 0);
        }
    };
}
