package org.runnerup.service;

import static org.junit.Assert.assertEquals;
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

public class PreparedStrengthSessionStoreTest {
  @Test
  public void storesPreparedSessionIdempotentlyAndRejectsConflictingIdentity() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    PreparedStrengthSessionStore store = new PreparedStrengthSessionStore(storage);
    StrengthSession prepared = prepared("Knäböj");
    byte[] first = prepare(prepared);
    byte[] retry = prepare(prepared);
    StrengthSession conflictSession =
        new StrengthSession(
            prepared.getPlanId(),
            prepared.getScheduledEntryId(),
            workout("Utfall"),
            0,
            1,
            StrengthSession.Phase.PREPARING,
            0,
            java.util.Collections.emptyList(),
            null,
            prepared.getExecutionId(),
            java.util.Collections.emptyList(),
            null,
            null,
            1234L);
    byte[] conflict = prepare(conflictSession);

    assertTrue(store.save(first));
    assertNotNull(store.load());
    assertTrue(store.save(retry));
    assertFalse(store.save(conflict));
    assertEquals(
        prepared.getExecutionId(),
        StrengthExecutionProtocol.decodePrepare(store.load()).getSessionId());
  }

  @Test
  public void newPreparedSessionReplacesPriorOneAndCancelOnlyRemovesItsOwnSession()
      throws Exception {
    PreparedStrengthSessionStore store = new PreparedStrengthSessionStore(new MemoryStorage());
    byte[] first = prepare(prepared("First"));
    byte[] second = prepare(prepared("Second"));
    String firstId = StrengthExecutionProtocol.decodePrepare(first).getSessionId();
    String secondId = StrengthExecutionProtocol.decodePrepare(second).getSessionId();

    assertTrue(store.save(first));
    assertTrue(store.save(second));
    assertTrue(store.cancel(firstId));
    assertEquals(secondId, StrengthExecutionProtocol.decodePrepare(store.load()).getSessionId());
    assertTrue(store.cancel(secondId));
    assertNull(store.load());
    assertFalse(store.save(second));
  }

  @Test
  public void delayedRetryOfSupersededSessionCannotReplaceNewerPreparation() throws Exception {
    MemoryStorage durableStorage = new MemoryStorage();
    PreparedStrengthSessionStore store = new PreparedStrengthSessionStore(durableStorage);
    byte[] first = prepare(prepared("First"));
    byte[] second = prepare(prepared("Second"));
    String secondId = StrengthExecutionProtocol.decodePrepare(second).getSessionId();

    assertTrue(store.save(first));
    assertTrue(store.save(second));
    store = new PreparedStrengthSessionStore(durableStorage);
    assertFalse(store.save(first));
    assertEquals(secondId, StrengthExecutionProtocol.decodePrepare(store.load()).getSessionId());
  }

  @Test
  public void failedDurableWriteDoesNotAppearAsSaved() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    storage.failWrites = true;
    PreparedStrengthSessionStore store = new PreparedStrengthSessionStore(storage);

    assertFalse(store.save(prepare(prepared("Workout"))));
    assertNull(store.load());
  }

  private static StrengthSession prepared(String name) throws Exception {
    return StrengthSession.Companion.prepare(
        "plan", UUID.randomUUID().toString(), workout(name), 1234L);
  }

  private static RelvenStrengthWorkoutParser.Workout workout(String name) throws Exception {
    String json =
        "{\"schemaVersion\":3,\"id\":\"test\",\"name\":\"Gym\",\"sport\":\"strength\",\"steps\":["
            + "{\"id\":\"squat\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"manual\"},"
            + "\"exercise\":{\"exerciseId\":\"00000000-0000-0000-0000-000000000001\",\"libraryRevision\":1,"
            + "\"exerciseRevision\":1,\"snapshot\":{\"name\":\""
            + name
            + "\",\"instructions\":\"Kontrollerat.\",\"equipment\":[],\"images\":[]},"
            + "\"sets\":3,\"minRepetitions\":6,\"maxRepetitions\":10,\"restSeconds\":60}}]}";
    return RelvenStrengthWorkoutParser.parse(json);
  }

  private static byte[] prepare(StrengthSession session) throws Exception {
    return StrengthExecutionProtocol.encodePrepare(session, UUID.randomUUID().toString());
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
