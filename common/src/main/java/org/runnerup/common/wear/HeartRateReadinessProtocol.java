package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/** Correlated, bounded readiness query for the Wear heart-rate sensor. */
public final class HeartRateReadinessProtocol {
  private static final int MAGIC = 0x57485231; // WHR1
  private static final int VERSION = 1;
  private static final int REQUEST = 1;
  private static final int RESPONSE = 2;
  private static final int MAX_PAYLOAD_BYTES = 96;

  private HeartRateReadinessProtocol() {}

  public enum Status {
    READY(1),
    SENSOR_UNAVAILABLE(2),
    PERMISSION_REQUIRED(3),
    BACKGROUND_PERMISSION_REQUIRED(4);

    private final int code;

    Status(int code) {
      this.code = code;
    }

    private static Status fromCode(int code) throws IOException {
      for (Status status : values()) if (status.code == code) return status;
      throw new IOException("Unknown heart-rate readiness status");
    }
  }

  public static byte[] encodeRequest(String sessionId, String requestId) throws IOException {
    validateUuid(sessionId, "session id");
    validateUuid(requestId, "request id");
    return encode(
        REQUEST,
        output -> {
          output.writeUTF(sessionId);
          output.writeUTF(requestId);
        });
  }

  public static Request decodeRequest(byte[] payload) throws IOException {
    DataInputStream input = open(payload, REQUEST);
    String sessionId = input.readUTF();
    String requestId = input.readUTF();
    ensureConsumed(input);
    validateUuid(sessionId, "session id");
    validateUuid(requestId, "request id");
    return new Request(sessionId, requestId);
  }

  public static byte[] encodeResponse(String sessionId, String requestId, Status status)
      throws IOException {
    validateUuid(sessionId, "session id");
    validateUuid(requestId, "request id");
    if (status == null) throw new IOException("Readiness status is required");
    return encode(
        RESPONSE,
        output -> {
          output.writeUTF(sessionId);
          output.writeUTF(requestId);
          output.writeByte(status.code);
        });
  }

  public static Response decodeResponse(byte[] payload) throws IOException {
    DataInputStream input = open(payload, RESPONSE);
    String sessionId = input.readUTF();
    String requestId = input.readUTF();
    Status status = Status.fromCode(input.readUnsignedByte());
    ensureConsumed(input);
    validateUuid(sessionId, "session id");
    validateUuid(requestId, "request id");
    return new Response(sessionId, requestId, status);
  }

  private static byte[] encode(int type, Writer writer) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeByte(VERSION);
      output.writeByte(type);
      writer.write(output);
    }
    if (bytes.size() > MAX_PAYLOAD_BYTES) throw new IOException("Readiness payload too large");
    return bytes.toByteArray();
  }

  private static DataInputStream open(byte[] payload, int expectedType) throws IOException {
    if (payload == null || payload.length < 6 || payload.length > MAX_PAYLOAD_BYTES) {
      throw new IOException("Invalid heart-rate readiness payload size");
    }
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
    if (input.readInt() != MAGIC) throw new IOException("Invalid heart-rate readiness magic");
    if (input.readUnsignedByte() != VERSION) throw new IOException("Unsupported readiness version");
    if (input.readUnsignedByte() != expectedType)
      throw new IOException("Unexpected readiness message");
    return input;
  }

  private static void ensureConsumed(DataInputStream input) throws IOException {
    if (input.available() != 0) throw new IOException("Unexpected readiness payload data");
  }

  private static void validateUuid(String value, String label) throws IOException {
    try {
      if (value == null || !UUID.fromString(value).toString().equals(value))
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException invalid) {
      throw new IOException("Invalid " + label, invalid);
    }
  }

  private interface Writer {
    void write(DataOutputStream output) throws IOException;
  }

  public static final class Request {
    private final String sessionId;
    private final String requestId;

    private Request(String sessionId, String requestId) {
      this.sessionId = sessionId;
      this.requestId = requestId;
    }

    public String getSessionId() {
      return sessionId;
    }

    public String getRequestId() {
      return requestId;
    }
  }

  public static final class Response {
    private final String sessionId;
    private final String requestId;
    private final Status status;

    private Response(String sessionId, String requestId, Status status) {
      this.sessionId = sessionId;
      this.requestId = requestId;
      this.status = status;
    }

    public boolean matches(Request request) {
      return request != null
          && sessionId.equals(request.sessionId)
          && requestId.equals(request.requestId);
    }

    public String getSessionId() {
      return sessionId;
    }

    public String getRequestId() {
      return requestId;
    }

    public Status getStatus() {
      return status;
    }
  }
}
