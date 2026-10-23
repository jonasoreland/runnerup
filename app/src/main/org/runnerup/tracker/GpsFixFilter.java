package org.runnerup.tracker;

import android.location.Location;
import androidx.annotation.NonNull;

/** Quality-aware acceptance filter for GPS fixes used to build a workout route. */
public final class GpsFixFilter {
  private static final long MAX_AGE_NANOS = 15_000_000_000L;
  private static final long MAX_FUTURE_NANOS = 1_000_000_000L;
  private static final long MAX_GAP_NANOS = 30_000_000_000L;
  private static final float MAX_ACCURACY_METERS = 25.0f;
  private static final double MAX_SPEED_METERS_PER_SECOND = 12.0;

  private Location anchor;

  @NonNull
  public Result accept(@NonNull Location fix, long nowElapsedRealtimeNanos) {
    long fixTime = fix.getElapsedRealtimeNanos();
    if (!isValid(fix)
        || fixTime <= 0
        || fixTime < nowElapsedRealtimeNanos - MAX_AGE_NANOS
        || fixTime > nowElapsedRealtimeNanos + MAX_FUTURE_NANOS) {
      return Result.rejected();
    }

    if (anchor == null) {
      anchor = new Location(fix);
      return Result.segmentStart(false);
    }

    long intervalNanos = fixTime - anchor.getElapsedRealtimeNanos();
    if (intervalNanos <= 0) return Result.rejected();
    if (intervalNanos > MAX_GAP_NANOS) {
      anchor = new Location(fix);
      return Result.segmentStart(true);
    }

    double distance = anchor.distanceTo(fix);
    double uncertainty = Math.hypot(anchor.getAccuracy(), fix.getAccuracy());
    double movementThreshold = Math.max(1.0, 1.5 * uncertainty);
    if (distance < movementThreshold) return Result.stationary();

    double speed = distance * 1_000_000_000.0 / intervalNanos;
    if (!Double.isFinite(speed) || speed > MAX_SPEED_METERS_PER_SECOND) {
      return Result.rejected();
    }

    anchor = new Location(fix);
    return Result.accepted(distance, speed);
  }

  public void reset() {
    anchor = null;
  }

  private static boolean isValid(Location location) {
    double latitude = location.getLatitude();
    double longitude = location.getLongitude();
    return Double.isFinite(latitude)
        && Double.isFinite(longitude)
        && latitude >= -90.0
        && latitude <= 90.0
        && longitude >= -180.0
        && longitude <= 180.0
        && location.hasAccuracy()
        && location.getAccuracy() > 0.0f
        && location.getAccuracy() <= MAX_ACCURACY_METERS;
  }

  public static final class Result {
    private static final Result REJECTED = new Result(false, false, 0.0, 0.0, false, false, false);
    private final boolean accepted;
    private final boolean startsNewSegment;
    private final double distanceMeters;
    private final double speedMetersPerSecond;
    private final boolean hasDerivedSpeed;
    private final boolean startsAfterGap;
    private final boolean stationary;

    private Result(
        boolean accepted,
        boolean startsNewSegment,
        double distanceMeters,
        double speedMetersPerSecond,
        boolean hasDerivedSpeed,
        boolean startsAfterGap,
        boolean stationary) {
      this.accepted = accepted;
      this.startsNewSegment = startsNewSegment;
      this.distanceMeters = distanceMeters;
      this.speedMetersPerSecond = speedMetersPerSecond;
      this.hasDerivedSpeed = hasDerivedSpeed;
      this.startsAfterGap = startsAfterGap;
      this.stationary = stationary;
    }

    private static Result rejected() {
      return REJECTED;
    }

    private static Result segmentStart(boolean afterGap) {
      return new Result(true, true, 0.0, 0.0, false, afterGap, false);
    }

    private static Result stationary() {
      return new Result(true, false, 0.0, 0.0, false, false, true);
    }

    private static Result accepted(double distance, double speed) {
      return new Result(true, false, distance, speed, true, false, false);
    }

    public boolean isAccepted() {
      return accepted;
    }

    public boolean startsNewSegment() {
      return startsNewSegment;
    }

    public boolean startsAfterGap() {
      return startsAfterGap;
    }

    public boolean isStationary() {
      return stationary;
    }

    public double getDistanceMeters() {
      return distanceMeters;
    }

    public double getSpeedMetersPerSecond() {
      return speedMetersPerSecond;
    }

    public boolean hasDerivedSpeed() {
      return hasDerivedSpeed;
    }
  }
}
