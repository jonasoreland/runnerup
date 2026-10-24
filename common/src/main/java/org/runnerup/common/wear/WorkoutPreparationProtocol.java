package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Bounded phone-to-watch workout preparation envelope, separate from runtime control messages. */
public final class WorkoutPreparationProtocol {
  public static final int CURRENT_VERSION = 1;
  public static final int MAX_WORKOUT_BYTES = 64 * 1024;
  private static final int MAGIC = 0x52575031; // RWP1
  public static final int MAX_ENCODED_BYTES = MAX_WORKOUT_BYTES + 512;
  private static final int MAX_LABEL_BYTES = 256;
  private static final int PREPARE = 1;
  private static final int ACKNOWLEDGEMENT = 2;
  private static final int CANCELLATION = 3;

  private WorkoutPreparationProtocol() {}

  public enum StorageDecision {
    REPLACE,
    DUPLICATE,
    CONFLICT
  }

  /** An exact retry of one session is safe; reusing its identity for different data is not. */
  public static StorageDecision assessReplacement(
      PrepareMessage existing, PrepareMessage incoming) {
    if (incoming == null) throw new IllegalArgumentException("Incoming workout is required");
    if (existing == null || !existing.sessionId.equals(incoming.sessionId)) {
      return StorageDecision.REPLACE;
    }
    return existing.workoutName.equals(incoming.workoutName)
            && existing.upcomingStep.equals(incoming.upcomingStep)
            && existing.workoutJson.equals(incoming.workoutJson)
            && existing.skipSupported == incoming.skipSupported
        ? StorageDecision.DUPLICATE
        : StorageDecision.CONFLICT;
  }

  public static byte[] encodePrepare(
      String sessionId,
      String requestId,
      String workoutName,
      String upcomingStep,
      String workoutJson)
      throws IOException {
    return encodePrepare(sessionId, requestId, workoutName, upcomingStep, workoutJson, false);
  }

  public static byte[] encodePrepare(
      String sessionId,
      String requestId,
      String workoutName,
      String upcomingStep,
      String workoutJson,
      boolean skipSupported)
      throws IOException {
    validateUuid(sessionId, "session id");
    validateUuid(requestId, "request id");
    validateLabel(workoutName, "workout name");
    validateLabel(upcomingStep, "upcoming step");
    String preparedWorkoutJson = addSkipCapability(workoutJson, skipSupported);
    validateWorkoutJson(preparedWorkoutJson);
    byte[] workout = preparedWorkoutJson.getBytes(StandardCharsets.UTF_8);
    if (workout.length > MAX_WORKOUT_BYTES) throw new IOException("Workout payload too large");
    return encode(
        PREPARE,
        output -> {
          writeString(output, sessionId, 36, "session id");
          writeString(output, requestId, 36, "request id");
          writeString(output, workoutName, MAX_LABEL_BYTES, "workout name");
          writeString(output, upcomingStep, MAX_LABEL_BYTES, "upcoming step");
          output.writeInt(workout.length);
          output.write(workout);
        });
  }

  public static PrepareMessage decodePrepare(byte[] payload) throws IOException {
    DataInputStream input = open(payload, PREPARE);
    String sessionId = readString(input, 36, "session id");
    String requestId = readString(input, 36, "request id");
    String workoutName = readString(input, MAX_LABEL_BYTES, "workout name");
    String upcomingStep = readString(input, MAX_LABEL_BYTES, "upcoming step");
    int workoutLength = input.readInt();
    if (workoutLength <= 0
        || workoutLength > MAX_WORKOUT_BYTES
        || workoutLength > input.available()) {
      throw new IOException("Invalid workout payload length");
    }
    byte[] workout = new byte[workoutLength];
    input.readFully(workout);
    ensureConsumed(input);
    String workoutJson = new String(workout, StandardCharsets.UTF_8);
    validateUuid(sessionId, "session id");
    validateUuid(requestId, "request id");
    validateLabel(workoutName, "workout name");
    validateLabel(upcomingStep, "upcoming step");
    validateWorkoutJson(workoutJson);
    return new PrepareMessage(
        sessionId,
        requestId,
        workoutName,
        upcomingStep,
        workoutJson,
        readSkipCapability(workoutJson));
  }

  public static byte[] encodeAcknowledgement(String sessionId, String requestId, boolean accepted)
      throws IOException {
    validateUuid(sessionId, "session id");
    validateUuid(requestId, "request id");
    return encode(
        ACKNOWLEDGEMENT,
        output -> {
          writeString(output, sessionId, 36, "session id");
          writeString(output, requestId, 36, "request id");
          output.writeBoolean(accepted);
        });
  }

  public static Acknowledgement decodeAcknowledgement(byte[] payload) throws IOException {
    DataInputStream input = open(payload, ACKNOWLEDGEMENT);
    String sessionId = readString(input, 36, "session id");
    String requestId = readString(input, 36, "request id");
    boolean accepted = input.readBoolean();
    ensureConsumed(input);
    validateUuid(sessionId, "session id");
    validateUuid(requestId, "request id");
    return new Acknowledgement(sessionId, requestId, accepted);
  }

  public static byte[] encodeCancellation(String sessionId) throws IOException {
    validateUuid(sessionId, "session id");
    return encode(CANCELLATION, output -> writeString(output, sessionId, 36, "session id"));
  }

  public static Cancellation decodeCancellation(byte[] payload) throws IOException {
    DataInputStream input = open(payload, CANCELLATION);
    String sessionId = readString(input, 36, "session id");
    ensureConsumed(input);
    validateUuid(sessionId, "session id");
    return new Cancellation(sessionId);
  }

  private static byte[] encode(int type, Writer writer) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeByte(CURRENT_VERSION);
      output.writeByte(type);
      writer.write(output);
    }
    if (bytes.size() > MAX_ENCODED_BYTES) throw new IOException("Preparation payload too large");
    return bytes.toByteArray();
  }

  private static DataInputStream open(byte[] payload, int expectedType) throws IOException {
    if (payload == null || payload.length < 6 || payload.length > MAX_ENCODED_BYTES) {
      throw new IOException("Invalid preparation payload size");
    }
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
    if (input.readInt() != MAGIC) throw new IOException("Invalid preparation payload header");
    if (input.readUnsignedByte() != CURRENT_VERSION)
      throw new IOException("Unsupported protocol version");
    if (input.readUnsignedByte() != expectedType)
      throw new IOException("Unexpected preparation message type");
    return input;
  }

  private static void writeString(DataOutputStream output, String value, int maximum, String label)
      throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length == 0 || bytes.length > maximum) throw new IOException("Invalid " + label);
    output.writeShort(bytes.length);
    output.write(bytes);
  }

  private static String readString(DataInputStream input, int maximum, String label)
      throws IOException {
    int length = input.readUnsignedShort();
    if (length == 0 || length > maximum || length > input.available()) {
      throw new IOException("Invalid " + label + " length");
    }
    byte[] bytes = new byte[length];
    input.readFully(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static void validateUuid(String value, String label) throws IOException {
    try {
      if (value == null || !UUID.fromString(value).toString().equals(value)) {
        throw new IOException("Invalid " + label);
      }
    } catch (IllegalArgumentException error) {
      throw new IOException("Invalid " + label, error);
    }
  }

  private static void validateLabel(String value, String label) throws IOException {
    if (value == null || value.trim().isEmpty()) throw new IOException("Invalid " + label);
    if (value.getBytes(StandardCharsets.UTF_8).length > MAX_LABEL_BYTES) {
      throw new IOException("Invalid " + label + " length");
    }
  }

  private static void validateWorkoutJson(String json) throws IOException {
    if (json == null) throw new IOException("Invalid workout JSON");
    try {
      JSONObject root = new JSONObject(json);
      JSONObject workout = root.getJSONObject("com.garmin.connect.workout.json.UserWorkoutJson");
      JSONArray steps = workout.getJSONArray("workoutSteps");
      for (int index = 0; index < steps.length(); index++) steps.getJSONObject(index);
    } catch (JSONException malformed) {
      throw new IOException("Invalid workout document", malformed);
    }
  }

  private static String addSkipCapability(String json, boolean skipSupported) throws IOException {
    if (!skipSupported) return json;
    try {
      return new JSONObject(json).put("relvenSkipSupported", skipSupported).toString();
    } catch (JSONException malformed) {
      throw new IOException("Invalid workout document", malformed);
    }
  }

  private static boolean readSkipCapability(String json) throws IOException {
    try {
      return new JSONObject(json).optBoolean("relvenSkipSupported", false);
    } catch (JSONException malformed) {
      throw new IOException("Invalid workout document", malformed);
    }
  }

  private static void ensureConsumed(DataInputStream input) throws IOException {
    if (input.available() != 0) throw new IOException("Trailing preparation payload data");
  }

  private interface Writer {
    void write(DataOutputStream output) throws IOException;
  }

  public static final class PrepareMessage {
    private final String sessionId;
    private final String requestId;
    private final String workoutName;
    private final String upcomingStep;
    private final String workoutJson;
    private final boolean skipSupported;

    private PrepareMessage(
        String sessionId,
        String requestId,
        String workoutName,
        String upcomingStep,
        String workoutJson,
        boolean skipSupported) {
      this.sessionId = sessionId;
      this.requestId = requestId;
      this.workoutName = workoutName;
      this.upcomingStep = upcomingStep;
      this.workoutJson = workoutJson;
      this.skipSupported = skipSupported;
    }

    public String getSessionId() {
      return sessionId;
    }

    public String getRequestId() {
      return requestId;
    }

    public String getWorkoutName() {
      return workoutName;
    }

    public String getUpcomingStep() {
      return upcomingStep;
    }

    public String getWorkoutJson() {
      return workoutJson;
    }

    public boolean isSkipSupported() {
      return skipSupported;
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
