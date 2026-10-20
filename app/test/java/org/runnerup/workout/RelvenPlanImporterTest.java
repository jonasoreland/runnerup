package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RelvenPlanImporterTest {
  private static final String RUN =
      "{\"schemaVersion\":3,\"id\":\"run-a\",\"name\":\"Easy run\",\"sport\":\"running\",\"steps\":[{\"id\":\"step-a\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"time\",\"seconds\":1800}}]}";
  private static final String HEADER =
      "{\"format\":\"relven.plan\",\"schemaVersion\":1,\"id\":\"plan-1\",\"name\":\"First weeks\",\"timezone\":\"Europe/Stockholm\",\"workouts\":["
          + RUN
          + "],\"days\":";
  private static final String STRENGTH =
      "{\"schemaVersion\":3,\"id\":\"strength-a\",\"name\":\"Bodyweight basics\","
          + "\"sport\":\"strength\",\"steps\":[{\"id\":\"squat-step\","
          + "\"kind\":\"segment\",\"phase\":\"work\","
          + "\"end\":{\"type\":\"manual\"},\"exercise\":{"
          + "\"exerciseId\":\"3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0\","
          + "\"libraryRevision\":1,\"exerciseRevision\":1,\"snapshot\":{\"name\":\"Squat\","
          + "\"instructions\":\"Stand tall and bend your knees.\","
          + "\"equipment\":[],\"images\":[]},\"sets\":3,"
          + "\"minRepetitions\":8,\"maxRepetitions\":12,\"restSeconds\":60}}]}";

  @Test
  public void acceptsDatedRunningSessionsAndRestDay() {
    String json =
        HEADER
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"run-a\"},{\"id\":\"s2\",\"date\":\"2026-10-21\",\"kind\":\"rest\"},{\"id\":\"s3\",\"date\":\"2026-10-27\",\"kind\":\"workout\",\"workoutId\":\"run-a\"}]}";
    RelvenPlanImporter.Plan plan = RelvenPlanImporter.parse(json);
    assertEquals("First weeks", plan.name());
    assertEquals(2, plan.workoutDays());
    assertEquals(1, plan.restDays());
  }

  @Test
  public void keepsValidatedWorkoutNamesForPlanPresentation() {
    RelvenPlanImporter.Plan plan =
        RelvenPlanImporter.parse(
            HEADER
                + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"run-a\"}]}");

    assertEquals("Easy run", plan.workoutNames().get("run-a"));
  }

  @Test
  public void rejectsUnknownWorkoutReference() {
    String json =
        HEADER
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"missing\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsRestAndWorkoutOnSameDay() {
    String json =
        HEADER
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"rest\"},{\"id\":\"s2\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"run-a\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsInvalidDate() {
    String json = HEADER + "[{\"id\":\"s1\",\"date\":\"2026-02-30\",\"kind\":\"rest\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsPlanContainingUnsupportedStrengthWorkout() {
    String json =
        HEADER.replace("\"sport\":\"running\"", "\"sport\":\"strength\"")
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"run-a\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void acceptsStrengthWorkoutSnapshotsWithoutCreatingRunnerUpWorkout() {
    String json =
        HEADER.replace(RUN, STRENGTH)
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"strength-a\"}]}";

    RelvenPlanImporter.Plan plan = RelvenPlanImporter.parse(json);

    assertTrue(plan.workouts().isEmpty());
    assertEquals(1, plan.strengthWorkouts().size());
    assertEquals("Squat", plan.strengthWorkouts().get("strength-a").exercises().get(0).name());
    assertTrue(plan.sourceJson().contains("\"instructions\":\"Stand tall and bend your knees.\""));
    assertEquals("Bodyweight basics", plan.workoutNames().get("strength-a"));
  }

  @Test
  public void rejectsUnknownPlanFields() {
    String json =
        HEADER + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"rest\"}],\"unexpected\":true}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsNonStringNotesInsteadOfCoercingThem() {
    String json =
        HEADER + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"rest\",\"notes\":7}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsUnknownEmbeddedWorkoutFields() {
    String workout =
        RUN.replace("\"sport\":\"running\"", "\"sport\":\"running\",\"unknownMetric\":1");
    String json =
        HEADER.replace(RUN, workout)
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"run-a\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsUnknownFieldsInsideEmbeddedWorkoutSteps() {
    String workout = RUN.replace("\"phase\":\"work\"", "\"phase\":\"work\",\"cadence\":180");
    String json =
        HEADER.replace(RUN, workout)
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"run-a\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsUnknownFieldsInsideEmbeddedTargets() {
    String workout =
        RUN.replace(
            "\"end\":{\"type\":\"time\",\"seconds\":1800}",
            "\"end\":{\"type\":\"time\",\"seconds\":1800},"
                + "\"target\":{\"type\":\"pace\",\"minSecondsPerKm\":300,"
                + "\"maxSecondsPerKm\":360,\"unit\":\"wrong\"}");
    String json =
        HEADER.replace(RUN, workout)
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\","
            + "\"workoutId\":\"run-a\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsFractionalSchemaVersion() {
    String json =
        (HEADER + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"rest\"}]}")
            .replace("\"schemaVersion\":1", "\"schemaVersion\":1.5");
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsPackageLargerThanSchemaLimitBeforeParsing() {
    String largeNotes = "x".repeat(4_194_305);
    String json =
        HEADER
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"rest\",\"notes\":\""
            + largeNotes
            + "\"}]}";
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
    assertTrue(error.getMessage().contains("package too large"));
  }

  @Test
  public void rejectsDeepJsonBeforeBuildingJsonObjectTree() {
    String deeplyNested = "[".repeat(5_000) + "0" + "]".repeat(5_000);
    String json = HEADER + "[],\"unexpected\":" + deeplyNested + "}";
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
    assertTrue(error.getMessage().contains("JSON nesting too deep"));
  }

  @Test
  public void rejectsLargeShallowJsonTokenCountBeforeBuildingObjectTree() {
    String manyValues = "\"x\",".repeat(100_001) + "\"x\"";
    String json = HEADER + "[],\"unexpected\":[" + manyValues + "]}";
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
    assertTrue(error.getMessage().contains("too many JSON tokens"));
  }
}
