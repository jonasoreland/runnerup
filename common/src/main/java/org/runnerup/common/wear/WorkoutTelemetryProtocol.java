package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Versioned, bounded wire format for watch-origin workout telemetry. */
public final class WorkoutTelemetryProtocol {
  public static final int MAX_BATCH_SAMPLES = 64;
  public static final int MAX_PAYLOAD_BYTES = 32 * 1024;
  private static final int BATCH_MAGIC = 0x57544231; // WTB1
  private static final int ACK_MAGIC = 0x57544131; // WTA1
  private static final int VERSION = 1;
  private static final int LOCATION = 1;
  private static final int ACCURACY = 2;
  private static final int ALTITUDE = 4;
  private static final int SPEED = 8;
  private static final int HEART_RATE = 16;

  private WorkoutTelemetryProtocol() {}

  public static final class Sample {
    public final String sessionId;
    public final long sequence;
    public final long watchElapsedRealtimeNanos;
    public final long wallTimeMillis;
    public final Double latitude;
    public final Double longitude;
    public final Double horizontalAccuracyMeters;
    public final Double altitudeMeters;
    public final Double speedMetersPerSecond;
    public final Double heartRateBpm;

    public Sample(
        String sessionId,
        long sequence,
        long watchElapsedRealtimeNanos,
        long wallTimeMillis,
        Double latitude,
        Double longitude,
        Double horizontalAccuracyMeters,
        Double altitudeMeters,
        Double speedMetersPerSecond,
        Double heartRateBpm) {
      validateSessionId(sessionId);
      if (sequence <= 0 || watchElapsedRealtimeNanos < 0 || wallTimeMillis < 0) {
        throw new IllegalArgumentException("Invalid telemetry identity or timestamp");
      }
      boolean hasLatitude = latitude != null;
      boolean hasLongitude = longitude != null;
      if (hasLatitude != hasLongitude) throw new IllegalArgumentException("Incomplete coordinates");
      if ((horizontalAccuracyMeters != null
              || altitudeMeters != null
              || speedMetersPerSecond != null)
          && !hasLatitude)
        throw new IllegalArgumentException("Location fields require coordinates");
      if (hasLatitude) {
        finiteRange(latitude, -90, 90, "latitude");
        finiteRange(longitude, -180, 180, "longitude");
      }
      if (horizontalAccuracyMeters != null)
        finiteRange(horizontalAccuracyMeters, 0, 1000, "accuracy");
      if (altitudeMeters != null) finiteRange(altitudeMeters, -1000, 10000, "altitude");
      if (speedMetersPerSecond != null) finiteRange(speedMetersPerSecond, 0, 150, "speed");
      if (heartRateBpm != null) finiteRange(heartRateBpm, 20, 250, "heart rate");
      this.sessionId = sessionId;
      this.sequence = sequence;
      this.watchElapsedRealtimeNanos = watchElapsedRealtimeNanos;
      this.wallTimeMillis = wallTimeMillis;
      this.latitude = latitude;
      this.longitude = longitude;
      this.horizontalAccuracyMeters = horizontalAccuracyMeters;
      this.altitudeMeters = altitudeMeters;
      this.speedMetersPerSecond = speedMetersPerSecond;
      this.heartRateBpm = heartRateBpm;
    }

    @Override
    public boolean equals(Object value) {
      if (!(value instanceof Sample)) return false;
      Sample other = (Sample) value;
      return sequence == other.sequence
          && watchElapsedRealtimeNanos == other.watchElapsedRealtimeNanos
          && wallTimeMillis == other.wallTimeMillis
          && sessionId.equals(other.sessionId)
          && Objects.equals(latitude, other.latitude)
          && Objects.equals(longitude, other.longitude)
          && Objects.equals(horizontalAccuracyMeters, other.horizontalAccuracyMeters)
          && Objects.equals(altitudeMeters, other.altitudeMeters)
          && Objects.equals(speedMetersPerSecond, other.speedMetersPerSecond)
          && Objects.equals(heartRateBpm, other.heartRateBpm);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          sessionId,
          sequence,
          watchElapsedRealtimeNanos,
          wallTimeMillis,
          latitude,
          longitude,
          horizontalAccuracyMeters,
          altitudeMeters,
          speedMetersPerSecond,
          heartRateBpm);
    }
  }

  public static final class Batch {
    public final String sessionId;
    public final List<Sample> samples;

    public Batch(String sessionId, List<Sample> samples) {
      validateSessionId(sessionId);
      if (samples == null || samples.isEmpty() || samples.size() > MAX_BATCH_SAMPLES) {
        throw new IllegalArgumentException("Invalid telemetry batch size");
      }
      long expected = samples.get(0).sequence;
      List<Sample> copy = new ArrayList<>(samples.size());
      for (Sample sample : samples) {
        if (sample == null
            || !sessionId.equals(sample.sessionId)
            || sample.sequence != expected++) {
          throw new IllegalArgumentException(
              "Telemetry batch must be session-matched and contiguous");
        }
        copy.add(sample);
      }
      this.sessionId = sessionId;
      this.samples = Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(Object value) {
      return value instanceof Batch
          && sessionId.equals(((Batch) value).sessionId)
          && samples.equals(((Batch) value).samples);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sessionId, samples);
    }
  }

  public static final class Ack {
    public final String sessionId;
    public final long committedThroughSequence;

    public Ack(String sessionId, long committedThroughSequence) {
      validateSessionId(sessionId);
      if (committedThroughSequence < 0) throw new IllegalArgumentException("Invalid telemetry ack");
      this.sessionId = sessionId;
      this.committedThroughSequence = committedThroughSequence;
    }

    @Override
    public boolean equals(Object value) {
      return value instanceof Ack
          && sessionId.equals(((Ack) value).sessionId)
          && committedThroughSequence == ((Ack) value).committedThroughSequence;
    }

    @Override
    public int hashCode() {
      return Objects.hash(sessionId, committedThroughSequence);
    }
  }

  public static byte[] encodeBatch(Batch batch) throws IOException {
    if (batch == null) throw new IllegalArgumentException("Batch is required");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(BATCH_MAGIC);
      output.writeByte(VERSION);
      output.writeUTF(batch.sessionId);
      output.writeByte(batch.samples.size());
      for (Sample sample : batch.samples) writeSample(output, sample);
    }
    byte[] encoded = bytes.toByteArray();
    if (encoded.length > MAX_PAYLOAD_BYTES) throw new IOException("Telemetry batch is too large");
    return encoded;
  }

  public static Batch decodeBatch(byte[] payload) throws IOException {
    if (payload == null || payload.length < 6 || payload.length > MAX_PAYLOAD_BYTES) {
      throw new IOException("Invalid telemetry payload size");
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (input.readInt() != BATCH_MAGIC || input.readUnsignedByte() != VERSION) {
        throw new IOException("Unsupported telemetry protocol");
      }
      String session = input.readUTF();
      int count = input.readUnsignedByte();
      if (count == 0 || count > MAX_BATCH_SAMPLES) throw new IOException("Invalid telemetry count");
      List<Sample> samples = new ArrayList<>(count);
      for (int i = 0; i < count; i++) samples.add(readSample(input, session));
      if (input.available() != 0) throw new IOException("Trailing telemetry bytes");
      return new Batch(session, samples);
    } catch (IllegalArgumentException error) {
      throw new IOException("Invalid telemetry payload", error);
    }
  }

  public static byte[] encodeAck(Ack ack) throws IOException {
    if (ack == null) throw new IllegalArgumentException("Ack is required");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(ACK_MAGIC);
      output.writeByte(VERSION);
      output.writeUTF(ack.sessionId);
      output.writeLong(ack.committedThroughSequence);
    }
    return bytes.toByteArray();
  }

  public static Ack decodeAck(byte[] payload) throws IOException {
    if (payload == null || payload.length < 15 || payload.length > 512)
      throw new IOException("Invalid telemetry ack size");
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (input.readInt() != ACK_MAGIC || input.readUnsignedByte() != VERSION)
        throw new IOException("Unsupported telemetry ack");
      Ack ack = new Ack(input.readUTF(), input.readLong());
      if (input.available() != 0) throw new IOException("Trailing telemetry ack bytes");
      return ack;
    } catch (IllegalArgumentException error) {
      throw new IOException("Invalid telemetry ack", error);
    }
  }

  private static void writeSample(DataOutputStream output, Sample sample) throws IOException {
    output.writeLong(sample.sequence);
    output.writeLong(sample.watchElapsedRealtimeNanos);
    output.writeLong(sample.wallTimeMillis);
    int flags =
        (sample.latitude != null ? LOCATION : 0)
            | (sample.horizontalAccuracyMeters != null ? ACCURACY : 0)
            | (sample.altitudeMeters != null ? ALTITUDE : 0)
            | (sample.speedMetersPerSecond != null ? SPEED : 0)
            | (sample.heartRateBpm != null ? HEART_RATE : 0);
    output.writeByte(flags);
    if (sample.latitude != null) {
      output.writeDouble(sample.latitude);
      output.writeDouble(sample.longitude);
    }
    if (sample.horizontalAccuracyMeters != null)
      output.writeDouble(sample.horizontalAccuracyMeters);
    if (sample.altitudeMeters != null) output.writeDouble(sample.altitudeMeters);
    if (sample.speedMetersPerSecond != null) output.writeDouble(sample.speedMetersPerSecond);
    if (sample.heartRateBpm != null) output.writeDouble(sample.heartRateBpm);
  }

  private static Sample readSample(DataInputStream input, String session) throws IOException {
    long sequence = input.readLong();
    long elapsed = input.readLong();
    long wall = input.readLong();
    int flags = input.readUnsignedByte();
    if ((flags & ~(LOCATION | ACCURACY | ALTITUDE | SPEED | HEART_RATE)) != 0
        || ((flags & (ACCURACY | ALTITUDE | SPEED)) != 0 && (flags & LOCATION) == 0)) {
      throw new IOException("Invalid telemetry measurement flags");
    }
    Double lat = (flags & LOCATION) != 0 ? input.readDouble() : null;
    Double lon = (flags & LOCATION) != 0 ? input.readDouble() : null;
    Double accuracy = (flags & ACCURACY) != 0 ? input.readDouble() : null;
    Double altitude = (flags & ALTITUDE) != 0 ? input.readDouble() : null;
    Double speed = (flags & SPEED) != 0 ? input.readDouble() : null;
    Double hr = (flags & HEART_RATE) != 0 ? input.readDouble() : null;
    try {
      return new Sample(session, sequence, elapsed, wall, lat, lon, accuracy, altitude, speed, hr);
    } catch (IllegalArgumentException error) {
      throw new IOException("Invalid telemetry sample", error);
    }
  }

  private static void validateSessionId(String value) {
    if (value == null
        || value.trim().isEmpty()
        || value.getBytes(StandardCharsets.UTF_8).length > 128) {
      throw new IllegalArgumentException("Invalid telemetry session ID");
    }
  }

  private static void finiteRange(Double value, double minimum, double maximum, String name) {
    if (value == null || !Double.isFinite(value) || value < minimum || value > maximum) {
      throw new IllegalArgumentException("Invalid telemetry " + name);
    }
  }
}
