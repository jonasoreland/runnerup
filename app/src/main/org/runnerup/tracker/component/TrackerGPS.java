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

import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.text.TextUtils;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import org.runnerup.R;
import org.runnerup.tracker.GpsStatus;
import org.runnerup.tracker.Tracker;
import org.runnerup.util.TickListener;

public class TrackerGPS extends DefaultTrackerComponent implements TickListener {

  private boolean mWithoutGps = false;
  private int frequency_ms = 0;
  private Location mLastLocation;
  private final Tracker tracker;

  private static final String NAME = "GPS";
  private GpsStatus mGpsStatus;
  private Callback mConnectCallback;
  private LocationManager locationManager;

  @Override
  public String getName() {
    return NAME;
  }

  public TrackerGPS(Tracker tracker) {
    this.tracker = tracker;
  }

  @Override
  public ResultCode onInit(final Callback callback, Context context) {
    if (mWithoutGps) {
      return ResultCode.RESULT_OK;
    }
    try {
      LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
      if (lm == null) {
        return ResultCode.RESULT_NOT_SUPPORTED;
      }
      locationManager = lm;
      if (lm.getProvider(LocationManager.GPS_PROVIDER) == null) {
        return ResultCode.RESULT_NOT_SUPPORTED;
      }
    } catch (Exception ex) {
      return ResultCode.RESULT_ERROR;
    }
    return ResultCode.RESULT_OK;
  }

  public void setWithoutGps(boolean val) {
    if (mWithoutGps == val) {
      return;
    }
    mWithoutGps = val;
    if (mWithoutGps) {
      switch (tracker.getState()) {
        case INIT:
        case CLEANUP:
        case ERROR:
        case INITIALIZING:
        case INITIALIZED:
          return;
        default:
          break;
      }
      stopGps();
      onTick();
      gpsLessLocationProvider.run();
    }
  }

  private Integer parseAndFixInteger(
      SharedPreferences preferences, int resId, String def, Context context) {
    String s = preferences.getString(context.getString(resId), def);
    if (TextUtils.isEmpty(s)) {
      // Update the settings
      SharedPreferences.Editor prefedit = preferences.edit();
      prefedit.putString(context.getString(resId), def);
      prefedit.apply();
      s = def;
    }
    return Integer.parseInt(s);
  }

  static Location getLastKnownLocation(LocationManager lm) {
    String[] list = {GPS_PROVIDER, NETWORK_PROVIDER, PASSIVE_PROVIDER};
    Location lastLocation = null;
    for (String s : list) {
      Location tmp = lm.getLastKnownLocation(s);
      if (tmp == null) {
        continue;
      }
      if (lastLocation == null) {
        lastLocation = tmp;
      } else if (tmp.getTime() > lastLocation.getTime()) {
        lastLocation = tmp;
      }
    }
    if (lastLocation == null) {
      lastLocation = new Location("RunnerUp");
    } else {
      lastLocation.removeSpeed();
      lastLocation.removeAltitude();
      lastLocation.removeAccuracy();
      lastLocation.removeBearing();
    }
    lastLocation.setTime(System.currentTimeMillis());
    return lastLocation;
  }

  @Override
  public ResultCode onConnecting(final Callback callback, Context context) {
    if (mWithoutGps == false &&
        ContextCompat.checkSelfPermission(this.tracker, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      mWithoutGps = true;
    }

    try {
      var lm = locationManager;
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
      frequency_ms = parseAndFixInteger(preferences, R.string.pref_pollInterval, "1000", context);
      mLastLocation = getLastKnownLocation(lm);
      if (!mWithoutGps) {
        Integer frequency_meters =
            parseAndFixInteger(preferences, R.string.pref_pollDistance, "0", context);
        locationManager = lm;
        lm.requestLocationUpdates(GPS_PROVIDER, frequency_ms, frequency_meters, tracker);
        mGpsStatus = new GpsStatus(context);
        mGpsStatus.start(this);
        mConnectCallback = callback;
        return ResultCode.RESULT_PENDING;
      } else {
        gpsLessLocationProvider.run();
        return ResultCode.RESULT_OK;
      }

    } catch (Exception ex) {
      return ResultCode.RESULT_ERROR;
    }
  }

  @Override
  public boolean isConnected() {
    return (mWithoutGps) || (mGpsStatus != null) && mGpsStatus.isFixed();
  }

  private void stopGps() {
    if (locationManager != null) {
      try {
        locationManager.removeUpdates(tracker);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      locationManager = null;
    }

    if (mGpsStatus != null) {
      mGpsStatus.stop(this);
      mGpsStatus = null;
    }
  }

  @Override
  public ResultCode onEnd(Callback callback, Context context) {
    stopGps();
    mConnectCallback = null;
    mWithoutGps = false;
    return ResultCode.RESULT_OK;
  }

  private final Runnable gpsLessLocationProvider =
      new Runnable() {

        final Handler handler = new Handler();

        @Override
        public void run() {
          Location location = new Location(mLastLocation);
          location.setTime(System.currentTimeMillis());
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
    if (mWithoutGps == false) {
      if (mGpsStatus == null) {
        return;
      }

      if (!mGpsStatus.isFixed()) {
        return;
      }
    }

    if (mConnectCallback == null) {
      return;
    }

    Callback tmp = mConnectCallback;
    mConnectCallback = null;
    mGpsStatus.stop(this);
    // note: Don't reset mGpsStatus, it's used for isConnected()

    tmp.run(this, ResultCode.RESULT_OK);
  }
}
