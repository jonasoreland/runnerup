package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorkoutTelemetryOutboxTest {
  private static final String SESSION = "outbox-session";

  @Test
  public void appendsInOrderAndReplaysAfterLostAcknowledgement() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    WorkoutTelemetryOutboxStore store = new WorkoutTelemetryOutboxStore(storage);
    assertEquals(WorkoutTelemetryOutbox.AppendResult.APPENDED, store.append(SESSION, sample(1)));
    assertEquals(WorkoutTelemetryOutbox.AppendResult.APPENDED, store.append(SESSION, sample(2)));

    WorkoutTelemetryProtocol.Batch firstSend = store.pendingBatch(SESSION, 10, 32 * 1024);
    WorkoutTelemetryOutboxStore restarted = new WorkoutTelemetryOutboxStore(storage);
    WorkoutTelemetryProtocol.Batch retry = restarted.pendingBatch(SESSION, 10, 32 * 1024);
    assertEquals(firstSend, retry);
    assertEquals(
        WorkoutTelemetryOutbox.AppendResult.DUPLICATE, restarted.append(SESSION, sample(2)));
  }

  @Test
  public void rejectsConflictsGapsAndAcknowledgmentsPastContiguousTail() throws Exception {
    WorkoutTelemetryOutboxStore store = new WorkoutTelemetryOutboxStore(new MemoryStorage());
    assertEquals(
        WorkoutTelemetryOutbox.AppendResult.OUT_OF_ORDER, store.append(SESSION, sample(2)));
    assertEquals(WorkoutTelemetryOutbox.AppendResult.APPENDED, store.append(SESSION, sample(1)));
    assertEquals(
        WorkoutTelemetryOutbox.AppendResult.CONFLICT,
        store.append(
            SESSION,
            new WorkoutTelemetryProtocol.Sample(
                SESSION, 1, 10, 100, null, null, null, null, null, 100.0)));
    assertTrue(!store.acknowledge(new WorkoutTelemetryProtocol.Ack(SESSION, 2)));
    assertTrue(store.acknowledge(new WorkoutTelemetryProtocol.Ack(SESSION, 1)));
    assertEquals(null, store.pendingBatch(SESSION, 10, 32 * 1024));
  }

  @Test
  public void batchesRespectSampleAndByteBoundsAndRejectSessionMismatch() throws Exception {
    WorkoutTelemetryOutboxStore store = new WorkoutTelemetryOutboxStore(new MemoryStorage());
    for (int i = 1; i <= 4; i++) store.append(SESSION, sample(i));
    WorkoutTelemetryProtocol.Batch batch = store.pendingBatch(SESSION, 2, 1024);
    assertEquals(2, batch.samples.size());
    assertTrue(store.pendingBatch("other-session", 2, 1024) == null);
  }

  @Test
  public void ignoresOnlyCorruptTailAndRetainsCompleteRecords() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    WorkoutTelemetryOutboxStore store = new WorkoutTelemetryOutboxStore(storage);
    store.append(SESSION, sample(1));
    store.append(SESSION, sample(2));
    storage.truncate(SESSION, 3);
    WorkoutTelemetryOutboxStore recovered = new WorkoutTelemetryOutboxStore(storage);
    WorkoutTelemetryProtocol.Batch batch = recovered.pendingBatch(SESSION, 10, 32 * 1024);
    assertEquals(1, batch.samples.size());
    assertEquals(1, batch.samples.get(0).sequence);
  }

  @Test
  public void rejectsCorruptionInsideACompleteRecord() {
    MemoryStorage storage = new MemoryStorage();
    WorkoutTelemetryOutboxStore store = new WorkoutTelemetryOutboxStore(storage);
    store.append(SESSION, sample(1));
    byte[] bytes = storage.read(SESSION);
    bytes[bytes.length - 5] ^= 1;
    storage.write(SESSION, bytes);
    org.junit.Assert.assertEquals(
        WorkoutTelemetryOutbox.AppendResult.STORAGE_ERROR,
        new WorkoutTelemetryOutboxStore(storage).append(SESSION, sample(2)));
  }

  private static WorkoutTelemetryProtocol.Sample sample(long sequence) {
    return new WorkoutTelemetryProtocol.Sample(
        SESSION,
        sequence,
        sequence * 10,
        sequence * 100,
        59.3 + sequence / 10000.0,
        18.0,
        5.0,
        null,
        null,
        120.0 + sequence);
  }

  private static final class MemoryStorage implements WorkoutTelemetryOutboxStore.Storage {
    private final java.util.Map<String, byte[]> values = new java.util.HashMap<>();

    @Override
    public byte[] read(String sessionId) {
      byte[] bytes = values.get(sessionId);
      return bytes == null ? null : bytes.clone();
    }

    @Override
    public boolean write(String sessionId, byte[] value) {
      values.put(sessionId, value.clone());
      return true;
    }

    @Override
    public boolean delete(String sessionId) {
      values.remove(sessionId);
      return true;
    }

    void truncate(String sessionId, int bytesToRemove) {
      byte[] value = values.get(sessionId);
      values.put(sessionId, java.util.Arrays.copyOf(value, value.length - bytesToRemove));
    }
  }
}
