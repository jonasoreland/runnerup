package org.runnerup.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.runnerup.workout.Sport;

/** Validated, database-independent representation of one imported route activity. */
public final class ImportedActivity {
  public enum Format {
    GPX,
    TCX
  }

  private final Format format;
  private final Sport sport;
  private final long startTimeSeconds;
  private final long durationSeconds;
  private final double distanceMeters;
  private final Integer averageHeartRate;
  private final Integer maximumHeartRate;
  private final List<ActivityTrackPoint> trackPoints;

  ImportedActivity(
      Format format,
      Sport sport,
      long startTimeSeconds,
      long durationSeconds,
      double distanceMeters,
      Integer averageHeartRate,
      Integer maximumHeartRate,
      List<ActivityTrackPoint> trackPoints) {
    this.format = format;
    this.sport = sport;
    this.startTimeSeconds = startTimeSeconds;
    this.durationSeconds = durationSeconds;
    this.distanceMeters = distanceMeters;
    this.averageHeartRate = averageHeartRate;
    this.maximumHeartRate = maximumHeartRate;
    this.trackPoints = Collections.unmodifiableList(new ArrayList<>(trackPoints));
  }

  public Format getFormat() {
    return format;
  }

  public Sport getSport() {
    return sport;
  }

  public long getStartTimeSeconds() {
    return startTimeSeconds;
  }

  public long getDurationSeconds() {
    return durationSeconds;
  }

  public double getDistanceMeters() {
    return distanceMeters;
  }

  public Integer getAverageHeartRate() {
    return averageHeartRate;
  }

  public Integer getMaximumHeartRate() {
    return maximumHeartRate;
  }

  public List<ActivityTrackPoint> getTrackPoints() {
    return trackPoints;
  }
}
