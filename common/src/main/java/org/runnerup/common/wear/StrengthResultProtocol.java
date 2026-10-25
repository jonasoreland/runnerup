package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Versioned, bounded Wear message for one explicitly confirmed strength set result. */
public final class StrengthResultProtocol {
  public static final int CURRENT_VERSION = 1;
  public static final int MAX_ENCODED_BYTES = 4_096;
  private static final int MAGIC = 0x52535231; // RSR1
  private static final int RESULT = 1;
  private static final int ACKNOWLEDGEMENT = 2;
  private static final int MAX_STEP_ID_BYTES = 128;
  private static final int MAX_NAME_BYTES = 256;
  private static final int MAX_NOTES_BYTES = 1_000;

  private StrengthResultProtocol() {}

  /**
   * Deterministic event identity lets phone and watch independently confirm one set without
   * duplication.
   */
  public static String stableResultId(String executionId, String exerciseStepId, int setNumber) {
    return stableResultId(executionId, 0, exerciseStepId, setNumber);
  }

  /**
   * Includes expanded exercise position because repeated workout blocks reuse their source step ID.
   */
  public static String stableResultId(
      String executionId, int exerciseIndex, String exerciseStepId, int setNumber) {
    requireUuid(executionId, "execution id");
    if (exerciseIndex < 0) throw new IllegalArgumentException("Invalid exercise index");
    requireText(exerciseStepId, MAX_STEP_ID_BYTES, "exercise step id");
    if (setNumber < 1) throw new IllegalArgumentException("Invalid set number");
    byte[] identity =
        (executionId + "\n" + exerciseIndex + "\n" + exerciseStepId + "\n" + setNumber)
            .getBytes(StandardCharsets.UTF_8);
    return UUID.nameUUIDFromBytes(identity).toString();
  }

  public enum ResultStatus {
    COMPLETED,
    SKIPPED,
    INCOMPLETE
  }

  public enum AckOutcome {
    COMMITTED,
    DUPLICATE,
    CONFLICT,
    SESSION_MISSING
  }

  public static final class Result {
    private final String executionId;
    private final String resultId;
    private final int exerciseIndex;
    private final String exerciseStepId;
    private final String exerciseName;
    private final int setNumber;
    private final Integer repetitions;
    private final Integer durationSeconds;
    private final Long startedAtMillis;
    private final Long completedAtMillis;
    private final Integer restDurationSeconds;
    private final Integer leftRepetitions;
    private final Integer rightRepetitions;
    private final Double rpe;
    private final Integer painRating;
    private final String notes;
    private final ResultStatus status;

    public Result(
        String executionId,
        String resultId,
        int exerciseIndex,
        String exerciseStepId,
        String exerciseName,
        int setNumber,
        Integer repetitions) {
      this(
          executionId,
          resultId,
          exerciseIndex,
          exerciseStepId,
          exerciseName,
          setNumber,
          repetitions,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          ResultStatus.COMPLETED);
    }

    public Result(
        String executionId,
        String resultId,
        int exerciseIndex,
        String exerciseStepId,
        String exerciseName,
        int setNumber,
        Integer repetitions,
        Integer durationSeconds,
        Long startedAtMillis,
        Long completedAtMillis,
        Integer restDurationSeconds,
        Integer leftRepetitions,
        Integer rightRepetitions,
        Double rpe,
        Integer painRating,
        String notes) {
      this(
          executionId,
          resultId,
          exerciseIndex,
          exerciseStepId,
          exerciseName,
          setNumber,
          repetitions,
          durationSeconds,
          startedAtMillis,
          completedAtMillis,
          restDurationSeconds,
          leftRepetitions,
          rightRepetitions,
          rpe,
          painRating,
          notes,
          ResultStatus.COMPLETED);
    }

    public Result(
        String executionId,
        String resultId,
        int exerciseIndex,
        String exerciseStepId,
        String exerciseName,
        int setNumber,
        Integer repetitions,
        Integer durationSeconds,
        Long startedAtMillis,
        Long completedAtMillis,
        Integer restDurationSeconds,
        Integer leftRepetitions,
        Integer rightRepetitions,
        Double rpe,
        Integer painRating,
        String notes,
        ResultStatus status) {
      this.executionId = executionId;
      this.resultId = resultId;
      this.exerciseIndex = exerciseIndex;
      this.exerciseStepId = exerciseStepId;
      this.exerciseName = exerciseName;
      this.setNumber = setNumber;
      this.repetitions = repetitions;
      this.durationSeconds = durationSeconds;
      this.startedAtMillis = startedAtMillis;
      this.completedAtMillis = completedAtMillis;
      this.restDurationSeconds = restDurationSeconds;
      this.leftRepetitions = leftRepetitions;
      this.rightRepetitions = rightRepetitions;
      this.rpe = rpe;
      this.painRating = painRating;
      this.notes = notes;
      this.status = status;
      validate();
    }

    private void validate() {
      requireUuid(executionId, "execution id");
      requireUuid(resultId, "result id");
      if (exerciseIndex < 0 || setNumber < 1)
        throw new IllegalArgumentException("Invalid set identity");
      requireText(exerciseStepId, MAX_STEP_ID_BYTES, "exercise step id");
      requireText(exerciseName, MAX_NAME_BYTES, "exercise name");
      if (status != ResultStatus.COMPLETED)
        throw new IllegalArgumentException("Only confirmed results can sync");
      if (repetitions == null
          && durationSeconds == null
          && leftRepetitions == null
          && rightRepetitions == null) {
        throw new IllegalArgumentException("Result has no measured performance");
      }
      if (repetitions != null && (repetitions < 0 || repetitions > 10_000)) {
        throw new IllegalArgumentException("Invalid repetition count");
      }
      if (durationSeconds != null && (durationSeconds < 0 || durationSeconds > 86_400)) {
        throw new IllegalArgumentException("Invalid duration");
      }
      if ((leftRepetitions == null) != (rightRepetitions == null)) {
        throw new IllegalArgumentException("Both side counts are required");
      }
      if (leftRepetitions != null && (leftRepetitions < 0 || leftRepetitions > 10_000)) {
        throw new IllegalArgumentException("Invalid left repetition count");
      }
      if (rightRepetitions != null && (rightRepetitions < 0 || rightRepetitions > 10_000)) {
        throw new IllegalArgumentException("Invalid right repetition count");
      }
      if (startedAtMillis != null && startedAtMillis <= 0)
        throw new IllegalArgumentException("Invalid start time");
      if (completedAtMillis != null && completedAtMillis <= 0)
        throw new IllegalArgumentException("Invalid completion time");
      if (startedAtMillis != null
          && completedAtMillis != null
          && completedAtMillis < startedAtMillis) {
        throw new IllegalArgumentException("Result timestamps are out of order");
      }
      if (restDurationSeconds != null
          && (restDurationSeconds < 0 || restDurationSeconds > 86_400)) {
        throw new IllegalArgumentException("Invalid rest duration");
      }
      if (rpe != null && (!Double.isFinite(rpe) || rpe < 0 || rpe > 10)) {
        throw new IllegalArgumentException("Invalid perceived exertion");
      }
      if (painRating != null && (painRating < 0 || painRating > 10)) {
        throw new IllegalArgumentException("Invalid discomfort rating");
      }
      if (notes != null && notes.getBytes(StandardCharsets.UTF_8).length > MAX_NOTES_BYTES) {
        throw new IllegalArgumentException("Notes are too long");
      }
    }

    public String getExecutionId() {
      return executionId;
    }

    public String getResultId() {
      return resultId;
    }

    public int getExerciseIndex() {
      return exerciseIndex;
    }

    public String getExerciseStepId() {
      return exerciseStepId;
    }

    public String getExerciseName() {
      return exerciseName;
    }

    public int getSetNumber() {
      return setNumber;
    }

    public Integer getRepetitions() {
      return repetitions;
    }

    public Integer getDurationSeconds() {
      return durationSeconds;
    }

    public Long getStartedAtMillis() {
      return startedAtMillis;
    }

    public Long getCompletedAtMillis() {
      return completedAtMillis;
    }

    public Integer getRestDurationSeconds() {
      return restDurationSeconds;
    }

    public Integer getLeftRepetitions() {
      return leftRepetitions;
    }

    public Integer getRightRepetitions() {
      return rightRepetitions;
    }

    public Double getRpe() {
      return rpe;
    }

    public Integer getPainRating() {
      return painRating;
    }

    public String getNotes() {
      return notes;
    }

    public ResultStatus getStatus() {
      return status;
    }

    public Result withStatus(ResultStatus next) {
      return new Result(
          executionId,
          resultId,
          exerciseIndex,
          exerciseStepId,
          exerciseName,
          setNumber,
          repetitions,
          durationSeconds,
          startedAtMillis,
          completedAtMillis,
          restDurationSeconds,
          leftRepetitions,
          rightRepetitions,
          rpe,
          painRating,
          notes,
          next);
    }

    public Result withMeasurements(
        Integer nextRepetitions, Integer nextDuration, Integer nextLeft, Integer nextRight) {
      return new Result(
          executionId,
          resultId,
          exerciseIndex,
          exerciseStepId,
          exerciseName,
          setNumber,
          nextRepetitions,
          nextDuration,
          startedAtMillis,
          completedAtMillis,
          restDurationSeconds,
          nextLeft,
          nextRight,
          rpe,
          painRating,
          notes,
          status);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Result)) return false;
      Result that = (Result) other;
      return exerciseIndex == that.exerciseIndex
          && setNumber == that.setNumber
          && Objects.equals(executionId, that.executionId)
          && Objects.equals(resultId, that.resultId)
          && Objects.equals(exerciseStepId, that.exerciseStepId)
          && Objects.equals(exerciseName, that.exerciseName)
          && Objects.equals(repetitions, that.repetitions)
          && Objects.equals(durationSeconds, that.durationSeconds)
          && Objects.equals(startedAtMillis, that.startedAtMillis)
          && Objects.equals(completedAtMillis, that.completedAtMillis)
          && Objects.equals(restDurationSeconds, that.restDurationSeconds)
          && Objects.equals(leftRepetitions, that.leftRepetitions)
          && Objects.equals(rightRepetitions, that.rightRepetitions)
          && Objects.equals(rpe, that.rpe)
          && Objects.equals(painRating, that.painRating)
          && Objects.equals(notes, that.notes)
          && status == that.status;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          executionId,
          resultId,
          exerciseIndex,
          exerciseStepId,
          exerciseName,
          setNumber,
          repetitions,
          durationSeconds,
          startedAtMillis,
          completedAtMillis,
          restDurationSeconds,
          leftRepetitions,
          rightRepetitions,
          rpe,
          painRating,
          notes,
          status);
    }
  }

  public static final class Acknowledgement {
    private final String executionId;
    private final String resultId;
    private final AckOutcome outcome;

    public Acknowledgement(String executionId, String resultId, AckOutcome outcome) {
      requireUuid(executionId, "execution id");
      requireUuid(resultId, "result id");
      if (outcome == null) throw new IllegalArgumentException("Missing acknowledgement outcome");
      this.executionId = executionId;
      this.resultId = resultId;
      this.outcome = outcome;
    }

    public String getExecutionId() {
      return executionId;
    }

    public String getResultId() {
      return resultId;
    }

    public AckOutcome getOutcome() {
      return outcome;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Acknowledgement)) return false;
      Acknowledgement that = (Acknowledgement) other;
      return executionId.equals(that.executionId)
          && resultId.equals(that.resultId)
          && outcome == that.outcome;
    }

    @Override
    public int hashCode() {
      return Objects.hash(executionId, resultId, outcome);
    }
  }

  public static byte[] encodeResult(Result result) throws IOException {
    if (result == null) throw new IOException("Missing result");
    try {
      result.validate();
      return encode(RESULT, output -> writeResult(output, result));
    } catch (IllegalArgumentException invalid) {
      throw new IOException("Invalid strength result", invalid);
    }
  }

  public static Result decodeResult(byte[] payload) throws IOException {
    DataInputStream input = open(payload, RESULT);
    String executionId = readString(input, 36, "execution id");
    String resultId = readString(input, 36, "result id");
    int exerciseIndex = input.readInt();
    String stepId = readString(input, MAX_STEP_ID_BYTES, "exercise step id");
    String name = readString(input, MAX_NAME_BYTES, "exercise name");
    int setNumber = input.readInt();
    Integer repetitions = readInt(input);
    Integer duration = readInt(input);
    Long startedAt = readLong(input);
    Long completedAt = readLong(input);
    Integer restDuration = readInt(input);
    Integer left = readInt(input);
    Integer right = readInt(input);
    Double rpe = input.readBoolean() ? input.readDouble() : null;
    Integer pain = readInt(input);
    String notes = readNullableString(input, MAX_NOTES_BYTES, "notes");
    int statusOrdinal = input.readUnsignedByte();
    ensureConsumed(input);
    if (statusOrdinal >= ResultStatus.values().length)
      throw new IOException("Unknown result status");
    try {
      return new Result(
          executionId,
          resultId,
          exerciseIndex,
          stepId,
          name,
          setNumber,
          repetitions,
          duration,
          startedAt,
          completedAt,
          restDuration,
          left,
          right,
          rpe,
          pain,
          notes,
          ResultStatus.values()[statusOrdinal]);
    } catch (IllegalArgumentException invalid) {
      throw new IOException("Invalid strength result", invalid);
    }
  }

  public static byte[] encodeAcknowledgement(Acknowledgement acknowledgement) throws IOException {
    if (acknowledgement == null) throw new IOException("Missing acknowledgement");
    return encode(
        ACKNOWLEDGEMENT,
        output -> {
          writeString(output, acknowledgement.executionId, 36, "execution id");
          writeString(output, acknowledgement.resultId, 36, "result id");
          output.writeByte(acknowledgement.outcome.ordinal());
        });
  }

  public static Acknowledgement decodeAcknowledgement(byte[] payload) throws IOException {
    DataInputStream input = open(payload, ACKNOWLEDGEMENT);
    String executionId = readString(input, 36, "execution id");
    String resultId = readString(input, 36, "result id");
    int outcome = input.readUnsignedByte();
    ensureConsumed(input);
    if (outcome >= AckOutcome.values().length)
      throw new IOException("Unknown acknowledgement outcome");
    try {
      return new Acknowledgement(executionId, resultId, AckOutcome.values()[outcome]);
    } catch (IllegalArgumentException invalid) {
      throw new IOException("Invalid acknowledgement", invalid);
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
      throw new IOException("Strength result message too large");
    return bytes.toByteArray();
  }

  private static DataInputStream open(byte[] payload, int type) throws IOException {
    if (payload == null || payload.length < 6 || payload.length > MAX_ENCODED_BYTES) {
      throw new IOException("Invalid strength result payload size");
    }
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
    if (input.readInt() != MAGIC) throw new IOException("Invalid strength result header");
    if (input.readUnsignedByte() != CURRENT_VERSION)
      throw new IOException("Unsupported strength result version");
    if (input.readUnsignedByte() != type)
      throw new IOException("Unexpected strength result message type");
    return input;
  }

  private static void writeResult(DataOutputStream output, Result result) throws IOException {
    writeString(output, result.executionId, 36, "execution id");
    writeString(output, result.resultId, 36, "result id");
    output.writeInt(result.exerciseIndex);
    writeString(output, result.exerciseStepId, MAX_STEP_ID_BYTES, "exercise step id");
    writeString(output, result.exerciseName, MAX_NAME_BYTES, "exercise name");
    output.writeInt(result.setNumber);
    writeInt(output, result.repetitions);
    writeInt(output, result.durationSeconds);
    writeLong(output, result.startedAtMillis);
    writeLong(output, result.completedAtMillis);
    writeInt(output, result.restDurationSeconds);
    writeInt(output, result.leftRepetitions);
    writeInt(output, result.rightRepetitions);
    output.writeBoolean(result.rpe != null);
    if (result.rpe != null) output.writeDouble(result.rpe);
    writeInt(output, result.painRating);
    writeNullableString(output, result.notes, MAX_NOTES_BYTES, "notes");
    output.writeByte(result.status.ordinal());
  }

  private static void writeString(DataOutputStream output, String value, int max, String label)
      throws IOException {
    byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
    if (encoded.length == 0 || encoded.length > max || value.trim().isEmpty())
      throw new IOException("Invalid " + label);
    output.writeShort(encoded.length);
    output.write(encoded);
  }

  private static String readString(DataInputStream input, int max, String label)
      throws IOException {
    int length = input.readUnsignedShort();
    if (length == 0 || length > max || length > input.available())
      throw new IOException("Invalid " + label + " length");
    byte[] bytes = new byte[length];
    input.readFully(bytes);
    String value = new String(bytes, StandardCharsets.UTF_8);
    if (value.trim().isEmpty()) throw new IOException("Invalid " + label);
    return value;
  }

  private static void writeNullableString(
      DataOutputStream output, String value, int max, String label) throws IOException {
    output.writeBoolean(value != null);
    if (value != null) writeString(output, value, max, label);
  }

  private static String readNullableString(DataInputStream input, int max, String label)
      throws IOException {
    return input.readBoolean() ? readString(input, max, label) : null;
  }

  private static void writeInt(DataOutputStream output, Integer value) throws IOException {
    output.writeBoolean(value != null);
    if (value != null) output.writeInt(value);
  }

  private static Integer readInt(DataInputStream input) throws IOException {
    return input.readBoolean() ? input.readInt() : null;
  }

  private static void writeLong(DataOutputStream output, Long value) throws IOException {
    output.writeBoolean(value != null);
    if (value != null) output.writeLong(value);
  }

  private static Long readLong(DataInputStream input) throws IOException {
    return input.readBoolean() ? input.readLong() : null;
  }

  private static void requireUuid(String value, String label) {
    try {
      if (value == null || !UUID.fromString(value).toString().equals(value))
        throw new IllegalArgumentException("Invalid " + label);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("Invalid " + label, invalid);
    }
  }

  private static void requireText(String value, int maxBytes, String label) {
    if (value == null
        || value.trim().isEmpty()
        || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
      throw new IllegalArgumentException("Invalid " + label);
    }
  }

  private static void ensureConsumed(DataInputStream input) throws IOException {
    if (input.available() != 0) throw new IOException("Trailing strength result payload bytes");
  }

  private interface Writer {
    void write(DataOutputStream output) throws IOException;
  }
}
