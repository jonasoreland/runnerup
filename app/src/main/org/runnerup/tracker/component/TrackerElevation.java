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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import android.util.Log;

import org.matthiaszimmermann.location.egm96.Geoid;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.tracker.Tracker;

import java.io.IOException;

public class TrackerElevation extends DefaultTrackerComponent implements SensorEventListener {

    private static final String NAME = "Elevation";

    @Override
    public String getName() {
        return NAME;
    }

    private final Tracker tracker;
    private final TrackerGPS trackerGPS;
    private final TrackerPressure trackerPressure;

    private Double mElevationOffset = null;
    private Double mAverageGpsElevation = null;
    private long minEleAverageCutoffTime = Long.MAX_VALUE;
    private GeoidAdjust mGeoidAdjust = null;
    private boolean mAltitudeFromGpsAverage = true;
    private boolean isStarted;

    public TrackerElevation(Tracker tracker, TrackerGPS trackerGPS, TrackerPressure trackerPressure){
        this.tracker = tracker;
        this.trackerGPS = trackerGPS;
        this.trackerPressure = trackerPressure;
    }

    public class GeoidAdjust {
        // "static" constructor in subclass
        GeoidAdjust GetAltitudeAdjust(Context context) {
            try {
                Geoid.init(context.getAssets().open("egm96-delta.dat"));
                return new GeoidAdjust();
            } catch (IOException e) {
                Log.e("TrackerElevation", "Altitude correction " + e);
            }
            return null;
        }

        Double getOffset(Tracker tracker) {
            return Geoid.getOffset(tracker.getLastKnownLocation().getLatitude(),
                    tracker.getLastKnownLocation().getLongitude());
        }
    }

    @SuppressLint("NewApi")
    public Double getValue() {
        Double val;
        Float pressure = tracker.getCurrentPressure();
        if (pressure != null && BuildConfig.VERSION_CODE >= 9) {
            //Pressure available - use it for elevation
            //TODO get real sea level pressure (online) or set offset from start/end
            //noinspection InlinedApi
            val = ((Float) SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)).doubleValue();
            if (mElevationOffset == null) {
                //"Lock" the offset (can be unlocked in onLocationChanged)
                if (mAltitudeFromGpsAverage && mAverageGpsElevation != null) {
                    //pressure is low-pass filtered, compare to low-pass GPS elevation
                    mElevationOffset = mAverageGpsElevation - val;
                } else {
                    mElevationOffset = 0D;
                }
                if (tracker.getLastKnownLocation() != null && mGeoidAdjust != null) {
                    mElevationOffset -= mGeoidAdjust.getOffset(tracker);
                }
            }
            val += mElevationOffset;
        } else if (tracker.getLastKnownLocation() != null && tracker.getLastKnownLocation().hasAltitude()) {
            val = tracker.getLastKnownLocation().getAltitude();
            if (mGeoidAdjust != null) {
                val -= mGeoidAdjust.getOffset(tracker);
            }
        } else {
            val = null;
        }
        return val;
    }

    public void onLocationChanged(android.location.Location arg0) {
        if (arg0.hasAltitude()
                && (mElevationOffset == null || arg0.getTime() < minEleAverageCutoffTime || !isStarted)) {
            //If mElevationOffset is not "used" yet or shortly after first GPS, update the average
            double ele = arg0.getAltitude();
            final int minElevationStabilizeTime = 60;
            if (minEleAverageCutoffTime == Long.MAX_VALUE) {
                minEleAverageCutoffTime = arg0.getTime() + minElevationStabilizeTime * 1000;
            }
            if (mAverageGpsElevation == null) {
                mAverageGpsElevation = ele;
            } else {
                final float alpha = 0.5f;
                mAverageGpsElevation = mAverageGpsElevation * alpha + (1 - alpha) * ele;
            }
            //Recalculate offset when needed
            mElevationOffset = null;
        }
    }


    @Override
    public void onSensorChanged(SensorEvent event) { }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /*
     * Sensor is available
     */
    //@SuppressWarnings("unused")
    //public static boolean isAvailable(@SuppressWarnings("UnusedParameters") final Context context) {
    //    //Need trackerGPS or trackerPressure to determine this
    //    //GPS is mandatory
    //    return true;
    //}

     /**
     * Called by Tracker during initialization
     */
    @Override
    public ResultCode onInit(Callback callback, Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean altitudeAdjust = prefs.getBoolean(context.getString(R.string.pref_altitude_adjust), true);
        mAltitudeFromGpsAverage = prefs.getBoolean(context.getString(org.runnerup.R.string.pref_pressure_elevation_gps_average), false);
        if (altitudeAdjust) {
            mGeoidAdjust = (new GeoidAdjust()).GetAltitudeAdjust(context);
        }
        return ResultCode.RESULT_OK;
    }

    @Override
    public ResultCode onConnecting(final Callback callback, final Context context) {
        ResultCode res;
        if (trackerGPS.isConnected() || trackerPressure.isConnected()) {
            res = ResultCode.RESULT_OK;
        } else {
            res = ResultCode.RESULT_NOT_SUPPORTED;
        }
        return res;
    }

    @Override
    public boolean isConnected() {
        return (trackerGPS.isConnected() || trackerPressure.isConnected());
    }

    @Override
    public void onConnected() {
    }

    /*
     * Called by Tracker before start
     *   Component shall populate bindValues
     *   with objects that will then be passed
     *   to workout
     */
    //public void onBind(HashMap<String, Object> bindValues) {
    //}

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
        minEleAverageCutoffTime = Long.MAX_VALUE;
        mElevationOffset = null;
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
        minEleAverageCutoffTime = Long.MAX_VALUE;
        mElevationOffset = null;
        mAverageGpsElevation = null;
    }

    /**
     * Called by tracked after workout has ended
     */
    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        isStarted = false;
        minEleAverageCutoffTime = Long.MAX_VALUE;
        mElevationOffset = null;
        mAverageGpsElevation = null;
        return ResultCode.RESULT_OK;
    }
}