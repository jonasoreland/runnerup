package org.runnerup.tracker.component;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.location.Location;
import androidx.core.location.LocationCompat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TrackerElevationTest {
  @Test
  public void gpsElevationRequiresReportedVerticalAccuracyWithinFiveMeters() {
    Location location = new Location("gps");
    location.setAltitude(-117.0);
    LocationCompat.setVerticalAccuracyMeters(location, 5.0f);

    assertTrue(TrackerElevation.isReliableGpsAltitude(location));

    LocationCompat.setVerticalAccuracyMeters(location, 5.1f);
    assertFalse(TrackerElevation.isReliableGpsAltitude(location));
  }

  @Test
  public void gpsElevationWithoutVerticalAccuracyIsUnavailable() {
    Location location = new Location("gps");
    location.setAltitude(42.0);

    assertFalse(TrackerElevation.isReliableGpsAltitude(location));
  }

  @Test
  public void missingAltitudeIsUnavailableEvenWhenVerticalAccuracyExists() {
    Location location = new Location("gps");
    LocationCompat.setVerticalAccuracyMeters(location, 2.0f);

    assertFalse(TrackerElevation.isReliableGpsAltitude(location));
  }
}
