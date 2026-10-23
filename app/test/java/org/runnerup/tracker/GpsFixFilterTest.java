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
  public void
      stationaryNineMeterAccuracyCorrelatedDriftDoesNotAccumulateWhenSpeedIsConfidentlyNearZero() {
    GpsFixFilter filter = new GpsFixFilter();
    assertTrue(filter.accept(fix(0.0, 0.0, 9f, NOW_NANOS), NOW_NANOS).isAccepted());
    double accumulatedDistance = 0.0;
    int driftSegmentStarts = 0;

    // A stationary phone can report a slowly wandering position while GNSS Doppler speed stays
    // near zero. With the old fixed anchor, each ~19 m drift was added as a plausible 0.8 m/s move.
    for (int second = 1; second <= 79; second++) {
      Location sample =
          fix(0.0, metersToLongitude(0.8 * second), 9f, NOW_NANOS + second * 1_000_000_000L);
      sample.setSpeed(0.1f);
      sample.setSpeedAccuracyMetersPerSecond(0.05f);
      assertTrue("test provider must expose speed accuracy", sample.hasSpeedAccuracy());
      GpsFixFilter.Result result = filter.accept(sample, NOW_NANOS + second * 1_000_000_000L);
      assertTrue("fresh location should still advance workout time", result.isAccepted());
      assertTrue(
          "confidently stationary drift should not enter the route at second " + second,
          result.isStationary());
      if (result.startsNewSegment()) driftSegmentStarts++;
      accumulatedDistance += result.getDistanceMeters();
    }

    assertEquals(0.0, accumulatedDistance, 0.0);
    assertEquals("gap and drift resets should preserve segment boundaries", 3, driftSegmentStarts);

    double resumedWalkingDistance = 0.0;
    for (int second = 80; second <= 107; second++) {
      Location sample =
          fix(
              0.0,
              metersToLongitude(0.8 * 79 + 0.75 * (second - 79)),
              9f,
              NOW_NANOS + second * 1_000_000_000L);
      sample.setSpeed(0.75f);
      sample.setSpeedAccuracyMetersPerSecond(0.15f);
      GpsFixFilter.Result result = filter.accept(sample, NOW_NANOS + second * 1_000_000_000L);
      if (result.hasDerivedSpeed()) resumedWalkingDistance += result.getDistanceMeters();
    }
    assertTrue(
        "walking after stationary drift should recover without counting the offset",
        resumedWalkingDistance >= 12.0);
  }

  @Test
  public void slowWalkingWithConfidentProviderSpeedStillAccumulates() {
    GpsFixFilter filter = new GpsFixFilter();
    assertTrue(filter.accept(fix(0.0, 0.0, 4f, NOW_NANOS), NOW_NANOS).isAccepted());
    double accumulatedDistance = 0.0;

    for (int second = 1; second <= 24; second++) {
      Location sample =
          fix(0.0, metersToLongitude(0.75 * second), 4f, NOW_NANOS + second * 1_000_000_000L);
      sample.setSpeed(0.75f);
      sample.setSpeedAccuracyMetersPerSecond(0.15f);
      GpsFixFilter.Result result = filter.accept(sample, NOW_NANOS + second * 1_000_000_000L);
      if (result.hasDerivedSpeed()) accumulatedDistance += result.getDistanceMeters();
    }

    assertTrue("corroborated slow walking should be counted", accumulatedDistance >= 12.0);
  }

  @Test
  public void verySlowWalkingIsNotClassifiedAsStationary() {
    GpsFixFilter filter = new GpsFixFilter();
    assertTrue(filter.accept(fix(0.0, 0.0, 4f, NOW_NANOS), NOW_NANOS).isAccepted());
    GpsFixFilter.Result movement = null;

    for (int second = 1; second <= 30; second++) {
      Location sample =
          fix(0.0, metersToLongitude(0.3 * second), 4f, NOW_NANOS + second * 1_000_000_000L);
      sample.setSpeed(0.3f);
      sample.setSpeedAccuracyMetersPerSecond(0.02f);
      GpsFixFilter.Result result = filter.accept(sample, NOW_NANOS + second * 1_000_000_000L);
      if (result.hasDerivedSpeed()) movement = result;
    }

    assertTrue("very slow walking must still produce a movement fix", movement != null);
    assertEquals(0.3, movement.getSpeedMetersPerSecond(), 0.02);
  }

  @Test
  public void uncertainProviderSpeedDoesNotOverrideAccuracyQualifiedMovement() {
    GpsFixFilter filter = new GpsFixFilter();
    assertTrue(filter.accept(fix(0.0, 0.0, 9f, NOW_NANOS), NOW_NANOS).isAccepted());
    Location sample = fix(0.0, metersToLongitude(24.0), 9f, NOW_NANOS + 30_000_000_000L);
    sample.setSpeed(0.1f);
    sample.setSpeedAccuracyMetersPerSecond(1.0f);

    GpsFixFilter.Result result = filter.accept(sample, NOW_NANOS + 30_000_000_000L);

    assertTrue(
        "uncertain speed must not veto quality-qualified movement", result.hasDerivedSpeed());
    assertEquals(0.8, result.getSpeedMetersPerSecond(), 0.02);
  }

  @Test
  @Config(sdk = 25)
  public void preOreoLocationsWithoutSpeedAccuracyKeepTheGpsFallback() {
    GpsFixFilter filter = new GpsFixFilter();
    assertTrue(filter.accept(fix(0.0, 0.0, 9f, NOW_NANOS), NOW_NANOS).isAccepted());
    Location sample = fix(0.0, metersToLongitude(24.0), 9f, NOW_NANOS + 30_000_000_000L);
    sample.setSpeed(0.1f);

    GpsFixFilter.Result result = filter.accept(sample, NOW_NANOS + 30_000_000_000L);

    assertTrue("older Android has no speed confidence field", result.hasDerivedSpeed());
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

    Location spikeFix = fix(0.0, metersToLongitude(150.0), 3f, NOW_NANOS + 1_000_000_000L);
    spikeFix.setSpeed(0.1f);
    spikeFix.setSpeedAccuracyMetersPerSecond(0.1f);
    GpsFixFilter.Result spike = filter.accept(spikeFix, NOW_NANOS + 1_000_000_000L);
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
