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

package org.runnerup.gpstracker;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.LiveLogger;
import org.runnerup.export.UploadManager;
import org.runnerup.gpstracker.filter.PersistentGpsLoggerListener;
import org.runnerup.notification.ForegroundNotificationDisplayStrategy;
import org.runnerup.notification.GpsBoundState;
import org.runnerup.notification.NotificationState;
import org.runnerup.notification.NotificationStateManager;
import org.runnerup.notification.OngoingState;
import org.runnerup.hr.HRDeviceRef;
import org.runnerup.hr.HRManager;
import org.runnerup.hr.HRProvider;
import org.runnerup.hr.HRProvider.HRClient;
import org.runnerup.util.Constants;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Workout;

import java.util.ArrayList;
import java.util.List;

/**
 * GpsTracker - this class tracks Location updates
 * 
 * TODO: rename this class into ActivityTracker and factor out Gps stuff into own class
 *       that should be handled much like hrm (e.g as a sensor among others)
 * 
 * @author jonas.oreland@gmail.com
 */

@TargetApi(Build.VERSION_CODES.FROYO)
public class GpsTracker extends android.app.Service implements
        LocationListener, Constants, WorkoutProvider {
    public static final int MAX_HR_AGE = 3000; // 3s

    private Handler handler = new Handler();

    /**
     * Work-around for http://code.google.com/p/android/issues/detail?id=23937
     */
    boolean mBug23937Checked = false;
    long mBug23937Delta = 0;

    /**
	 *
	 */
    long mLapId = 0;
    long mActivityId = 0;
    double mElapsedTimeMillis = 0;
    double mElapsedDistance = 0;
    double mHeartbeats = 0;
    double mHeartbeatMillis = 0; // since we might loose HRM connectivity...
    long mMaxHR = 0;

    boolean mWithoutGps = false;

    enum State {
        INIT, LOGGING, STARTED, PAUSED,
        ERROR /* Failed to init GPS */
    }

    State state = State.INIT;
    int mLocationType = DB.LOCATION.TYPE_START;

    /**
     * Last location given by LocationManager
     */
    Location mLastLocation = null;

    /**
     * Last location given by LocationManager when in state STARTED
     */
    Location mActivityLastLocation = null;

    DBHelper mDBHelper = null;
    SQLiteDatabase mDB = null;
    PersistentGpsLoggerListener mDBWriter = null;
    PowerManager.WakeLock mWakeLock = null;
    List<LiveLogger> liveLoggers = new ArrayList<LiveLogger>();

    private Workout workout = null;

    private double mMinLiveLogDelayMillis = 5000;

    private double mElapsedTimeMillisSinceLiveLog = 0;
    private NotificationStateManager notificationStateManager;

    private NotificationState gpsTrackerBoundState;
    private NotificationState activityOngoingState;

    @Override
    public void onCreate() {
        mDBHelper = new DBHelper(this);
        mDB = mDBHelper.getWritableDatabase();
        notificationStateManager = new NotificationStateManager(new ForegroundNotificationDisplayStrategy(this));
        activityOngoingState = new OngoingState(new Formatter(this), this, this);
        gpsTrackerBoundState = new GpsBoundState(this);

        wakelock(false);
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
            mDB.close();
            mDB = null;
        }

        if (mDBHelper != null) {
            mDBHelper.close();
            mDBHelper = null;
        }

        stopLogging();
    }

    public void setWorkout(Workout w) {
        this.workout = w;
        w.setGpsTracker(this);
    }

    @Override
    public Workout getWorkout() {
        return this.workout;
    }

    public void startLogging() {
        assert (state == State.INIT);

        wakelock(true);
        // TODO add preference
        mMinLiveLogDelayMillis = PreferenceManager
                .getDefaultSharedPreferences(this).getInt(
                        getString(R.string.pref_min_livelog_delay_millis),
                        (int) mMinLiveLogDelayMillis);

        try {
            requestLocationUpdates();
            state = State.LOGGING;
        } catch (Exception ex) {
            state = State.ERROR;
        }

        startHRMonitor();

        UploadManager u = new UploadManager(this);
        u.loadLiveLoggers(liveLoggers);
        u.close();
    }

    private void requestLocationUpdates() {
        LocationManager lm = (LocationManager) this
                .getSystemService(LOCATION_SERVICE);
        if (mWithoutGps == false) {
            String frequency_ms = PreferenceManager.getDefaultSharedPreferences(
                    this).getString(getString(R.string.pref_pollInterval), "500");
            String frequency_meters = PreferenceManager
                    .getDefaultSharedPreferences(this).getString(
                            getString(R.string.pref_pollDistance), "5");
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    Integer.valueOf(frequency_ms),
                    Integer.valueOf(frequency_meters), this);
        } else {
            String list[] = { LocationManager.GPS_PROVIDER, 
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER };
            mLastLocation = null;
            for (String s : list) {
                Location tmp = lm.getLastKnownLocation(s);
                if (mLastLocation == null || tmp.getTime() > mLastLocation.getTime()) {
                    mLastLocation = tmp;
                }
            }
            mLastLocation.removeSpeed();
            mLastLocation.removeAltitude();
            mLastLocation.removeAccuracy();
            mLastLocation.removeBearing();
            gpsLessLocationProvider.run();
        }
    }

    Runnable gpsLessLocationProvider = new Runnable() {

        int frequency_ms = 0;
        Location location = null;
        
        @Override
        public void run() {
            if (location == null) {
                location = new Location(mLastLocation);
                mLastLocation = null;
                String frequency_ms_str = PreferenceManager.getDefaultSharedPreferences(
                        GpsTracker.this).getString(getString(R.string.pref_pollInterval), "500");
                frequency_ms = Integer.valueOf(frequency_ms_str);
            }
            location.setTime(System.currentTimeMillis());
            if (isLogging()) {
                onLocationChanged(location);
                handler.postDelayed(this, frequency_ms);
            }
        }
    };

    private void removeLocationUpdates() {
        if (mWithoutGps == false) {
            LocationManager lm = (LocationManager) this
                    .getSystemService(LOCATION_SERVICE);
            lm.removeUpdates(this);
        } else {
        }
    }
    
    public boolean isLogging() {
        switch (state) {
            case ERROR:
            case INIT:
                return false;
            case LOGGING:
            case STARTED:
            case PAUSED:
                return true;
        }
        return true;
    }

    public long getBug23937Delta() {
        return mBug23937Delta;
    }

    public long createActivity(int sport) {
        assert (state == State.INIT);
        /**
         * Create an Activity instance
         */
        ContentValues tmp = new ContentValues();
        tmp.put(DB.ACTIVITY.SPORT, sport);
        mActivityId = mDB.insert(DB.ACTIVITY.TABLE, "nullColumnHack", tmp);

        tmp.clear();
        tmp.put(DB.LOCATION.ACTIVITY, mActivityId);
        tmp.put(DB.LOCATION.LAP, 0); // always start with lap 0
        mDBWriter = new PersistentGpsLoggerListener(mDB, DB.LOCATION.TABLE, tmp);
        return mActivityId;
    }

    public void start() {
        assert (state == State.LOGGING);
        state = State.STARTED;
        mElapsedTimeMillis = 0;
        mElapsedDistance = 0;
        mHeartbeats = 0;
        mHeartbeatMillis = 0;
        mMaxHR = 0;
        // TODO: check if mLastLocation is recent enough
        mActivityLastLocation = null;
        setNextLocationType(DB.LOCATION.TYPE_START); // New location update will
                                                     // be tagged with START
    }

    public void startOrResume() {
        switch (state) {
            case INIT:
            case ERROR:
                assert (false);
                break;
            case LOGGING:
                start();
                break;
            case PAUSED:
                resume();
                break;
            case STARTED:
                break;
        }
    }

    public void setGpsTrackerBound() {
        notificationStateManager.displayNotificationState(gpsTrackerBoundState);
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

    public void stopOrPause() {
        switch (state) {
            case INIT:
            case ERROR:
            case LOGGING:
            case PAUSED:
                break;
            case STARTED:
                stop();
        }
    }

    private ContentValues createActivityRow() {
        ContentValues tmp = new ContentValues();
        tmp.put(Constants.DB.ACTIVITY.DISTANCE, mElapsedDistance);
        tmp.put(Constants.DB.ACTIVITY.TIME, getTime());
        if (mHeartbeatMillis > 0) {
            long avgHR = Math.round((60 * 1000 * mHeartbeats) / mHeartbeatMillis); // BPM
            tmp.put(Constants.DB.ACTIVITY.AVG_HR, avgHR);
        }
        if (mMaxHR > 0)
            tmp.put(Constants.DB.ACTIVITY.MAX_HR, mMaxHR);
        return tmp;
    }

    public void stop() {
        setNextLocationType(DB.LOCATION.TYPE_PAUSE);
        if (mActivityLastLocation != null) {
            /**
             * This saves mLastLocation as a PAUSE location
             */
            internalOnLocationChanged(mActivityLastLocation);
        }

        ContentValues tmp = createActivityRow();
        String key[] = {
            Long.toString(mActivityId)
        };
        mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", key);
        state = State.PAUSED;
    }

    private void internalOnLocationChanged(Location arg0) {
        long save = mBug23937Delta;
        mBug23937Delta = 0;
        onLocationChanged(arg0);
        mBug23937Delta = save;
    }

    public boolean isPaused() {
        return state == State.PAUSED;
    }

    public void resume() {
        assert (state == State.PAUSED);
        // TODO: check is mLastLocation is recent enough
        mActivityLastLocation = mLastLocation;
        state = State.STARTED;
        setNextLocationType(DB.LOCATION.TYPE_RESUME);
        if (mActivityLastLocation != null) {
            /**
             * save last know location as resume location
             */
            internalOnLocationChanged(mActivityLastLocation);
        }
    }

    public void stopLogging() {
        assert (state == State.PAUSED || state == State.LOGGING);
        wakelock(false);
        if (state != State.INIT) {
            removeLocationUpdates();
            state = State.INIT;
        }
        liveLoggers.clear();
        stopHRMonitor();
    }

    public void completeActivity(boolean save) {
        assert (state == State.PAUSED);

        setNextLocationType(DB.LOCATION.TYPE_END);
        if (mActivityLastLocation != null) {
            mDBWriter.onLocationChanged(mActivityLastLocation);
        }

        if (save) {
            ContentValues tmp = createActivityRow();
            String key[] = {
                Long.toString(mActivityId)
            };
            mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", key);
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
        notificationStateManager.cancelNotification();
        stopLogging();
    }

    void setNextLocationType(int newType) {
        ContentValues key = mDBWriter.getKey();
        key.put(DB.LOCATION.TYPE, newType);
        mDBWriter.setKey(key);
        mLocationType = newType;
    }

    public double getTime() {
        return mElapsedTimeMillis / 1000;
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

    public double getElapsedTimeMillis() {
        return mElapsedTimeMillis;
    }

    public Location getLastLocation() {
        return mLastLocation;
    }

    public double getElapsedDistance() {
        return mElapsedDistance;
    }

    @Override
    public void onLocationChanged(Location arg0) {
        long now = System.currentTimeMillis();
        if (mBug23937Checked == false) {
            long gpsTime = arg0.getTime();
            long utcTime = now;
            if (gpsTime > utcTime + 3 * 1000) {
                mBug23937Delta = utcTime - gpsTime;
            } else {
                mBug23937Delta = 0;
            }
            mBug23937Checked = true;
            System.err.println("Bug23937: gpsTime: " + gpsTime + " utcTime: " + utcTime
                    + " (diff: " + Math.abs(gpsTime - utcTime) + ") => delta: " + mBug23937Delta);
        }
        if (mBug23937Delta != 0) {
            arg0.setTime(arg0.getTime() + mBug23937Delta);
        }

        if (state == State.STARTED) {
            Integer hrValue = getCurrentHRValue(now, MAX_HR_AGE);
            if (mActivityLastLocation != null) {
                double timeDiff = (double) (arg0.getTime() - mActivityLastLocation
                        .getTime());
                double distDiff = arg0.distanceTo(mActivityLastLocation);
                if (timeDiff < 0) {
                    // time moved backward ??
                    System.err.println("lastTime:       " + mActivityLastLocation.getTime());
                    System.err.println("arg0.getTime(): " + arg0.getTime());
                    System.err.println(" => delta time: " + timeDiff);
                    System.err.println(" => delta dist: " + distDiff);
                    // TODO investigate if this is known...only seems to happen
                    // in emulator
                    timeDiff = 0;
                }
                mElapsedTimeMillis += timeDiff;
                mElapsedDistance += distDiff;
                mElapsedTimeMillisSinceLiveLog += timeDiff;
                if (hrValue != null) {
                    mHeartbeats += (hrValue * timeDiff) / (60 * 1000);
                    mHeartbeatMillis += timeDiff; // TODO handle loss of HRM
                                                  // connection
                    mMaxHR = Math.max(hrValue, mMaxHR);
                }
            }
            mActivityLastLocation = arg0;

            mDBWriter.onLocationChanged(arg0, hrValue);

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
                    assert (false);
                    break;
            }
            liveLog(mLocationType);

            notificationStateManager.displayNotificationState(activityOngoingState);
        }
        mLastLocation = arg0;
    }

    private void liveLog(int type) {

        for (LiveLogger l : liveLoggers) {
            l.liveLog(this, type);
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

    /**
     * Service interface stuff...
     */
    public class LocalBinder extends android.os.Binder {
        public GpsTracker getService() {
            return GpsTracker.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void wakelock(boolean get) {
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
                mWakeLock.acquire();
            }
        }
    }

    HRProvider hrProvider = null;
    boolean btDisabled = true;

    private void startHRMonitor() {
        Resources res = getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null);
        final String btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider),
                null);
        final String btDeviceName = prefs.getString(res.getString(R.string.pref_bt_name), null);
        if (btAddress == null || btProviderName == null)
            return;

        btDisabled = true;

        hrProvider = HRManager.getHRProvider(this, btProviderName);
        if (hrProvider != null) {
            hrProvider.open(handler, new HRClient() {
                @Override
                public void onOpenResult(boolean ok) {
                    if (!ok) {
                        hrProvider = null;
                        return;
                    }
                    if (hrProvider == null) {
                        return;
                    }
                    hrProvider.connect(HRDeviceRef.create(btProviderName, btDeviceName, btAddress));
                }

                @Override
                public void onScanResult(HRDeviceRef device) {
                }

                @Override
                public void onConnectResult(boolean connectOK) {
                    if (connectOK) {
                        btDisabled = false;
                        Toast.makeText(GpsTracker.this, "Connected to HRM " + btDeviceName,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        btDisabled = true;
                        Toast.makeText(GpsTracker.this, "Failed to connect to HRM " + btDeviceName,
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onDisconnectResult(boolean disconnectOK) {
                }

                @Override
                public void onCloseResult(boolean closeOK) {
                }
            });
        }
    }

    private void stopHRMonitor() {
        if (hrProvider != null) {
            hrProvider.close();
            hrProvider = null;
        }
    }

    public boolean isHRConfigured() {
        if (hrProvider != null) {
            return true;
        }
        return false;
    }

    public boolean isHRConnected() {
        if (hrProvider == null)
            return false;

        return hrProvider.isConnected();
    }

    public Integer getCurrentHRValue(long now, long maxAge) {
        if (hrProvider == null || !hrProvider.isConnected())
            return null;

        if (now > hrProvider.getHRValueTimestamp() + maxAge)
            return null;

        return hrProvider.getHRValue();
    }

    public Integer getCurrentHRValue() {
        return getCurrentHRValue(System.currentTimeMillis(), 3000);
    }

    public Double getCurrentSpeed() {
        return getCurrentSpeed(System.currentTimeMillis(), 3000);
    }

    public Double getCurrentPace() {
        Double speed = getCurrentSpeed();
        if (speed == null)
            return null;
        if (speed == 0.0)
            return Double.MAX_VALUE;
        return 1000 / (speed * 60);
    }

    private Double getCurrentSpeed(long now, long maxAge) {
        if (mLastLocation == null)
            return null;
        if (!mLastLocation.hasSpeed())
            return null;
        if (now > mLastLocation.getTime() + maxAge)
            return null;
        return (double) mLastLocation.getSpeed();
    }

    public double getHeartbeats() {
        return mHeartbeats;
    }
}
