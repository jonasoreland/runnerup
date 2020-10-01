/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.preference.PreferenceManager;

import androidx.core.content.ContextCompat;

import org.runnerup.R;
import org.runnerup.tracker.GpsStatus;
import org.runnerup.tracker.Tracker;
import org.runnerup.util.TickListener;

import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;


public class TrackerGPS extends DefaultTrackerComponent implements TickListener {

    private boolean mWithoutGps = false;
    private int frequency_ms = 0;
    private Location mLastLocation;
    private final Tracker tracker;

    private static final String NAME = "GPS";
    private GpsStatus mGpsStatus;
    private Callback mConnectCallback;

    @Override
    public String getName() {
        return NAME;
    }

    public TrackerGPS(Tracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public ResultCode onInit(final Callback callback, Context context) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return ResultCode.RESULT_NOT_SUPPORTED;
            }
            if (lm.getProvider(LocationManager.GPS_PROVIDER) == null) {
                return ResultCode.RESULT_NOT_SUPPORTED;
            }
        } catch (Exception ex) {
            return ResultCode.RESULT_ERROR;
        }
        return ResultCode.RESULT_OK;
    }

    @Override
    public ResultCode onConnecting(final Callback callback, Context context) {
        if (ContextCompat.checkSelfPermission(this.tracker,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            mWithoutGps = true;
        }
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            frequency_ms = Integer.parseInt(preferences.getString(context.getString(
                    R.string.pref_pollInterval), "1000"));
            if (!mWithoutGps) {
                String frequency_meters = preferences.getString(context.getString(
                        R.string.pref_pollDistance), "0");
                lm.requestLocationUpdates(GPS_PROVIDER,
                        frequency_ms,
                        Integer.parseInt(frequency_meters),
                        tracker);
                mGpsStatus = new GpsStatus(context);
                mGpsStatus.start(this);
                mConnectCallback = callback;
                return ResultCode.RESULT_PENDING;
            } else {
                String[] list = {
                        GPS_PROVIDER,
                        NETWORK_PROVIDER,
                        PASSIVE_PROVIDER };
                mLastLocation = null;
                for (String s : list) {
                    Location tmp = lm.getLastKnownLocation(s);
                    if (mLastLocation == null || tmp.getTime() > mLastLocation.getTime()) {
                        mLastLocation = tmp;
                    }
                }
                if (mLastLocation != null) {
                    mLastLocation.removeSpeed();
                    mLastLocation.removeAltitude();
                    mLastLocation.removeAccuracy();
                    mLastLocation.removeBearing();
                }
                gpsLessLocationProvider.run();
                return ResultCode.RESULT_OK;
            }


        } catch (Exception ex) {
            return ResultCode.RESULT_ERROR;
        }
    }

    @Override
    public boolean isConnected() {
        return (mWithoutGps) ||
                (mGpsStatus != null) && mGpsStatus.isFixed();
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (!mWithoutGps) {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            try {
                lm.removeUpdates(tracker);
            } catch (SecurityException ex) {
                ex.printStackTrace();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (mGpsStatus != null) {
                mGpsStatus.stop(this);
            }
            mGpsStatus = null;
            mConnectCallback = null;
        }
        return ResultCode.RESULT_OK;
    }

    private final Runnable gpsLessLocationProvider = new Runnable() {

        Location location = null;
        final Handler handler = new Handler();

        @Override
        public void run() {
            if (location == null) {
                location = new Location(mLastLocation);
                mLastLocation = null;
            }
            switch (tracker.getState()) {
                case INIT:
                case CLEANUP:
                case ERROR:
                    /* end loop be returning directly here */
                    return;
                case INITIALIZING:
                case INITIALIZED:
                case STARTED:
                case PAUSED:
                    /* continue looping */
                    break;
            }
            tracker.onLocationChanged(location);
            handler.postDelayed(this, frequency_ms);
        }
    };

    @Override
    public void onTick() {
        if (mGpsStatus == null)
            return;

        if (!mGpsStatus.isFixed())
            return;

        if (mConnectCallback == null)
            return;

        Callback tmp = mConnectCallback;

        mConnectCallback = null;
        mGpsStatus.stop(this);
        //note: Don't reset mGpsStatus, it's used for isConnected()

        tmp.run(this, ResultCode.RESULT_OK);
    }
}
