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
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.preference.PreferenceManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.hr.WearHRConnectionState;
import org.runnerup.hr.WearHRProvider;
import org.runnerup.relven.ui.RunControlsCompose;
import org.runnerup.relven.ui.RunControlsStateHolder;
import org.runnerup.relven.ui.RunMetricValues;
import org.runnerup.relven.ui.RunMetricsCompose;
import org.runnerup.relven.ui.RunMetricsScreenKt;
import org.runnerup.relven.ui.RunMetricsStateHolder;
import org.runnerup.relven.ui.RunWorkoutStepItem;
import org.runnerup.relven.ui.RunWorkoutStepsCompose;
import org.runnerup.relven.ui.RunWorkoutStepsPresentationFactory;
import org.runnerup.relven.ui.RunWorkoutStepsStateHolder;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.util.Formatter;
import org.runnerup.util.TickListener;
import org.runnerup.util.ViewUtil;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;

public class RunActivity extends AppCompatActivity implements TickListener {
  private Workout workout = null;
  private Tracker mTracker = null;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ActivityResultLauncher<Intent> detailActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> handleActivityResult(0, result.getResultCode(), result.getData()));
  private final ActivityResultLauncher<Intent> pausedDetailActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> handleActivityResult(1, result.getResultCode(), result.getData()));

  private ComposeView workoutControlsView;
  private RunControlsStateHolder runControlsState;
  private final RunMetricsStateHolder runMetricsState = new RunMetricsStateHolder();
  private TextView countdownView = null;
  private ComposeView workoutStepsView;
  private Step currentStep = null;
  private final RunWorkoutStepsStateHolder runWorkoutStepsState = new RunWorkoutStepsStateHolder();
  private List<RunWorkoutStepItem> runWorkoutStepItems = List.of();
  private final Map<Step, Integer> workoutStepIndexByIdentity = new IdentityHashMap<>();
  private Formatter formatter = null;
  private String paceLabel = "";
  private TextView hrDebug;
  // A circular buffer for tap events
  private final long[] mTapArray = {0, 0};
  private int mTapIndex = 0;
  private boolean buttonsLocked = false;

  @SuppressLint("SourceLockedOrientationActivity")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    Window window = getWindow();
    WindowCompat.getInsetsController(window, window.getDecorView())
        .setAppearanceLightStatusBars(false);
    super.onCreate(savedInstanceState);
    if (!isLargeScreen()) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    setContentView(R.layout.run);
    formatter = new Formatter(this);
    paceLabel = formatter.formatVelocityLabel();
    // HRZones hrZones = new HRZones(this);
    RunMetricsCompose.install(findViewById(R.id.run_metrics), runMetricsState);

    workoutControlsView = findViewById(R.id.run_controls);
    runControlsState =
        new RunControlsStateHolder(
            () -> newLapButtonClick.onClick(workoutControlsView),
            () -> pauseButtonClick.onClick(workoutControlsView),
            () -> stopButtonClick.onClick(workoutControlsView));
    RunControlsCompose.install(workoutControlsView, runControlsState);
    countdownView = findViewById(R.id.countdown_text_view);
    hrDebug = findViewById(R.id.hr_debug);
    workoutStepsView = findViewById(R.id.workout_steps);
    RunWorkoutStepsCompose.install(workoutStepsView, runWorkoutStepsState, formatter);

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final Resources res = this.getResources();
    final KeyguardManager km = ContextCompat.getSystemService(this, KeyguardManager.class);
    final boolean lockButtons = prefs.getBoolean(res.getString(R.string.pref_lock_run), false);
    final boolean showOnLockScreen =
        prefs.getBoolean(res.getString(R.string.pref_show_on_lock_screen), true);
    final boolean keepScreenOn =
        prefs.getBoolean(res.getString(R.string.pref_keep_screen_on), false);
    buttonsLocked = lockButtons && showOnLockScreen;
    runControlsState.update(false, buttonsLocked);

    if (!prefs.getBoolean(res.getString(R.string.pref_bt_debug), false)) {
      hrDebug = null;
    }

    View root = findViewById(R.id.start_view);
    bindGpsTracker();

    View.OnTouchListener touchListener =
        (v, event) -> {
          // Detect tapping on the view
          int action = event.getAction();
          if (lockButtons && action == MotionEvent.ACTION_DOWN) {
            // Check circular buffer for mTapArray.length+1 tap events within time
            final int maxTapTime = 1000;
            long time = event.getEventTime();
            if (mTapArray[mTapIndex] != 0 && time - mTapArray[mTapIndex] < maxTapTime) {
              buttonsLocked = !buttonsLocked;
              runControlsState.update(workout != null && workout.isPaused(), buttonsLocked);
              Arrays.fill(mTapArray, 0);
              // set index so next tap is not message
              mTapIndex = 1;
            } else {
              if (mTapIndex == 0) {
                Toast.makeText(
                        getApplicationContext(),
                        res.getString(org.runnerup.common.R.string.Lock_activity_buttons_message),
                        Toast.LENGTH_SHORT)
                    .show();
              }
              mTapArray[mTapIndex] = time;
              mTapIndex = (mTapIndex + 1) % mTapArray.length;
            }
          }
          return false;
        };

    root.setOnTouchListener(touchListener);
    workoutStepsView.setOnTouchListener(touchListener);
    workoutControlsView.setOnTouchListener(touchListener);

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                // Ignore back while in an activity and the device is unlocked
                assert km != null;
                if (km.isKeyguardLocked()) {
                  // Original state is re-enabled on resume
                  showOnLockScreen(false);
                }
              }
            });
    ViewUtil.Insets(findViewById(R.id.start_view), true);

    showOnLockScreen(showOnLockScreen);
    if (keepScreenOn) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
  }

  private boolean isLargeScreen() {
    int screenSize =
        getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
    return screenSize >= Configuration.SCREENLAYOUT_SIZE_LARGE;
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Log.d(getClass().getName(), "onConfigurationChange => do NOTHING!!");
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public void onResume() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final Resources res = this.getResources();
    final boolean showOnLockScreen =
        prefs.getBoolean(res.getString(R.string.pref_show_on_lock_screen), true);

    super.onResume();
    showOnLockScreen(showOnLockScreen);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    unbindGpsTracker();
    stopTimer();
  }

  private void onGpsTrackerBound() {
    if (mTracker == null) {
      // should not happen
      return;
    }

    workout = mTracker.getWorkout();
    if (workout == null) {
      // should not happen
      return;
    }

    {
      /*
       * Countdown view can't be bound until RunActivity is started
       *   since it's not created until then
       */
      HashMap<String, Object> bindValues = new HashMap<>();
      bindValues.put(Workout.KEY_COUNTER_VIEW, countdownView);
      workout.onBind(workout, bindValues);
    }

    startTimer();

    populateWorkoutList();
    mTracker.displayNotificationState();
  }

  private void populateWorkoutList() {
    List<Workout.StepListEntry> entries = workout.getStepList();
    List<RunWorkoutStepItem> items = new java.util.ArrayList<>(entries.size());
    workoutStepIndexByIdentity.clear();
    for (int index = 0; index < entries.size(); index++) {
      Workout.StepListEntry entry = entries.get(index);
      Step step = entry.step();
      workoutStepIndexByIdentity.putIfAbsent(step, index);
      items.add(new RunWorkoutStepItem(step, entry.level()));
    }
    runWorkoutStepItems = List.copyOf(items);
    currentStep = workout.getCurrentStep();
    runWorkoutStepsState.update(
        RunWorkoutStepsPresentationFactory.INSTANCE.create(
            runWorkoutStepItems, workoutStepIndexByIdentity.get(currentStep)));
  }

  private Timer timer = null;

  private void startTimer() {
    timer = new Timer();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            RunActivity.this.handler.post(RunActivity.this::onTick);
          }
        },
        0,
        500);
  }

  private void stopTimer() {
    if (timer != null) {
      timer.cancel();
      timer.purge();
      timer = null;
    }
  }

  private Location l = null;

  public void onTick() {
    if (workout != null) {
      workout.onTick();
      updateView();

      if (mTracker != null) {
        Location l2 = mTracker.getLastKnownLocation();
        if (l2 != null && !l2.equals(l)) {
          l = l2;
        }
      }
    }
  }

  private void handleActivityResult(int requestCode, int resultCode, Intent data) {
    if (workout == null) {
      // "should not happen"
      finish();
      return;
    }
    if (resultCode == AppCompatActivity.RESULT_OK) {
      /*
       * they saved
       */
      Double manualDistance =
          data != null && data.hasExtra("MANUAL_DISTANCE")
              ? data.getDoubleExtra("MANUAL_DISTANCE", 0)
              : null;
      workout.onComplete(Scope.ACTIVITY, workout);
      if (mTracker != null) {
        mTracker.completeActivity(/* save= */ true, manualDistance);
      }
      mTracker = null;
      finish();
    } else if (resultCode == AppCompatActivity.RESULT_CANCELED) {
      /*
       * they discarded
       */
      workout.onComplete(Scope.ACTIVITY, workout);
      mTracker.completeActivity(/* save= */ false, /* manualDistance= */ null);
      mTracker = null;
      finish();
    } else if (resultCode == AppCompatActivity.RESULT_FIRST_USER) {
      startTimer();
      if (requestCode == 0) {
        workout.onResume(workout);
        // else: we were paused before stopButtonClick...don't resume
      }
    } else {
      if (BuildConfig.DEBUG) {
        throw new AssertionError();
      }
    }
  }

  private void newLap() {
    if (workout != null) {
      workout.onNewLapOrNextStep();
    }
  }

  private void setPauseButtonEnabled(boolean enabled) {
    runControlsState.update(!enabled, buttonsLocked);
  }

  private void togglePause() {
    if (workout == null) {
      // "should not happen"
      return;
    }

    if (workout.isPaused()) {
      workout.onResume(workout);
    } else {
      workout.onPause(workout);
    }
    setPauseButtonEnabled(!workout.isPaused());
  }

  private void doStop() {
    if (timer != null) {
      workout.onStop(workout);
      stopTimer(); // set timer=null;
      ServiceCompat.stopForeground(mTracker, ServiceCompat.STOP_FOREGROUND_REMOVE);
      Intent intent = new Intent(RunActivity.this, DetailActivity.class);
      /*
       * The same activity is used to show details and to save
       * activity they show almost the same information
       */
      intent.putExtra("mode", "save");
      intent.putExtra("ID", mTracker.getActivityId());
      if (workout.isPaused()) {
        pausedDetailActivityLauncher.launch(intent);
      } else {
        detailActivityLauncher.launch(intent);
      }
    }
  }

  private final OnClickListener newLapButtonClick = v -> newLap();
  private final OnClickListener pauseButtonClick =
      v -> {
        if (buttonsLocked) {
          Toast.makeText(
                  getApplicationContext(),
                  getResources()
                      .getString(org.runnerup.common.R.string.Lock_activity_buttons_message),
                  Toast.LENGTH_SHORT)
              .show();
        } else {
          togglePause();
        }
      };

  private final OnClickListener stopButtonClick =
      v -> {
        if (buttonsLocked) {
          Toast.makeText(
                  getApplicationContext(),
                  getResources()
                      .getString(org.runnerup.common.R.string.Lock_activity_buttons_message),
                  Toast.LENGTH_SHORT)
              .show();
        } else {
          doStop();
        }
      };

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final Resources res = this.getResources();
    final boolean volumeButtonControls =
        prefs.getBoolean(res.getString(R.string.pref_volume_button_controls), false);

    if (volumeButtonControls
        && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final Resources res = this.getResources();
    final boolean volumeButtonControls =
        prefs.getBoolean(res.getString(R.string.pref_volume_button_controls), false);

    if (volumeButtonControls
        && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
      switch (keyCode) {
        case KeyEvent.KEYCODE_VOLUME_UP:
          newLap();
          break;
        case KeyEvent.KEYCODE_VOLUME_DOWN:
          togglePause();
      }
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  private void showOnLockScreen(boolean enabled) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(enabled);
    } else {
      setShowWhenLockedPreOreo(enabled);
    }
  }

  @SuppressWarnings("deprecation")
  private void setShowWhenLockedPreOreo(boolean enabled) {
    Window window = getWindow();
    if (enabled) {
      window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }
  }

  private void updateView() {
    boolean isPaused = workout != null && workout.isPaused();
    if (mTracker.getState() == TrackerState.STOPPED && !isPaused) {
      doStop();
    } else {
      if (workout == null) {
        // "should not happen"
        return;
      }

      setPauseButtonEnabled(!workout.isPaused());
      double ad = workout.getDistance(Scope.ACTIVITY);
      double at = workout.getTime(Scope.ACTIVITY);
      double ap = workout.getSpeed(Scope.ACTIVITY);
      String activityTimeText =
          formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(at));
      String activityDistanceText =
          formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(ad));
      String activityPaceText =
          formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, ap);

      double ld = workout.getDistance(Scope.LAP);
      double lt = workout.getTime(Scope.LAP);
      double lp = workout.getSpeed(Scope.LAP);
      String lapTimeText = formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(lt));
      String lapDistanceText = formatter.formatDistance(Formatter.Format.TXT_LONG, Math.round(ld));
      String lapPaceText = formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, lp);

      boolean showInterval =
          this.currentStep != null
              && workout.getWorkoutType() != Constants.WORKOUT_TYPE.BASIC
              && this.currentStep.getIntensity() == Intensity.ACTIVE;
      String intervalTimeText = "";
      String intervalDistanceText = "";
      String intervalPaceText = "";
      if (showInterval) {
        double id = workout.getDistance(Scope.STEP);
        double it = workout.getTime(Scope.STEP);
        double ip = workout.getSpeed(Scope.STEP);

        intervalTimeText = formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(it));
        intervalDistanceText = formatter.formatDistance(Formatter.Format.TXT_LONG, Math.round(id));
        intervalPaceText = formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, ip);
      }

      double cp = workout.getSpeed(Scope.CURRENT);
      String currentPaceText =
          formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, cp);

      WearHRConnectionState.Status wearHrStatus = null;
      if (mTracker.getHRProvider() instanceof WearHRProvider) {
        wearHrStatus = ((WearHRProvider) mTracker.getHRProvider()).getConnectionStatus();
      }
      boolean heartRateAvailable = mTracker.isComponentConnected(TrackerHRM.NAME);
      String activityHeartRateText = "";
      String lapHeartRateText = "";
      String intervalHeartRateText = "";
      String currentHeartRateText = "";
      String heartRateNotice = null;
      if (heartRateAvailable) {
        activityHeartRateText =
            formatter.formatHeartRate(
                Formatter.Format.TXT_SHORT, workout.getHeartRate(Scope.ACTIVITY));
        lapHeartRateText =
            formatter.formatHeartRate(Formatter.Format.TXT_SHORT, workout.getHeartRate(Scope.LAP));
        intervalHeartRateText =
            formatter.formatHeartRate(Formatter.Format.TXT_SHORT, workout.getHeartRate(Scope.STEP));
        currentHeartRateText =
            formatter.formatHeartRate(
                Formatter.Format.TXT_SHORT, workout.getHeartRate(Scope.CURRENT));
        if (hrDebug != null) {
          hrDebug.setVisibility(View.VISIBLE);
          mTracker.setHrDebug(hrDebug);
        }
      } else {
        if (wearHrStatus == WearHRConnectionState.Status.CONNECTING
            || wearHrStatus == WearHRConnectionState.Status.RECONNECTING
            || wearHrStatus == WearHRConnectionState.Status.UNAVAILABLE) {
          heartRateNotice = getString(WearHRStatusText.resourceFor(wearHrStatus));
        }
      }
      runMetricsState.update(
          RunMetricsScreenKt.runMetricsPresentation(
              new RunMetricValues(
                  activityTimeText, activityDistanceText, activityPaceText, activityHeartRateText),
              new RunMetricValues(lapTimeText, lapDistanceText, lapPaceText, lapHeartRateText),
              new RunMetricValues(
                  intervalTimeText, intervalDistanceText, intervalPaceText, intervalHeartRateText),
              currentPaceText,
              currentHeartRateText,
              paceLabel,
              showInterval,
              heartRateAvailable,
              heartRateNotice));

      Step current = workout.getCurrentStep();
      if (current != currentStep) {
        currentStep = current;
        runWorkoutStepsState.update(
            RunWorkoutStepsPresentationFactory.INSTANCE.create(
                runWorkoutStepItems, workoutStepIndexByIdentity.get(currentStep)));
      }
    }
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
          if (mTracker == null) {
            mTracker = ((Tracker.LocalBinder) service).getService();
            // Tell the user about this for our demo.
            RunActivity.this.onGpsTrackerBound();
          }
        }

        public void onServiceDisconnected(ComponentName className) {
          // This is called when the connection with the service has been
          // unexpectedly disconnected -- that is, its process crashed.
          // Because it is running in our same process, we should never
          // see this happen.
          mIsBound = false;
          mTracker = null;
        }
      };

  private void bindGpsTracker() {
    // Establish a connection with the service. We use an explicit
    // class name because we want a specific service implementation that
    // we know will be running in our own process (and thus won't be
    // supporting component replacement by other applications).
    mIsBound =
        getApplicationContext()
            .bindService(new Intent(this, Tracker.class), mConnection, Context.BIND_AUTO_CREATE);
  }

  private void unbindGpsTracker() {
    if (mIsBound) {
      // Detach our existing connection.
      getApplicationContext().unbindService(mConnection);
      mIsBound = false;
    }
  }
}
