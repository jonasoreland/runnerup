package org.runnerup.importer;

import android.util.Xml;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.runnerup.importer.ActivityImportException.Reason;
import org.runnerup.workout.Sport;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Secure, bounded GPX/TCX route parser. It does not persist imported data. */
public final class ActivityFileImporter {
  private static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
  private static final int MAX_TRACK_POINTS = 100_000;
  private static final double EARTH_RADIUS_METERS = 6_371_000.0;
  private static final double MAX_ACTIVITY_DISTANCE_METERS = 1_000_000_000.0;
  private static final long MAX_ACTIVITY_DURATION_SECONDS = 10L * 365 * 24 * 60 * 60;
  private static final String GPX_10_NAMESPACE = "http://www.topografix.com/GPX/1/0";
  private static final String GPX_11_NAMESPACE = "http://www.topografix.com/GPX/1/1";
  private static final String TCX_2_NAMESPACE =
      "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2";

  private ActivityFileImporter() {}

  public static ImportedActivity read(String displayName, InputStream input)
      throws ActivityImportException {
    if (input == null) throw failure(Reason.INVALID_DATA, "Missing activity input");
    try {
      XmlPullParser parser = Xml.newPullParser();
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
      parser.setInput(new BoundedInputStream(input), null);
      int event = parser.getEventType();
      while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
        event = parser.next();
      }
      if (event != XmlPullParser.START_TAG)
        throw failure(Reason.MALFORMED_XML, "Empty XML document");
      String root = parser.getName();
      String namespace = parser.getNamespace();
      if ("gpx".equals(root) && isGpxNamespace(namespace)) return parseGpx(parser);
      if ("TrainingCenterDatabase".equals(root) && TCX_2_NAMESPACE.equals(namespace))
        return parseTcx(parser);
      throw failure(Reason.UNSUPPORTED_FORMAT, "Unsupported activity file format");
    } catch (ActivityImportException error) {
      throw error;
    } catch (XmlPullParserException | IOException | RuntimeException error) {
      if (causedByInputLimit(error))
        throw failure(Reason.INPUT_TOO_LARGE, "Activity file is too large");
      throw new ActivityImportException(Reason.MALFORMED_XML, "Could not read activity XML", error);
    }
  }

  private static boolean isGpxNamespace(String namespace) {
    return GPX_10_NAMESPACE.equals(namespace) || GPX_11_NAMESPACE.equals(namespace);
  }

  private static boolean causedByInputLimit(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof InputLimitException) return true;
    }
    return false;
  }

  private static final class InputLimitException extends IOException {
    private InputLimitException() {
      super("Activity input exceeds the configured size limit");
    }
  }

  private static final class BoundedInputStream extends FilterInputStream {
    private int bytesRead;

    private BoundedInputStream(InputStream input) {
      super(input);
    }

    @Override
    public int read() throws IOException {
      if (bytesRead == MAX_INPUT_BYTES) {
        if (in.read() == -1) return -1;
        throw new InputLimitException();
      }
      int value = in.read();
      if (value != -1) bytesRead++;
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      if (length == 0) return 0;
      if (bytesRead == MAX_INPUT_BYTES) {
        if (in.read() == -1) return -1;
        throw new InputLimitException();
      }
      int allowed = Math.min(length, MAX_INPUT_BYTES - bytesRead);
      int count = in.read(buffer, offset, allowed);
      if (count > 0) bytesRead += count;
      return count;
    }

    @Override
    public long skip(long count) throws IOException {
      long skipped = 0;
      byte[] buffer = new byte[(int) Math.min(8192, Math.max(1, count))];
      while (skipped < count) {
        int read = read(buffer, 0, (int) Math.min(buffer.length, count - skipped));
        if (read == -1) break;
        skipped += read;
      }
      return skipped;
    }
  }

  private static ImportedActivity parseGpx(XmlPullParser parser)
      throws XmlPullParserException, IOException, ActivityImportException {
    List<ActivityTrackPoint> points = new ArrayList<>();
    List<Boolean> startsSegment = new ArrayList<>();
    PointBuilder point = null;
    boolean nextPointStartsSegment = true;
    int event;
    while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.DOCDECL)
        throw failure(Reason.MALFORMED_XML, "DOCTYPE is not allowed");
      if (event == XmlPullParser.START_TAG) {
        String name = parser.getName();
        if ("trkseg".equals(name)) {
          nextPointStartsSegment = true;
        } else if ("trkpt".equals(name)) {
          if (point != null) throw failure(Reason.INVALID_DATA, "Nested track points are invalid");
          point = new PointBuilder();
          point.latitude = parseRequired(parser.getAttributeValue(null, "lat"), "latitude");
          point.longitude = parseRequired(parser.getAttributeValue(null, "lon"), "longitude");
          validateCoordinates(point.latitude, point.longitude);
        } else if (point != null && isPointField(name)) {
          point.readField = name;
        }
      } else if (event == XmlPullParser.TEXT && point != null && point.readField != null) {
        point.text = parser.getText();
      } else if (event == XmlPullParser.END_TAG) {
        String name = parser.getName();
        if (point != null && isPointField(name)) point.setField(name, point.text);
        if ("trkpt".equals(name)) {
          if (point == null) throw failure(Reason.INVALID_DATA, "Unexpected track point end");
          points.add(point.build());
          startsSegment.add(nextPointStartsSegment);
          nextPointStartsSegment = false;
          if (points.size() > MAX_TRACK_POINTS)
            throw failure(Reason.TOO_MANY_POINTS, "Activity has too many route points");
          point = null;
        }
        if (point != null && name.equals(point.readField)) {
          point.readField = null;
          point.text = null;
        }
      }
    }
    if (points.isEmpty()) throw failure(Reason.MISSING_ROUTE, "Activity has no GPS route");
    for (int index = 1; index < startsSegment.size(); index++) {
      if (startsSegment.get(index)) {
        throw failure(
            Reason.MULTIPLE_ROUTE_SEGMENTS,
            "GPX activities with multiple route segments cannot be imported without changing the route");
      }
    }
    List<ActivityTrackPoint> enriched = new ArrayList<>(points.size());
    double distance = 0.0;
    ActivityTrackPoint previous = null;
    for (int index = 0; index < points.size(); index++) {
      ActivityTrackPoint sample = points.get(index);
      if (previous != null && !startsSegment.get(index)) {
        if (sample.getTimeSeconds() < previous.getTimeSeconds()) {
          throw failure(Reason.INVALID_DATA, "Route timestamps are out of order");
        }
        distance +=
            distance(
                previous.getLatitude(),
                previous.getLongitude(),
                sample.getLatitude(),
                sample.getLongitude());
      }
      enriched.add(
          new ActivityTrackPoint(
              sample.getTimeSeconds(),
              sample.getLatitude(),
              sample.getLongitude(),
              sample.getAltitudeMeters(),
              distance,
              sample.getHeartRate(),
              sample.getCadence()));
      previous = sample;
    }
    return activity(ImportedActivity.Format.GPX, Sport.RUNNING, enriched, distance, null);
  }

  private static ImportedActivity parseTcx(XmlPullParser parser)
      throws XmlPullParserException, IOException, ActivityImportException {
    List<ActivityTrackPoint> points = new ArrayList<>();
    PointBuilder point = null;
    int activities = 0;
    String activitySport = "Running";
    boolean inActivity = false;
    boolean inLap = false;
    String field = null;
    String text = null;
    double summaryDistance = 0.0;
    double summaryDuration = 0.0;
    boolean hasSummaryDistance = false;
    boolean hasSummaryDuration = false;
    int event;
    while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.DOCDECL)
        throw failure(Reason.MALFORMED_XML, "DOCTYPE is not allowed");
      if (event == XmlPullParser.START_TAG) {
        String name = parser.getName();
        if ("Activity".equals(name)) {
          activities++;
          if (activities > 1)
            throw failure(Reason.MULTIPLE_ACTIVITIES, "Import one activity per file");
          inActivity = true;
          activitySport = parser.getAttributeValue(null, "Sport");
        } else if (inActivity && "Lap".equals(name)) {
          inLap = true;
        } else if (inActivity && "Trackpoint".equals(name)) {
          point = new PointBuilder();
        } else if (point != null && isTcxPointField(name)) {
          field = name;
        } else if (inLap
            && point == null
            && ("TotalTimeSeconds".equals(name) || "DistanceMeters".equals(name))) {
          field = name;
        } else if (point != null && "Value".equals(name)) {
          field = "HeartRateValue";
        }
      } else if (event == XmlPullParser.TEXT && field != null) {
        text = parser.getText();
      } else if (event == XmlPullParser.END_TAG) {
        String name = parser.getName();
        if (point != null && isTcxPointField(name)) point.setField(name, text);
        if (point != null && "Value".equals(name) && "HeartRateValue".equals(field))
          point.setField("HeartRateValue", text);
        if (inLap && point == null && "TotalTimeSeconds".equals(name)) {
          Double value = parseSummaryValue(text, "duration");
          if (value != null) {
            summaryDuration += value;
            hasSummaryDuration = true;
          }
        }
        if (inLap && point == null && "DistanceMeters".equals(name)) {
          Double value = parseSummaryValue(text, "distance");
          if (value != null) {
            summaryDistance += value;
            hasSummaryDistance = true;
          }
        }
        if ("Trackpoint".equals(name) && point != null) {
          points.add(point.build());
          if (points.size() > MAX_TRACK_POINTS)
            throw failure(Reason.TOO_MANY_POINTS, "Activity has too many route points");
          point = null;
        }
        if ("Lap".equals(name)) inLap = false;
        if ("Activity".equals(name)) inActivity = false;
        if (name.equals(field)) {
          field = null;
          text = null;
        }
        if ("HeartRateValue".equals(field) && "Value".equals(name)) {
          field = null;
          text = null;
        }
      }
    }
    if (activities != 1) throw failure(Reason.MISSING_ROUTE, "TCX file has no activity");
    if (points.isEmpty()) throw failure(Reason.MISSING_ROUTE, "Activity has no GPS route");
    double distance = hasSummaryDistance ? summaryDistance : calculateDistance(points);
    long duration = hasSummaryDuration ? Math.round(summaryDuration) : routeDuration(points);
    validateSummary(duration, distance);
    List<ActivityTrackPoint> normalized = normalizeTcxPoints(points);
    return new ImportedActivity(
        ImportedActivity.Format.TCX,
        tcxSport(activitySport),
        normalized.get(0).getTimeSeconds(),
        duration,
        distance,
        averageHeartRate(normalized),
        maximumHeartRate(normalized),
        normalized);
  }

  private static ImportedActivity activity(
      ImportedActivity.Format format,
      Sport sport,
      List<ActivityTrackPoint> points,
      double distance,
      Double durationOverride)
      throws ActivityImportException {
    long duration = durationOverride == null ? routeDuration(points) : Math.round(durationOverride);
    validateSummary(duration, distance);
    return new ImportedActivity(
        format,
        sport,
        points.get(0).getTimeSeconds(),
        duration,
        distance,
        averageHeartRate(points),
        maximumHeartRate(points),
        points);
  }

  private static List<ActivityTrackPoint> normalizeTcxPoints(List<ActivityTrackPoint> points)
      throws ActivityImportException {
    List<ActivityTrackPoint> normalized = new ArrayList<>(points.size());
    double calculatedDistance = 0.0;
    ActivityTrackPoint previous = null;
    for (ActivityTrackPoint point : points) {
      if (previous != null) {
        if (point.getTimeSeconds() < previous.getTimeSeconds())
          throw failure(Reason.INVALID_DATA, "Route timestamps are out of order");
        calculatedDistance +=
            distance(
                previous.getLatitude(),
                previous.getLongitude(),
                point.getLatitude(),
                point.getLongitude());
      }
      double pointDistance =
          point.getDistanceMeters() == null ? calculatedDistance : point.getDistanceMeters();
      if (!Double.isFinite(pointDistance)
          || pointDistance < 0
          || pointDistance > MAX_ACTIVITY_DISTANCE_METERS)
        throw failure(Reason.INVALID_DATA, "Invalid route distance");
      normalized.add(
          new ActivityTrackPoint(
              point.getTimeSeconds(),
              point.getLatitude(),
              point.getLongitude(),
              point.getAltitudeMeters(),
              pointDistance,
              point.getHeartRate(),
              point.getCadence()));
      previous = point;
    }
    return normalized;
  }

  private static boolean isPointField(String name) {
    return "ele".equals(name) || "time".equals(name) || "hr".equals(name) || "cad".equals(name);
  }

  private static boolean isTcxPointField(String name) {
    return "Time".equals(name)
        || "LatitudeDegrees".equals(name)
        || "LongitudeDegrees".equals(name)
        || "AltitudeMeters".equals(name)
        || "DistanceMeters".equals(name)
        || "Cadence".equals(name);
  }

  private static final class PointBuilder {
    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private Long timeSeconds;
    private Double altitude;
    private Double distance;
    private Integer heartRate;
    private Integer cadence;
    private String readField;
    private String text;

    private void setField(String name, String value) throws ActivityImportException {
      if (value == null) return;
      String trimmed = value.trim();
      if (trimmed.isEmpty()) return;
      switch (name) {
        case "ele":
        case "AltitudeMeters":
          altitude = parseOptional(trimmed);
          break;
        case "time":
        case "Time":
          timeSeconds = parseTime(trimmed);
          break;
        case "hr":
        case "HeartRateValue":
          heartRate = parseInteger(trimmed, 1, 255);
          break;
        case "cad":
        case "Cadence":
          cadence = parseInteger(trimmed, 0, 255);
          break;
        case "DistanceMeters":
          distance = parseOptional(trimmed);
          break;
        case "LatitudeDegrees":
          latitude = parseRequired(trimmed, "latitude");
          break;
        case "LongitudeDegrees":
          longitude = parseRequired(trimmed, "longitude");
          break;
      }
    }

    private ActivityTrackPoint build() throws ActivityImportException {
      if (timeSeconds == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
        throw failure(Reason.INVALID_DATA, "Route point is missing a valid time or position");
      }
      validateCoordinates(latitude, longitude);
      if (altitude != null
          && (!Double.isFinite(altitude) || altitude < -12_000 || altitude > 100_000))
        throw failure(Reason.INVALID_DATA, "Invalid altitude");
      if (distance != null
          && (!Double.isFinite(distance)
              || distance < 0
              || distance > MAX_ACTIVITY_DISTANCE_METERS))
        throw failure(Reason.INVALID_DATA, "Invalid route distance");
      return new ActivityTrackPoint(
          timeSeconds, latitude, longitude, altitude, distance, heartRate, cadence);
    }
  }

  private static Sport tcxSport(String value) throws ActivityImportException {
    if ("Running".equals(value)) return Sport.RUNNING;
    if ("Biking".equals(value)) return Sport.BIKING;
    if ("Other".equals(value)) return Sport.OTHER;
    throw failure(Reason.INVALID_DATA, "Unsupported TCX sport");
  }

  private static Integer maximumHeartRate(List<ActivityTrackPoint> points) {
    int maximum = 0;
    for (ActivityTrackPoint point : points)
      if (point.getHeartRate() != null) maximum = Math.max(maximum, point.getHeartRate());
    return maximum == 0 ? null : maximum;
  }

  private static Integer averageHeartRate(List<ActivityTrackPoint> points) {
    long total = 0;
    int count = 0;
    for (ActivityTrackPoint point : points)
      if (point.getHeartRate() != null) {
        total += point.getHeartRate();
        count++;
      }
    return count == 0 ? null : (int) Math.round((double) total / count);
  }

  private static double calculateDistance(List<ActivityTrackPoint> points)
      throws ActivityImportException {
    double distance = 0;
    ActivityTrackPoint previous = null;
    for (ActivityTrackPoint point : points) {
      if (previous != null)
        distance +=
            distance(
                previous.getLatitude(),
                previous.getLongitude(),
                point.getLatitude(),
                point.getLongitude());
      previous = point;
    }
    return distance;
  }

  private static long routeDuration(List<ActivityTrackPoint> points)
      throws ActivityImportException {
    if (points.size() < 2)
      throw failure(Reason.INVALID_DATA, "Activity needs at least two timed route points");
    long first = points.get(0).getTimeSeconds();
    long last = points.get(points.size() - 1).getTimeSeconds();
    if (last < first) throw failure(Reason.INVALID_DATA, "Route timestamps are out of order");
    return last - first;
  }

  private static double distance(double lat1, double lon1, double lat2, double lon2) {
    double p1 = Math.toRadians(lat1);
    double p2 = Math.toRadians(lat2);
    double deltaP = Math.toRadians(lat2 - lat1);
    double deltaL = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(deltaP / 2) * Math.sin(deltaP / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(deltaL / 2) * Math.sin(deltaL / 2);
    double boundedA = Math.max(0.0, Math.min(1.0, a));
    return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(boundedA), Math.sqrt(1 - boundedA));
  }

  private static void validateCoordinates(double latitude, double longitude)
      throws ActivityImportException {
    if (!Double.isFinite(latitude)
        || !Double.isFinite(longitude)
        || latitude < -90
        || latitude > 90
        || longitude < -180
        || longitude > 180) {
      throw failure(Reason.INVALID_DATA, "Route point has invalid coordinates");
    }
  }

  private static double parseRequired(String value, String field) throws ActivityImportException {
    Double parsed = parseOptional(value);
    if (parsed == null) throw failure(Reason.INVALID_DATA, "Route point has invalid " + field);
    return parsed;
  }

  private static Double parseOptional(String value) {
    if (value == null) return null;
    try {
      double parsed = Double.parseDouble(value.trim());
      return Double.isFinite(parsed) ? parsed : null;
    } catch (NumberFormatException error) {
      return null;
    }
  }

  private static Double parseSummaryValue(String value, String field)
      throws ActivityImportException {
    if (value == null || value.trim().isEmpty()) return null;
    Double parsed = parseOptional(value);
    double maximum =
        "duration".equals(field) ? MAX_ACTIVITY_DURATION_SECONDS : MAX_ACTIVITY_DISTANCE_METERS;
    if (parsed == null || parsed < 0 || parsed > maximum)
      throw failure(Reason.INVALID_DATA, "Invalid activity " + field);
    return parsed;
  }

  private static Integer parseInteger(String value, int minimum, int maximum)
      throws ActivityImportException {
    try {
      int parsed = Integer.parseInt(value);
      if (parsed < minimum || parsed > maximum)
        throw failure(Reason.INVALID_DATA, "Invalid activity metric");
      return parsed;
    } catch (NumberFormatException error) {
      throw new ActivityImportException(Reason.INVALID_DATA, "Invalid activity metric", error);
    }
  }

  private static long parseTime(String value) throws ActivityImportException {
    String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX"};
    for (String pattern : patterns) {
      SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
      format.setLenient(false);
      format.setTimeZone(TimeZone.getTimeZone("UTC"));
      ParsePosition position = new ParsePosition(0);
      Date parsed = format.parse(value, position);
      if (parsed != null && position.getIndex() == value.length()) {
        long seconds = parsed.getTime() / 1000;
        if (seconds < 0) throw failure(Reason.INVALID_DATA, "Route timestamp is before Unix epoch");
        return seconds;
      }
    }
    throw failure(Reason.INVALID_DATA, "Route point has invalid timestamp");
  }

  private static void validateSummary(long durationSeconds, double distanceMeters)
      throws ActivityImportException {
    if (durationSeconds <= 0
        || durationSeconds > MAX_ACTIVITY_DURATION_SECONDS
        || !Double.isFinite(distanceMeters)
        || distanceMeters <= 0
        || distanceMeters > MAX_ACTIVITY_DISTANCE_METERS) {
      throw failure(Reason.INVALID_DATA, "Activity summary is invalid");
    }
  }

  private static ActivityImportException failure(Reason reason, String message) {
    return new ActivityImportException(reason, message);
  }
}
