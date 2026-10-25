package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.runnerup.common.util.Constants;

/** Bounded latest-state envelope for phone-owned GPS preparation readiness. */
public final class GpsPreparationProtocol {
  private static final int MAGIC = 0x52475031; // RGP1
  private static final int VERSION = 1;
  private static final int MAX_PAYLOAD_BYTES = 64;
  private static final int MAX_ACCURACY_METERS = 10_000;
  public static final String DATA_KEY = "gps_readiness_payload";

  private GpsPreparationProtocol() {}

  public enum Status {
    NOT_REQUIRED(0),
    PERMISSION_REQUIRED(1),
    UNAVAILABLE(2),
    DISABLED(3),
    SEARCHING(4),
    FIX_ACQUIRED(5),
    ACCURACY_INSUFFICIENT(6),
    READY(7);

    private final int wireValue;

    Status(int wireValue) {
      this.wireValue = wireValue;
    }

    private static Status fromWireValue(int value) throws IOException {
      for (Status status : values()) {
        if (status.wireValue == value) return status;
      }
      throw new IOException("Unknown GPS readiness status");
    }
  }

  public static final class Readiness {
    private final String sessionId;
    private final long revision;
    private final Status status;
    private final Float accuracyMeters;

    private Readiness(String sessionId, long revision, Status status, Float accuracyMeters) {
      this.sessionId = sessionId;
      this.revision = revision;
      this.status = status;
      this.accuracyMeters = accuracyMeters;
    }

    public String getSessionId() {
      return sessionId;
    }

    public long getRevision() {
      return revision;
    }

    public Status getStatus() {
      return status;
    }

    public Float getAccuracyMeters() {
      return accuracyMeters;
    }
  }

  public static byte[] encode(String sessionId, long revision, Status status, Float accuracyMeters)
      throws IOException {
    validateSessionId(sessionId);
    if (revision <= 0L) throw new IOException("Invalid GPS readiness revision");
    if (status == null) throw new IOException("GPS readiness status is required");
    validateAccuracy(accuracyMeters);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeByte(VERSION);
      output.writeUTF(sessionId);
      output.writeLong(revision);
      output.writeByte(status.wireValue);
      output.writeBoolean(accuracyMeters != null);
      if (accuracyMeters != null) output.writeFloat(accuracyMeters);
    }
    if (bytes.size() > MAX_PAYLOAD_BYTES) throw new IOException("GPS readiness payload too large");
    return bytes.toByteArray();
  }

  public static Readiness decode(byte[] payload) throws IOException {
    if (payload == null || payload.length < 6 || payload.length > MAX_PAYLOAD_BYTES) {
      throw new IOException("Invalid GPS readiness payload size");
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (input.readInt() != MAGIC) throw new IOException("Invalid GPS readiness header");
      if (input.readUnsignedByte() != VERSION)
        throw new IOException("Unsupported GPS readiness version");
      String sessionId = input.readUTF();
      long revision = input.readLong();
      Status status = Status.fromWireValue(input.readUnsignedByte());
      Float accuracyMeters = input.readBoolean() ? input.readFloat() : null;
      if (input.available() != 0) throw new IOException("Trailing GPS readiness payload data");
      validateSessionId(sessionId);
      if (revision <= 0L) throw new IOException("Invalid GPS readiness revision");
      validateAccuracy(accuracyMeters);
      return new Readiness(sessionId, revision, status, accuracyMeters);
    } catch (IllegalArgumentException malformed) {
      throw new IOException("Invalid GPS readiness value", malformed);
    }
  }

  public static String dataItemPath(String sessionId) {
    try {
      validateSessionId(sessionId);
    } catch (IOException invalid) {
      throw new IllegalArgumentException("Invalid session ID", invalid);
    }
    return Constants.Wear.Path.GPS_READINESS_PREFIX + "/" + sessionId;
  }

  public static String sessionIdFromDataItemPath(String path) {
    if (path == null || !path.startsWith(Constants.Wear.Path.GPS_READINESS_PREFIX + "/")) {
      return null;
    }
    String sessionId = path.substring((Constants.Wear.Path.GPS_READINESS_PREFIX + "/").length());
    try {
      validateSessionId(sessionId);
      return sessionId;
    } catch (IOException invalid) {
      return null;
    }
  }

  private static void validateSessionId(String sessionId) throws IOException {
    try {
      if (sessionId == null || !UUID.fromString(sessionId).toString().equals(sessionId)) {
        throw new IOException("Invalid GPS readiness session ID");
      }
    } catch (IllegalArgumentException malformed) {
      throw new IOException("Invalid GPS readiness session ID", malformed);
    }
    if (sessionId.getBytes(StandardCharsets.UTF_8).length != 36) {
      throw new IOException("Invalid GPS readiness session ID length");
    }
  }

  private static void validateAccuracy(Float accuracyMeters) throws IOException {
    if (accuracyMeters == null) return;
    if (!Float.isFinite(accuracyMeters)
        || accuracyMeters <= 0f
        || accuracyMeters > MAX_ACCURACY_METERS) {
      throw new IOException("Invalid GPS readiness accuracy");
    }
  }
}
