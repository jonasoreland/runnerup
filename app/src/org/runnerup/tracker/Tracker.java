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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

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
import org.runnerup.tracker.component.TrackerComponent;
import org.runnerup.tracker.component.TrackerComponentCollection;
import org.runnerup.tracker.component.TrackerElevation;
import org.runnerup.tracker.component.TrackerGPS;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.tracker.component.TrackerPebble;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerTemperature;
import org.runnerup.tracker.component.TrackerPressure;
import org.runnerup.tracker.component.TrackerWear;
import org.runnerup.tracker.filter.PersistentGpsLoggerListener;
import org.runnerup.util.Formatter;
import org.runnerup.util.HRZones;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * GpsTracker - this class tracks Location updates
 *
 * TODO: rename this class into ActivityTracker and factor out Gps stuff into own class
 *       that should be handled much like hrm (e.g as a sensor among others)
 *
 * @author jonas.oreland@gmail.com
 */


public class Tracker extends android.app.Service implements
        LocationListener, Constants {
    private static final int MAX_HR_AGE = 3000; // 3s

    private final Handler handler = new Handler();

    private final TrackerComponentCollection components = new TrackerComponentCollection();
    //Some trackers may select separate sensors depending on sport, handled in onBind()
    private final TrackerGPS trackerGPS = (TrackerGPS) components.addComponent(new TrackerGPS(this));
    private final TrackerHRM trackerHRM = (TrackerHRM) components.addComponent(new TrackerHRM());
    private final TrackerCadence trackerCadence = (TrackerCadence) components.addComponent(new TrackerCadence());
    private final TrackerTemperature trackerTemperature = (TrackerTemperature) components.addComponent(new TrackerTemperature());
    private final TrackerPressure trackerPressure = (TrackerPressure) components.addComponent(new TrackerPressure());
    private final TrackerElevation trackerElevation = (TrackerElevation) components.addComponent(new TrackerElevation(this, trackerGPS, trackerPressure));
    private TrackerWear trackerWear; // created if version is sufficient
    private TrackerPebble trackerPebble; // created if version is sufficient

    private boolean mBug23937Checked = false;
    private long mBug23937Delta = 0;
    private long mSystemToGpsDiffTimeMillis = 0;

    private long mLapId = 0;
    private long mActivityId = 0;
    private long mElapsedTimeMillis = 0;
    private double mElapsedDistance = 0;
    private double mHeartbeats = 0;
    private double mHeartbeatMillis = 0; // since we might loose HRM connectivity...
    private long mMaxHR = 0;

    private TrackerState nextState;
    private final ValueModel<TrackerState> state = new ValueModel<>(TrackerState.INIT);
    private int mLocationType = DB.LOCATION.TYPE_START;

    // Last location given by LocationManager
    private Location mLastLocation = null;
    //Second to last location - to get speed
    private Location mLast2Location = null;

    // Last location given by LocationManager when in state STARTED
    private Location mActivityLastLocation = null;

    private SQLiteDatabase mDB = null;
    private PersistentGpsLoggerListener mDBWriter = null;
    private PowerManager.WakeLock mWakeLock = null;
    private final List<WorkoutObserver> liveLoggers = new ArrayList<>();

    private Workout workout = null;
    private NotificationStateManager notificationStateManager;
    private NotificationState activityOngoingState;

    @Override
    public void onCreate() {
        mDB = DBHelper.getWritableDatabase(this);
        notificationStateManager = new NotificationStateManager(
                new ForegroundNotificationDisplayStrategy(this));

        wakeLock(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // >= 4.3
            trackerWear = (TrackerWear) components.addComponent(new TrackerWear(this));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // >= 4.1
            trackerPebble = (TrackerPebble) components.addComponent(new TrackerPebble(this));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mDB != null) {
            DBHelper.closeDB(mDB);
            mDB = null;
        }

        reset();
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

        TrackerComponent.ResultCode result = components.onInit(onInitCallback,
                getApplicationContext());
        if (result != TrackerComponent.ResultCode.RESULT_PENDING) {
            onInitCallback.run(components, result);
        }
    }

    private final TrackerComponent.Callback onInitCallback = new TrackerComponent.Callback() {
        @Override
        public void run(TrackerComponent component, TrackerComponent.ResultCode resultCode) {
            if (resultCode == TrackerComponent.ResultCode.RESULT_ERROR_FATAL) {
                state.set(TrackerState.ERROR);
            } else {
                state.set(TrackerState.INITIALIZED);
            }

            Log.e(getClass().getName(), "state.set(" + getState() + ")");
            handleNextState();
        }
    };

    private void handleNextState() {
        if (nextState == null)
            return;

        // if last phase ended in error,
        // don't continue with a new
        if (state.get() == TrackerState.ERROR)
            return;

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
        Log.e(getClass().getName(), "Tracker.connect() - state: " + state.get());
        switch (state.get()) {
            case INIT:
                setup();
            case INITIALIZING:
            case CLEANUP:
                nextState = TrackerState.CONNECTED;
                Log.e(getClass().getName(), " => nextState: " + nextState);
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

        TrackerComponent.ResultCode result = components.onConnecting(onConnectCallback,
                getApplicationContext());
        if (result != TrackerComponent.ResultCode.RESULT_PENDING) {
            onConnectCallback.run(components, result);
        }
    }

    private final TrackerComponent.Callback onConnectCallback = new TrackerComponent.Callback() {
        @Override
        public void run(TrackerComponent component, TrackerComponent.ResultCode resultCode) {
            if (resultCode == TrackerComponent.ResultCode.RESULT_ERROR_FATAL) {
                state.set(TrackerState.ERROR);
            } else if (state.get() == TrackerState.CONNECTING) {
                state.set(TrackerState.CONNECTED);
                /* now we're connected */
                components.onConnected();
            }
        }
    };

    private long getBug23937Delta() {
        return mBug23937Delta;
    }

    @SuppressWarnings("UnusedReturnValue")
    private long createActivity(int sport) {
        Resources res = getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean logGpxAccuracy = prefs.getBoolean(res.getString(R.string.pref_log_gpx_accuracy), false);

        //Create an Activity instance
        ContentValues tmp = new ContentValues();
        tmp.put(DB.ACTIVITY.SPORT, sport);
        mActivityId = mDB.insert(DB.ACTIVITY.TABLE, "nullColumnHack", tmp);

        tmp.clear();
        tmp.put(DB.LOCATION.ACTIVITY, mActivityId);
        tmp.put(DB.LOCATION.LAP, 0); // always start with lap 0
        mDBWriter = new PersistentGpsLoggerListener(mDB, DB.LOCATION.TABLE, tmp, logGpxAccuracy);
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

        if (components.getResultCode(TrackerPebble.NAME) == TrackerComponent.ResultCode.RESULT_OK)
            liveLoggers.add(trackerPebble);

        // create the DB activity
        createActivity(workout.getSport());

        // do bindings
        doBind();

        // Let workout do initializations
        workout.onInit(workout);

        // Let components know we're starting
        components.onStart();

        mElapsedTimeMillis = 0;
        mElapsedDistance = 0;
        mHeartbeats = 0;
        mHeartbeatMillis = 0;
        mMaxHR = 0;
        // TODO: check if mLastLocation is recent enough
        mActivityLastLocation = null;

        // New location update will be tagged with START
        setNextLocationType(DB.LOCATION.TYPE_START);

        state.set(TrackerState.STARTED);

        activityOngoingState = new OngoingState(new Formatter(this), workout, this);

        // And finally let workout know that we started
        workout.onStart(Scope.ACTIVITY, this.workout);
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
        String key[] = {
                Long.toString(mLapId)
        };
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
        if (mActivityLastLocation != null) {
            // This saves mLastLocation as a PAUSE location
            internalOnLocationChanged(mActivityLastLocation);
        }

        saveActivity();
        components.onPause();
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
        if (mActivityLastLocation != null) {
            // This saves mLastLocation as a PAUSE location
            internalOnLocationChanged(mActivityLastLocation);
        }

        saveActivity();
        components.onPause(); // TODO add new callback for this
    }

    private void internalOnLocationChanged(Location arg0) {
        long save = mBug23937Delta;
        mBug23937Delta = 0;
        onLocationChangedImpl(arg0, true); // always save this location to db
        mBug23937Delta = save;
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

        // TODO: check is mLastLocation is recent enough
        mActivityLastLocation = mLastLocation;
        state.set(TrackerState.STARTED);
        setNextLocationType(DB.LOCATION.TYPE_RESUME);
        if (mActivityLastLocation != null) {
            // save last know location as resume location
            internalOnLocationChanged(mActivityLastLocation);
        }
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
        if (res != TrackerComponent.ResultCode.RESULT_PENDING)
            onEndCallback.run(components, res);
    }

    private final TrackerComponent.Callback onEndCallback = new TrackerComponent.Callback() {
        @Override
        public void run(TrackerComponent component, TrackerComponent.ResultCode resultCode) {
            if (resultCode == TrackerComponent.ResultCode.RESULT_ERROR_FATAL) {
                state.set(TrackerState.ERROR);
            } else {
                state.set(TrackerState.INIT);
            }

            handleNextState();
        }
    };

    public void completeActivity(boolean save) {
        if (BuildConfig.DEBUG &&
                state.get() != TrackerState.PAUSED &&
                state.get() != TrackerState.STOPPED) {
            throw new AssertionError();
        }

        setNextLocationType(DB.LOCATION.TYPE_END);
        if (mActivityLastLocation != null) {
            internalOnLocationChanged(mActivityLastLocation);
        }

        if (save) {
            saveActivity();
            liveLog(DB.LOCATION.TYPE_END);
        } else {
            ContentValues tmp = new ContentValues();
            tmp.put("deleted", 1);
            String key[] = {
                    Long.toString(mActivityId)
            };
            mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", key);
            liveLog(DB.LOCATION.TYPE_DISCARD);
        }
        components.onComplete(!save);
        notificationStateManager.cancelNotification();
        reset();
    }

    private void saveActivity() {
        ContentValues tmp = new ContentValues();
        if (mHeartbeatMillis > 0) {
            long avgHR = Math.round((60 * 1000 * mHeartbeats) / mHeartbeatMillis); // BPM
            tmp.put(Constants.DB.ACTIVITY.AVG_HR, avgHR);
        }
        if (mMaxHR > 0)
            tmp.put(Constants.DB.ACTIVITY.MAX_HR, mMaxHR);
        tmp.put(Constants.DB.ACTIVITY.DISTANCE, mElapsedDistance);
        tmp.put(Constants.DB.ACTIVITY.TIME, getTime()); // time should be updated last for conditionalRecompute
        if (TrackerPressure.isAvailable(this)) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean enabled = prefs.getBoolean(this.getString(org.runnerup.R.string.pref_use_pressure_sensor), false);
            if (enabled) {
                //Save information about barometer usage, used in uploads (like Strava)
                tmp.put(DB.ACTIVITY.META_DATA, DB.ACTIVITY.WITH_BAROMETER);
            }
        }

        String key[] = {
                Long.toString(mActivityId)
        };
        mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", key);
    }

    private void setNextLocationType(int newType) {
        ContentValues key = mDBWriter.getKey();
        key.put(DB.LOCATION.TYPE, newType);
        mDBWriter.setKey(key);
        mLocationType = newType;
    }

    public long getTime() {
        return mElapsedTimeMillis / 1000;
    }

    public long getTimeMs() {
        return mElapsedTimeMillis;
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

    @Override
    public void onLocationChanged(Location arg0) {
        //Elevation depends on GPS updates
        trackerElevation.onLocationChanged(arg0);
        onLocationChangedImpl(arg0, false);
    }

    private void onLocationChangedImpl(Location arg0, boolean internal) {
        if (!mBug23937Checked) {
            long now = System.currentTimeMillis();
            long gpsTime = arg0.getTime();
            // http://code.google.com/p/android/issues/detail?id=23937
            // Some GPS chipset reported that the time was a day off
            // The original Android issue is closed, probably a firmware issue
            // This check is used for a similar problem
            // System time is manually set, differs from GPS time
            // The GPS time stamp should normally not need to be changed,
            // but approx diff is needed to find if data is valid
            mSystemToGpsDiffTimeMillis = now - gpsTime;
            if (gpsTime > now + (24 * 3600 - 120) * 1000) {
                mBug23937Delta = mSystemToGpsDiffTimeMillis;
                mSystemToGpsDiffTimeMillis = 0;
            } else {
                mBug23937Delta = 0;
            }
            mBug23937Checked = true;
            Log.e(getClass().getName(), "Bug23937: gpsTime: " + gpsTime
                    + " (diff to system: " + mSystemToGpsDiffTimeMillis + ") => delta: " + mBug23937Delta);
        }
        if (mBug23937Delta != 0) {
            arg0.setTime(arg0.getTime() + mBug23937Delta);
        }

        if (internal || state.get() == TrackerState.STARTED) {
            Integer hrValue = getCurrentHRValue(arg0.getTime(), MAX_HR_AGE);
            Double eleValue = getCurrentElevation();
            Float cadValue = getCurrentCadence();
            Float temperatureValue = getCurrentTemperature();
            Float pressureValue = getCurrentPressure();
            if (mActivityLastLocation != null) {
                long timeDiff;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    timeDiff = (arg0.getElapsedRealtimeNanos() - mActivityLastLocation
                            .getElapsedRealtimeNanos()) / 1000000;
                } else {
                    timeDiff = arg0.getTime() - mActivityLastLocation.getTime();
                }
                double distDiff = arg0.distanceTo(mActivityLastLocation);
                if (timeDiff < 0) {
                    // time moved backward ??
                    Log.e(getClass().getName(), "lastTime:       " + mActivityLastLocation.getTime());
                    Log.e(getClass().getName(), "arg0.getTime(): " + arg0.getTime());
                    Log.e(getClass().getName(), " => delta time: " + timeDiff);
                    Log.e(getClass().getName(), " => delta dist: " + distDiff);
                    // TODO investigate if this is known...only seems to happen
                    // in emulator
                    timeDiff = 0;
                }
                mElapsedTimeMillis += timeDiff;
                mElapsedDistance += distDiff;
                if (hrValue != null) {
                    mHeartbeats += (hrValue * timeDiff) / (60 * 1000);
                    mHeartbeatMillis += timeDiff; // TODO handle loss of HRM connection
                    mMaxHR = Math.max(hrValue, mMaxHR);
                }
            }
            mActivityLastLocation = arg0;

            mDBWriter.onLocationChanged(arg0, eleValue, mElapsedTimeMillis, mElapsedDistance, hrValue, cadValue, temperatureValue, pressureValue);

            switch (mLocationType) {
                case DB.LOCATION.TYPE_START:
                case DB.LOCATION.TYPE_RESUME:
                    liveLog(mLocationType);
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

            notificationStateManager.displayNotificationState(activityOngoingState);
        }
        mLast2Location = mLastLocation;
        mLastLocation = arg0;
    }

    private void liveLog(int type) {
        for (WorkoutObserver l : liveLoggers) {
            l.workoutEvent(workout, type);
        }
    }

    @Override
    public void onProviderDisabled(String arg0) {
    }

    @Override
    public void onProviderEnabled(String arg0) {
    }

    @Override
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
    }

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
    public class LocalBinder extends android.os.Binder {
        public Tracker getService() {
            return Tracker.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
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
            PowerManager pm = (PowerManager) this
                    .getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "RunnerUp");
            if (mWakeLock != null) {
                //Set a timeout, this is before activity is started
                mWakeLock.acquire(300000);
            }
        }
    }

    public boolean isComponentConfigured(String name) {
        switch (getState()) {
            case INIT:    // before onInit we don't know, so say no
            case CLEANUP: // when cleaning, say no
            case ERROR:   // on error, say no
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
        return (component == null) && component.isConnected();
    }

    public HRProvider getHRProvider() {
        return (trackerHRM.getHrProvider());
    }

    private Integer getCurrentHRValue(long now, long maxAge) {
        HRProvider hrProvider = trackerHRM.getHrProvider();
        if (hrProvider == null ||
                now > hrProvider.getHRValueTimestamp() + maxAge)
            return null;

        return hrProvider.getHRValue();
    }

    public Integer getCurrentHRValue() {
        return getCurrentHRValue(System.currentTimeMillis() - mSystemToGpsDiffTimeMillis, 3000);
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

    public Double getCurrentSpeed() {
        return getCurrentSpeed(System.currentTimeMillis() - mSystemToGpsDiffTimeMillis, 3000);
    }

    private Double getCurrentSpeed(long now, long maxAge) {
        if (mLastLocation == null ||
                now > mLastLocation.getTime() + maxAge) {
            return null;
        }
        double speed = mLastLocation.getSpeed();
        if (!mLastLocation.hasSpeed() || speed == 0.0f) {
            //Some Android (at least emulators) do not implement getSpeed() (even if hasSpeed() is true)
            if (mLastLocation == null || mLast2Location == null ||
                    mLastLocation.getTime() <= mLast2Location.getTime()) {
                return null;
            }
            double d = mLastLocation.distanceTo(mLast2Location);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                speed = d * 1000000000 / (mLastLocation.getElapsedRealtimeNanos() -
                        mLast2Location.getElapsedRealtimeNanos());
            } else {
                speed = d * 1000 / (mLastLocation.getTime() - mLast2Location.getTime());
            }
        }
        return speed;
    }

    public double getHeartbeats() {
        return mHeartbeats;
    }

    public Integer getCurrentBatteryLevel() {
        HRProvider hrProvider = trackerHRM.getHrProvider();
        if (hrProvider == null)
            return null;
        return hrProvider.getBatteryLevel();
    }

    public Workout getWorkout() {
        return workout;
    }
}