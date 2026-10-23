package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class RelvenStrengthWorkoutParserTest {
  private static final String WORKOUT =
      "{\"schemaVersion\":3,\"id\":\"strength-a\",\"name\":\"Bodyweight basics\","
          + "\"sport\":\"strength\",\"steps\":[{\"id\":\"squat-step\","
          + "\"kind\":\"segment\",\"phase\":\"work\","
          + "\"end\":{\"type\":\"manual\"},\"exercise\":{"
          + "\"exerciseId\":\"3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0\","
          + "\"libraryRevision\":1,\"exerciseRevision\":1,"
          + "\"snapshot\":{\"name\":\"Bodyweight squat\","
          + "\"instructions\":\"Stand with your feet about shoulder-width apart.\\n"
          + "Bend your knees and hips as far as comfortable while keeping your chest lifted.\\n"
          + "Press through your feet to stand tall again.\","
          + "\"equipment\":[],\"images\":[]},\"sets\":3,"
          + "\"minRepetitions\":8,\"maxRepetitions\":12,\"restSeconds\":60}}]}";

  @Test
  public void parsesOfflineExerciseSnapshotAndPrescription() {
    RelvenStrengthWorkoutParser.Workout workout = RelvenStrengthWorkoutParser.parse(WORKOUT);

    assertEquals("strength-a", workout.id());
    assertEquals("Bodyweight basics", workout.name());
    assertEquals("strength", workout.sport());
    assertEquals(1, workout.exercises().size());
    RelvenStrengthWorkoutParser.Exercise exercise = workout.exercises().get(0);
    assertEquals("3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0", exercise.exerciseId());
    assertEquals(1, exercise.libraryRevision());
    assertEquals(1, exercise.exerciseRevision());
    assertEquals("Bodyweight squat", exercise.name());
    assertEquals(3, exercise.sets());
    assertEquals(8, exercise.minRepetitions());
    assertEquals(12, exercise.maxRepetitions());
    assertEquals(60, exercise.restSeconds());
    assertEquals(WORKOUT, workout.sourceJson());
  }

  @Test
  public void acceptsPrehabAndNestedWorkoutOrder() {
    String prehab = WORKOUT.replace("\"strength\"", "\"prehab\"");
    RelvenStrengthWorkoutParser.Workout workout = RelvenStrengthWorkoutParser.parse(prehab);

    assertEquals("prehab", workout.sport());
    assertEquals(1, workout.exercises().size());
  }

  @Test
  public void rejectsRunningSportAndUnknownFields() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenStrengthWorkoutParser.parse(WORKOUT.replace("\"strength\"", "\"running\"")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RelvenStrengthWorkoutParser.parse(
                WORKOUT.replace("\"restSeconds\":60", "\"restSeconds\":60,\"loadKg\":5")));
  }

  @Test
  public void rejectsInvalidExerciseIdentityRepetitionRangeAndEquipment() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenStrengthWorkoutParser.parse(WORKOUT.replace("3b0e6f4b", "not-a-uuid")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RelvenStrengthWorkoutParser.parse(
                WORKOUT.replace("\"maxRepetitions\":12", "\"maxRepetitions\":7")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RelvenStrengthWorkoutParser.parse(
                WORKOUT.replace("\"equipment\":[]", "\"equipment\":[\"dumbbell\"]")));
  }

  @Test
  public void checksExerciseIdsAgainstTheInstalledCatalogWhenProvided() {
    assertEquals(
        1, RelvenStrengthWorkoutParser.parse(WORKOUT, catalogReferences()).exercises().size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RelvenStrengthWorkoutParser.parse(
                WORKOUT,
                Map.of(
                    "3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0",
                    new RelvenExerciseReference(
                        1, 1, "Different name", "Different instructions", List.of()))));
  }

  @Test
  public void acceptsHistoricalLibraryRevisionWhenThePinnedExerciseIsUnchanged() {
    Map<String, RelvenExerciseReference> currentCatalog =
        Map.of(
            "3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0",
            new RelvenExerciseReference(
                2,
                1,
                "Bodyweight squat",
                "Stand with your feet about shoulder-width apart.\n"
                    + "Bend your knees and hips as far as comfortable while keeping your chest lifted.\n"
                    + "Press through your feet to stand tall again.",
                List.of()));

    assertEquals(1, RelvenStrengthWorkoutParser.parse(WORKOUT, currentCatalog).exercises().size());
  }

  @Test
  public void rejectsLibraryRevisionsNewerThanTheInstalledCatalog() {
    String futureLibrary = WORKOUT.replace("\"libraryRevision\":1", "\"libraryRevision\":3");
    Map<String, RelvenExerciseReference> currentCatalog =
        Map.of(
            "3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0",
            new RelvenExerciseReference(
                2,
                1,
                "Bodyweight squat",
                "Stand with your feet about shoulder-width apart.\n"
                    + "Bend your knees and hips as far as comfortable while keeping your chest lifted.\n"
                    + "Press through your feet to stand tall again.",
                List.of()));

    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenStrengthWorkoutParser.parse(futureLibrary, currentCatalog));
  }

  @Test
  public void rejectsCatalogRevisionMismatch() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RelvenStrengthWorkoutParser.parse(
                WORKOUT,
                Map.of(
                    "3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0",
                    new RelvenExerciseReference(
                        1,
                        2,
                        "Bodyweight squat",
                        "Stand with your feet about shoulder-width apart.\n"
                            + "Bend your knees and hips as far as comfortable while keeping your chest lifted.\n"
                            + "Press through your feet to stand tall again.",
                        List.of()))));
  }

  private static Map<String, RelvenExerciseReference> catalogReferences() {
    return Map.of(
        "3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0",
        new RelvenExerciseReference(
            1,
            1,
            "Bodyweight squat",
            "Stand with your feet about shoulder-width apart.\n"
                + "Bend your knees and hips as far as comfortable while keeping your chest lifted.\n"
                + "Press through your feet to stand tall again.",
            List.of()));
  }

  @Test
  public void rejectsMissingExerciseAndDuplicateStepIds() throws Exception {
    JSONObject withoutExercise = new JSONObject(WORKOUT);
    withoutExercise.getJSONArray("steps").getJSONObject(0).remove("exercise");
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenStrengthWorkoutParser.parse(withoutExercise.toString()));
    JSONObject withDuplicateStep = new JSONObject(WORKOUT);
    JSONArray steps = withDuplicateStep.getJSONArray("steps");
    steps.put(new JSONObject(steps.getJSONObject(0).toString()));
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenStrengthWorkoutParser.parse(withDuplicateStep.toString()));
  }
}
