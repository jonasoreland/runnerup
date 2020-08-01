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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.*;
import android.preference.PreferenceManager;

import java.util.Random;

public class TrackerTemperature extends DefaultTrackerComponent implements SensorEventListener {

    public static final String NAME = "Temperature";

    @Override
    public String getName() {
        return NAME;
    }

    private SensorManager sensorManager = null;

    private static boolean isMockSensor = false;

    private Float latestVal = null;
    //private long latestTime = -1;

    public Float getValue(){
        if (isMockSensor) {
            latestVal = (new Random()).nextFloat()*20+15;
            //latestTime = SystemClock.elapsedRealtime()*1000000;
        }
        return latestVal;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values != null && event.values.length > 0) {
            if (latestVal == null) {
                latestVal = event.values[0];
            } else {
                final float alpha = 0.3f;
                latestVal = event.values[0] * alpha + (1 - alpha) * latestVal;
                //latestTime = event.timestamp;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Sensor is available
     */
    public static boolean isAvailable(final Context context) {
        return ((new TrackerTemperature()).getSensor(context) != null) || isMockSensor;
    }

    @SuppressLint("ObsoleteSdkInt")
    private Sensor getSensor(final Context context) {
        Sensor sensor;
        if (sensorManager == null) {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        if (sensor == null) {
            //noinspection deprecation
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_TEMPERATURE);
        }

        if (sensor == null) {
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
        boolean enabled = prefs.getBoolean(context.getString(org.runnerup.R.string.pref_use_temperature_sensor), false);

        if (enabled) {
            Sensor sensor = getSensor(context);
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
                res = ResultCode.RESULT_OK;
            } else if (isMockSensor) {
                res = ResultCode.RESULT_OK;
            } else {
                res = ResultCode.RESULT_NOT_SUPPORTED;
            }
        } else {
            res = ResultCode.RESULT_NOT_SUPPORTED;
        }
        return res;
    }

    @Override
    public boolean isConnected() {
        return sensorManager != null || isMockSensor;
    }

    @Override
    public void onConnected() {
    }

    /**
     * Called by tracked after workout has ended
     */
    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (sensorManager != null) { sensorManager.unregisterListener(this); }
        sensorManager = null;
        isMockSensor = false;
        return ResultCode.RESULT_OK;
    }
}