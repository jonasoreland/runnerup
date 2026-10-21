package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import java.io.File;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class FitWorkoutNativeRoundTripTest {
  @Test
  public void importedFitWorkoutPersistsAndReloadsAsRunnerUpWorkout() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    File savedFile = WorkoutSerializer.getFile(context, "fit-native-round-trip");
    savedFile.delete();
    byte[] bytes;
    try (InputStream input = getClass().getResourceAsStream("/fit/running-intervals.fit")) {
      assertNotNull(input);
      bytes = input.readAllBytes();
    }

    Workout imported = FitWorkoutImporter.parse(bytes);
    try {
      WorkoutSerializer.writeFile(context, "fit-native-round-trip", imported);
      Workout restored = WorkoutSerializer.readFile(context, "fit-native-round-trip");

      assertEquals(2, restored.getSteps().size());
      assertStepPreserved(imported.getSteps().get(0), restored.getSteps().get(0));
      assertStepPreserved(imported.getSteps().get(1), restored.getSteps().get(1));
    } finally {
      savedFile.delete();
    }
  }

  private void assertStepPreserved(Step expected, Step actual) {
    assertEquals(expected.getIntensity(), actual.getIntensity());
    assertEquals(expected.getDurationType(), actual.getDurationType());
    assertEquals(expected.getDurationValue(), actual.getDurationValue(), 0.001);
    assertEquals(expected.getTargetType(), actual.getTargetType());
    if (expected.getTargetValue() != null) {
      assertNotNull(actual.getTargetValue());
      assertEquals(expected.getTargetValue().minValue, actual.getTargetValue().minValue, 0.001);
      assertEquals(expected.getTargetValue().maxValue, actual.getTargetValue().maxValue, 0.001);
    } else {
      assertNull(actual.getTargetValue());
    }
  }
}
