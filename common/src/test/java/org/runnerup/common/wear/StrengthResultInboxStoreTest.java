package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;

public class StrengthResultInboxStoreTest {
  private static final String SESSION = "123e4567-e89b-42d3-a456-426614174000";
  private static final String RESULT = "123e4567-e89b-42d3-b456-426614174001";

  @Test
  public void acceptsOnceAndRecognizesExactRetryAfterReopen() {
    MemoryStorage storage = new MemoryStorage();
    StrengthResultInboxStore first = new StrengthResultInboxStore(storage);
    StrengthResultProtocol.Result result = result(12);

    assertEquals(StrengthResultInboxStore.AcceptResult.STORED, first.accept(result));
    assertEquals(StrengthResultInboxStore.AcceptResult.DUPLICATE, first.accept(result));
    assertEquals(1, new StrengthResultInboxStore(storage).received().size());
  }

  @Test
  public void rejectsChangedPayloadForAnExistingResultId() {
    StrengthResultInboxStore store = new StrengthResultInboxStore(new MemoryStorage());

    assertEquals(StrengthResultInboxStore.AcceptResult.STORED, store.accept(result(12)));
    assertEquals(StrengthResultInboxStore.AcceptResult.CONFLICT, store.accept(result(13)));
    assertEquals(Integer.valueOf(12), store.received().get(0).getRepetitions());
  }

  @Test
  public void inboxRetainsNewestResultsInsteadOfBlockingAfterItsBound() {
    StrengthResultInboxStore store = new StrengthResultInboxStore(new MemoryStorage());
    StrengthResultProtocol.Result latest = null;

    for (int index = 0; index < StrengthResultOutboxStore.MAX_PENDING_RESULTS + 8; index++) {
      String resultId =
          UUID.nameUUIDFromBytes(("result-" + index).getBytes(StandardCharsets.UTF_8)).toString();
      latest =
          new StrengthResultProtocol.Result(
              SESSION, resultId, 0, "exercise-" + index, "Exercise " + index, 1, index);
      assertEquals(StrengthResultInboxStore.AcceptResult.STORED, store.accept(latest));
    }

    assertEquals(StrengthResultOutboxStore.MAX_PENDING_RESULTS, store.received().size());
    assertEquals(latest, store.received().get(store.received().size() - 1));
  }

  private static StrengthResultProtocol.Result result(int reps) {
    return new StrengthResultProtocol.Result(SESSION, RESULT, 0, "push-up", "Armhävning", 1, reps);
  }

  private static final class MemoryStorage implements StrengthResultInboxStore.Storage {
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
