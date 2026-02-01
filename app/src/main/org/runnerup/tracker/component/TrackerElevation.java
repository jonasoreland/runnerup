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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.core.location.LocationCompat;
import androidx.core.location.altitude.AltitudeConverterCompat;
import androidx.preference.PreferenceManager;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.runnerup.R;
import org.runnerup.tracker.Tracker;

public class TrackerElevation extends DefaultTrackerComponent implements SensorEventListener {

  private static final String NAME = "Elevation";
  private final Tracker tracker;
  private final TrackerGPS trackerGPS;
  private final TrackerPressure trackerPressure;
  private Double mPressureOffset = null;
  private Double mAverageGpsElevation = null;
  private long minEleAverageCutoffTime = Long.MAX_VALUE;
  private AltitudeConverterWrapper mAltitudeConverter = null;
  private boolean isStarted;

  public TrackerElevation(Tracker tracker, TrackerGPS trackerGPS, TrackerPressure trackerPressure) {
    this.tracker = tracker;
    this.trackerGPS = trackerGPS;
    this.trackerPressure = trackerPressure;
  }

  @Override
  public String getName() {
    return NAME;
  }

  public Double getValue() {
    Location lastLocation = tracker.getLastKnownLocation();
    if (lastLocation == null) {
      return null;
    }

    double val;
    Float pressure = tracker.getCurrentPressure();
    // pressure only ignore pressure if in mock mode
    if (pressure != null && !lastLocation.isFromMockProvider()) {
      // Pressure available - use it for elevation
      float pressureElevation =
          SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure);
      if (mPressureOffset == null) {
        double mslOffset =
            (mAverageGpsElevation != null)
                ? (mAverageGpsElevation - pressureElevation)
                : 0D;
        // Apply correction to mean-sea-level
        Double offset = mAltitudeConverter == null ? 0 : mAltitudeConverter.getOffset(lastLocation);
        if (offset != null) {
          mslOffset = mslOffset - offset;
          // "Lock" the offset (can be unlocked in onLocationChanged)
          mPressureOffset = mslOffset;
        }
      }
      val = pressureElevation + (mPressureOffset != null ? mPressureOffset : 0D);
    } else if (AltitudeConverterWrapper.hasMslAltitude(lastLocation)) {
      // note that msl offset is still applied in mock mode (use "raw" data when testing)
      val = LocationCompat.getMslAltitudeMeters(lastLocation);
    } else {
      val = lastLocation.getAltitude();
      if (mAltitudeConverter != null) {
        Double offset = mAltitudeConverter.getOffset(lastLocation);
        if (offset != null) {
          val -= offset;
        }
      }
    }
    return val;
  }

  public void onLocationChanged(android.location.Location arg0) {
    if (arg0.hasAltitude()
        && (!isStarted || mPressureOffset == null || arg0.getTime() < minEleAverageCutoffTime)) {
      // If mPressureOffset is not "used" yet or shortly after first GPS, update the average
      final int minElevationStabilizeTime = 60;
      if (minEleAverageCutoffTime == Long.MAX_VALUE) {
        minEleAverageCutoffTime = arg0.getTime() + minElevationStabilizeTime * 1000;
      }
      double ele = arg0.getAltitude();
      if (mAverageGpsElevation == null) {
        mAverageGpsElevation = ele;
      } else {
        // low pass filter to align relative quickly
        final float alpha = 0.3f;
        mAverageGpsElevation = mAverageGpsElevation * alpha + (1 - alpha) * ele;
      }
      if (mAltitudeConverter != null && !LocationCompat.hasMslAltitude(arg0)) {
        mAltitudeConverter.calcOffset(arg0);
      }
      // Recalculate offset when needed
      mPressureOffset = null;
    }
  }

  @Override
  public void onSensorChanged(SensorEvent event) {}

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}

  /** Called by Tracker during initialization */
  @Override
  public ResultCode onInit(Callback callback, Context context) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    boolean altitudeAdjust =
        prefs.getBoolean(context.getString(R.string.pref_altitude_adjust), true);
    if (altitudeAdjust) {
       mAltitudeConverter = new AltitudeConverterWrapper(context);
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

  /** Called by Tracker when workout starts */
  @Override
  public void onStart() {
    isStarted = true;
  }

  /** Called by Tracker when workout is paused */
  @Override
  public void onPause() {
    isStarted = false;
    minEleAverageCutoffTime = Long.MAX_VALUE;
    mPressureOffset = null;
  }

  /** Called by Tracker when workout is resumed */
  @Override
  public void onResume() {
    isStarted = true;
  }

  /** Called by Tracker when workout is complete */
  @Override
  public void onComplete(boolean discarded) {
    isStarted = false;
    minEleAverageCutoffTime = Long.MAX_VALUE;
    mPressureOffset = null;
    mAverageGpsElevation = null;
  }

  /** Called by tracked after workout has ended */
  @Override
  public ResultCode onEnd(Callback callback, Context context) {
    isStarted = false;
    minEleAverageCutoffTime = Long.MAX_VALUE;
    mPressureOffset = null;
    mAverageGpsElevation = null;
    return ResultCode.RESULT_OK;
  }

  /**
   * Convert GPS elevation to mean-sea-level elevation wrapper for AltitudeConverterCompat to handle
   * asynchronous loading and offset calculation.
   */
  private static class AltitudeConverterWrapper {
    // Keep the interval reasonable to avoid constant background work
    private static final long CALCULATION_INTERVAL_MS = 1000; // 2 minutes
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Context context;

    @GuardedBy("this")
    private Double mLastOffset = null;

    @GuardedBy("this")
    private long mLastCalculationTime = 0;

    public AltitudeConverterWrapper(Context context) {
      this.context = context;
    }

    // mAltitudeConverter is needed also if SDK > 34 (Android 14),
    // all devices do not calculate
    public static boolean hasMslAltitude(Location location) {
      return location != null && LocationCompat.hasMslAltitude(location);
    }

    /** Gets the last known offset without blocking. Triggers a new calculation if needed. */
    synchronized Double getOffset(Location location) {
      if (hasMslAltitude(location)) {
        calcOffset(location);
      } else {
        long currentTime = System.currentTimeMillis();
        if (location != null && (currentTime - mLastCalculationTime > CALCULATION_INTERVAL_MS)) {
          mLastCalculationTime = currentTime;
          calcOffset(location);
        }
      }
      return mLastOffset;
    }

    /** Initiates a calculation of the last known offset without blocking if needed. */
    synchronized void calcOffset(Location location) {
      if (location == null) {
        return;
      }

      if (hasMslAltitude(location)) {
        double newOffset = location.getAltitude() - LocationCompat.getMslAltitudeMeters(location);
        synchronized (AltitudeConverterWrapper.this) {
          mLastOffset = newOffset;
        }
      } else {
        mExecutor.execute(
            () -> {
              try {
                // Create a copy to avoid race conditions if the original location is modified
                Location locationCopy = new Location(location);
                double altitude = locationCopy.getAltitude();

                AltitudeConverterCompat.addMslAltitudeToLocation(context, locationCopy);
                double newOffset = altitude - LocationCompat.getMslAltitudeMeters(locationCopy);
                synchronized (AltitudeConverterWrapper.this) {
                  mLastOffset = newOffset;
                }
              } catch (IOException e) {
                Log.w(NAME, "Failed to compute MSL altitude offset: " + e.getMessage());
              }
            });
      }
    }
  }
}
