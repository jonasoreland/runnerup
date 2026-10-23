package org.runnerup.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.location.Location;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class GpsCurrentSpeedFilterTest {
  private static final long SECOND = 1_000_000_000L;

  @Test
  public void stationaryFixesDoNotEraseTheLastMeasuredRunningPaceImmediately() {
    GpsFixFilter fixes = new GpsFixFilter();
    GpsCurrentSpeedFilter speed = new GpsCurrentSpeedFilter();

    Location start = location(0.0, 0.0, SECOND);
    assertEquals(0.0, speed.onFix(fixes.accept(start, SECOND), SECOND), 0.0);

    Location moved = location(0.00015, 0.0, 5 * SECOND);
    GpsFixFilter.Result movement = fixes.accept(moved, 5 * SECOND);
    assertTrue(movement.hasDerivedSpeed());
    double measuredSpeed = movement.getSpeedMetersPerSecond();
    assertEquals(measuredSpeed, speed.onFix(movement, 5 * SECOND), 0.001);

    GpsFixFilter.Result stationary = fixes.accept(location(0.00015, 0.0, 6 * SECOND), 6 * SECOND);
    assertTrue(stationary.isStationary());
    assertEquals(measuredSpeed, speed.onFix(stationary, 6 * SECOND), 0.001);
  }

  @Test
  public void speedReturnsToUnavailableAfterSustainedStationarity() {
    GpsFixFilter fixes = new GpsFixFilter();
    GpsCurrentSpeedFilter speed = new GpsCurrentSpeedFilter();

    Location start = location(0.0, 0.0, SECOND);
    speed.onFix(fixes.accept(start, SECOND), SECOND);
    GpsFixFilter.Result movement = fixes.accept(location(0.00015, 0.0, 5 * SECOND), 5 * SECOND);
    speed.onFix(movement, 5 * SECOND);

    GpsFixFilter.Result stationary = fixes.accept(location(0.00015, 0.0, 16 * SECOND), 16 * SECOND);
    assertTrue(stationary.isStationary());
    assertEquals(0.0, speed.onFix(stationary, 16 * SECOND), 0.0);
  }

  private static Location location(double longitude, double latitude, long elapsedNanos) {
    Location location = new Location("gps");
    location.setLongitude(longitude);
    location.setLatitude(latitude);
    location.setAccuracy(5.0f);
    location.setElapsedRealtimeNanos(elapsedNanos);
    return location;
  }
}
