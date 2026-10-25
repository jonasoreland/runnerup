package org.runnerup.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.runnerup.common.wear.StrengthExecutionProtocol;
import org.runnerup.relven.strength.StrengthSession;
import org.runnerup.workout.RelvenStrengthWorkoutParser;

public class PreparedStrengthSessionReceiverTest {
  @Test
  public void returnsAcceptedAcknowledgementOnlyAfterDurableStore() throws Exception {
    PreparedStrengthSessionStore store = new PreparedStrengthSessionStore(new MemoryStorage());
    PreparedStrengthSessionReceiver receiver = new PreparedStrengthSessionReceiver(store);
    StrengthSession session = prepared();
    byte[] request = StrengthExecutionProtocol.encodePrepare(session, UUID.randomUUID().toString());

    var acknowledgement =
        StrengthExecutionProtocol.decodeAcknowledgement(receiver.receive(request));
    assertTrue(acknowledgement.isAccepted());
    assertNotNull(store.load());
  }

  @Test
  public void malformedOrFailedPreparationIsNotAcknowledged() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    storage.failWrites = true;
    PreparedStrengthSessionStore store = new PreparedStrengthSessionStore(storage);
    PreparedStrengthSessionReceiver receiver = new PreparedStrengthSessionReceiver(store);

    assertNull(receiver.receive(new byte[] {1, 2, 3}));
    byte[] request =
        StrengthExecutionProtocol.encodePrepare(prepared(), UUID.randomUUID().toString());
    assertFalse(
        StrengthExecutionProtocol.decodeAcknowledgement(receiver.receive(request)).isAccepted());
  }

  @Test
  public void cancellationIsSessionScopedAndMalformedCancellationPreservesState() throws Exception {
    PreparedStrengthSessionStore store = new PreparedStrengthSessionStore(new MemoryStorage());
    PreparedStrengthSessionReceiver receiver = new PreparedStrengthSessionReceiver(store);
    byte[] request =
        StrengthExecutionProtocol.encodePrepare(prepared(), UUID.randomUUID().toString());
    String sessionId = StrengthExecutionProtocol.decodePrepare(request).getSessionId();
    assertNotNull(receiver.receive(request));

    assertFalse(receiver.cancel(new byte[] {1, 2, 3}));
    assertNotNull(store.load());
    assertTrue(receiver.cancel(StrengthExecutionProtocol.encodeCancellation(sessionId)));
    assertNull(store.load());
  }

  private static StrengthSession prepared() throws Exception {
    String json =
        "{\"schemaVersion\":3,\"id\":\"test\",\"name\":\"Gym\",\"sport\":\"strength\",\"steps\":["
            + "{\"id\":\"push\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"manual\"},"
            + "\"exercise\":{\"exerciseId\":\"00000000-0000-0000-0000-000000000001\",\"libraryRevision\":1,"
            + "\"exerciseRevision\":1,\"snapshot\":{\"name\":\"Armhävning\",\"instructions\":\"Kontrollerat.\",\"equipment\":[],\"images\":[]},"
            + "\"sets\":2,\"minRepetitions\":1,\"maxRepetitions\":20,\"restSeconds\":30}}]}";
    return StrengthSession.Companion.prepare(
        "plan", UUID.randomUUID().toString(), RelvenStrengthWorkoutParser.parse(json), 1234L);
  }

  private static final class MemoryStorage implements PreparedStrengthSessionStore.Storage {
    private final Map<String, String> values = new HashMap<>();
    private boolean failWrites;

    @Override
    public String read(String key) {
      return values.get(key);
    }

    @Override
    public boolean writeState(String preparedSession, String cancelledSessions) {
      if (failWrites) return false;
      if (preparedSession == null) values.remove("prepared_strength_session_v1");
      else values.put("prepared_strength_session_v1", preparedSession);
      values.put("cancelled_strength_sessions", cancelledSessions);
      return true;
    }
  }
}
