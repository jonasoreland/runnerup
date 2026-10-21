package org.runnerup.importer;

/** One timestamped GPS sample from an imported recorded activity. */
public final class ActivityTrackPoint {
  private final long timeSeconds;
  private final double latitude;
  private final double longitude;
  private final Double altitudeMeters;
  private final Double distanceMeters;
  private final Integer heartRate;
  private final Integer cadence;

  ActivityTrackPoint(
      long timeSeconds,
      double latitude,
      double longitude,
      Double altitudeMeters,
      Double distanceMeters,
      Integer heartRate,
      Integer cadence) {
    this.timeSeconds = timeSeconds;
    this.latitude = latitude;
    this.longitude = longitude;
    this.altitudeMeters = altitudeMeters;
    this.distanceMeters = distanceMeters;
    this.heartRate = heartRate;
    this.cadence = cadence;
  }

  public long getTimeSeconds() {
    return timeSeconds;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public Double getAltitudeMeters() {
    return altitudeMeters;
  }

  public Double getDistanceMeters() {
    return distanceMeters;
  }

  public Integer getHeartRate() {
    return heartRate;
  }

  public Integer getCadence() {
    return cadence;
  }
}
