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

package org.runnerup.tracker;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationListenerCompat;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.ValueModel;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.hr.HRProvider;
import org.runnerup.notification.ForegroundNotificationDisplayStrategy;
import org.runnerup.notification.NotificationState;
import org.runnerup.notification.NotificationStateManager;
import org.runnerup.notification.OngoingState;
import org.runnerup.relven.health.HealthConnectActivitySync;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerComponent;
import org.runnerup.tracker.component.TrackerComponentCollection;
import org.runnerup.tracker.component.TrackerElevation;
import org.runnerup.tracker.component.TrackerGPS;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.tracker.component.TrackerPressure;
import org.runnerup.tracker.component.TrackerReceiver;
import org.runnerup.tracker.component.TrackerTTS;
import org.runnerup.tracker.component.TrackerTemperature;
import org.runnerup.tracker.component.TrackerWear;
import org.runnerup.tracker.filter.PersistentGpsLoggerListener;
import org.runnerup.util.Formatter;
import org.runnerup.util.HRZones;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutBuilder;
import org.runnerup.workout.WorkoutSerializer;

/**
 * GpsTracker - this class tracks Location updates
 *
 * <p>TODO: rename this class into ActivityTracker and factor out Gps stuff into own class that
 * should be handled much like hrm (e.g as a sensor among others)
 *
 * @author jonas.oreland@gmail.com
 */
public class Tracker extends Service implements LocationListenerCompat, Constants {
  private static final String ACTION_ACTIVE_WORKOUT = "org.runnerup.tracker.ACTIVE_WORKOUT";
  // Max age for current data to be considered valid
  private static final int MAX_CURRENT_AGE = 15000;
  private static final long NANO_IN_MILLI = 1000000;

  private final Handler handler = new Handler(Looper.getMainLooper());

  private final TrackerComponentCollection components = new TrackerComponentCollection();
  // Some trackers may select separate sensors depending on sport, handled in onBind()
  private final TrackerGPS trackerGPS = (TrackerGPS) components.addComponent(new TrackerGPS(this));
  private final TrackerHRM trackerHRM = (TrackerHRM) components.addComponent(new TrackerHRM());
  TrackerTTS trackerTTS = (TrackerTTS) components.addComponent(new TrackerTTS());
  private final TrackerCadence trackerCadence =
      (TrackerCadence) components.addComponent(new TrackerCadence());
  private final TrackerTemperature trackerTemperature =
      (TrackerTemperature) components.addComponent(new TrackerTemperature());
  private final TrackerPressure trackerPressure =
      (TrackerPressure) components.addComponent(new TrackerPressure());
  private final TrackerElevation trackerElevation =
      (TrackerElevation)
          components.addComponent(new TrackerElevation(this, trackerGPS, trackerPressure));
  TrackerReceiver trackerReceiver =
      (TrackerReceiver) components.addComponent(new TrackerReceiver(this));
  private final TrackerWear trackerWear =
      (TrackerWear) components.addComponent(new TrackerWear(this));

  private boolean mTimeFromGpsPoints = false;
  private boolean mCurrentSpeedFromGpsPoints = false;

  private long mLapId = 0;
  private long mActivityId = 0;
  private long mStartedAtEpochMillis = 0;
  private final ActivityCompletionGate activityCompletionGate = new ActivityCompletionGate();
  private long mElapsedTimeNanos = 0;
  private double mElapsedDistance = 0;
  private double mHeartbeats = 0;
  private double mHeartbeatNanos = 0; // since we might loose HRM connectivity...
  private long mMaxHR = 0;
  private double mCurrentSpeed = 0.0;

  private TrackerState nextState;
  private final ValueModel<TrackerState> state = new ValueModel<>(TrackerState.INIT);
  private int mLocationType = DB.LOCATION.TYPE_START;

  // Last location given by LocationManager
  private Location mLastLocation = null;
  private final GpsFixFilter gpsFixFilter = new GpsFixFilter();
  private final GpsCurrentSpeedFilter gpsCurrentSpeedFilter = new GpsCurrentSpeedFilter();
  // Last location given by LocationManager when in state STARTED
  private Location mLastLocationStarted = null;

  private SQLiteDatabase mDB = null;
  private PersistentGpsLoggerListener mDBWriter = null;
  private PowerManager.WakeLock mWakeLock = null;
  private final List<WorkoutObserver> liveLoggers = new ArrayList<>();
  private final List<TrackerLocationObserver> locationObservers = new ArrayList<>();

  private Workout workout = null;
  private NotificationStateManager notificationStateManager;
  private NotificationState activityOngoingState;
  private ActiveWorkoutCheckpointStore checkpointStore;
  private ActiveWorkoutCheckpoint pendingRecoveryCheckpoint;
  private boolean recoveryStarted;
  private final ExecutorService checkpointExecutor = Executors.newSingleThreadExecutor();
  private final Runnable checkpointRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (state.get() == TrackerState.STARTED || state.get() == TrackerState.PAUSED) {
            queueActiveCheckpoint();
            handler.postDelayed(this, 1000);
          }
        }
      };

  @Override
  public void onCreate() {
    mDB = DBHelper.getWritableDatabase(this);
    checkpointStore =
        new ActiveWorkoutCheckpointStore(new File(getFilesDir(), "active-workout-checkpoint.json"));
    notificationStateManager =
        new NotificationStateManager(new ForegroundNotificationDisplayStrategy(this));

    wakeLock(false);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    maybeRestoreActiveWorkout(/* startForegroundService= */ false);
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacks(checkpointRunnable);
    queueActiveCheckpoint();
    if (mDB != null) {
      DBHelper.closeDB(mDB);
      mDB = null;
    }

    if (state.get() == TrackerState.STARTED) {
      wakeLock(false);
      if (workout != null) workout.setTracker(null);
      workout = null;
    } else {
      reset();
    }
    checkpointExecutor.shutdown();
  }

  public void setup() {
    switch (state.get()) {
      case INIT:
        break;
      case INITIALIZING:
      case INITIALIZED:
        return;
      case CONNECTING:
      case CONNECTED:
      case STARTED:
      case PAUSED:
      case ERROR:
      case STOPPED:
        if (BuildConfig.DEBUG) {
          throw new AssertionError();
        }
        return;
      case CLEANUP:
        // if CLEANUP is in progress, setup will continue once complete
        nextState = TrackerState.INITIALIZING;
        return;
    }

    state.set(TrackerState.INITIALIZING);

    TrackerComponent.ResultCode result = components.onInit(onInitCallback, getApplicationContext());
    if (result != TrackerComponent.ResultCode.RESULT_PENDING) {
      onInitCallback.run(components, result);
    }
  }

  public void setWithoutGps(boolean val) {
    trackerGPS.setWithoutGps(val);
  }

  private final TrackerComponent.Callback onInitCallback =
      new TrackerComponent.Callback() {
        @Override
        public void run(TrackerComponent component, TrackerComponent.ResultCode resultCode) {
          if (resultCode == TrackerComponent.ResultCode.RESULT_ERROR_FATAL) {
            state.set(TrackerState.ERROR);
          } else {
            state.set(TrackerState.INITIALIZED);
            if (pendingRecoveryCheckpoint != null) {
              connect();
              return;
            }
          }

          Log.d(getClass().getName(), "state.set(" + getState() + ")");
          handleNextState();
        }
      };

  private void handleNextState() {
    if (nextState == null) return;

    // if last phase ended in error,
    // don't continue with a new
    if (state.get() == TrackerState.ERROR) return;

    if (state.get() == nextState) {
      nextState = null;
      return;
    }

    switch (nextState) {
      case INIT:
        reset();
        break;
      case INITIALIZING:
        break;
      case INITIALIZED:
        setup();
        break;
      case CONNECTING:
        break;
      case CONNECTED:
        connect();
        break;
      case STARTED:
        break;
      case PAUSED:
        break;
      case STOPPED:
        break;
      case CLEANUP:
        break;
      case ERROR:
        break;
    }
  }

  public void connect() {
    Log.d(getClass().getName(), "Tracker.connect() - state: " + state.get());
    switch (state.get()) {
      case INIT:
        setup();
      case INITIALIZING:
      case CLEANUP:
        nextState = TrackerState.CONNECTED;
        Log.d(getClass().getName(), " => nextState: " + nextState);
        return;
      case INITIALIZED:
        break;
      case CONNECTING:
      case CONNECTED:
        return;
      case STARTED:
      case PAUSED:
      case ERROR:
      case STOPPED:
        if (BuildConfig.DEBUG) {
          throw new AssertionError();
        }
        return;
    }

    state.set(TrackerState.CONNECTING);

    wakeLock(true);

    SyncManager u = new SyncManager(getApplicationContext());
    u.loadLiveLoggers(liveLoggers);
    u.close();

    TrackerComponent.ResultCode result =
        components.onConnecting(onConnectCallback, getApplicationContext());
    if (result != TrackerComponent.ResultCode.RESULT_PENDING) {
      onConnectCallback.run(components, result);
    }
  }

  private final TrackerComponent.Callback onConnectCallback =
      (component, resultCode) -> {
        if (resultCode == TrackerComponent.ResultCode.RESULT_ERROR_FATAL) {
          state.set(TrackerState.ERROR);
        } else if (state.get() == TrackerState.CONNECTING) {
          state.set(TrackerState.CONNECTED);
          /* now we're connected */
          components.onConnected();
          if (pendingRecoveryCheckpoint != null) restoreActiveWorkoutAfterConnect();
        }
      };

  @SuppressWarnings("UnusedReturnValue")
  private long createActivity(int sport) {
    Resources res = getResources();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    boolean logGpxAccuracy = prefs.getBoolean(res.getString(R.string.pref_log_gpx_accuracy), false);
    mTimeFromGpsPoints = prefs.getBoolean(res.getString(R.string.pref_time_from_gps_points), false);
    mCurrentSpeedFromGpsPoints =
        prefs.getBoolean(res.getString(R.string.pref_speed_from_gps_points), false);

    // Create an Activity instance
    activityCompletionGate.beginActivity();
    ContentValues tmp = new ContentValues();
    tmp.put(DB.ACTIVITY.SPORT, sport);
    try {
      mActivityId = mDB.insert(DB.ACTIVITY.TABLE, "nullColumnHack", tmp);

      tmp.clear();
      tmp.put(DB.LOCATION.ACTIVITY, mActivityId);
      tmp.put(DB.LOCATION.LAP, 0); // always start with lap 0
      mDBWriter = new PersistentGpsLoggerListener(mDB, DB.LOCATION.TABLE, tmp, logGpxAccuracy);
    } catch (IllegalStateException ex) {
      Log.i(getClass().getName(), "Query failed:", ex);
    }
    return mActivityId;
  }

  public void setWorkout(Workout workout) {
    this.workout = workout;
  }

  public void start() {
    if (BuildConfig.DEBUG && state.get() != TrackerState.CONNECTED) {
      throw new AssertionError();
    }

    // connect workout and tracker
    workout.setTracker(this);

    // Add Wear to live loggers if it's active
    if (components.getResultCode(TrackerWear.NAME) == TrackerComponent.ResultCode.RESULT_OK)
      liveLoggers.add(trackerWear);

    // create the DB activity
    createActivity(workout.getSport());
    mStartedAtEpochMillis = System.currentTimeMillis();

    // do bindings
    doBind();

    // Let workout do initializations
    workout.onInit(workout);

    // Let components know we're starting
    components.onStart();

    mElapsedTimeNanos = 0;
    mElapsedDistance = 0;
    mHeartbeats = 0;
    mHeartbeatNanos = 0;
    mMaxHR = 0;
    mLastLocationStarted = null;
    mLastLocation = null;
    gpsFixFilter.reset();
    gpsCurrentSpeedFilter.reset();

    // New location update will be tagged with START
    setNextLocationType(DB.LOCATION.TYPE_START);

    state.set(TrackerState.STARTED);

    activityOngoingState = new OngoingState(new Formatter(this), workout, this);

    // And finally let workout know that we started
    workout.onStart(Scope.ACTIVITY, this.workout);
    ContextCompat.startForegroundService(
        this, new Intent(this, Tracker.class).setAction(ACTION_ACTIVE_WORKOUT));
    notificationStateManager.displayNotificationState(activityOngoingState);
    handler.removeCallbacks(checkpointRunnable);
    handler.post(checkpointRunnable);
  }

  private void doBind() {
    // Let components populate bindValues
    HashMap<String, Object> bindValues = new HashMap<>();
    Context ctx = getApplicationContext();
    bindValues.put(TrackerComponent.KEY_CONTEXT, ctx);
    bindValues.put(Workout.KEY_FORMATTER, new Formatter(ctx));
    bindValues.put(Workout.KEY_HRZONES, new HRZones(ctx));
    bindValues.put(Workout.KEY_MUTE, workout.getMute());
    bindValues.put(Workout.KEY_SPORT_TYPE, workout.getSport());
    bindValues.put(Workout.KEY_WORKOUT_TYPE, workout.getWorkoutType());

    components.onBind(bindValues);

    // and then give them to workout
    workout.onBind(workout, bindValues);
  }

  public void newLap(ContentValues tmp) {
    tmp.put(DB.LAP.ACTIVITY, mActivityId);
    mLapId = mDB.insert(DB.LAP.TABLE, null, tmp);
    ContentValues key = mDBWriter.getKey();
    key.put(DB.LOCATION.LAP, tmp.getAsLong(DB.LAP.LAP));
    mDBWriter.setKey(key);
  }

  public void saveLap(ContentValues tmp) {
    tmp.put(DB.LAP.ACTIVITY, mActivityId);
    String[] key = {Long.toString(mLapId)};
    mDB.update(DB.LAP.TABLE, tmp, "_id = ?", key);
  }

  public void pause() {
    switch (state.get()) {
      case INIT:
      case ERROR:
      case INITIALIZING:
      case INITIALIZED:
      case PAUSED:
      case CONNECTING:
      case CONNECTED:
      case CLEANUP:
      case STOPPED:
        return;
      case STARTED:
        break;
    }
    state.set(TrackerState.PAUSED);
    setNextLocationType(DB.LOCATION.TYPE_PAUSE);
    // This saves a PAUSE location
    internalOnLocationChanged(mLastLocationStarted);
    gpsFixFilter.reset();
    gpsCurrentSpeedFilter.reset();
    mCurrentSpeed = 0.0;

    saveActivity(/* manualDistance= */ null);
    components.onPause();
    queueActiveCheckpoint();
  }

  /** Refresh the ongoing notification from the workout */
  public void displayNotificationState() {
    notificationStateManager.displayNotificationState(activityOngoingState);
  }

  public void stop() {
    switch (state.get()) {
      case INIT:
      case ERROR:
      case INITIALIZING:
      case INITIALIZED:
      case CONNECTING:
      case CONNECTED:
      case CLEANUP:
      case STOPPED:
        return;
      case PAUSED:
      case STARTED:
        break;
    }
    state.set(TrackerState.STOPPED);
    setNextLocationType(DB.LOCATION.TYPE_PAUSE);
    // This saves a PAUSE location
    internalOnLocationChanged(mLastLocationStarted);
    gpsFixFilter.reset();
    gpsCurrentSpeedFilter.reset();
    mCurrentSpeed = 0.0;

    saveActivity(/* manualDistance= */ null);
    components.onPause(); // TODO add new callback for this
    queueActiveCheckpoint();
  }

  private void internalOnLocationChanged(Location arg0) {
    // insert location, set internal time
    if (arg0 != null) {
      onLocationChangedImpl(arg0, true);
    }
  }

  public void resume() {
    switch (state.get()) {
      case INIT:
      case ERROR:
      case INITIALIZING:
      case CLEANUP:
      case INITIALIZED:
      case CONNECTING:
      case CONNECTED:
        if (BuildConfig.DEBUG) {
          throw new AssertionError();
        }
        return;
      case PAUSED:
      case STOPPED:
        break;
      case STARTED:
        return;
    }

    state.set(TrackerState.STARTED);
    setNextLocationType(DB.LOCATION.TYPE_RESUME);
    // save a resume location
    internalOnLocationChanged(mLastLocation);
    gpsFixFilter.reset();
    gpsCurrentSpeedFilter.reset();
    mCurrentSpeed = 0.0;
    components.onResume();
    queueActiveCheckpoint();
  }

  public void reset() {
    switch (state.get()) {
      case INIT:
        return;
      case INITIALIZING:
        // cleanup when INITIALIZE is complete
        nextState = TrackerState.INIT;
        return;
      case INITIALIZED:
      case ERROR:
      case PAUSED:
      case CONNECTING:
      case CONNECTED:
      case STOPPED:
        nextState = TrackerState.INIT;
        // it's ok to "abort" connecting
        break;
      case STARTED:
        if (BuildConfig.DEBUG) {
          throw new AssertionError();
        }
        return;
      case CLEANUP:
        return;
    }

    wakeLock(false);

    if (workout != null) {
      workout.setTracker(null);
      workout = null;
    }

    state.set(TrackerState.CLEANUP);
    liveLoggers.clear();
    TrackerComponent.ResultCode res = components.onEnd(onEndCallback, getApplicationContext());
    if (res != TrackerComponent.ResultCode.RESULT_PENDING) onEndCallback.run(components, res);
  }

  private final TrackerComponent.Callback onEndCallback =
      (component, resultCode) -> {
        if (resultCode == TrackerComponent.ResultCode.RESULT_ERROR_FATAL) {
          state.set(TrackerState.ERROR);
        } else {
          state.set(TrackerState.INIT);
        }

        handleNextState();
      };

  public void completeActivity(boolean save, Double manualDistance) {
    if (!activityCompletionGate.tryBeginCompletion(state.get())) {
      return;
    }
    handler.removeCallbacks(checkpointRunnable);
    if (!clearActiveCheckpointBeforeCompletion()) {
      activityCompletionGate.cancelCompletion();
      Log.e("Tracker", "Unable to clear active workout checkpoint before completion");
      handler.post(checkpointRunnable);
      return;
    }

    setNextLocationType(DB.LOCATION.TYPE_END);
    internalOnLocationChanged(mLastLocationStarted);

    if (save) {
      saveActivity(manualDistance);
      liveLog(DB.LOCATION.TYPE_END);
      if (mDB != null) {
        HealthConnectActivitySync.onActivitySaved(this, mDB, mActivityId);
      }
    } else {
      ContentValues tmp = new ContentValues();
      tmp.put("deleted", 1);
      String[] key = {Long.toString(mActivityId)};

      if (mDB == null) {
        Log.e("Tracker", "completeActivity called but mDB is null");
      } else {
        mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", key);
      }
      liveLog(DB.LOCATION.TYPE_DISCARD);
    }

    components.onComplete(!save);
    notificationStateManager.cancelNotification();
    reset();
    stopSelf();
  }

  private void saveActivity(Double manualDistance) {
    if (mDB == null) {
      Log.e("Tracker", "saveActivity called but mDB is null");
      return;
    }
    ContentValues tmp = new ContentValues();
    if (mHeartbeatNanos > 0) {
      long avgHR = Math.round(60 * mHeartbeats * 1000 * NANO_IN_MILLI / mHeartbeatNanos); // BPM
      tmp.put(DB.ACTIVITY.AVG_HR, avgHR);
    }
    if (mMaxHR > 0) tmp.put(DB.ACTIVITY.MAX_HR, mMaxHR);

    if (TrackerPressure.isAvailable(this)) {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      boolean enabled = prefs.getBoolean(this.getString(R.string.pref_use_pressure_sensor), false);
      if (enabled) {
        // Save information about barometer usage, used in uploads (like Strava)
        tmp.put(DB.ACTIVITY.META_DATA, DB.ACTIVITY.WITH_BAROMETER);
      }
    }

    if (manualDistance != null) {
      tmp.put(DB.ACTIVITY.DISTANCE, manualDistance);
    } else {
      tmp.put(DB.ACTIVITY.DISTANCE, mElapsedDistance);
    }
    tmp.put(
        DB.ACTIVITY.TIME,
        Math.round(getTimeMs() / 1000.0d)); // time should be updated last for conditionalRecompute

    String[] key = {Long.toString(mActivityId)};
    mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", key);
  }

  private void setNextLocationType(int newType) {
    if (mDBWriter == null) {
      Log.w("Tracker", "setNextLocationType: mDBWriter is null (newType=" + newType + ")");
      return;
    }
    ContentValues key = mDBWriter.getKey();
    key.put(DB.LOCATION.TYPE, newType);
    mDBWriter.setKey(key);
    mLocationType = newType;
  }

  // public long getTime() {
  //    return getTimeMs() / 1000;
  // }

  public long getTimeMs() {
    return mElapsedTimeNanos / NANO_IN_MILLI;
  }

  public double getDistance() {
    return mElapsedDistance;
  }

  public Location getLastKnownLocation() {
    return mLastLocation;
  }

  public long getActivityId() {
    return mActivityId;
  }

  /** Register a read-only observer for accepted location samples. Call from the main thread. */
  public void addLocationObserver(TrackerLocationObserver observer) {
    synchronized (locationObservers) {
      if (!locationObservers.contains(observer)) locationObservers.add(observer);
    }
  }

  /** Remove a previously registered read-only location observer. */
  public void removeLocationObserver(TrackerLocationObserver observer) {
    synchronized (locationObservers) {
      locationObservers.remove(observer);
    }
  }

  @Override
  public void onLocationChanged(@NonNull Location arg0) {
    GpsFixFilter.Result fixResult = null;
    if (!trackerGPS.isWithoutGps()) {
      fixResult = gpsFixFilter.accept(arg0, SystemClock.elapsedRealtimeNanos());
      if (!fixResult.isAccepted()) return;
      if (fixResult.isStationary() && !fixResult.startsNewSegment() && mLastLocation != null) {
        // Advance timers on reliable fixes without letting coordinate jitter shape the route.
        arg0 = new Location(arg0);
        arg0.setLatitude(mLastLocation.getLatitude());
        arg0.setLongitude(mLastLocation.getLongitude());
      }
      if (fixResult.startsNewSegment() && mLastLocation != null) {
        setNextLocationType(DB.LOCATION.TYPE_RESUME);
      }
      if (fixResult.startsNewSegment()) {
        mCurrentSpeed = 0.0;
        gpsCurrentSpeedFilter.reset();
      }
      if (fixResult.hasDerivedSpeed()) {
        arg0.setSpeed((float) fixResult.getSpeedMetersPerSecond());
      } else {
        // Location.removeSpeed() is newer than the app's minimum supported Android version.
        arg0.setSpeed(0.0f);
      }
    }
    // Elevation and persisted telemetry use only accepted GPS fixes.
    if (fixResult == null || !fixResult.isStationary()) {
      trackerElevation.onLocationChanged(arg0);
    }
    onLocationChangedImpl(arg0, false, fixResult);
  }

  private void onLocationChangedImpl(Location arg0, boolean internal) {
    onLocationChangedImpl(arg0, internal, null);
  }

  private void onLocationChangedImpl(
      Location arg0, boolean internal, GpsFixFilter.Result fixResult) {
    int persistedType = mLocationType;
    if ((!mTimeFromGpsPoints && fixResult == null) || internal) {
      long elapsedNs = SystemClock.elapsedRealtimeNanos();
      arg0.setElapsedRealtimeNanos(elapsedNs);
      long timeMs = System.currentTimeMillis();
      arg0.setTime(timeMs);
    }

    Integer hrValue = getCurrentHRValueElapsed(arg0.getElapsedRealtimeNanos(), MAX_CURRENT_AGE);
    Double eleValue = getCurrentElevation();
    Float cadValue = getCurrentCadence();
    Float temperatureValue = getCurrentTemperature();
    Float pressureValue = getCurrentPressure();

    if (mLastLocation != null) {
      double distDiff =
          fixResult == null ? arg0.distanceTo(mLastLocation) : fixResult.getDistanceMeters();
      long timeDiffNanos =
          (arg0.getElapsedRealtimeNanos() - mLastLocation.getElapsedRealtimeNanos());
      long activeTimeDiffNanos =
          fixResult != null && fixResult.startsAfterGap() ? 0 : timeDiffNanos;

      float speed = arg0.getSpeed();
      if (fixResult != null && fixResult.startsNewSegment()) {
        speed = 0.0f;
      } else if (fixResult != null && fixResult.hasDerivedSpeed()) {
        speed = (float) fixResult.getSpeedMetersPerSecond();
      } else if (fixResult == null
          && (!arg0.hasSpeed() || speed == 0.0f || mCurrentSpeedFromGpsPoints)) {
        // at least the emulator can return 0 speed with hasSpeed()
        speed =
            (timeDiffNanos == 0)
                ? speed
                : (float) (distDiff * 1000 * NANO_IN_MILLI / (float) timeDiffNanos);
      }

      if (!internal && timeDiffNanos > 0 && (fixResult == null || !fixResult.startsNewSegment())) {
        if (fixResult != null) {
          mCurrentSpeed = gpsCurrentSpeedFilter.onFix(fixResult, arg0.getElapsedRealtimeNanos());
        } else {
          // Low pass filter, maintain also when paused
          final float alpha = 0.4f;
          mCurrentSpeed = speed * alpha + (1 - alpha) * mCurrentSpeed;
        }
      }

      if (internal || state.get() == TrackerState.STARTED) {
        if (!internal) {
          if (fixResult == null || !fixResult.startsAfterGap()) {
            mElapsedTimeNanos += activeTimeDiffNanos;
          }
          mElapsedDistance += distDiff;
        }
        if (hrValue != null && hrValue > 0) {
          mHeartbeats += (hrValue * activeTimeDiffNanos) / (double) (60 * 1000 * NANO_IN_MILLI);
          mHeartbeatNanos += activeTimeDiffNanos; // TODO handle loss of HRM connection
          mMaxHR = Math.max(hrValue, mMaxHR);
        }
      }
    }

    if (internal || state.get() == TrackerState.STARTED) {
      mDBWriter.onLocationChanged(
          arg0,
          eleValue,
          getTimeMs(),
          mElapsedDistance,
          hrValue,
          cadValue,
          temperatureValue,
          pressureValue);

      switch (mLocationType) {
        case DB.LOCATION.TYPE_START:
        case DB.LOCATION.TYPE_RESUME:
          setNextLocationType(DB.LOCATION.TYPE_GPS);
          break;
        case DB.LOCATION.TYPE_GPS:
          break;
        case DB.LOCATION.TYPE_PAUSE:
          break;
        case DB.LOCATION.TYPE_END:
          if (!internal && BuildConfig.DEBUG) {
            throw new AssertionError();
          }
          break;
      }
      liveLog(mLocationType);

      List<TrackerLocationObserver> observers;
      synchronized (locationObservers) {
        observers = new ArrayList<>(locationObservers);
      }
      for (TrackerLocationObserver observer : observers) {
        observer.onAcceptedLocation(
            new Location(arg0),
            persistedType,
            mElapsedDistance,
            mElapsedTimeNanos,
            hrValue,
            eleValue);
      }

      notificationStateManager.displayNotificationState(activityOngoingState);
    }

    if (!internal) {
      mLastLocation = arg0;
      if (state.get() == TrackerState.STARTED) {
        mLastLocationStarted = arg0;
      }
    }
  }

  private void queueActiveCheckpoint() {
    if (checkpointStore == null || workout == null || mActivityId <= 0) return;
    TrackerState currentState = state.get();
    if (currentState != TrackerState.STARTED && currentState != TrackerState.PAUSED) return;
    try {
      StringWriter workoutDefinition = new StringWriter();
      WorkoutSerializer.writeJSON(workoutDefinition, workout);
      ActiveWorkoutCheckpoint checkpoint =
          new ActiveWorkoutCheckpoint(
              mActivityId,
              currentState,
              mElapsedTimeNanos,
              mElapsedDistance,
              mHeartbeats,
              Math.round(mHeartbeatNanos),
              mMaxHR,
              workoutDefinition.toString(),
              workout.toRuntimeCheckpoint().toString(),
              mLapId,
              mStartedAtEpochMillis,
              SystemClock.elapsedRealtimeNanos(),
              workout.getSport(),
              workout.getWorkoutType(),
              workout.getMute(),
              trackerGPS.isWithoutGps());
      checkpointExecutor.execute(
          () -> {
            try {
              checkpointStore.save(checkpoint);
            } catch (IOException exception) {
              Log.e("Tracker", "Unable to persist active workout checkpoint", exception);
            }
          });
    } catch (JSONException | IOException | IllegalArgumentException exception) {
      Log.e("Tracker", "Unable to create active workout checkpoint", exception);
    }
  }

  private void clearActiveCheckpoint() {
    if (checkpointStore == null) return;
    try {
      checkpointExecutor.execute(checkpointStore::clear);
    } catch (RuntimeException exception) {
      Log.e("Tracker", "Unable to clear active workout checkpoint", exception);
    }
  }

  private boolean clearActiveCheckpointBeforeCompletion() {
    if (checkpointStore == null) return false;
    try {
      Future<?> clearTask = checkpointExecutor.submit(checkpointStore::clear);
      clearTask.get();
      return true;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      Log.e("Tracker", "Interrupted while clearing active workout checkpoint", exception);
      return false;
    } catch (ExecutionException | RuntimeException exception) {
      Log.e("Tracker", "Unable to clear active workout checkpoint", exception);
      return false;
    }
  }

  private void maybeRestoreActiveWorkout(boolean startForegroundService) {
    if (recoveryStarted || workout != null || state.get() != TrackerState.INIT) return;
    recoveryStarted = true;
    ActiveWorkoutCheckpoint checkpoint = checkpointStore == null ? null : checkpointStore.load();
    if (checkpoint == null) return;
    if (!isRecoverableActivity(checkpoint.getActivityId())) {
      clearActiveCheckpoint();
      return;
    }

    try {
      Workout restored =
          WorkoutSerializer.readJSON(new StringReader(checkpoint.getWorkoutDefinitionJson()));
      restored.sport = checkpoint.getSport();
      restored.setWorkoutType(checkpoint.getWorkoutType());
      restored.setMute(checkpoint.isMute());
      WorkoutBuilder.prepareForRecovery(this, restored, checkpoint.isMute());
      restored.setTracker(this);
      restored.onInit(restored);
      if (!restored.restoreRuntimeCheckpoint(new JSONObject(checkpoint.getWorkoutRuntimeJson()))) {
        Log.e("Tracker", "Workout runtime checkpoint does not match its definition");
        clearActiveCheckpoint();
        return;
      }
      if (restored.isPaused() != (checkpoint.getTrackerState() == TrackerState.PAUSED)) {
        Log.e("Tracker", "Tracker and workout pause states do not match in checkpoint");
        clearActiveCheckpoint();
        return;
      }

      workout = restored;
      mActivityId = checkpoint.getActivityId();
      mLapId = checkpoint.getLapId();
      mStartedAtEpochMillis = checkpoint.getStartedAtEpochMillis();
      mElapsedTimeNanos = checkpoint.getElapsedTimeNanos();
      mElapsedDistance = checkpoint.getDistanceMeters();
      mHeartbeats = checkpoint.getHeartbeats();
      mHeartbeatNanos = checkpoint.getHeartbeatNanos();
      mMaxHR = checkpoint.getMaxHeartRate();
      trackerGPS.setWithoutGps(checkpoint.isWithoutGps());
      restorePersistedLocationState(checkpoint);

      boolean clockReset =
          SystemClock.elapsedRealtimeNanos() < checkpoint.getCheckpointElapsedRealtimeNanos();
      if (clockReset && checkpoint.getTrackerState() == TrackerState.STARTED) {
        workout.pauseForRecovery();
        pendingRecoveryCheckpoint = checkpoint;
      } else {
        pendingRecoveryCheckpoint = checkpoint;
      }
      activityOngoingState = new OngoingState(new Formatter(this), workout, this);
      if (startForegroundService) {
        ContextCompat.startForegroundService(
            this, new Intent(this, Tracker.class).setAction(ACTION_ACTIVE_WORKOUT));
      }
      notificationStateManager.displayNotificationState(activityOngoingState);
      setup();
      if (state.get() == TrackerState.INITIALIZED) connect();
    } catch (JSONException | RuntimeException exception) {
      Log.e("Tracker", "Unable to restore active workout checkpoint", exception);
      workout = null;
      pendingRecoveryCheckpoint = null;
      clearActiveCheckpoint();
    }
  }

  private boolean isRecoverableActivity(long activityId) {
    if (mDB == null) return false;
    try (Cursor cursor =
        mDB.query(
            DB.ACTIVITY.TABLE,
            new String[] {DB.ACTIVITY.DELETED},
            "_id = ?",
            new String[] {Long.toString(activityId)},
            null,
            null,
            null)) {
      return isRecoverableActivity(cursor);
    } catch (IllegalStateException exception) {
      Log.e("Tracker", "Unable to validate active workout activity", exception);
      return false;
    }
  }

  static boolean isRecoverableActivity(Cursor cursor) {
    return cursor != null && cursor.moveToFirst() && cursor.getInt(0) == 0;
  }

  private void restorePersistedLocationState(ActiveWorkoutCheckpoint checkpoint) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    Resources resources = getResources();
    boolean logGpxAccuracy =
        preferences.getBoolean(resources.getString(R.string.pref_log_gpx_accuracy), false);
    mTimeFromGpsPoints =
        preferences.getBoolean(resources.getString(R.string.pref_time_from_gps_points), false);
    mCurrentSpeedFromGpsPoints =
        preferences.getBoolean(resources.getString(R.string.pref_speed_from_gps_points), false);

    ContentValues key = new ContentValues();
    key.put(DB.LOCATION.ACTIVITY, mActivityId);
    key.put(DB.LOCATION.LAP, 0);
    key.put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_START);
    mLastLocation = null;
    mLastLocationStarted = null;
    mLocationType = DB.LOCATION.TYPE_START;

    try (Cursor cursor =
        mDB.query(
            DB.LOCATION.TABLE,
            new String[] {
              DB.LOCATION.LATITUDE,
              DB.LOCATION.LONGITUDE,
              DB.LOCATION.TIME,
              DB.LOCATION.ELAPSED,
              DB.LOCATION.DISTANCE,
              DB.LOCATION.ACCURANCY,
              DB.LOCATION.LAP
            },
            DB.LOCATION.ACTIVITY + " = ?",
            new String[] {Long.toString(mActivityId)},
            null,
            null,
            "_id DESC",
            "1")) {
      if (cursor.moveToFirst()) {
        Location location = new Location("Relven recovered track");
        location.setLatitude(cursor.getDouble(0));
        location.setLongitude(cursor.getDouble(1));
        location.setTime(cursor.getLong(2));
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        if (!cursor.isNull(5)) location.setAccuracy((float) cursor.getDouble(5));
        mLastLocation = location;
        if (checkpoint.getTrackerState() == TrackerState.STARTED) {
          mLastLocationStarted = new Location(location);
        }
        if (!cursor.isNull(3)) {
          mElapsedTimeNanos =
              Math.max(mElapsedTimeNanos, Math.round(cursor.getDouble(3) * NANO_IN_MILLI));
        }
        if (!cursor.isNull(4)) mElapsedDistance = Math.max(mElapsedDistance, cursor.getDouble(4));
        key.put(
            DB.LOCATION.LAP,
            resolveRecoveryLapNumber(mDB, mActivityId, checkpoint.getLapId(), cursor.getInt(6)));
        key.put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_RESUME);
        mLocationType = DB.LOCATION.TYPE_RESUME;
      }
    } catch (IllegalStateException exception) {
      Log.e("Tracker", "Unable to restore route checkpoint", exception);
    }
    mDBWriter = new PersistentGpsLoggerListener(mDB, DB.LOCATION.TABLE, key, logGpxAccuracy);
    gpsFixFilter.reset();
    gpsCurrentSpeedFilter.reset();
    mCurrentSpeed = 0.0;
  }

  static int resolveRecoveryLapNumber(
      SQLiteDatabase database, long activityId, long lapId, int persistedLocationLap) {
    if (database == null || activityId <= 0 || lapId <= 0) return persistedLocationLap;
    try (Cursor cursor =
        database.query(
            DB.LAP.TABLE,
            new String[] {DB.LAP.LAP},
            "_id = ? AND " + DB.LAP.ACTIVITY + " = ?",
            new String[] {Long.toString(lapId), Long.toString(activityId)},
            null,
            null,
            null,
            "1")) {
      return cursor.moveToFirst() ? cursor.getInt(0) : persistedLocationLap;
    } catch (IllegalStateException exception) {
      Log.e("Tracker", "Unable to restore current lap number", exception);
      return persistedLocationLap;
    }
  }

  private void restoreActiveWorkoutAfterConnect() {
    ActiveWorkoutCheckpoint checkpoint = pendingRecoveryCheckpoint;
    if (checkpoint == null || workout == null || mDBWriter == null) return;
    wakeLock(true);
    SyncManager liveSyncManager = new SyncManager(getApplicationContext());
    liveSyncManager.loadLiveLoggers(liveLoggers);
    liveSyncManager.close();
    doBind();
    TrackerState recoveredState = workout.isPaused() ? TrackerState.PAUSED : TrackerState.STARTED;
    activityCompletionGate.beginActivity();
    state.set(recoveredState);
    if (components.getResultCode(TrackerWear.NAME) == TrackerComponent.ResultCode.RESULT_OK
        && !liveLoggers.contains(trackerWear)) liveLoggers.add(trackerWear);
    activityOngoingState = new OngoingState(new Formatter(this), workout, this);
    notificationStateManager.displayNotificationState(activityOngoingState);
    if (mLocationType == DB.LOCATION.TYPE_RESUME) {
      setNextLocationType(DB.LOCATION.TYPE_RESUME);
    }
    pendingRecoveryCheckpoint = null;
    handler.removeCallbacks(checkpointRunnable);
    handler.post(checkpointRunnable);
    queueActiveCheckpoint();

    components.onStart();
    if (recoveredState == TrackerState.PAUSED) components.onPause();
  }

  private void liveLog(int type) {
    for (WorkoutObserver l : liveLoggers) {
      l.workoutEvent(workout, type);
    }
  }

  @Override
  public void onProviderDisabled(@NonNull String arg0) {}

  @Override
  public void onProviderEnabled(@NonNull String arg0) {}

  // Before Android Q
  @Override
  @SuppressWarnings("deprecation")
  public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}

  public TrackerState getState() {
    return state.get();
  }

  public void registerTrackerStateListener(ValueModel.ChangeListener<TrackerState> listener) {
    state.registerChangeListener(listener);
  }

  public void unregisterTrackerStateListener(ValueModel.ChangeListener<TrackerState> listener) {
    state.unregisterChangeListener(listener);
  }

  // Service interface stuff...
  public class LocalBinder extends Binder {
    public Tracker getService() {
      return Tracker.this;
    }
  }

  private final IBinder mBinder = new LocalBinder();

  @Override
  public IBinder onBind(Intent intent) {
    maybeRestoreActiveWorkout(/* startForegroundService= */ true);
    return mBinder;
  }

  @SuppressLint("Wakelock")
  private void wakeLock(boolean get) {
    if (mWakeLock != null) {
      if (mWakeLock.isHeld()) {
        mWakeLock.release();
      }
      mWakeLock = null;
    }
    if (get) {
      PowerManager pm = ContextCompat.getSystemService(this, PowerManager.class);
      mWakeLock =
          Objects.requireNonNull(pm)
              .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RunnerUp:wakeLock");
      if (mWakeLock != null) {
        // Set a timeout, this is before activity is started
        mWakeLock.acquire(300000);
      }
    }
  }

  public boolean isComponentConfigured(String name) {
    switch (getState()) {
      case INIT: // before onInit we don't know, so say no
      case CLEANUP: // when cleaning, say no
      case ERROR: // on error, say no
        return false;
      case INITIALIZING:
        // If we're initializing...say no
        if (components.getResultCode(name) == TrackerComponent.ResultCode.RESULT_PENDING)
          return false;
      case INITIALIZED:
      case CONNECTING:
      case CONNECTED:
      case STARTED:
      case PAUSED:
      case STOPPED:
        // check component
        break;
    }

    switch (components.getResultCode(name)) {
      case RESULT_OK:
      case RESULT_PENDING:
        return true;
      case RESULT_NOT_SUPPORTED:
      case RESULT_NOT_ENABLED:
      case RESULT_ERROR:
      case RESULT_ERROR_FATAL:
        return false;
    }
    return false;
  }

  public boolean isComponentConnected(String name) {
    TrackerComponent component = components.getComponent(name);
    return (component != null) && component.isConnected();
  }

  public HRProvider getHRProvider() {
    return (trackerHRM.getHrProvider());
  }

  public void setHrDebug(TextView tv) {
    if (trackerHRM != null) {
      trackerHRM.setDebugLog(tv);
    }
  }

  private Integer getCurrentHRValueElapsed(long now, long maxAge) {
    HRProvider hrProvider = trackerHRM.getHrProvider();
    if ((hrProvider == null) || !hrProvider.isConnected()) return null;

    // now is elapsed nanosec, no sensor adjust needed
    if (now > hrProvider.getHRValueElapsedRealtime() + maxAge * NANO_IN_MILLI) return null;

    return hrProvider.getHRValue();
  }

  private Integer getCurrentHRValue(long now, long maxAge) {
    HRProvider hrProvider = trackerHRM.getHrProvider();
    if ((hrProvider == null) || !hrProvider.isConnected()) return null;

    if (now > hrProvider.getHRValueTimestamp() + maxAge) return null;

    return hrProvider.getHRValue();
  }

  public Integer getCurrentHRValue() {
    return getCurrentHRValueElapsed(SystemClock.elapsedRealtimeNanos(), MAX_CURRENT_AGE);
  }

  public double getHeartbeats() {
    return mHeartbeats;
  }

  public Integer getCurrentBatteryLevel() {
    HRProvider hrProvider = trackerHRM.getHrProvider();
    if (hrProvider == null) return HRProvider.BATTERY_LEVEL_UNAVAILABLE;
    return hrProvider.getBatteryLevel();
  }

  public Float getCurrentCadence() {
    return trackerCadence.getValue();
  }

  public Float getCurrentTemperature() {
    return trackerTemperature.getValue();
  }

  public Float getCurrentPressure() {
    return trackerPressure.getValue();
  }

  public Double getCurrentElevation() {
    return trackerElevation.getValue();
  }

  // Tracker for current speed
  public Double getCurrentSpeed() {
    if (mLastLocation == null) {
      return null;
    }
    if ((SystemClock.elapsedRealtimeNanos() - mLastLocation.getElapsedRealtimeNanos())
        > MAX_CURRENT_AGE * NANO_IN_MILLI) {
      return null;
    }
    return mCurrentSpeed;
  }

  public Workout getWorkout() {
    return workout;
  }
}
