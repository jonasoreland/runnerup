package org.runnerup.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.location.Location;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class GpsFixFilterTest {
  private static final long NOW_NANOS = 1_000_000_000_000L;

  @Test
  public void accurateFirstFixCreatesAnAnchorWithoutInventingDistance() {
    GpsFixFilter filter = new GpsFixFilter();

    GpsFixFilter.Result result = filter.accept(fix(0.0, 0.0, 4f, NOW_NANOS), NOW_NANOS);

    assertTrue(result.isAccepted());
    assertTrue(result.startsNewSegment());
    assertFalse(result.startsAfterGap());
    assertEquals(0.0, result.getDistanceMeters(), 0.0);
    assertFalse(result.hasDerivedSpeed());
  }

  @Test
  public void rejectsStaleFutureInvalidAndNonMonotonicFixes() {
    GpsFixFilter filter = new GpsFixFilter();
    assertFalse(
        filter.accept(fix(0.0, 0.0, 4f, NOW_NANOS - 16_000_000_000L), NOW_NANOS).isAccepted());
    assertFalse(
        filter.accept(fix(0.0, 0.0, 4f, NOW_NANOS + 2_000_000_000L), NOW_NANOS).isAccepted());
    assertFalse(filter.accept(fix(91.0, 0.0, 4f, NOW_NANOS), NOW_NANOS).isAccepted());
    assertFalse(filter.accept(fix(0.0, 0.0, 0f, NOW_NANOS), NOW_NANOS).isAccepted());
    assertFalse(filter.accept(fix(0.0, 0.0, 26f, NOW_NANOS), NOW_NANOS).isAccepted());

    assertTrue(filter.accept(fix(0.0, 0.0, 4f, NOW_NANOS), NOW_NANOS).isAccepted());
    assertFalse(
        filter
            .accept(fix(0.0, metersToLongitude(20.0), 4f, NOW_NANOS), NOW_NANOS + 1_000_000_000L)
            .isAccepted());
  }

  @Test
  public void stationaryNineMeterAccuracyJitterDoesNotAccumulateDistance() {
    GpsFixFilter filter = new GpsFixFilter();
    assertTrue(filter.accept(fix(0.0, 0.0, 9f, NOW_NANOS), NOW_NANOS).isAccepted());
    double[] offsetsMeters = {3, -4, 5, -2, 4, -5, 2, -3, 5, -4, 3, -1};

    for (int index = 0; index < offsetsMeters.length; index++) {
      GpsFixFilter.Result result =
          filter.accept(
              fix(
                  0.0,
                  metersToLongitude(offsetsMeters[index]),
                  9f,
                  NOW_NANOS + (index + 1) * 1_000_000_000L),
              NOW_NANOS + (index + 1) * 1_000_000_000L);
      assertTrue(
          "reliable stationary fix " + index + " should advance workout time", result.isAccepted());
      assertTrue(result.isStationary());
      assertEquals(0.0, result.getDistanceMeters(), 0.0);
      assertFalse(result.hasDerivedSpeed());
    }
  }

  @Test
  public void slowWalkingEventuallyAccumulatesAccuracyQualifiedDistance() {
    GpsFixFilter filter = new GpsFixFilter();
    assertTrue(filter.accept(fix(0.0, 0.0, 4f, NOW_NANOS), NOW_NANOS).isAccepted());
    double acceptedDistance = 0.0;
    GpsFixFilter.Result latestAccepted = null;

    for (int second = 1; second <= 24; second++) {
      GpsFixFilter.Result result =
          filter.accept(
              fix(0.0, metersToLongitude(0.75 * second), 4f, NOW_NANOS + second * 1_000_000_000L),
              NOW_NANOS + second * 1_000_000_000L);
      if (result.isAccepted()) {
        acceptedDistance += result.getDistanceMeters();
        latestAccepted = result;
      }
    }

    assertTrue("slow movement should not be discarded forever", acceptedDistance >= 12.0);
    assertTrue(latestAccepted != null && latestAccepted.hasDerivedSpeed());
    assertEquals(0.75, latestAccepted.getSpeedMetersPerSecond(), 0.08);
  }

  @Test
  public void plausibleRunningMovementProducesBoundedDerivedSpeed() {
    GpsFixFilter filter = new GpsFixFilter();
    assertTrue(filter.accept(fix(0.0, 0.0, 3f, NOW_NANOS), NOW_NANOS).isAccepted());

    GpsFixFilter.Result result =
        filter.accept(
            fix(0.0, metersToLongitude(8.0), 3f, NOW_NANOS + 2_000_000_000L),
            NOW_NANOS + 2_000_000_000L);

    assertTrue(result.isAccepted());
    assertEquals(8.0, result.getDistanceMeters(), 0.2);
    assertEquals(4.0, result.getSpeedMetersPerSecond(), 0.1);
  }

  @Test
  public void isolatedSpeedSpikeIsRejectedAndDoesNotPoisonTheNextFix() {
    GpsFixFilter filter = new GpsFixFilter();
    assertTrue(filter.accept(fix(0.0, 0.0, 3f, NOW_NANOS), NOW_NANOS).isAccepted());

    GpsFixFilter.Result spike =
        filter.accept(
            fix(0.0, metersToLongitude(150.0), 3f, NOW_NANOS + 1_000_000_000L),
            NOW_NANOS + 1_000_000_000L);
    GpsFixFilter.Result recovered =
        filter.accept(
            fix(0.0, metersToLongitude(8.0), 3f, NOW_NANOS + 2_000_000_000L),
            NOW_NANOS + 2_000_000_000L);

    assertFalse(spike.isAccepted());
    assertTrue(recovered.isAccepted());
    assertEquals(4.0, recovered.getSpeedMetersPerSecond(), 0.1);
  }

  @Test
  public void pauseResetAndLongGapStartANewSegmentWithoutBridgeDistance() {
    GpsFixFilter filter = new GpsFixFilter();
    assertTrue(filter.accept(fix(0.0, 0.0, 3f, NOW_NANOS), NOW_NANOS).isAccepted());
    filter.reset();

    GpsFixFilter.Result resumed =
        filter.accept(
            fix(0.0, metersToLongitude(100.0), 3f, NOW_NANOS + 10_000_000_000L),
            NOW_NANOS + 10_000_000_000L);
    GpsFixFilter.Result afterGap =
        filter.accept(
            fix(0.0, metersToLongitude(300.0), 3f, NOW_NANOS + 50_000_000_000L),
            NOW_NANOS + 50_000_000_000L);

    assertTrue(resumed.isAccepted());
    assertTrue(resumed.startsNewSegment());
    assertEquals(0.0, resumed.getDistanceMeters(), 0.0);
    assertTrue(afterGap.isAccepted());
    assertTrue(afterGap.startsNewSegment());
    assertTrue(afterGap.startsAfterGap());
    assertEquals(0.0, afterGap.getDistanceMeters(), 0.0);
  }

  private static Location fix(
      double latitude, double longitude, float accuracy, long elapsedNanos) {
    Location location = new Location("gps");
    location.setLatitude(latitude);
    location.setLongitude(longitude);
    location.setAccuracy(accuracy);
    location.setElapsedRealtimeNanos(elapsedNanos);
    location.setTime(elapsedNanos / 1_000_000L);
    return location;
  }

  private static double metersToLongitude(double meters) {
    return meters / 111_320.0;
  }
}
