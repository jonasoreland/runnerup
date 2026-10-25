package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StrengthResultOutboxStoreTest {
  private static final String SESSION = "99f7f1de-73ad-4a52-8d22-3df6a5b8b75a";
  private static final String OTHER_SESSION = "671a25fc-cdb2-4d62-a3a2-175f2826040b";
  private static final String RESULT = "fef77b0a-7012-4dd7-a374-cf7a1dd6f083";

  @Test
  public void queuedResultSurvivesReopenAndExactRetryDoesNotDuplicate() {
    MemoryStorage storage = new MemoryStorage();
    StrengthResultOutboxStore first = new StrengthResultOutboxStore(storage);
    StrengthResultProtocol.Result result = result(10);

    assertEquals(StrengthResultOutboxStore.EnqueueResult.ENQUEUED, first.enqueue(result));
    assertEquals(StrengthResultOutboxStore.EnqueueResult.DUPLICATE, first.enqueue(result));

    StrengthResultOutboxStore reopened = new StrengthResultOutboxStore(storage);
    assertEquals(1, reopened.pending(10).size());
    assertEquals(result, reopened.pending(10).get(0));
  }

  @Test
  public void reusedResultIdWithDifferentPayloadIsRejected() {
    StrengthResultOutboxStore store = new StrengthResultOutboxStore(new MemoryStorage());
    assertEquals(StrengthResultOutboxStore.EnqueueResult.ENQUEUED, store.enqueue(result(10)));

    assertEquals(StrengthResultOutboxStore.EnqueueResult.CONFLICT, store.enqueue(result(11)));
    assertEquals(10, store.pending(10).get(0).getRepetitions().intValue());
  }

  @Test
  public void onlyMatchingCommittedAcknowledgementRemovesResult() {
    StrengthResultOutboxStore store = new StrengthResultOutboxStore(new MemoryStorage());
    store.enqueue(result(10));

    assertEquals(
        StrengthResultOutboxStore.AcknowledgeResult.MISMATCH,
        store.acknowledge(
            new StrengthResultProtocol.Acknowledgement(
                OTHER_SESSION, RESULT, StrengthResultProtocol.AckOutcome.COMMITTED)));
    assertEquals(
        StrengthResultOutboxStore.AcknowledgeResult.NOT_COMMITTED,
        store.acknowledge(
            new StrengthResultProtocol.Acknowledgement(
                SESSION, RESULT, StrengthResultProtocol.AckOutcome.CONFLICT)));
    assertEquals(1, store.pending(10).size());
    assertEquals(
        StrengthResultOutboxStore.AcknowledgeResult.REMOVED,
        store.acknowledge(
            new StrengthResultProtocol.Acknowledgement(
                SESSION, RESULT, StrengthResultProtocol.AckOutcome.COMMITTED)));
    assertTrue(store.pending(10).isEmpty());
  }

  private static StrengthResultProtocol.Result result(int repetitions) {
    return new StrengthResultProtocol.Result(
        SESSION, RESULT, 0, "push-up", "Armhävning", 1, repetitions);
  }

  private static final class MemoryStorage implements StrengthResultOutboxStore.Storage {
    private String value;

    @Override
    public String read() {
      return value;
    }

    @Override
    public boolean write(String content) {
      value = content;
      return true;
    }
  }
}
