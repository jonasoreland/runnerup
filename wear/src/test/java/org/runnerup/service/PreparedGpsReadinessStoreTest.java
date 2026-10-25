package org.runnerup.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.runnerup.common.wear.GpsPreparationProtocol;
import org.runnerup.common.wear.WorkoutPreparationProtocol;

public class PreparedGpsReadinessStoreTest {
  private static final String WORKOUT_JSON =
      "{\"com.garmin.connect.workout.json.UserWorkoutJson\":{\"workoutSteps\":[]}}";

  @Test
  public void acceptsOnlyTheCurrentlyPreparedSession() throws Exception {
    String activeSessionId = UUID.randomUUID().toString();
    String otherSessionId = UUID.randomUUID().toString();
    PreparedWorkoutStore workoutStore = preparedWorkoutStore(activeSessionId);
    PreparedGpsReadinessStore store =
        new PreparedGpsReadinessStore(new MemoryStorage(), workoutStore);

    assertFalse(store.save(readiness(otherSessionId, 1L, GpsPreparationProtocol.Status.READY)));
    assertTrue(store.save(readiness(activeSessionId, 1L, GpsPreparationProtocol.Status.SEARCHING)));
    assertEquals(GpsPreparationProtocol.Status.SEARCHING, store.load(activeSessionId).getStatus());
    assertNull(store.load(otherSessionId));
  }

  @Test
  public void newerRevisionsReplaceCurrentStateAndDuplicatesAreIdempotent() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    PreparedWorkoutStore workoutStore = preparedWorkoutStore(sessionId);
    PreparedGpsReadinessStore store =
        new PreparedGpsReadinessStore(new MemoryStorage(), workoutStore);
    byte[] first = readiness(sessionId, 3L, GpsPreparationProtocol.Status.SEARCHING);
    byte[] second = readiness(sessionId, 4L, GpsPreparationProtocol.Status.READY);

    assertTrue(store.save(first));
    assertTrue(store.save(first));
    assertFalse(store.save(readiness(sessionId, 2L, GpsPreparationProtocol.Status.DISABLED)));
    assertTrue(store.save(second));
    assertEquals(4L, store.load(sessionId).getRevision());
    assertEquals(GpsPreparationProtocol.Status.READY, store.load(sessionId).getStatus());
  }

  @Test
  public void clearingAnOldSessionCannotClearTheCurrentSession() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    PreparedWorkoutStore workoutStore = preparedWorkoutStore(sessionId);
    PreparedGpsReadinessStore store =
        new PreparedGpsReadinessStore(new MemoryStorage(), workoutStore);
    assertTrue(store.save(readiness(sessionId, 1L, GpsPreparationProtocol.Status.READY)));

    assertFalse(store.clear(UUID.randomUUID().toString()));
    assertEquals(GpsPreparationProtocol.Status.READY, store.load(sessionId).getStatus());
    assertTrue(store.clear(sessionId));
    assertNull(store.load(sessionId));
  }

  @Test
  public void replacingThePreparedWorkoutClearsItsPreviousGpsState() throws Exception {
    String firstSessionId = UUID.randomUUID().toString();
    String secondSessionId = UUID.randomUUID().toString();
    PreparedWorkoutStore workoutStore = preparedWorkoutStore(firstSessionId);
    PreparedGpsReadinessStore store =
        new PreparedGpsReadinessStore(new MemoryStorage(), workoutStore);
    assertTrue(store.save(readiness(firstSessionId, 1L, GpsPreparationProtocol.Status.READY)));
    workoutStore.save(prepareWorkout(secondSessionId));

    store.clearForDifferentSession(secondSessionId);

    assertNull(store.load(firstSessionId));
    assertNull(store.load(secondSessionId));
  }

  private static PreparedWorkoutStore preparedWorkoutStore(String sessionId) throws IOException {
    PreparedWorkoutStore store = new PreparedWorkoutStore(new WorkoutMemoryStorage());
    assertTrue(store.save(prepareWorkout(sessionId)));
    return store;
  }

  private static byte[] prepareWorkout(String sessionId) throws IOException {
    return WorkoutPreparationProtocol.encodePrepare(
        sessionId, UUID.randomUUID().toString(), "Löpning", "Uppvärmning", WORKOUT_JSON);
  }

  private static byte[] readiness(
      String sessionId, long revision, GpsPreparationProtocol.Status status) throws IOException {
    return GpsPreparationProtocol.encode(sessionId, revision, status, null);
  }

  private static final class MemoryStorage implements PreparedGpsReadinessStore.Storage {
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

  private static final class WorkoutMemoryStorage implements PreparedWorkoutStore.Storage {
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
