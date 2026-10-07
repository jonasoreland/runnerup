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
import android.location.Location;
import android.location.LocationManager;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.location.GnssStatusCompat;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;
import java.util.Objects;
import org.runnerup.util.TickListener;

/**
 * This is a helper class that is used to determine when the GPS status is good enough (isFixed())
 */
public class GpsStatus implements LocationListenerCompat {

  private static final int HIST_LEN = 3;

  private boolean mIsFixed = false;
  private final Context context;
  private final Location[] mHistory;
  private LocationManager locationManager = null;
  private TickListener listener = null;

  private int mKnownSatellites = 0;
  private int mUsedInLastFixSatellites = 0;
  private GnssStatusCompat.Callback mGnssStatusCallback;

  public GpsStatus(Context ctx) {
    this.context = ctx;
    mHistory = new Location[HIST_LEN];
  }

  public void start(TickListener listener) {
    clear(true);
    this.listener = listener;
    if (ContextCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      return;
    }
    LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    try {
      LocationManagerCompat.requestLocationUpdates(
          lm,
          LocationManager.GPS_PROVIDER,
          new LocationRequestCompat.Builder(0)
              .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
              .build(),
          ContextCompat.getMainExecutor(context),
          this);

    } catch (Exception ex) {
      return;
    }
    locationManager = lm;
    mGnssStatusCallback =
        new GnssStatusCompat.Callback() {
          public void onSatelliteStatusChanged(@NonNull GnssStatusCompat status) {
            mKnownSatellites = status.getSatelliteCount();
            mUsedInLastFixSatellites = 0;
            for (int i = 0; i < mKnownSatellites; i++) {
              if (status.usedInFix(i)) {
                mUsedInLastFixSatellites++;
              }
            }
          }
        };

    LocationManagerCompat.registerGnssStatusCallback(
        locationManager, ContextCompat.getMainExecutor(context), mGnssStatusCallback);
  }

  public void stop(TickListener listener) {
    this.listener = null;
    if (locationManager != null) {
      LocationManagerCompat.unregisterGnssStatusCallback(locationManager, mGnssStatusCallback);

      try {
        LocationManagerCompat.removeUpdates(locationManager, this);
      } catch (SecurityException ex) {
        // Ignore if user turn off GPS
      }
    }
    locationManager = null;
  }

  public boolean isStarted() {
    return listener != null;
  }

  public void onLocationChanged(Location location) {
    // If we get a location with accuracy <= mFixAccurancy mFixed => true
    final float mFixAccurancy = 10;
    // If we get fixed satellites >= mFixSatellites mFixed => true
    final int mFixSatellites = 2;
    // If we get location updates with time difference <= mFixTime mFixed => true
    final int mFixTime = 3;

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
    if (listener != null) listener.onTick();
  }

  @Override
  public void onProviderDisabled(String provider) {
    if (provider.equalsIgnoreCase("gps")) {
      clear(true);
      if (listener != null) listener.onTick();
    }
  }

  @Override
  public void onProviderEnabled(String provider) {
    if (provider.equalsIgnoreCase("gps")) {
      clear(false);
      if (listener != null) listener.onTick();
    }
  }

  private void clear(boolean resetIsFixed) {
    if (resetIsFixed) {
      mIsFixed = false;
    }
    mKnownSatellites = 0;
    mUsedInLastFixSatellites = 0;
    for (int i = 0; i < HIST_LEN; i++) mHistory[i] = null;
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
    LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    return Objects.requireNonNull(lm).isProviderEnabled(LocationManager.GPS_PROVIDER);
  }
}
