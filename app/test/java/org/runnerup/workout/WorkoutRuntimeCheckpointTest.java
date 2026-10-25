package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
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

  @Test
  public void restoresBasicRunAfterWorkoutBuilderAddsRuntimeOnlyTriggers() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    Workout source = buildBasicRun(context, preferences);
    AtomicLong elapsedMillis = new AtomicLong(0);
    attachClock(source, elapsedMillis);
    source.onInit(source);
    source.onStart(Scope.ACTIVITY, source);
    elapsedMillis.set(5_000);
    source.onTick();

    StringWriter definition = new StringWriter();
    WorkoutSerializer.writeJSON(definition, source);
    JSONObject runtimeState = source.toRuntimeCheckpoint();
    Workout restored = WorkoutSerializer.readJSON(new StringReader(definition.toString()));
    restored.sport = source.sport;
    restored.setWorkoutType(source.getWorkoutType());
    restored.setMute(source.getMute());
    WorkoutBuilder.prepareForRecovery(context, restored);
    attachClock(restored, elapsedMillis);
    restored.onInit(restored);

    assertTrue(restored.restoreRuntimeCheckpoint(runtimeState));
  }

  @Test
  public void restoresBasicRunWhenRuntimePreferencesChangeAfterCheckpoint() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences audioPreferences =
        WorkoutBuilder.getAudioCuePreferences(
            context, preferences, context.getString(org.runnerup.R.string.pref_basic_audio));
    audioPreferences
        .edit()
        .putBoolean(context.getString(org.runnerup.R.string.pref_mute_bool), true)
        .commit();
    Workout source = buildBasicRun(context, preferences);
    source.setTracker(mock(Tracker.class));
    source.onInit(source);
    source.onStart(Scope.ACTIVITY, source);
    JSONObject runtimeState = source.toRuntimeCheckpoint();
    StringWriter definition = new StringWriter();
    WorkoutSerializer.writeJSON(definition, source);

    preferences
        .edit()
        .putBoolean(context.getString(org.runnerup.R.string.pref_autopause_active), true)
        .commit();
    audioPreferences
        .edit()
        .putBoolean(context.getString(org.runnerup.R.string.pref_mute_bool), false)
        .commit();
    Workout restored = WorkoutSerializer.readJSON(new StringReader(definition.toString()));
    restored.setWorkoutType(source.getWorkoutType());
    WorkoutBuilder.prepareForRecovery(context, restored, source.getMute());
    restored.onInit(restored);

    assertTrue(restored.restoreRuntimeCheckpoint(runtimeState));
    assertTrue(restored.getMute());
  }

  @Test
  public void resetsSameTypeTriggerWhenItsConfigurationChanged() throws Exception {
    Workout original = workoutWithIntervalTrigger(12);
    attachClock(original, new AtomicLong(0));
    original.onInit(original);
    original.onStart(Scope.ACTIVITY, original);
    JSONObject checkpoint = original.toRuntimeCheckpoint();

    Workout restored = workoutWithIntervalTrigger(30);
    attachClock(restored, new AtomicLong(0));
    restored.onInit(restored);
    assertTrue(restored.restoreRuntimeCheckpoint(checkpoint));

    JSONObject restoredTrigger =
        restored
            .toRuntimeCheckpoint()
            .getJSONArray("steps")
            .getJSONObject(0)
            .getJSONArray("triggers")
            .getJSONObject(0);
    assertEquals(30, restoredTrigger.getDouble("next"), 0.0);
  }

  @Test
  public void persistsWatchCommandIdWithTheWorkoutRuntimeCheckpoint() throws Exception {
    String commandId = "fef77b0a-7012-4dd7-a374-cf7a1dd6f083";
    Workout original = repeatedWorkout();
    attachClock(original, new AtomicLong(0));
    original.onInit(original);
    original.onStart(Scope.ACTIVITY, original);
    original.markWatchSessionCommandApplied(commandId);
    JSONObject runtimeState = original.toRuntimeCheckpoint();

    Workout restored = repeatedWorkout();
    attachClock(restored, new AtomicLong(0));
    restored.onInit(restored);

    assertTrue(restored.restoreRuntimeCheckpoint(runtimeState));
    assertTrue(restored.hasAppliedWatchSessionCommand(commandId));
  }

  private static Workout buildBasicRun(Context context, SharedPreferences preferences) {
    Workout workout =
        WorkoutBuilder.createDefaultWorkout(context.getResources(), preferences, null);
    WorkoutBuilder.prepareWorkout(context.getResources(), preferences, workout);
    SharedPreferences audioPreferences =
        WorkoutBuilder.getAudioCuePreferences(
            context, preferences, context.getString(org.runnerup.R.string.pref_basic_audio));
    WorkoutBuilder.addAudioCuesToWorkout(
        context.getResources(), workout, audioPreferences, preferences);
    return workout;
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

  private static Workout workoutWithIntervalTrigger(double intervalSeconds) {
    Workout workout = new Workout();
    Step step = timedStep("Run", 300);
    IntervalTrigger trigger = new IntervalTrigger();
    trigger.scope = Scope.STEP;
    trigger.first = intervalSeconds;
    trigger.interval = intervalSeconds;
    step.triggers.add(trigger);
    workout.addStep(step);
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
