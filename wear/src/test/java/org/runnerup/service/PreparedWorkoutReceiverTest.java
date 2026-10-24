package org.runnerup.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.runnerup.common.wear.WorkoutPreparationProtocol;

public class PreparedWorkoutReceiverTest {
  private static final String WORKOUT_JSON =
      "{\"com.garmin.connect.workout.json.UserWorkoutJson\":{\"workoutSteps\":[]}}";

  @Test
  public void acknowledgesOnlyAfterPersistingTheWorkoutAndRejectsConflictingRetry()
      throws Exception {
    PreparedWorkoutStore store = new PreparedWorkoutStore(new MemoryStorage());
    PreparedWorkoutReceiver receiver = new PreparedWorkoutReceiver(store);
    String sessionId = UUID.randomUUID().toString();
    byte[] request = prepare(sessionId, "Intervals", "Warm-up");

    WorkoutPreparationProtocol.Acknowledgement accepted =
        WorkoutPreparationProtocol.decodeAcknowledgement(receiver.receive(request));
    assertTrue(accepted.isAccepted());
    assertNotNull(store.load());

    WorkoutPreparationProtocol.Acknowledgement rejected =
        WorkoutPreparationProtocol.decodeAcknowledgement(
            receiver.receive(prepare(sessionId, "Tempo", "Warm-up")));
    assertFalse(rejected.isAccepted());
    assertNotNull(store.load());
  }

  @Test
  public void malformedPreparationDoesNotProduceAnAcknowledgement() {
    PreparedWorkoutReceiver receiver =
        new PreparedWorkoutReceiver(new PreparedWorkoutStore(new MemoryStorage()));
    assertNull(receiver.receive(new byte[] {1, 2, 3}));
  }

  @Test
  public void cancellationClearsOnlyItsOwnPreparedSession() throws Exception {
    PreparedWorkoutStore store = new PreparedWorkoutStore(new MemoryStorage());
    PreparedWorkoutReceiver receiver = new PreparedWorkoutReceiver(store);
    String currentSession = UUID.randomUUID().toString();
    assertTrue(receiver.receive(prepare(currentSession, "Intervals", "Warm-up")) != null);

    assertTrue(receiver.cancel(WorkoutPreparationProtocol.encodeCancellation(currentSession)));
    assertNull(store.load());
  }

  @Test
  public void malformedCancellationDoesNotClearPreparedWorkout() throws Exception {
    PreparedWorkoutStore store = new PreparedWorkoutStore(new MemoryStorage());
    PreparedWorkoutReceiver receiver = new PreparedWorkoutReceiver(store);
    byte[] payload = prepare(UUID.randomUUID().toString(), "Intervals", "Warm-up");
    assertTrue(receiver.receive(payload) != null);

    assertFalse(receiver.cancel(new byte[] {1, 2, 3}));
    assertNotNull(store.load());
  }

  private byte[] prepare(String sessionId, String name, String next) throws Exception {
    return WorkoutPreparationProtocol.encodePrepare(
        sessionId, UUID.randomUUID().toString(), name, next, WORKOUT_JSON);
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
