package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import org.runnerup.tracker.Tracker;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class WorkoutRuntimeCheckpointTest {
  @Test
  public void restoresNestedRepeatAtTheSameStepAndContinuesFromSavedProgress() throws Exception {
    AtomicLong elapsedMillis = new AtomicLong(0);
    Workout original = repeatedWorkout();
    attachClock(original, elapsedMillis);
    original.onInit(original);
    original.onStart(Scope.ACTIVITY, original);

    elapsedMillis.set(1_300);
    original.onTick();
    assertEquals(2.0, original.getCurrentStep().getDurationValue(), 0.0);
    JSONObject runtimeState = original.toRuntimeCheckpoint();

    StringWriter definition = new StringWriter();
    WorkoutSerializer.writeJSON(definition, original);
    Workout restored = WorkoutSerializer.readJSON(new StringReader(definition.toString()));
    attachClock(restored, elapsedMillis);
    assertTrue(restored.restoreRuntimeCheckpoint(runtimeState));

    elapsedMillis.set(3_500);
    restored.onTick();

    assertEquals(1, restored.getSteps().get(0).getCurrentRepeat());
    assertEquals(1.0, restored.getCurrentStep().getDurationValue(), 0.0);
  }

  @Test
  public void restoresPauseStepElapsedTimeAndReanchorsItsClock() throws Exception {
    Workout original = new Workout();
    PauseStep rest = new PauseStep();
    rest.setIntensity(Intensity.RESTING);
    rest.setDurationType(Dimension.TIME);
    rest.setDurationValue(30);
    original.addStep(rest);
    attachClock(original, new AtomicLong(0));
    original.onInit(original);
    original.onStart(Scope.ACTIVITY, original);

    ShadowSystemClock.advanceBy(Duration.ofSeconds(2));
    double elapsedAtCheckpoint = original.getTime(Scope.STEP);
    JSONObject runtimeState = original.toRuntimeCheckpoint();

    StringWriter definition = new StringWriter();
    WorkoutSerializer.writeJSON(definition, original);
    Workout restored = WorkoutSerializer.readJSON(new StringReader(definition.toString()));
    attachClock(restored, new AtomicLong(0));
    ShadowSystemClock.advanceBy(Duration.ofSeconds(5));

    assertTrue(restored.restoreRuntimeCheckpoint(runtimeState));
    assertEquals(elapsedAtCheckpoint, restored.getTime(Scope.STEP), 0.1);
  }

  private static Workout repeatedWorkout() {
    Workout workout = new Workout();
    RepeatStep repeat = new RepeatStep();
    repeat.setRepeatCount(2);
    repeat.getSteps().add(timedStep("First", 1));
    repeat.getSteps().add(timedStep("Second", 2));
    workout.addStep(repeat);
    return workout;
  }

  private static Step timedStep(String name, double seconds) {
    Step step = new Step();
    step.setName(name);
    step.setDurationType(Dimension.TIME);
    step.setDurationValue(seconds);
    return step;
  }

  private static void attachClock(Workout workout, AtomicLong elapsedMillis) {
    Tracker tracker = mock(Tracker.class);
    when(tracker.getTimeMs()).thenAnswer(call -> elapsedMillis.get());
    when(tracker.getDistance()).thenReturn(0.0);
    when(tracker.getHeartbeats()).thenReturn(0.0);
    workout.setTracker(tracker);
  }
}
