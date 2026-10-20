package org.runnerup.relven.migration;

import android.location.Location;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Validated, bounded mapping of a supported legacy running activity document. */
public final class LegacyRelvenActivityDocument {
  private static final int MAX_DOCUMENT_CHARS = 16 * 1024 * 1024;
  private static final int MAX_SAMPLES = 200_000;
  private static final double MAX_RUNNING_SEGMENT_SPEED_METERS_PER_SECOND = 15.0;
  private static final Pattern ISO_INSTANT_PATTERN =
      Pattern.compile(
          "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:\\d{2})$");

  public static final class RouteSample {
    public final long elapsedMilliseconds;
    public final double latitude;
    public final double longitude;
    public final Double altitudeMeters;
    public final Double speedMetersPerSecond;
    public final Double accuracyMeters;

    private RouteSample(
        long elapsedMilliseconds,
        double latitude,
        double longitude,
        Double altitudeMeters,
        Double speedMetersPerSecond,
        Double accuracyMeters) {
      this.elapsedMilliseconds = elapsedMilliseconds;
      this.latitude = latitude;
      this.longitude = longitude;
      this.altitudeMeters = altitudeMeters;
      this.speedMetersPerSecond = speedMetersPerSecond;
      this.accuracyMeters = accuracyMeters;
    }
  }

  public static final class PauseInterval {
    public final long startElapsedMilliseconds;
    public final long endElapsedMilliseconds;

    private PauseInterval(long startElapsedMilliseconds, long endElapsedMilliseconds) {
      this.startElapsedMilliseconds = startElapsedMilliseconds;
      this.endElapsedMilliseconds = endElapsedMilliseconds;
    }
  }

  private final String clientActivityId;
  private final String workoutName;
  private final long startedAtEpochMilliseconds;
  private final long activeDurationSeconds;
  private final long elapsedMilliseconds;
  private final List<RouteSample> samples;
  private final List<PauseInterval> technicalPauses;

  private LegacyRelvenActivityDocument(
      String clientActivityId,
      String workoutName,
      long startedAtEpochMilliseconds,
      long activeDurationSeconds,
      long elapsedMilliseconds,
      List<RouteSample> samples,
      List<PauseInterval> technicalPauses) {
    this.clientActivityId = clientActivityId;
    this.workoutName = workoutName;
    this.startedAtEpochMilliseconds = startedAtEpochMilliseconds;
    this.activeDurationSeconds = activeDurationSeconds;
    this.elapsedMilliseconds = elapsedMilliseconds;
    this.samples = Collections.unmodifiableList(samples);
    this.technicalPauses = Collections.unmodifiableList(technicalPauses);
  }

  public static LegacyRelvenActivityDocument parse(String source) {
    if (source == null || source.length() > MAX_DOCUMENT_CHARS) {
      throw new IllegalArgumentException("Legacy activity document is missing or too large");
    }
    try {
      JSONObject root = new JSONObject(source);
      if (root.optInt("schemaVersion", -1) != 1 || !"completed".equals(root.optString("outcome"))) {
        throw new IllegalArgumentException("Legacy activity is not a completed schema v1 record");
      }
      JSONObject workout = root.optJSONObject("workout");
      if (workout == null || !"running".equalsIgnoreCase(workout.optString("sport"))) {
        throw new IllegalArgumentException("Legacy activity is not a running workout");
      }
      long start = parseInstantEpochMilliseconds(root.getString("startedAt"));
      long end = parseInstantEpochMilliseconds(root.getString("endedAt"));
      long wallDuration = Math.subtractExact(end, start);
      long activeSeconds = requiredWholeSeconds(root, "activeDurationSeconds");
      if (wallDuration <= 0
          || activeSeconds <= 0
          || activeSeconds > (wallDuration + 999L) / 1000L) {
        throw new IllegalArgumentException("Legacy activity duration is invalid");
      }
      long activeElapsed = Math.multiplyExact(activeSeconds, 1000L);
      JSONArray sourceSamples = root.optJSONArray("samples");
      if (sourceSamples == null
          || sourceSamples.length() < 2
          || sourceSamples.length() > MAX_SAMPLES) {
        throw new IllegalArgumentException("Legacy activity route sample count is invalid");
      }
      List<RouteSample> samples = new ArrayList<>(sourceSamples.length());
      long previousElapsed = -1;
      for (int i = 0; i < sourceSamples.length(); i++) {
        JSONObject item = sourceSamples.optJSONObject(i);
        if (item == null || !"location".equals(item.optString("type"))) continue;
        long elapsed = requiredWholeMilliseconds(item, "elapsedMilliseconds");
        JSONObject data = item.optJSONObject("data");
        if (data == null) throw new IllegalArgumentException("Legacy route sample has no data");
        double latitude = requiredFiniteNumber(data, "latitude");
        double longitude = requiredFiniteNumber(data, "longitude");
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
          throw new IllegalArgumentException("Legacy route coordinate is out of range");
        }
        if (elapsed <= previousElapsed || elapsed < 0 || elapsed > wallDuration) {
          throw new IllegalArgumentException("Legacy route sample times are not increasing");
        }
        previousElapsed = elapsed;
        samples.add(
            new RouteSample(
                elapsed,
                latitude,
                longitude,
                optionalFiniteNumber(data, "altitudeMeters"),
                optionalNonNegativeNumber(data, "speedMetersPerSecond"),
                optionalNonNegativeNumber(data, "accuracyMeters")));
      }
      if (samples.size() < 2) {
        throw new IllegalArgumentException("Legacy activity has fewer than two location samples");
      }
      List<PauseInterval> pauses =
          parseTechnicalPauses(root.optJSONArray("deviations"), wallDuration);
      LegacyRelvenActivityDocument result =
          new LegacyRelvenActivityDocument(
              root.optString("clientActivityId", "").trim(),
              workout.optString("name", "").trim(),
              start,
              activeSeconds,
              wallDuration,
              samples,
              pauses);
      result.getRouteDistanceMeters();
      return result;
    } catch (JSONException | ParseException | ArithmeticException e) {
      throw new IllegalArgumentException("Legacy activity document is malformed", e);
    }
  }

  private static long parseInstantEpochMilliseconds(String source) throws ParseException {
    Matcher match = ISO_INSTANT_PATTERN.matcher(source);
    if (!match.matches()) throw new ParseException("Invalid ISO instant", 0);
    String fraction = match.group(2);
    String milliseconds = fraction == null ? "000" : (fraction + "000").substring(0, 3);
    String zone = "Z".equals(match.group(3)) ? "+00:00" : match.group(3);
    String normalized = match.group(1) + "." + milliseconds + zone;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);
    format.setLenient(false);
    ParsePosition position = new ParsePosition(0);
    Date parsed = format.parse(normalized, position);
    if (parsed == null || position.getIndex() != normalized.length()) {
      throw new ParseException("Invalid ISO instant", Math.max(0, position.getErrorIndex()));
    }
    return parsed.getTime();
  }

  private static List<PauseInterval> parseTechnicalPauses(JSONArray deviations, long wallDuration)
      throws JSONException {
    List<PauseInterval> intervals = new ArrayList<>();
    if (deviations == null) return intervals;
    for (int i = 0; i < deviations.length(); i++) {
      JSONObject item = deviations.optJSONObject(i);
      if (item == null)
        throw new IllegalArgumentException("Legacy activity deviation is malformed");
      if (!"technical_pause".equals(item.optString("kind"))) continue;
      long start = requiredWholeMilliseconds(item, "startElapsedMilliseconds");
      long end = requiredWholeMilliseconds(item, "endElapsedMilliseconds");
      if (start < 0 || end <= start || end > wallDuration) {
        throw new IllegalArgumentException("Legacy technical pause interval is invalid");
      }
      intervals.add(new PauseInterval(start, end));
    }
    intervals.sort(Comparator.comparingLong(interval -> interval.startElapsedMilliseconds));
    long previousEnd = -1;
    for (PauseInterval interval : intervals) {
      if (interval.startElapsedMilliseconds < previousEnd) {
        throw new IllegalArgumentException("Legacy technical pause intervals overlap");
      }
      previousEnd = interval.endElapsedMilliseconds;
    }
    return intervals;
  }

  private static long requiredWholeSeconds(JSONObject object, String key) throws JSONException {
    long value = requiredWholeNumber(object, key);
    return value;
  }

  private static long requiredWholeMilliseconds(JSONObject object, String key)
      throws JSONException {
    return requiredWholeNumber(object, key);
  }

  private static long requiredWholeNumber(JSONObject object, String key) throws JSONException {
    Object value = object.get(key);
    if (!(value instanceof Number))
      throw new IllegalArgumentException("Invalid numeric field: " + key);
    double decimal = ((Number) value).doubleValue();
    long whole = ((Number) value).longValue();
    if (!Double.isFinite(decimal) || decimal != (double) whole) {
      throw new IllegalArgumentException("Invalid integer field: " + key);
    }
    return whole;
  }

  private static double requiredFiniteNumber(JSONObject object, String key) throws JSONException {
    Object value = object.get(key);
    if (!(value instanceof Number))
      throw new IllegalArgumentException("Invalid numeric field: " + key);
    double result = ((Number) value).doubleValue();
    if (!Double.isFinite(result))
      throw new IllegalArgumentException("Invalid numeric field: " + key);
    return result;
  }

  private static Double optionalFiniteNumber(JSONObject object, String key) {
    if (!object.has(key) || object.isNull(key)) return null;
    Object value = object.opt(key);
    if (!(value instanceof Number))
      throw new IllegalArgumentException("Invalid numeric field: " + key);
    double result = ((Number) value).doubleValue();
    if (!Double.isFinite(result))
      throw new IllegalArgumentException("Invalid numeric field: " + key);
    return result;
  }

  private static Double optionalNonNegativeNumber(JSONObject object, String key) {
    Double value = optionalFiniteNumber(object, key);
    if (value != null && value < 0)
      throw new IllegalArgumentException("Invalid numeric field: " + key);
    return value;
  }

  public String getClientActivityId() {
    return clientActivityId;
  }

  public String getWorkoutName() {
    return workoutName;
  }

  public long getStartedAtEpochMilliseconds() {
    return startedAtEpochMilliseconds;
  }

  public long getActiveDurationSeconds() {
    return activeDurationSeconds;
  }

  public long getElapsedMilliseconds() {
    return elapsedMilliseconds;
  }

  public List<RouteSample> getSamples() {
    return samples;
  }

  public List<PauseInterval> getTechnicalPauses() {
    return technicalPauses;
  }

  public double getRouteDistanceMeters() {
    double total = 0.0;
    for (int i = 1; i < samples.size(); i++) {
      RouteSample previous = samples.get(i - 1);
      RouteSample current = samples.get(i);
      float[] result = {0};
      Location.distanceBetween(
          previous.latitude, previous.longitude, current.latitude, current.longitude, result);
      if (!Float.isFinite(result[0]) || result[0] < 0) {
        throw new IllegalArgumentException("Legacy route contains an invalid segment");
      }
      long elapsedDelta = current.elapsedMilliseconds - previous.elapsedMilliseconds;
      double speed = result[0] / (elapsedDelta / 1000.0);
      if (!Double.isFinite(speed) || speed > MAX_RUNNING_SEGMENT_SPEED_METERS_PER_SECOND) {
        throw new IllegalArgumentException("Legacy route contains an implausible GPS segment");
      }
      total += result[0];
      if (!Double.isFinite(total))
        throw new IllegalArgumentException("Legacy route distance is invalid");
    }
    return total;
  }
}
