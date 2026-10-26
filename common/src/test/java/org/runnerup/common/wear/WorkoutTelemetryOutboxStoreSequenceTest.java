package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class WorkoutTelemetryOutboxStoreSequenceTest {
  private static final String SESSION = "b6bc0461-8785-4c09-90f5-55bdc361592f";

  @Test
  public void sequenceContinuesAfterAcknowledgedSamplesAreCompacted() {
    MemoryStorage storage = new MemoryStorage();
    WorkoutTelemetryOutboxStore store = new WorkoutTelemetryOutboxStore(storage);
    assertEquals(WorkoutTelemetryOutbox.AppendResult.APPENDED, store.append(SESSION, sample(1)));
    assertEquals(WorkoutTelemetryOutbox.AppendResult.APPENDED, store.append(SESSION, sample(2)));

    assertEquals(2L, store.lastSequence(SESSION));
    assertEquals(true, store.acknowledge(new WorkoutTelemetryProtocol.Ack(SESSION, 2)));
    assertEquals(2L, store.lastSequence(SESSION));
  }

  private static WorkoutTelemetryProtocol.Sample sample(long sequence) {
    return new WorkoutTelemetryProtocol.Sample(
        SESSION, sequence, sequence * 1_000_000L, sequence, null, null, null, null, null, 120.0);
  }

  private static final class MemoryStorage implements WorkoutTelemetryOutboxStore.Storage {
    private final Map<String, byte[]> values = new HashMap<>();

    @Override
    public byte[] read(String sessionId) {
      return values.get(sessionId);
    }

    @Override
    public boolean write(String sessionId, byte[] payload) {
      values.put(sessionId, payload.clone());
      return true;
    }

    @Override
    public boolean delete(String sessionId) throws IOException {
      return values.remove(sessionId) != null;
    }
  }
}
