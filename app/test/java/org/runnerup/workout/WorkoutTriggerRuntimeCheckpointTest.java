package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class WorkoutTriggerRuntimeCheckpointTest {
  @Test
  public void intervalTriggerKeepsItsNextCueDeadline() throws Exception {
    IntervalTrigger running = new IntervalTrigger();
    running.first = 90;
    running.interval = 15;
    running.onStart(Scope.ACTIVITY, new Workout());

    JSONObject saved = running.toRuntimeCheckpoint();
    IntervalTrigger restored = new IntervalTrigger();
    restored.first = 90;
    restored.interval = 15;

    assertTrue(restored.restoreRuntimeCheckpoint(saved));
    assertEquals(90.0, restored.toRuntimeCheckpoint().getDouble("next"), 0.0);
  }

  @Test
  public void eventTriggerKeepsItsEmissionCounter() throws Exception {
    EventTrigger running = new EventTrigger();
    running.onStart(Scope.STEP, new Workout());
    JSONObject saved = running.toRuntimeCheckpoint();
    EventTrigger restored = new EventTrigger();

    assertTrue(restored.restoreRuntimeCheckpoint(saved));
    assertEquals(1, restored.toRuntimeCheckpoint().getInt("counter"));
  }
}
