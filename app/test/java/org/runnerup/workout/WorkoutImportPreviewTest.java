package org.runnerup.workout;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class WorkoutImportPreviewTest {
  @Test
  public void listsNamedStepsAndNestedRepeatDetails() {
    Workout workout = new Workout();
    Step warmup = new Step();
    warmup.setName("Warm up");
    warmup.setDurationType(Dimension.TIME);
    warmup.setDurationValue(600);
    workout.addStep(warmup);

    RepeatStep repeats = new RepeatStep();
    repeats.setRepeatCount(3);
    Step interval = new Step();
    interval.setName("Fast");
    interval.setDurationType(Dimension.DISTANCE);
    interval.setDurationValue(400);
    interval.setTargetType(Dimension.HR);
    interval.setTargetValue(150, 170);
    repeats.getSteps().add(interval);
    workout.addStep(repeats);

    String preview = WorkoutImportPreview.format(RuntimeEnvironment.getApplication(), workout);

    assertTrue(preview.contains("Warm up"));
    assertTrue(preview.contains("10"));
    assertTrue(
        preview.contains(
            RuntimeEnvironment.getApplication()
                .getString(org.runnerup.common.R.string.repeat_times, 3)));
    assertTrue(preview.contains("Fast"));
    assertTrue(preview.contains("400"));
    assertTrue(
        preview.contains(
            RuntimeEnvironment.getApplication().getString(org.runnerup.common.R.string.Target)));
    assertTrue(
        preview.contains(
            RuntimeEnvironment.getApplication()
                .getString(org.runnerup.common.R.string.Heart_rate)));
    assertTrue(preview.contains("150"));
    assertTrue(preview.contains("170"));
  }

  @Test
  public void capsLongPreviewAndSaysWhenStepsAreHidden() {
    Workout workout = new Workout();
    for (int i = 0; i < 300; i++) {
      Step step = new Step();
      step.setName("Step " + i);
      workout.addStep(step);
    }

    String preview = WorkoutImportPreview.format(RuntimeEnvironment.getApplication(), workout);

    assertTrue(
        preview.contains(
            RuntimeEnvironment.getApplication()
                .getString(org.runnerup.common.R.string.additional_workout_steps_hidden)));
    assertTrue(preview.contains("Step 0"));
    assertFalse(preview.contains("Step 299"));
  }
}
