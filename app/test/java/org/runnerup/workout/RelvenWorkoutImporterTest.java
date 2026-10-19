package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.StringWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class RelvenWorkoutImporterTest {
  private static final String PREFIX = "{\"schemaVersion\":3,\"id\":\"run-1\",\"name\":\"Intervals\",\"sport\":\"running\",\"steps\":";

  @Test
  public void importsTimedIntervalWithPaceInSecondsPerMeter() {
    String json = PREFIX + "[{\"id\":\"a\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"time\",\"seconds\":120},\"target\":{\"type\":\"pace\",\"minSecondsPerKm\":240,\"maxSecondsPerKm\":300}}]}";
    Workout workout = RelvenWorkoutImporter.parse(json);
    Step step = workout.getSteps().get(0);
    assertEquals(Dimension.TIME, step.getDurationType());
    assertEquals(120, step.getDurationValue(), 0);
    assertEquals(Dimension.PACE, step.getTargetType());
    assertEquals(0.24, step.getTargetValue().minValue, 0.000001);
    assertEquals(0.30, step.getTargetValue().maxValue, 0.000001);
  }

  @Test
  public void preservesRepeatAndDistance() {
    String json = PREFIX + "[{\"id\":\"r\",\"kind\":\"repeat\",\"count\":4,\"steps\":[{\"id\":\"a\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"distance\",\"meters\":400}}]}]}";
    RepeatStep repeat = (RepeatStep) RelvenWorkoutImporter.parse(json).getSteps().get(0);
    assertEquals(4, repeat.getRepeatCount());
    assertEquals(400, repeat.getSteps().get(0).getDurationValue(), 0);
  }

  @Test
  public void rejectsUnsupportedCompositeEndInsteadOfChangingWorkout() {
    String json = PREFIX + "[{\"id\":\"a\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"any\",\"conditions\":[{\"type\":\"time\",\"seconds\":30},{\"type\":\"distance\",\"meters\":100}]}}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenWorkoutImporter.parse(json));
  }

  @Test
  public void rejectsStrengthDocuments() {
    String json = PREFIX.replace("\"running\"", "\"strength\"") + "[{\"id\":\"a\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"manual\"}}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenWorkoutImporter.parse(json));
  }

  @Test
  public void convertedWorkoutWritesExistingFileFormat() throws Exception {
    String json = PREFIX + "[{\"id\":\"r\",\"kind\":\"repeat\",\"count\":3,\"steps\":[{\"id\":\"a\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"distance\",\"meters\":400}},{\"id\":\"b\",\"kind\":\"segment\",\"phase\":\"recovery\",\"end\":{\"type\":\"time\",\"seconds\":60}}]}]}";
    StringWriter output = new StringWriter();
    WorkoutSerializer.writeJSON(output, RelvenWorkoutImporter.parse(json));
    JSONArray steps = new JSONObject(output.toString())
        .getJSONObject("com.garmin.connect.workout.json.UserWorkoutJson")
        .getJSONArray("workoutSteps");
    assertEquals("iterations", steps.getJSONObject(0).getString("endConditionTypeKey"));
    assertEquals(3, steps.getJSONObject(0).getInt("endConditionValue"));
    assertEquals(40000, steps.getJSONObject(1).getInt("endConditionValue"));
    assertEquals(60000, steps.getJSONObject(2).getInt("endConditionValue"));
  }
}
