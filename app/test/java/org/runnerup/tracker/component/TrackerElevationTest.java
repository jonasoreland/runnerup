package org.runnerup.tracker.component;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.location.Location;
import androidx.core.location.LocationCompat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.runnerup.tracker.GpsAltitudeQuality;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TrackerElevationTest {
  @Test
  public void gpsElevationRequiresReportedVerticalAccuracyWithinFiveMeters() {
    Location location = new Location("gps");
    location.setAltitude(-117.0);
    LocationCompat.setVerticalAccuracyMeters(location, 5.0f);

    assertTrue(GpsAltitudeQuality.isReliable(location));

    LocationCompat.setVerticalAccuracyMeters(location, 5.1f);
    assertFalse(GpsAltitudeQuality.isReliable(location));
  }

  @Test
  public void gpsElevationWithoutVerticalAccuracyIsUnavailable() {
    Location location = new Location("gps");
    location.setAltitude(42.0);

    assertFalse(GpsAltitudeQuality.isReliable(location));
  }

  @Test
  public void missingAltitudeIsUnavailableEvenWhenVerticalAccuracyExists() {
    Location location = new Location("gps");
    LocationCompat.setVerticalAccuracyMeters(location, 2.0f);

    assertFalse(GpsAltitudeQuality.isReliable(location));
  }

  @Test
  public void unreliableGpsAltitudeCannotCalibratePressureElevation() {
    Location location = new Location("gps");
    location.setAltitude(-117.0);
    LocationCompat.setVerticalAccuracyMeters(location, 7.0f);

    assertFalse(TrackerElevation.mayUseGpsAltitudeForPressureCalibration(location));
  }

  @Test
  public void reliableGpsAltitudeCanCalibratePressureElevation() {
    Location location = new Location("gps");
    location.setAltitude(42.0);
    LocationCompat.setVerticalAccuracyMeters(location, 3.0f);

    assertTrue(TrackerElevation.mayUseGpsAltitudeForPressureCalibration(location));
  }
}
