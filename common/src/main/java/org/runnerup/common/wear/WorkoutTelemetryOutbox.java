package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

/** Bounded ordered in-memory outbox; persist with {@link WorkoutTelemetryOutboxStore}. */
public final class WorkoutTelemetryOutbox {
  static final int MAGIC = 0x57544F31; // WTO1
  static final int VERSION = 1;
  static final int MAX_STORAGE_BYTES = 1_048_576;
  static final int MAX_PENDING_SAMPLES = 4096;

  public enum AppendResult {
    APPENDED,
    DUPLICATE,
    CONFLICT,
    OUT_OF_ORDER,
    FULL,
    STORAGE_ERROR
  }

  private final String sessionId;
  private long lastSequence;
  private long acknowledgedThrough;
  private final List<WorkoutTelemetryProtocol.Sample> pending = new ArrayList<>();

  private WorkoutTelemetryOutbox(String sessionId, long lastSequence, long acknowledgedThrough) {
    this.sessionId = sessionId;
    this.lastSequence = lastSequence;
    this.acknowledgedThrough = acknowledgedThrough;
  }

  public static WorkoutTelemetryOutbox empty(String sessionId) {
    return new WorkoutTelemetryOutbox(sessionId, 0, 0);
  }

  public AppendResult append(WorkoutTelemetryProtocol.Sample sample) {
    if (sample == null || !sessionId.equals(sample.sessionId)) return AppendResult.CONFLICT;
    if (sample.sequence <= acknowledgedThrough) return AppendResult.CONFLICT;
    if (sample.sequence <= lastSequence) {
      WorkoutTelemetryProtocol.Sample prior = pendingAt(sample.sequence);
      return sample.equals(prior) ? AppendResult.DUPLICATE : AppendResult.CONFLICT;
    }
    if (sample.sequence != lastSequence + 1) return AppendResult.OUT_OF_ORDER;
    if (pending.size() >= MAX_PENDING_SAMPLES) return AppendResult.FULL;
    pending.add(sample);
    lastSequence = sample.sequence;
    return AppendResult.APPENDED;
  }

  /** Returns the next complete contiguous batch, or null when there is nothing pending. */
  public WorkoutTelemetryProtocol.Batch pendingBatch(int maxSamples, int maxBytes) {
    if (pending.isEmpty() || maxSamples <= 0 || maxBytes <= 0) return null;
    int count =
        Math.min(Math.min(maxSamples, WorkoutTelemetryProtocol.MAX_BATCH_SAMPLES), pending.size());
    List<WorkoutTelemetryProtocol.Sample> selected = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      selected.add(pending.get(i));
      WorkoutTelemetryProtocol.Batch candidate =
          new WorkoutTelemetryProtocol.Batch(sessionId, selected);
      try {
        if (WorkoutTelemetryProtocol.encodeBatch(candidate).length
            > Math.min(maxBytes, WorkoutTelemetryProtocol.MAX_PAYLOAD_BYTES)) {
          selected.remove(selected.size() - 1);
          break;
        }
      } catch (IOException impossible) {
        throw new IllegalStateException(impossible);
      }
    }
    return selected.isEmpty() ? null : new WorkoutTelemetryProtocol.Batch(sessionId, selected);
  }

  public boolean acknowledge(WorkoutTelemetryProtocol.Ack ack) {
    if (ack == null
        || !sessionId.equals(ack.sessionId)
        || ack.committedThroughSequence < acknowledgedThrough
        || ack.committedThroughSequence > lastSequence) return false;
    long delta = ack.committedThroughSequence - acknowledgedThrough;
    if (delta > pending.size()) return false;
    for (int i = 0; i < delta; i++) {
      if (pending.get(i).sequence != acknowledgedThrough + i + 1) return false;
    }
    pending.subList(0, (int) delta).clear();
    acknowledgedThrough = ack.committedThroughSequence;
    return true;
  }

  public String sessionId() {
    return sessionId;
  }

  public long lastSequence() {
    return lastSequence;
  }

  public long acknowledgedThrough() {
    return acknowledgedThrough;
  }

  public List<WorkoutTelemetryProtocol.Sample> pendingSamples() {
    return Collections.unmodifiableList(new ArrayList<>(pending));
  }

  byte[] encode() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeByte(VERSION);
      output.writeUTF(sessionId);
      output.writeLong(lastSequence);
      output.writeLong(acknowledgedThrough);
      output.writeInt(pending.size());
      for (WorkoutTelemetryProtocol.Sample sample : pending) {
        byte[] encoded =
            WorkoutTelemetryProtocol.encodeBatch(
                new WorkoutTelemetryProtocol.Batch(sessionId, Collections.singletonList(sample)));
        output.writeInt(encoded.length);
        output.write(encoded);
        output.writeInt(checksum(encoded));
      }
    }
    byte[] encoded = bytes.toByteArray();
    if (encoded.length > MAX_STORAGE_BYTES) throw new IOException("Telemetry outbox is full");
    return encoded;
  }

  static WorkoutTelemetryOutbox decode(String expectedSessionId, byte[] payload)
      throws IOException {
    if (payload == null || payload.length < 25 || payload.length > MAX_STORAGE_BYTES) {
      throw new IOException("Invalid telemetry outbox size");
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (input.readInt() != MAGIC || input.readUnsignedByte() != VERSION)
        throw new IOException("Unsupported telemetry outbox");
      String session = input.readUTF();
      long lastSequence = input.readLong();
      long acknowledged = input.readLong();
      int count = input.readInt();
      if (!expectedSessionId.equals(session)
          || lastSequence < 0
          || acknowledged < 0
          || acknowledged > lastSequence
          || count < 0
          || count > MAX_PENDING_SAMPLES
          || lastSequence - acknowledged != count)
        throw new IOException("Invalid telemetry outbox metadata");
      WorkoutTelemetryOutbox outbox =
          new WorkoutTelemetryOutbox(session, acknowledged, acknowledged);
      boolean tornTail = false;
      for (int index = 0; index < count; index++) {
        if (input.available() == 0) {
          tornTail = true;
          break;
        }
        if (input.available() < 4) {
          tornTail = true;
          break;
        } // Ignore a torn final length prefix.
        int length = input.readInt();
        if (length <= 0 || length > WorkoutTelemetryProtocol.MAX_PAYLOAD_BYTES)
          throw new IOException("Invalid outbox record length");
        if (input.available() < length + 4) {
          tornTail = true;
          break;
        } // Ignore a torn final record only.
        byte[] record = new byte[length];
        input.readFully(record);
        if (input.readInt() != checksum(record))
          throw new IOException("Corrupt telemetry outbox record");
        WorkoutTelemetryProtocol.Batch batch = WorkoutTelemetryProtocol.decodeBatch(record);
        if (!session.equals(batch.sessionId)
            || batch.samples.size() != 1
            || outbox.append(batch.samples.get(0)) != AppendResult.APPENDED)
          throw new IOException("Invalid telemetry outbox record");
      }
      if ((!tornTail && input.available() != 0) || outbox.lastSequence > lastSequence)
        throw new IOException("Invalid telemetry outbox tail");
      return outbox;
    }
  }

  private WorkoutTelemetryProtocol.Sample pendingAt(long sequence) {
    long index = sequence - acknowledgedThrough - 1;
    return index < 0 || index >= pending.size() ? null : pending.get((int) index);
  }

  private static int checksum(byte[] bytes) {
    CRC32 crc = new CRC32();
    crc.update(bytes);
    return (int) crc.getValue();
  }
}
