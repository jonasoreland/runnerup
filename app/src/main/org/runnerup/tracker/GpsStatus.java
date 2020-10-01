/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import org.runnerup.util.TickListener;

import java.util.Objects;

/**
 *
 * This is a helper class that is used to determine when the GPS status is good
 * enough (isFixed())
 *
 */


public class GpsStatus implements LocationListener,
        android.location.GpsStatus.Listener {

    private static final int HIST_LEN = 3;

    private boolean mIsFixed = false;
    private final Context context;
    private final Location[] mHistory;
    private LocationManager locationManager = null;
    private TickListener listener = null;

    /**
     * If we get a location with accurancy <= mFixAccurancy mFixed => true
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final float mFixAccurancy = 10;

    /**
     * If we get fixed satellites >= mFixSatellites mFixed => true
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final int mFixSatellites = 2;

    /**
     * If we get location updates with time difference <= mFixTime mFixed =>
     * true
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final int mFixTime = 3;

    private int mKnownSatellites = 0;
    private int mUsedInLastFixSatellites = 0;

    public GpsStatus(Context ctx) {
        this.context = ctx;
        mHistory = new Location[HIST_LEN];
    }

    public void start(TickListener listener) {
        clear(true);
        this.listener = listener;
        if (ContextCompat.checkSelfPermission(this.context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            try {
                Objects.requireNonNull(lm).requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            } catch (Exception ex) {
                lm = null;
            }
            if (lm != null) {
                locationManager = lm;
                locationManager.addGpsStatusListener(this);
            }
        }
    }

    public void stop(TickListener listener) {
        this.listener = null;
        if (locationManager != null) {
            locationManager.removeGpsStatusListener(this);

            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ex) {
                //Ignore if user turn off GPS
            }
        }
        locationManager = null;
    }

    @Override
    public void onLocationChanged(Location location) {
        System.arraycopy(mHistory, 0, mHistory, 1, HIST_LEN - 1);
        mHistory[0] = location;
        if (location.hasAccuracy() && location.getAccuracy() < mFixAccurancy) {
            mIsFixed = true;
        } else if (mHistory[1] != null
                && (location.getTime() - mHistory[1].getTime()) <= (1000 * mFixTime)) {
            mIsFixed = true;
        } else if (mKnownSatellites >= mFixSatellites) {
            mIsFixed = true;
        }
        if (listener != null)
            listener.onTick();
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (provider.equalsIgnoreCase("gps")) {
            clear(true);
            if (listener != null)
                listener.onTick();
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (provider.equalsIgnoreCase("gps")) {
            clear(false);
            if (listener != null)
                listener.onTick();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (provider.equalsIgnoreCase("gps")) {
            if (status == LocationProvider.OUT_OF_SERVICE
                    || status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                clear(true);
            }
            if (listener != null)
                listener.onTick();
        }
    }

    @Override
    public void onGpsStatusChanged(int event) {
        if (locationManager == null)
            return;

        android.location.GpsStatus gpsStatus;
        try {
            gpsStatus = locationManager.getGpsStatus(null);
        } catch (SecurityException ex) {
            gpsStatus = null;
        }

        if (gpsStatus == null)
            return;

        int cnt0 = 0, cnt1 = 0;
        Iterable<GpsSatellite> list = gpsStatus.getSatellites();
        for (GpsSatellite satellite : list) {
            cnt0++;
            if (satellite.usedInFix()) {
                cnt1++;
            }
        }
        mKnownSatellites = cnt0;
        mUsedInLastFixSatellites = cnt1;
        if (listener != null)
            listener.onTick();
    }

    private void clear(boolean resetIsFixed) {
        if (resetIsFixed) {
            mIsFixed = false;
        }
        mKnownSatellites = 0;
        mUsedInLastFixSatellites = 0;
        for (int i = 0; i < HIST_LEN; i++)
            mHistory[i] = null;
    }

    public boolean isLogging() {
        return locationManager != null;
    }

    public boolean isFixed() {
        return mIsFixed;
    }

    public int getSatellitesAvailable() {
        return mKnownSatellites;
    }

    public int getSatellitesFixed() {
        return mUsedInLastFixSatellites;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isEnabled() {
        LocationManager lm = (LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);
        return Objects.requireNonNull(lm).isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
}
