package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import kotlin.Pair;
import org.runnerup.relven.strength.StrengthSession;
import org.runnerup.relven.strength.StrengthSessionCodec;

/**
 * Bounded Wear handoff for a prepared Relven strength execution, separate from Tracker messages.
 */
public final class StrengthExecutionProtocol {
  public static final int CURRENT_VERSION = 1;
  public static final int MAX_SESSION_BYTES = 60 * 1024;
  public static final int MAX_ENCODED_BYTES = 64 * 1024;
  private static final int MAGIC = 0x52534531; // RSE1
  private static final int PREPARE = 1;
  private static final int ACKNOWLEDGEMENT = 2;
  private static final int CANCELLATION = 3;

  private StrengthExecutionProtocol() {}

  public enum StorageDecision {
    REPLACE,
    DUPLICATE,
    CONFLICT
  }

  /** Same execution identity may be retried only with the exact same prepared reducer state. */
  public static StorageDecision assessReplacement(
      PrepareMessage existing, PrepareMessage incoming) {
    if (incoming == null)
      throw new IllegalArgumentException("Incoming strength session is required");
    if (existing == null || !existing.sessionId.equals(incoming.sessionId)) {
      return StorageDecision.REPLACE;
    }
    return existing.sessionJson.equals(incoming.sessionJson)
        ? StorageDecision.DUPLICATE
        : StorageDecision.CONFLICT;
  }

  public static byte[] encodePrepare(StrengthSession session, String requestId) throws IOException {
    if (session == null || session.getPhase() != StrengthSession.Phase.PREPARING) {
      throw new IOException("Only a prepared strength session can be transferred");
    }
    String sessionId = session.getExecutionId();
    validateUuid(sessionId, "session id");
    validateUuid(requestId, "request id");
    final String sessionJson;
    try {
      sessionJson = StrengthSessionCodec.INSTANCE.encode(session, null);
      validateSession(sessionId, sessionJson);
    } catch (RuntimeException invalid) {
      throw new IOException("Invalid prepared strength session", invalid);
    }
    byte[] encodedSession = sessionJson.getBytes(StandardCharsets.UTF_8);
    if (encodedSession.length > MAX_SESSION_BYTES)
      throw new IOException("Strength session too large");
    return encode(
        PREPARE,
        output -> {
          writeUuid(output, sessionId);
          writeUuid(output, requestId);
          output.writeInt(encodedSession.length);
          output.write(encodedSession);
        });
  }

  public static PrepareMessage decodePrepare(byte[] payload) throws IOException {
    DataInputStream input = open(payload, PREPARE);
    String sessionId = readUuid(input, "session id");
    String requestId = readUuid(input, "request id");
    int length = input.readInt();
    if (length <= 0 || length > MAX_SESSION_BYTES || length > input.available()) {
      throw new IOException("Invalid strength session size");
    }
    byte[] sessionBytes = new byte[length];
    input.readFully(sessionBytes);
    ensureConsumed(input);
    String sessionJson = new String(sessionBytes, StandardCharsets.UTF_8);
    validateSession(sessionId, sessionJson);
    return new PrepareMessage(sessionId, requestId, sessionJson);
  }

  public static byte[] encodeAcknowledgement(String sessionId, String requestId, boolean accepted)
      throws IOException {
    validateUuid(sessionId, "session id");
    validateUuid(requestId, "request id");
    return encode(
        ACKNOWLEDGEMENT,
        output -> {
          writeUuid(output, sessionId);
          writeUuid(output, requestId);
          output.writeBoolean(accepted);
        });
  }

  public static Acknowledgement decodeAcknowledgement(byte[] payload) throws IOException {
    DataInputStream input = open(payload, ACKNOWLEDGEMENT);
    String sessionId = readUuid(input, "session id");
    String requestId = readUuid(input, "request id");
    boolean accepted = input.readBoolean();
    ensureConsumed(input);
    return new Acknowledgement(sessionId, requestId, accepted);
  }

  public static byte[] encodeCancellation(String sessionId) throws IOException {
    validateUuid(sessionId, "session id");
    return encode(CANCELLATION, output -> writeUuid(output, sessionId));
  }

  public static Cancellation decodeCancellation(byte[] payload) throws IOException {
    DataInputStream input = open(payload, CANCELLATION);
    String sessionId = readUuid(input, "session id");
    ensureConsumed(input);
    return new Cancellation(sessionId);
  }

  private static void validateSession(String sessionId, String sessionJson) throws IOException {
    if (sessionJson == null
        || sessionJson.getBytes(StandardCharsets.UTF_8).length > MAX_SESSION_BYTES) {
      throw new IOException("Invalid strength session size");
    }
    try {
      Pair<StrengthSession, Long> decoded = StrengthSessionCodec.INSTANCE.decode(sessionJson);
      StrengthSession state = decoded.getFirst();
      if (state == null
          || decoded.getSecond() != null
          || state.getPhase() != StrengthSession.Phase.PREPARING
          || !sessionId.equals(state.getExecutionId())
          || state.getExerciseIndex() != 0
          || state.getSetNumber() != 1
          || state.getTimerDeadlineElapsedRealtimeMillis() != null
          || state.getTimerAnchorElapsedRealtimeMillis() != null) {
        throw new IOException("Strength preparation must be idle and unstarted");
      }
    } catch (RuntimeException invalid) {
      throw new IOException("Invalid prepared strength session", invalid);
    }
  }

  private static byte[] encode(int type, Writer writer) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeByte(CURRENT_VERSION);
      output.writeByte(type);
      writer.write(output);
    }
    if (bytes.size() > MAX_ENCODED_BYTES)
      throw new IOException("Strength preparation payload too large");
    return bytes.toByteArray();
  }

  private static DataInputStream open(byte[] payload, int expectedType) throws IOException {
    if (payload == null || payload.length < 6 || payload.length > MAX_ENCODED_BYTES) {
      throw new IOException("Invalid strength preparation payload size");
    }
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
    if (input.readInt() != MAGIC) throw new IOException("Invalid strength preparation header");
    if (input.readUnsignedByte() != CURRENT_VERSION)
      throw new IOException("Unsupported protocol version");
    if (input.readUnsignedByte() != expectedType) throw new IOException("Unexpected message type");
    return input;
  }

  private static void writeUuid(DataOutputStream output, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
    output.writeByte(bytes.length);
    output.write(bytes);
  }

  private static String readUuid(DataInputStream input, String label) throws IOException {
    int length = input.readUnsignedByte();
    if (length != 36 || length > input.available())
      throw new IOException("Invalid " + label + " length");
    byte[] bytes = new byte[length];
    input.readFully(bytes);
    String value = new String(bytes, StandardCharsets.US_ASCII);
    validateUuid(value, label);
    return value;
  }

  private static void validateUuid(String value, String label) throws IOException {
    try {
      if (value == null || !UUID.fromString(value).toString().equals(value)) {
        throw new IOException("Invalid " + label);
      }
    } catch (IllegalArgumentException invalid) {
      throw new IOException("Invalid " + label, invalid);
    }
  }

  private static void ensureConsumed(DataInputStream input) throws IOException {
    if (input.available() != 0) throw new IOException("Trailing strength preparation data");
  }

  private interface Writer {
    void write(DataOutputStream output) throws IOException;
  }

  public static final class PrepareMessage {
    private final String sessionId;
    private final String requestId;
    private final String sessionJson;

    private PrepareMessage(String sessionId, String requestId, String sessionJson) {
      this.sessionId = sessionId;
      this.requestId = requestId;
      this.sessionJson = sessionJson;
    }

    public String getSessionId() {
      return sessionId;
    }

    public String getRequestId() {
      return requestId;
    }

    public String getSessionJson() {
      return sessionJson;
    }
  }

  public static final class Acknowledgement {
    private final String sessionId;
    private final String requestId;
    private final boolean accepted;

    private Acknowledgement(String sessionId, String requestId, boolean accepted) {
      this.sessionId = sessionId;
      this.requestId = requestId;
      this.accepted = accepted;
    }

    public boolean matches(PrepareMessage request) {
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

    public boolean isAccepted() {
      return accepted;
    }
  }

  public static final class Cancellation {
    private final String sessionId;

    private Cancellation(String sessionId) {
      this.sessionId = sessionId;
    }

    public String getSessionId() {
      return sessionId;
    }
  }
}
