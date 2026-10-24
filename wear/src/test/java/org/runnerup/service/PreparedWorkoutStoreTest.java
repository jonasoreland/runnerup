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
import org.runnerup.common.wear.WorkoutPreparationProtocol;

public class PreparedWorkoutStoreTest {
  private static final String WORKOUT_JSON =
      "{\"com.garmin.connect.workout.json.UserWorkoutJson\":{\"workoutSteps\":[]}}";

  @Test
  public void storesExactRetryIdempotentlyAndRejectsConflictingSessionIdentity() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    PreparedWorkoutStore store = new PreparedWorkoutStore(storage);
    String sessionId = UUID.randomUUID().toString();
    byte[] first = prepare(sessionId, "Intervals", "Warm-up", WORKOUT_JSON);
    byte[] retry = prepare(sessionId, "Intervals", "Warm-up", WORKOUT_JSON);
    byte[] conflict = prepare(sessionId, "Tempo", "Warm-up", WORKOUT_JSON);

    assertTrue(store.save(first));
    assertNotNull(store.load());
    assertTrue(store.save(retry));
    assertFalse(store.save(conflict));
    assertTrue(
        WorkoutPreparationProtocol.decodePrepare(store.load())
            .getWorkoutName()
            .equals("Intervals"));
  }

  @Test
  public void aNewSessionReplacesThePreviouslyPreparedWorkout() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    PreparedWorkoutStore store = new PreparedWorkoutStore(storage);
    assertTrue(store.save(prepare(UUID.randomUUID().toString(), "First", "Start", WORKOUT_JSON)));
    assertTrue(
        store.save(prepare(UUID.randomUUID().toString(), "Second", "Warm-up", WORKOUT_JSON)));
    assertTrue(
        WorkoutPreparationProtocol.decodePrepare(store.load()).getWorkoutName().equals("Second"));
    store.clear();
    assertNull(store.load());
  }

  @Test
  public void cancellationClearsMatchingWorkoutAndRejectsLatePrepareRetry() throws Exception {
    PreparedWorkoutStore store = new PreparedWorkoutStore(new MemoryStorage());
    String sessionId = UUID.randomUUID().toString();
    byte[] payload = prepare(sessionId, "Intervaller", "Uppvärmning", WORKOUT_JSON);

    assertTrue(store.save(payload));
    assertTrue(store.cancel(sessionId));

    assertNull(store.load());
    assertFalse(store.save(payload));
  }

  @Test
  public void staleCancellationDoesNotClearANewerWorkout() throws Exception {
    PreparedWorkoutStore store = new PreparedWorkoutStore(new MemoryStorage());
    byte[] current =
        prepare(UUID.randomUUID().toString(), "Intervaller", "Uppvärmning", WORKOUT_JSON);
    String currentSession = WorkoutPreparationProtocol.decodePrepare(current).getSessionId();
    assertTrue(store.save(current));

    assertTrue(store.cancel(UUID.randomUUID().toString()));
    assertEquals(
        currentSession, WorkoutPreparationProtocol.decodePrepare(store.load()).getSessionId());
  }

  private byte[] prepare(String sessionId, String name, String next, String json) throws Exception {
    return WorkoutPreparationProtocol.encodePrepare(
        sessionId, UUID.randomUUID().toString(), name, next, json);
  }

  private static final class MemoryStorage implements PreparedWorkoutStore.Storage {
    private final Map<String, String> values = new HashMap<>();

    @Override
    public String read(String key) {
      return values.get(key);
    }

    @Override
    public boolean write(String key, String value) {
      values.put(key, value);
      return true;
    }

    @Override
    public void remove(String key) {
      values.remove(key);
    }
  }
}
