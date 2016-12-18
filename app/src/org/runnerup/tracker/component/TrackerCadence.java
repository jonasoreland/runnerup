/*
 * Copyright (C) 2016 gerhard.nospam@gmail.com
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

package org.runnerup.tracker.component;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.*;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import org.runnerup.BuildConfig;
import org.runnerup.common.util.Constants;

import java.util.HashMap;
import java.util.Random;

public class TrackerCadence extends DefaultTrackerComponent implements SensorEventListener {

    public static final String NAME = "Cadence";

    @Override
    public String getName() {
        return NAME;
    }

    private SensorManager sensorManager = null;

    //For debug builds, use random if sensor is unavailable
    private final static boolean testMode = BuildConfig.DEBUG;
    private static boolean isEmulating = false;

    private boolean isSportEnabled = true;
    //The sensor fires continuously, use the last available values (no smoothing)
    private boolean isStarted = true;
    private Float latestVal = null;
    private long latestTime = -1;
    private Float prevVal = null;
    private long prevTime = -1;
    private Float currentCadence = null;

    public Float getValue() {
        if (isEmulating) {
            if (latestVal == null) {latestVal = 0.0f;}
            //if GPS update is every second, this is 0-120 rpm
            latestVal += (int)((new Random()).nextFloat() * 4);
            latestTime = SystemClock.elapsedRealtime()*1000000;
        }
        final long noDataNs = 5000 * 1000000L;
        if (!isSportEnabled || latestTime < 0 || latestVal == null ||
            prevTime == latestTime && SystemClock.elapsedRealtime()*1000000 - latestTime < noDataNs ) {
            //No data in this point. Do not report 0 as the data just not is available yet but dont use last known as it may zero
            //report 0 after a grace time
            return null;
        }
        Float val;
        if (prevTime == latestTime || prevTime < 0 || prevVal == null) {
            //TODO Should the currentCadence be adjusted too?
            val = currentCadence;
        } else {
            val = 60 * (latestVal - prevVal) / 2 * 1000000000 / (latestTime - prevTime);
        }
        //"Consumed" values
        prevVal = latestVal;
        prevTime = latestTime;

        if (currentCadence == null) {
            currentCadence = val;
        } else {
            //Low pass filter
            final float alpha = 0.4f;
            currentCadence = val * alpha + (1 - alpha) * currentCadence;
        }

        return currentCadence;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values != null && event.values.length > 0) {
            if (!isStarted && (prevTime < 0 || event.timestamp - prevTime > 3000000000L)) {
                //one period to a few seconds before start so first getValue() after start/resume (may) have data
                prevTime = latestTime;
                prevVal = latestVal;
            }
            latestVal = event.values[0];
            latestTime = event.timestamp;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Sensor is available
     */
    public static boolean isAvailable(final Context context) {
        return ((new TrackerCadence()).getSensor(context) != null) || testMode;
    }

    private Sensor getSensor(final Context context) {
        Sensor sensor = null;
        if (Build.VERSION.SDK_INT >= 20) {
            if (sensorManager == null) {
                sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            }
            //noinspection InlinedApi
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (sensor == null) {
                sensorManager = null;
            }
        }
        if (testMode && sensor == null) {
            //No real sensor, emulate
            isEmulating = true;
        }
        return sensor;
    }

    /**
     * Called by Tracker during initialization
     */
    @Override
    public ResultCode onInit(Callback callback, Context context) {
         return ResultCode.RESULT_OK;
    }

    @Override
    public ResultCode onConnecting(final Callback callback, final Context context) {
        ResultCode res;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enabled = prefs.getBoolean(context.getString(org.runnerup.R.string.pref_use_cadence_step_sensor), false);

        if (!enabled) {
            res = ResultCode.RESULT_NOT_ENABLED;
        } else {
            Sensor sensor = getSensor(context);
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
                res = ResultCode.RESULT_OK;
            } else if (isEmulating) {
                res = ResultCode.RESULT_OK;
            } else {
                res = ResultCode.RESULT_NOT_SUPPORTED;
            }
        }
        return res;
    }

    @Override
    public boolean isConnected() {
        return sensorManager != null;
    }

    @Override
    public void onConnected() {
    }

    /**
     * Called by Tracker before start
     *   Component shall populate bindValues
     *   with objects that will then be passed
     *   to workout
     */
    public void onBind(HashMap<String, Object> bindValues) {
        int sport = (int) bindValues.get(Constants.DB.ACTIVITY.SPORT);
        if (sport == Constants.DB.ACTIVITY.SPORT_BIKING) {
            //Not used, disconnect sensor so nothing is returned
            isSportEnabled = false;
            latestVal = null;
        } else {
            isSportEnabled = true;
        }
    }

    /**
     * Called by Tracker when workout starts
     */
    @Override
    public void onStart() {
        isStarted = true;
    }

    /**
     * Called by Tracker when workout is paused
     */
    @Override
    public void onPause() {
        isStarted = false;
    }

    /**
     * Called by Tracker when workout is resumed
     */
    @Override
    public void onResume() {
        isStarted = true;
    }

    /**
     * Called by Tracker when workout is complete
     */
    @Override
    public void onComplete(boolean discarded) {
        isStarted = false;
    }

    /**
     * Called by tracked after workout has ended
     */
    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        isStarted = false;
        if (sensorManager != null) { sensorManager.unregisterListener(this); }
        sensorManager = null;
        isEmulating = false;
        return ResultCode.RESULT_OK;
    }
}