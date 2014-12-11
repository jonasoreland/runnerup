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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;

import org.runnerup.R;
import org.runnerup.tracker.GpsTracker;

import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;

/**
 * Created by jonas on 12/11/14.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
class TrackerGPS extends DefaultTrackerComponent {

    private final boolean mWithoutGps = false;
    private int frequency_ms = 0;
    private Location mLastLocation;
    private final GpsTracker tracker;

    public TrackerGPS(GpsTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public ResultCode onInit(final Callback callback, Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        frequency_ms = Integer.valueOf(preferences.getString(context.getString(
                R.string.pref_pollInterval), "500"));
        if (mWithoutGps == false) {
            String frequency_meters = preferences.getString(context.getString(
                    R.string.pref_pollDistance), "5");
            lm.requestLocationUpdates(GPS_PROVIDER,
                    frequency_ms,
                    Integer.valueOf(frequency_meters),
                    tracker);
        } else {
            String list[] = {GPS_PROVIDER,
                    NETWORK_PROVIDER,
                    PASSIVE_PROVIDER};
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
        }
        return ResultCode.RESULT_OK;
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (mWithoutGps == false) {
            lm.removeUpdates(tracker);
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
            location.setTime(System.currentTimeMillis());
            if (tracker.isLogging()) {
                tracker.onLocationChanged(location);
                handler.postDelayed(this, frequency_ms);
            }
        }
    };
}
