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

import org.runnerup.common.util.Constants;
import org.runnerup.workout.Workout;

import java.util.HashMap;
import java.util.Random;

public class TrackerCadence extends DefaultTrackerComponent implements SensorEventListener {

    public static final String NAME = "Cadence";

    @Override
    public String getName() {
        return NAME;
    }

    private SensorManager mSensorManager = null;

    //For debug builds, use random if sensor is unavailable
    private static boolean isMockSensor = false;

    private boolean isSportEnabled = true;
    private Float mPrevVal = null;
    private long mPrevTime = -1;
    private Float mCurrentCadence = null;
    final int cutOffTime = 3;

    public Float getValue() {
        if (!isSportEnabled) {
            return null;
        }
        if (isMockSensor) {
            return (new Random()).nextFloat() * 120;
        }

        if (mCurrentCadence == null) {
            return null;
        }

        // It can take seconds between sensor updates
        // Cut-off at 3s corresponds to 60/2/3 => 10 rpm (or 20 steps per minute)
        final long nanoSec = 1000000000L;
        long now;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            now = SystemClock.elapsedRealtimeNanos();
        } else {
            now = SystemClock.elapsedRealtime() * nanoSec / 1000;
        }
        long timeDiff = now - mPrevTime;
        Float res = mCurrentCadence;
        if (timeDiff > cutOffTime * nanoSec) {
            mCurrentCadence = null;
            res = 0.0f;
        }

        return res;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values == null || event.values.length < 1) {
            mCurrentCadence = null;
            return;
        }

        float latestVal = event.values[0];
        long latestTime = event.timestamp;
        long timeDiff = latestTime - mPrevTime;
        final long nanoSec = 1000000000L;

        if (timeDiff > cutOffTime * nanoSec || mPrevTime < 0 || mPrevVal == null || latestVal <= mPrevVal) {
            mCurrentCadence = null;
        } else {
            float val = (latestVal - mPrevVal) / 2 * 60 * nanoSec / timeDiff;
            if (val > 300) {
                // ignore this reading, use previous point next time
                return;
            }

            if (mCurrentCadence == null) {
                mCurrentCadence = val;
            } else {
                //Low pass filter
                final float alpha = 0.4f;
                mCurrentCadence = val * alpha + (1 - alpha) * mCurrentCadence;
            }
        }
        mPrevTime = latestTime;
        mPrevVal = latestVal;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        mCurrentCadence = null;
    }

    /**
     * Sensor is available
     */
    public static boolean isAvailable(final Context context) {
        return ((new TrackerCadence()).getSensor(context) != null) || isMockSensor;
    }

    private Sensor getSensor(final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return null;
        }

        if (mSensorManager == null) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }
        //noinspection InlinedApi
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor == null) {
            mSensorManager = null;

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            isMockSensor = prefs.getBoolean(context.getString(org.runnerup.R.string.pref_bt_mock), false);
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
        boolean enabled = prefs.getBoolean(context.getString(org.runnerup.R.string.pref_use_cadence_step_sensor), true);

        if (!enabled) {
            res = ResultCode.RESULT_NOT_ENABLED;
        } else {
            Sensor sensor = getSensor(context);
            if (sensor != null) {
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
                res = ResultCode.RESULT_OK;
            } else if (isMockSensor) {
                res = ResultCode.RESULT_OK;
            } else {
                res = ResultCode.RESULT_NOT_SUPPORTED;
            }
        }
        return res;
    }

    @Override
    public boolean isConnected() {
        return mSensorManager != null || isMockSensor;
    }

    /**
     * Called by Tracker before start
     *   Component shall populate bindValues
     *   with objects that will then be passed
     *   to workout
     */
    public void onBind(HashMap<String, Object> bindValues) {
        int sport = (int) bindValues.get(Workout.KEY_SPORT_TYPE);
        if (sport == Constants.DB.ACTIVITY.SPORT_BIKING) {
            //Not used, disconnect sensor so nothing is returned
            isSportEnabled = false;
            mPrevVal = null;
            mSensorManager = null;
            isMockSensor = false;
        } else {
            isSportEnabled = true;
        }
    }

    /**
     * Called by tracked after workout has ended
     */
    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (mSensorManager != null) { mSensorManager.unregisterListener(this); }
        mSensorManager = null;
        isMockSensor = false;

        return ResultCode.RESULT_OK;
    }
}