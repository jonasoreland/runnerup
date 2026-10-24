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
import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.ValueModel;
import org.runnerup.db.DBHelper;
import org.runnerup.hr.HRProvider;
import org.runnerup.hr.MockHRProvider;
import org.runnerup.hr.WearHRConnectionState;
import org.runnerup.hr.WearHRProvider;
import org.runnerup.notification.GpsBoundState;
import org.runnerup.notification.GpsSearchingState;
import org.runnerup.notification.NotificationManagerDisplayStrategy;
import org.runnerup.notification.NotificationStateManager;
import org.runnerup.relven.exercise.ExerciseCatalog;
import org.runnerup.relven.exercise.ExerciseCatalogRepository;
import org.runnerup.relven.exercise.ExerciseEntry;
import org.runnerup.relven.ui.GymExerciseDraft;
import org.runnerup.relven.ui.GymExerciseOption;
import org.runnerup.relven.ui.GymWorkoutDraftBuilder;
import org.runnerup.relven.ui.GymWorkoutStarter;
import org.runnerup.relven.ui.StartReadinessItem;
import org.runnerup.relven.ui.StartReadinessPresentation;
import org.runnerup.relven.ui.StartReadinessPresentationFactory;
import org.runnerup.relven.ui.StartScreenChromeCompose;
import org.runnerup.relven.ui.StartWorkoutAvailability;
import org.runnerup.relven.ui.StartWorkoutTabsCompose;
import org.runnerup.relven.ui.StartWorkoutTabsStateHolder;
import org.runnerup.relven.ui.WorkoutSetupChoice;
import org.runnerup.relven.ui.WorkoutSetupCompose;
import org.runnerup.relven.ui.WorkoutSetupListener;
import org.runnerup.relven.ui.WorkoutSetupPresentation;
import org.runnerup.relven.ui.WorkoutSetupState;
import org.runnerup.relven.ui.WorkoutSetupStateHolder;
import org.runnerup.relven.ui.WorkoutSetupStep;
import org.runnerup.tracker.GpsInformation;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.tracker.component.TrackerWear;
import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.util.TickListener;
import org.runnerup.widget.ClassicSpinner;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.RelvenExerciseReference;
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
  private static final String STATE_SELECTED_TAB_POSITION = "selectedTab";

  private boolean statusDetailsShown = false;

  // StartFragment normally stop GPS in onDestroy (or onStop)
  // but if the fragment stop as it has started a RunActivity
  // it should not!
  // TODO Figure out a way to do this prettier
  private boolean runActivityPending = false;
  private Tracker mTracker = null;
  private org.runnerup.tracker.GpsStatus mGpsStatus = null;

  private ComposeView workoutTabsView = null;
  private StartWorkoutTabsStateHolder workoutTabsState = null;
  private final ActivityResultLauncher<Intent> runActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> handleActivityResult(START_ACTIVITY, result.getResultCode(), result.getData()));
  private ComposeView workoutSetupView;
  private WorkoutSetupStateHolder workoutSetupState;
  private boolean startWorkoutReady;
  private final ExecutorService gymCatalogExecutor = Executors.newSingleThreadExecutor();
  private final ArrayList<GymExerciseOption> gymExercises = new ArrayList<>();
  private final ArrayList<GymExerciseDraft> gymDraft = new ArrayList<>();
  private Map<String, RelvenExerciseReference> gymExerciseReferences = Map.of();
  private String gymName = "";
  private String gymQuery = "";
  private boolean gymCatalogLoading;
  private boolean gymCatalogError;
  private ClassicSpinner sportSpinner;
  private Workout plannedWorkout;

  private ComposeView readinessComposeView = null;
  private boolean gpsActionVisible;
  private String gpsActionLabel = "";
  private StartReadinessItem gpsSummaryItem;
  private StartReadinessItem gpsDetailsItem;
  private StartReadinessItem heartRateItem;
  private StartReadinessItem wearOsItem;
  private TrackerWear.WearNotifier mWearNotifier = null;

  boolean sportWithoutGps = false;
  boolean batteryLevelMessageShown = false;

  AudioSchemeListAdapter simpleAudioListAdapter = null;
  HRZonesListAdapter hrZonesAdapter = null;
  AudioSchemeListAdapter intervalAudioListAdapter = null;

  WorkoutListAdapter advancedWorkoutListAdapter = null;
  Workout advancedWorkout = null;
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

  private final SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener =
      (sharedPrefs, key) -> {
        assert key != null;
        if (key.equals(getString(R.string.pref_advanced_workout))) {
          String newName = sharedPrefs.getString(key, "");
          if (!newName.isEmpty()) {
            loadAdvanced(newName);
            if (advancedWorkoutListAdapter != null) {
              advancedWorkoutListAdapter.reload();
            }
            refreshWorkoutSetup();
          }
        }
      };

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

    ComposeView sportToolbar = view.findViewById(R.id.start_toolbar);
    sportSpinner =
        (ClassicSpinner)
            LayoutInflater.from(context)
                .inflate(R.layout.start_sport_spinner_component, sportToolbar, false);
    ArrayAdapter<CharSequence> adapter =
        new ArrayAdapter<>(
            context, R.layout.start_sport_spinner, Sport.getStringArray(getResources()));
    adapter.setDropDownViewResource(R.layout.start_sport_dropdown_spinner);
    sportSpinner.setAdapter(adapter);
    sportSpinner.setPopupBackgroundResource(R.color.relven_surface);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    sportSpinner.setViewSelection(
        prefs.getInt(getResources().getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING));

    StartScreenChromeCompose.installHeader(sportToolbar, sportSpinner);
    readinessComposeView = view.findViewById(R.id.status_layout);
    StartScreenChromeCompose.installReadiness(readinessComposeView);

    workoutTabsView = view.findViewById(R.id.start_tabs);
    int selectedTabPosition =
        savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_SELECTED_TAB_POSITION, 0);
    workoutTabsState =
        new StartWorkoutTabsStateHolder(selectedTabPosition, this::onWorkoutTabSelected);
    StartWorkoutTabsCompose.install(workoutTabsView, workoutTabsState);
    LayoutInflater inflater = getLayoutInflater();
    simpleAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
    simpleAudioListAdapter.reload();
    hrZonesAdapter = new HRZonesListAdapter(context, inflater);
    intervalAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
    intervalAudioListAdapter.reload();

    advancedAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
    advancedAudioListAdapter.reload();
    advancedWorkoutListAdapter = new WorkoutListAdapter(inflater);
    advancedWorkoutListAdapter.reload();
    workoutSetupView = view.findViewById(R.id.start_setup);
    workoutSetupState =
        new WorkoutSetupStateHolder(
            setupState(),
            new WorkoutSetupListener() {
              @Override
              public void onPreferenceChanged(String key, String value) {
                SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(requireContext());
                SharedPreferences.Editor editor = prefs.edit();
                if (key.equals(getString(R.string.pref_basic_goal_type))) {
                  editor.putString(key, value);
                  editor.putInt(
                      getString(R.string.pref_basic_target_type),
                      WorkoutSetupPresentation.targetDimensionForGoal(value));
                } else if (key.equals(getString(R.string.pref_basic_target_type))
                    || key.equals(getString(R.string.pref_basic_target_hrz))
                    || key.equals(getString(R.string.pref_interval_type))
                    || key.equals(getString(R.string.pref_interval_rest_type))) {
                  editor.putInt(key, Integer.parseInt(value));
                } else {
                  editor.putString(key, value);
                }
                editor.apply();
                refreshWorkoutSetup();
              }

              @Override
              public void onSelectWorkout(String name) {
                if (name.isEmpty()) return;
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putString(getString(R.string.pref_advanced_workout), name)
                    .apply();
                loadAdvanced(name);
                refreshWorkoutSetup();
                updateView();
              }

              @Override
              public void onEditStep(int index) {
                if (advancedWorkout == null) return;
                List<StepListEntry> steps = advancedWorkout.getStepList();
                if (index < 0 || index >= steps.size()) return;
                StepButton editor = new StepButton(requireContext(), null);
                editor.setStep(steps.get(index).step());
                editor.setOnChangedListener(StartFragment.this::saveAdvancedWorkout);
                editor.editStep();
              }

              @Override
              public void onManageWorkouts() {
                startActivity(new Intent(requireContext(), ManageWorkoutsActivity.class));
              }

              @Override
              public void onManageAudio(int tab) {
                startActivity(new Intent(requireContext(), AudioCueSettingsActivity.class));
              }

              @Override
              public void onGymNameChanged(String name) {
                gymName = name;
                persistGymDraft();
                refreshWorkoutSetup();
                updateView();
              }

              @Override
              public void onGymQueryChanged(String query) {
                gymQuery = query;
                persistGymDraft();
                refreshWorkoutSetup();
              }

              @Override
              public void onAddGymExercise(String exerciseId) {
                if (gymDraft.stream().anyMatch(item -> item.getExerciseId().equals(exerciseId)))
                  return;
                GymExerciseOption option =
                    gymExercises.stream()
                        .filter(item -> item.getExerciseId().equals(exerciseId))
                        .findFirst()
                        .orElse(null);
                if (option == null) return;
                gymDraft.add(new GymExerciseDraft(exerciseId, option.getName(), "3", "8", "60"));
                persistGymDraft();
                refreshWorkoutSetup();
                updateView();
              }

              @Override
              public void onRemoveGymExercise(String exerciseId) {
                gymDraft.removeIf(item -> item.getExerciseId().equals(exerciseId));
                persistGymDraft();
                refreshWorkoutSetup();
                updateView();
              }

              @Override
              public void onUpdateGymExercise(
                  String exerciseId, String sets, String repetitions, String restSeconds) {
                for (int index = 0; index < gymDraft.size(); index++) {
                  GymExerciseDraft item = gymDraft.get(index);
                  if (item.getExerciseId().equals(exerciseId)) {
                    gymDraft.set(
                        index,
                        new GymExerciseDraft(
                            item.getExerciseId(), item.getName(), sets, repetitions, restSeconds));
                    break;
                  }
                }
                persistGymDraft();
                refreshWorkoutSetup();
                updateView();
              }

              @Override
              public void onRetryGymCatalog() {
                loadGymCatalog();
              }

              @Override
              public void onStartGymWorkout() {
                onStartWorkoutAction();
              }
            });
    WorkoutSetupCompose.install(workoutSetupView, workoutSetupState);
    restoreGymDraft();
    loadGymCatalog();
    applySportMode();

    if (selectedTabPosition == 2) {
      loadAdvanced(null);
    }

    Intent i = requireActivity().getIntent();
    if (i != null) {
      if (i.hasExtra("mode")) {
        if (Objects.equals(i.getStringExtra("mode"), TAB_ADVANCED)) {
          workoutTabsState.select(2);
          i.removeExtra("mode");
        }
      }
    }

    refreshWorkoutSetup();

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
            applySportMode();
            refreshWorkoutSetup();
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
        if (getAutoStartGps()) {
          if (!checkPermissions(false)) {
            startGps(false);
          }
        } else if (mGpsStatus.isStarted()) {
          mTracker.setup();
          startGps(true);
        }
      }
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

    if (getSelectedTabTag().contentEquals(TAB_ADVANCED)) {
      loadAdvanced(null);
    }
    refreshWorkoutSetup();
    applySportMode();

    if (!mIsBound || mTracker == null) {
      bindGpsTracker();
    } else {
      onGpsTrackerBound();
    }
    this.updateView();
    PreferenceManager.getDefaultSharedPreferences(requireContext())
        .registerOnSharedPreferenceChangeListener(prefChangeListener);
    mWearNotifier.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    PreferenceManager.getDefaultSharedPreferences(requireContext())
        .unregisterOnSharedPreferenceChangeListener(prefChangeListener);

    if (getAutoStartGps()) {
      if (shouldStopGpsForLifecycle()) {
        stopGps();
      }
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
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (workoutTabsState != null) {
      outState.putInt(STATE_SELECTED_TAB_POSITION, workoutTabsState.getSelectedIndex());
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    unregisterStartEventListener();
  }

  @Override
  public void onDestroy() {
    gymCatalogExecutor.shutdownNow();
    if (shouldStopGpsForLifecycle()) {
      stopGps();
    } else if (mGpsStatus != null) {
      // The home activity owns the shared Tracker warm-up across in-app navigation.
      mGpsStatus.stop(this);
    }
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
                    if (mTracker == null || !startWorkoutReady) {
                      return;
                    }

                    if (mTracker.getState() == TrackerState.INIT /* this will start gps */
                        || mTracker.getState() == TrackerState.INITIALIZED /* ...start a workout*/
                        || mTracker.getState() == TrackerState.CONNECTED) {
                      onStartWorkoutAction();
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
    sportWithoutGps =
        Sport.isWithoutGps(
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getInt(getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING));
    // check and request permissions at startup
    boolean missingEssentialPermission = sportWithoutGps ? false : checkPermissions(false);
    mTracker.setWithoutGps(sportWithoutGps);
    if (!missingEssentialPermission && getAutoStartGps()) {
      startGps(false);
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
    return pref.getBoolean(getString(R.string.pref_startgps), true);
  }

  private boolean shouldStopGpsForLifecycle() {
    if (!getAutoStartGps()) {
      return true;
    }
    Activity activity = getActivity();
    return !(activity instanceof FragmentActivity)
        || !((FragmentActivity) activity)
            .getLifecycle()
            .getCurrentState()
            .isAtLeast(Lifecycle.State.RESUMED);
  }

  private void startGps(boolean openLocationSettingsIfDisabled) {
    Log.d(getClass().getName(), "StartFragment.startGps()");
    if (!sportWithoutGps) {
      if (!mGpsStatus.isEnabled()) {
        if (openLocationSettingsIfDisabled) {
          startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
        return;
      }
      notificationStateManager.displayNotificationState(gpsSearchingState);
    }

    if (!sportWithoutGps && mGpsStatus != null && !mGpsStatus.isStarted()) {
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

    final CheckBox dontShowAgain =
        new CheckBox(new ContextThemeWrapper(context, R.style.RelvenStartDialogTheme));
    dontShowAgain.setTextColor(ContextCompat.getColor(context, android.R.color.white));
    dontShowAgain.setButtonTintList(
        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.relven_orange)));
    dontShowAgain.setText(getResources().getText(org.runnerup.common.R.string.Do_not_show_again));

    new AlertDialog.Builder(context, R.style.RelvenStartDialogTheme)
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

  private String getSelectedTabTag() {
    return switch (workoutTabsState.getSelectedIndex()) {
      case 1 -> TAB_INTERVAL;
      case 2 -> TAB_ADVANCED;
      default -> TAB_BASIC;
    };
  }

  private void onWorkoutTabSelected(int selectedPosition) {
    if (selectedPosition == 2 && plannedWorkout == null) {
      loadAdvanced(null);
    } else if (plannedWorkout != null) {
      advancedWorkout = plannedWorkout;
    }
    refreshWorkoutSetup();
    updateView();
  }

  private Workout prepareWorkout() {
    Context ctx = requireActivity().getApplicationContext();
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
    SharedPreferences audioPref;
    Workout w;

    if (getSelectedTabTag().contentEquals(TAB_BASIC)) {
      audioPref =
          WorkoutBuilder.getAudioCuePreferences(ctx, pref, getString(R.string.pref_basic_audio));
      int targetType = pref.getInt(getString(R.string.pref_basic_target_type), -1);
      Dimension target = Dimension.valueOf(targetType);
      w = WorkoutBuilder.createDefaultWorkout(getResources(), pref, target);
    } else if (getSelectedTabTag().contentEquals(TAB_INTERVAL)) {
      audioPref =
          WorkoutBuilder.getAudioCuePreferences(ctx, pref, getString(R.string.pref_interval_audio));
      w = WorkoutBuilder.createDefaultIntervalWorkout(getResources(), pref);
    } else if (getSelectedTabTag().contentEquals(TAB_ADVANCED)) {
      audioPref =
          WorkoutBuilder.getAudioCuePreferences(ctx, pref, getString(R.string.pref_advanced_audio));
      w = plannedWorkout != null ? plannedWorkout : advancedWorkout;
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

  /** Selects a validated plan workout while keeping RunnerUp's tracker as the execution engine. */
  public void usePlannedWorkout(Workout workout) {
    if (workout == null) {
      if (plannedWorkout == null) return;
      plannedWorkout = null;
      advancedWorkout = null;
      if (!isAdded()) return;
      loadAdvanced(null);
      refreshWorkoutSetup();
      updateView();
      return;
    }
    if (plannedWorkout == workout) return;
    plannedWorkout = workout;
    advancedWorkout = workout;
    if (!isAdded()) return;
    PreferenceManager.getDefaultSharedPreferences(requireContext())
        .edit()
        .putInt(getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING)
        .apply();
    if (sportSpinner != null) sportSpinner.setViewSelection(DB.ACTIVITY.SPORT_RUNNING);
    if (workoutTabsState != null) workoutTabsState.select(2);
    refreshWorkoutSetup();
    updateView();
  }

  private void refreshWorkoutSetup() {
    if (workoutSetupState != null) workoutSetupState.update(setupState());
  }

  private boolean isGymSelected() {
    return PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getInt(getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING)
        == DB.ACTIVITY.SPORT_GYM;
  }

  private boolean isInRange(String value, int minimum, int maximum) {
    if (value == null) return false;
    try {
      int number = Integer.parseInt(value.trim());
      return number >= minimum && number <= maximum;
    } catch (NumberFormatException invalid) {
      return false;
    }
  }

  private void applySportMode() {
    if (workoutTabsView != null) {
      workoutTabsView.setVisibility(isGymSelected() ? View.GONE : View.VISIBLE);
    }
  }

  private void loadGymCatalog() {
    gymCatalogLoading = true;
    gymCatalogError = false;
    refreshWorkoutSetup();
    Context appContext = requireContext().getApplicationContext();
    gymCatalogExecutor.execute(
        () -> {
          try {
            ExerciseCatalog catalog =
                new ExerciseCatalogRepository(appContext).load(Locale.getDefault().toLanguageTag());
            ArrayList<GymExerciseOption> options = new ArrayList<>();
            for (ExerciseEntry entry : catalog.getExercises()) {
              if (!entry.isArchived() && entry.getEquipment().isEmpty()) {
                options.add(
                    new GymExerciseOption(
                        entry.getExerciseId(),
                        entry.getName(),
                        entry.getCategory().getKey(),
                        String.join(
                            " ",
                            entry.getMuscleGroups().toString(),
                            entry.getMovementPattern().replace('_', ' '))));
              }
            }
            Map<String, RelvenExerciseReference> references =
                new ExerciseCatalogRepository(appContext).exerciseReferences();
            Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(
                () -> {
                  if (!isAdded() || getView() == null) return;
                  gymExercises.clear();
                  gymExercises.addAll(options);
                  gymExerciseReferences = references;
                  for (int index = 0; index < gymDraft.size(); index++) {
                    GymExerciseDraft item = gymDraft.get(index);
                    GymExerciseOption option =
                        gymExercises.stream()
                            .filter(value -> value.getExerciseId().equals(item.getExerciseId()))
                            .findFirst()
                            .orElse(null);
                    if (option != null) {
                      gymDraft.set(
                          index,
                          new GymExerciseDraft(
                              item.getExerciseId(),
                              option.getName(),
                              item.getSets(),
                              item.getRepetitions(),
                              item.getRestSeconds()));
                    }
                  }
                  gymCatalogLoading = false;
                  gymCatalogError = false;
                  refreshWorkoutSetup();
                  updateView();
                });
          } catch (Exception error) {
            Log.w(getClass().getName(), "Could not load offline gym exercise catalog", error);
            Activity activity = getActivity();
            if (activity != null) {
              activity.runOnUiThread(
                  () -> {
                    if (!isAdded() || getView() == null) return;
                    gymCatalogLoading = false;
                    gymCatalogError = true;
                    refreshWorkoutSetup();
                    updateView();
                  });
            }
          }
        });
  }

  private void restoreGymDraft() {
    gymName = getString(R.string.gym_workout_default_name);
    String stored =
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString("relven.gym_workout_draft", null);
    if (stored == null) return;
    try {
      JSONObject root = new JSONObject(stored);
      gymName = root.optString("name", gymName);
      gymQuery = root.optString("query", "");
      JSONArray entries = root.optJSONArray("exercises");
      if (entries == null || entries.length() > 100) return;
      for (int index = 0; index < entries.length(); index++) {
        JSONObject item = entries.getJSONObject(index);
        String id = item.getString("id");
        if (gymDraft.stream().anyMatch(value -> value.getExerciseId().equals(id))) continue;
        gymDraft.add(
            new GymExerciseDraft(
                id,
                id,
                item.optString("sets", "3"),
                item.optString("repetitions", "8"),
                item.optString("restSeconds", "60")));
      }
    } catch (Exception error) {
      Log.w(getClass().getName(), "Ignoring invalid saved gym workout draft", error);
      gymDraft.clear();
    }
  }

  private void persistGymDraft() {
    if (getContext() == null) return;
    try {
      JSONArray entries = new JSONArray();
      for (GymExerciseDraft item : gymDraft) {
        entries.put(
            new JSONObject()
                .put("id", item.getExerciseId())
                .put("sets", item.getSets())
                .put("repetitions", item.getRepetitions())
                .put("restSeconds", item.getRestSeconds()));
      }
      JSONObject root = new JSONObject();
      root.put("name", gymName);
      root.put("query", gymQuery);
      root.put("exercises", entries);
      PreferenceManager.getDefaultSharedPreferences(requireContext())
          .edit()
          .putString("relven.gym_workout_draft", root.toString())
          .apply();
    } catch (org.json.JSONException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private WorkoutSetupState setupState() {
    Context context = requireContext();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    ArrayList<WorkoutSetupChoice> basicAudio = audioChoices(simpleAudioListAdapter);
    ArrayList<WorkoutSetupChoice> intervalAudio = audioChoices(intervalAudioListAdapter);
    ArrayList<WorkoutSetupChoice> advancedAudio = audioChoices(advancedAudioListAdapter);
    ArrayList<WorkoutSetupChoice> workouts = new ArrayList<>();
    if (advancedWorkoutListAdapter != null) {
      for (int i = 0; i < advancedWorkoutListAdapter.getCount() - 1; i++) {
        String name = String.valueOf(advancedWorkoutListAdapter.getItem(i));
        workouts.add(new WorkoutSetupChoice(name, name));
      }
    }
    CharSequence[] targetLabels =
        getResources().getTextArray(org.runnerup.common.R.array.target_types);
    ArrayList<WorkoutSetupChoice> goals = new ArrayList<>();
    goals.add(new WorkoutSetupChoice("none", targetLabels[0].toString()));
    goals.add(new WorkoutSetupChoice("pace", targetLabels[1].toString()));
    goals.add(new WorkoutSetupChoice("time", getString(org.runnerup.common.R.string.Time)));
    goals.add(new WorkoutSetupChoice("distance", getString(org.runnerup.common.R.string.Distance)));
    if (hrZonesAdapter.hrZones.isConfigured()) {
      goals.add(
          new WorkoutSetupChoice("heart_rate", targetLabels[targetLabels.length - 1].toString()));
    }
    ArrayList<WorkoutSetupChoice> zones = new ArrayList<>();
    for (int i = 0; i < hrZonesAdapter.getCount(); i++) {
      zones.add(
          new WorkoutSetupChoice(String.valueOf(i), String.valueOf(hrZonesAdapter.getItem(i))));
    }
    ArrayList<WorkoutSetupStep> steps = new ArrayList<>();
    if (advancedWorkout != null) {
      for (StepListEntry entry : advancedWorkout.getStepList()) {
        var step = entry.step();
        String title = step.getName();
        if (title == null || title.isBlank()) {
          title = getString(step.getIntensity().getTextId());
        }
        String detail = "";
        if (step.getDurationType() != null) {
          detail =
              formatter.format(
                  Formatter.Format.TXT_LONG, step.getDurationType(), step.getDurationValue());
        }
        if (step.getTargetType() != null && step.getTargetValue() != null) {
          String minimum =
              formatter.format(
                  Formatter.Format.TXT_SHORT, step.getTargetType(), step.getTargetValue().minValue);
          String maximum =
              formatter.format(
                  Formatter.Format.TXT_LONG, step.getTargetType(), step.getTargetValue().maxValue);
          String target =
              org.runnerup.relven.ui.WorkoutSetupPresentation.formatRange(minimum, maximum);
          detail = detail.isEmpty() ? target : detail + " · " + target;
        }
        steps.add(new WorkoutSetupStep(title, detail, entry.level()));
      }
    }
    int targetType = prefs.getInt(getString(R.string.pref_basic_target_type), -1);
    return new WorkoutSetupState(
        workoutTabsState == null ? 0 : workoutTabsState.getSelectedIndex(),
        WorkoutSetupPresentation.goalTypeFromPreferences(
            prefs.getString(getString(R.string.pref_basic_goal_type), null), targetType),
        goals,
        prefs.getString(getString(R.string.pref_basic_target_pace_max), "00:05:00"),
        prefs.getString(getString(R.string.pref_basic_goal_time), "00:30:00"),
        prefs.getString(getString(R.string.pref_basic_goal_distance), "5"),
        formatter.getUnitString(),
        String.valueOf(prefs.getInt(getString(R.string.pref_basic_target_hrz), -1)),
        zones,
        prefs.getString(
            getString(R.string.pref_basic_audio), getString(org.runnerup.common.R.string.Default)),
        basicAudio,
        prefs.getString(getString(R.string.pref_interval_repetitions), "8"),
        prefs.getInt(getString(R.string.pref_interval_type), 1),
        prefs.getString(getString(R.string.pref_interval_time), "00:04:00"),
        prefs.getString(getString(R.string.pref_interval_distance), "1000"),
        prefs.getInt(getString(R.string.pref_interval_rest_type), 0),
        prefs.getString(getString(R.string.pref_interval_rest_time), "00:01:00"),
        prefs.getString(getString(R.string.pref_interval_rest_distance), "200"),
        prefs.getString(
            getString(R.string.pref_interval_audio),
            getString(org.runnerup.common.R.string.Default)),
        intervalAudio,
        prefs.getString(getString(R.string.pref_advanced_workout), ""),
        workouts,
        steps,
        prefs.getString(
            getString(R.string.pref_advanced_audio),
            getString(org.runnerup.common.R.string.Default)),
        advancedAudio,
        isGymSelected(),
        gymName,
        gymQuery,
        gymCatalogLoading,
        gymCatalogError,
        new ArrayList<>(gymExercises),
        new ArrayList<>(gymDraft));
  }

  private ArrayList<WorkoutSetupChoice> audioChoices(AudioSchemeListAdapter adapter) {
    ArrayList<WorkoutSetupChoice> choices = new ArrayList<>();
    if (adapter == null) return choices;
    for (int i = 0; i < adapter.getCount() - 1; i++) {
      String value = String.valueOf(adapter.getItem(i));
      choices.add(new WorkoutSetupChoice(value, value));
    }
    return choices;
  }

  private void saveAdvancedWorkout() {
    if (advancedWorkout == null) return;
    String name =
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(getString(R.string.pref_advanced_workout), "");
    if (name.isEmpty()) return;
    try {
      WorkoutSerializer.writeFile(requireContext().getApplicationContext(), name, advancedWorkout);
      refreshWorkoutSetup();
    } catch (Exception ex) {
      new AlertDialog.Builder(requireContext(), R.style.RelvenStartDialogTheme)
          .setTitle(org.runnerup.common.R.string.Failed_to_load_workout)
          .setMessage(ex.toString())
          .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
          .show();
    }
  }

  private void startWorkout() {
    // unregister receivers
    unregisterStartEventListener();

    // RunnerUp's Tracker remains the execution engine, but selection only prepares the session.
    Workout workout = prepareWorkout();
    if (workout == null || mTracker == null) {
      registerStartEventListener();
      updateView();
      return;
    }
    try {
      StringWriter serialized = new StringWriter();
      WorkoutSerializer.writeJSON(serialized, workout);
      String workoutName = Sport.textOf(getResources(), workout.getSport());
      String upcomingStep = "Första steget";
      if (!workout.getStepList().isEmpty()) {
        String stepName = workout.getStepList().get(0).step().getName();
        if (stepName != null && !stepName.isBlank()) upcomingStep = stepName;
      }
      mTracker.setWorkout(workout);
      runActivityPending = true;
      runActivityLauncher.launch(
          org.runnerup.relven.ui.WorkoutPreparationActivity.intent(
              requireContext(),
              UUID.randomUUID().toString(),
              workoutName,
              upcomingStep,
              serialized.toString()));
    } catch (Exception error) {
      runActivityPending = false;
      Log.w(getClass().getName(), "Could not prepare workout", error);
      registerStartEventListener();
      updateView();
      new AlertDialog.Builder(requireContext(), R.style.RelvenStartDialogTheme)
          .setTitle(org.runnerup.common.R.string.Failed_to_load_workout)
          .setMessage(
              error.getLocalizedMessage() == null ? error.toString() : error.getLocalizedMessage())
          .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
          .show();
    }
  }

  private void onStartWorkoutAction() {
    updateStartButtonView();
    if (isGymSelected()) {
      if (!startWorkoutReady || !(requireActivity() instanceof GymWorkoutStarter starter)) {
        updateView();
        return;
      }
      try {
        String workoutJson =
            GymWorkoutDraftBuilder.INSTANCE.build(gymName, gymDraft, gymExerciseReferences);
        starter.startQuickStrengthWorkout(gymName.trim(), workoutJson);
      } catch (Exception error) {
        Log.w(getClass().getName(), "Could not create gym workout", error);
        new AlertDialog.Builder(requireContext(), R.style.RelvenStartDialogTheme)
            .setTitle(R.string.gym_workout_error_title)
            .setMessage(
                error.getLocalizedMessage() == null
                    ? getString(R.string.gym_exercises_error)
                    : error.getLocalizedMessage())
            .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
            .show();
      }
      return;
    }
    if (startWorkoutReady && mTracker != null && mTracker.getState() == TrackerState.CONNECTED) {
      startWorkout();
    } else {
      updateView();
    }
  }

  private final OnClickListener gpsEnableClick =
      v -> {
        if (checkPermissions(true)) {
          // Handle view update etc in permission callback
          return;
        }

        if (mTracker.getState() != TrackerState.CONNECTED) {
          startGps(true);
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
            new AlertDialog.Builder(ctx, R.style.RelvenStartDialogTheme)
                .setTitle(org.runnerup.common.R.string.GPS_permission_required)
                .setNegativeButton(
                    org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss());
        if (!requestPerms.isEmpty()) {
          // Let Android request the permissions
          builder
              .setPositiveButton(
                  org.runnerup.common.R.string.OK,
                  (dialog, id) -> {
                    Activity activity = getActivity();
                    if (activity != null) {
                      ActivityCompat.requestPermissions(activity, permissions, REQUEST_LOCATION);
                    }
                  })
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
    PowerManager pm = ContextCompat.getSystemService(ctx, PowerManager.class);
    if (popup
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

      new AlertDialog.Builder(ctx, R.style.RelvenStartDialogTheme)
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
    updateHRView();
    updateWearOSView();
    renderReadinessStatus();
  }

  private void renderReadinessStatus() {
    String noDevicesMessage = getString(org.runnerup.common.R.string.No_devices_connected);
    StartReadinessPresentation presentation =
        isGymSelected()
            ? new StartReadinessPresentation(List.of(), null)
            : StartReadinessPresentationFactory.INSTANCE.create(
                gpsSummaryItem,
                gpsDetailsItem,
                heartRateItem,
                wearOsItem,
                statusDetailsShown,
                noDevicesMessage);
    StartScreenChromeCompose.setReadiness(
        readinessComposeView,
        presentation,
        statusDetailsShown,
        gpsActionVisible,
        gpsActionLabel,
        startWorkoutReady,
        getString(
            isGymSelected()
                ? R.string.gym_start_workout
                : org.runnerup.common.R.string.Start_workout_action),
        () -> toggleStatusDetails(),
        () -> gpsEnableClick.onClick(null),
        () -> onStartWorkoutAction());
  }

  private void updateStartButtonView() {
    if (isGymSelected()) {
      boolean validDraft =
          !gymName.isBlank()
              && gymName.trim().length() <= 80
              && gymDraft.size() > 0
              && !gymCatalogLoading
              && !gymCatalogError
              && gymDraft.stream()
                  .allMatch(
                      item ->
                          gymExerciseReferences.containsKey(item.getExerciseId())
                              && isInRange(item.getSets(), 1, 100)
                              && isInRange(item.getRepetitions(), 1, 500)
                              && isInRange(item.getRestSeconds(), 0, 3600));
      startWorkoutReady = validDraft && gymExerciseReferences.size() > 0;
      return;
    }
    boolean gpsStarted = mGpsStatus != null && mGpsStatus.isStarted();
    boolean gpsLogging = mGpsStatus != null && mGpsStatus.isLogging();
    boolean gpsFixed = mGpsStatus != null && mGpsStatus.isFixed();
    startWorkoutReady =
        StartWorkoutAvailability.isAvailable(
            gpsStarted,
            sportWithoutGps,
            gpsLogging,
            gpsFixed,
            mTracker != null && mIsBound && mTracker.getState() == TrackerState.CONNECTED,
            getSelectedTabTag().contentEquals(TAB_ADVANCED),
            plannedWorkout != null || advancedWorkout != null);
  }

  private void updateStartGpsButtonView() {
    if (isGymSelected()) {
      gpsActionVisible = false;
      gpsActionLabel = "";
      return;
    }
    do {
      if (mGpsStatus.isStarted()) {
        break;
      }

      //
      if (sportWithoutGps) {
        gpsActionLabel = getString(org.runnerup.common.R.string.Start_tracker);
      } else if (mGpsStatus.isEnabled()) {
        gpsActionLabel = getString(org.runnerup.common.R.string.Start_GPS);
      } else {
        gpsActionLabel = getString(org.runnerup.common.R.string.Enable_GPS);
      }
      gpsActionVisible = true;
      return;
    } while (false);

    gpsActionVisible = false;
  }

  private void updateGPSView() {
    if (!mGpsStatus.isEnabled() || !mGpsStatus.isStarted() || sportWithoutGps) {
      String gpsOffMessage = getString(org.runnerup.common.R.string.GPS_indicator_off);
      gpsSummaryItem = new StartReadinessItem(null, null, gpsOffMessage);
      gpsDetailsItem = new StartReadinessItem(null, null, gpsOffMessage);
      return;
    }

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
    var gpsLevel = getGpsLevel(accuracy, satFixedCount);
    int gpsIcon;
    int gpsMessageResource;
    switch (gpsLevel) {
      case NOT_FIXED:
        gpsIcon = R.drawable.ic_gps_0;
        gpsMessageResource = org.runnerup.common.R.string.Waiting_for_GPS;
        break;
      case POOR:
        gpsIcon = R.drawable.ic_gps_1;
        gpsMessageResource = org.runnerup.common.R.string.GPS_level_poor;
        break;
      case ACCEPTABLE:
        gpsIcon = R.drawable.ic_gps_2;
        gpsMessageResource = org.runnerup.common.R.string.GPS_level_acceptable;
        break;
      case GOOD:
        gpsIcon = R.drawable.ic_gps_3;
        gpsMessageResource = org.runnerup.common.R.string.GPS_level_good;
        break;
      default:
        throw new IllegalStateException("Unhandled GPS level " + gpsLevel);
    }
    gpsSummaryItem = new StartReadinessItem(gpsIcon, "GPS", getString(gpsMessageResource));
    gpsDetailsItem = new StartReadinessItem(gpsIcon, "GPS", gpsDetail);
    if (gpsLevel == GpsLevel.NOT_FIXED) {
      notificationStateManager.displayNotificationState(gpsSearchingState);
    } else {
      notificationStateManager.displayNotificationState(gpsBoundState);
    }
  }

  private void updateHRView() {
    if (mTracker == null || !mTracker.isComponentConfigured(TrackerHRM.NAME)) {
      heartRateItem = null;
      return;
    }
    if (mTracker.getHRProvider() instanceof WearHRProvider) {
      WearHRConnectionState.Status status =
          ((WearHRProvider) mTracker.getHRProvider()).getConnectionStatus();
      if (status == WearHRConnectionState.Status.CONNECTING
          || status == WearHRConnectionState.Status.RECONNECTING
          || status == WearHRConnectionState.Status.UNAVAILABLE) {
        heartRateItem =
            new StartReadinessItem(
                R.drawable.ic_heartrate_white_24dp,
                getString(org.runnerup.common.R.string.Heart_Rate_Monitor),
                getString(WearHRStatusText.resourceFor(status)));
        return;
      }
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

    heartRateItem =
        new StartReadinessItem(
            R.drawable.ic_heartrate_white_24dp,
            getString(org.runnerup.common.R.string.Heart_Rate_Monitor),
            statusDetailsShown ? getHRDetailString() : "");
  }

  private void updateWearOSView() {
    if (mTracker == null || !mTracker.isComponentConfigured(TrackerWear.NAME)) {
      wearOsItem = null;
      return;
    }
    String message = "";
    if (!mTracker.isComponentConnected(TrackerWear.NAME)) {
      message = "?";
    }
    wearOsItem =
        new StartReadinessItem(
            R.drawable.ic_watch_white_24dp,
            getString(org.runnerup.common.R.string.Wear_OS_Device),
            message);
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
                  " [%1$s, %2$s/%3$s, %4$.1f/%5$.1f deg]",
                  formatter.formatElevation(
                      Formatter.Format.TXT_LONG, l.getVerticalAccuracyMeters()),
                  formatter.formatSpeed(Formatter.Format.TXT_SHORT, l.getSpeed()),
                  formatter.formatSpeed(
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

  private void handleActivityResult(int requestCode, int resultCode, Intent data) {
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
      refreshWorkoutSetup();
    }
    updateView();
  }

  @Override
  public void onTick() {
    updateView();
  }

  private void loadAdvanced(String name) {
    Context ctx = requireActivity().getApplicationContext();
    if (name == null) {
      SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
      name = pref.getString(getResources().getString(R.string.pref_advanced_workout), "");
    }
    advancedWorkout = null;
    if ("".contentEquals(name)) {
      refreshWorkoutSetup();
      return;
    }
    try {
      advancedWorkout = WorkoutSerializer.readFile(ctx, name);
      refreshWorkoutSetup();
    } catch (Exception ex) {
      ex.printStackTrace();
      new AlertDialog.Builder(requireActivity(), R.style.RelvenStartDialogTheme)
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

  private final ValueModel.ChangeListener<TrackerState> trackerStateListener =
      new ValueModel.ChangeListener<>() {
        @Override
        public void onValueChanged(
            ValueModel<TrackerState> instance, TrackerState oldValue, TrackerState newValue) {
          onTick();
        }
      };
}
