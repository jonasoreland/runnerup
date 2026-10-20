package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;

public class RelvenPlanRevisionTest {
  private static final String SOURCE =
      "{\"format\":\"relven.plan\",\"schemaVersion\":1,\"id\":\"base-plan\","
          + "\"name\":\"Base plan\",\"timezone\":\"Europe/Stockholm\","
          + "\"workouts\":[{\"schemaVersion\":3,\"id\":\"easy\","
          + "\"name\":\"Easy run\",\"sport\":\"running\",\"steps\":[{"
          + "\"id\":\"run\",\"kind\":\"segment\",\"phase\":\"work\","
          + "\"end\":{\"type\":\"time\",\"seconds\":1800}}]}],"
          + "\"days\":[{\"id\":\"work-a\",\"date\":\"2026-10-20\","
          + "\"kind\":\"workout\",\"workoutId\":\"easy\",\"notes\":\"Morning\"},"
          + "{\"id\":\"rest-a\",\"date\":\"2026-10-21\",\"kind\":\"rest\",\"notes\":\"Recovery\"},"
          + "{\"id\":\"work-b\",\"date\":\"2026-10-27\",\"kind\":\"workout\","
          + "\"workoutId\":\"easy\"}]}";

  @Test
  public void rescheduleCreatesFullImmutableRevisionAndKeepsV1SourceUntouched() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);

    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.reschedule(original, "work-a", "2026-10-27", "revision-1");

    assertEquals(1, original.schemaVersion());
    assertEquals("base-plan", original.id());
    assertEquals(SOURCE, original.sourceJson());
    assertEquals(2, revision.schemaVersion());
    assertEquals("revision-1", revision.id());
    assertEquals("base-plan", revision.rootPlanId());
    assertEquals(1, revision.revisionNumber());
    assertEquals("base-plan", revision.previousRevisionId());
    assertEquals("2026-10-27", revision.days().get(0).date());
    assertEquals("work-a", revision.days().get(0).id());
    assertEquals("Morning", revision.days().get(0).notes());
    assertEquals("2026-10-21", revision.days().get(1).date());
    assertEquals("easy", revision.days().get(0).workoutId());
    assertEquals(
        List.of("work-a", "rest-a", "work-b"),
        revision.days().stream().map(RelvenPlanImporter.Session::id).toList());
    assertEquals("Easy run", revision.workoutNames().get("easy"));
    assertNotEquals(original.sourceJson(), revision.sourceJson());
  }

  @Test
  public void rejectsMovingWorkoutOntoRestDay() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);

    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.reschedule(original, "work-a", "2026-10-21", "revision-1"));
  }

  @Test
  public void rejectsMovingRestOntoWorkoutDay() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);

    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.reschedule(original, "rest-a", "2026-10-27", "revision-1"));
  }

  @Test
  public void reschedulesRestDayAndPreservesItsNotes() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);

    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.reschedule(original, "rest-a", "2026-10-22", "revision-1");

    assertEquals("2026-10-22", revision.days().get(1).date());
    assertEquals("rest", revision.days().get(1).kind());
    assertEquals("Recovery", revision.days().get(1).notes());
  }

  @Test
  public void allowsMultipleWorkoutsOnOneDate() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);

    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.reschedule(original, "work-a", "2026-10-27", "revision-1");

    assertEquals(
        2, revision.days().stream().filter(day -> day.date().equals("2026-10-27")).count());
  }

  @Test
  public void canCreateNextRevisionFromPreviousRevision() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);
    RelvenPlanImporter.Plan first =
        RelvenPlanRevision.reschedule(original, "work-a", "2026-10-22", "revision-1");

    RelvenPlanImporter.Plan second =
        RelvenPlanRevision.reschedule(first, "work-a", "2026-10-23", "revision-2");

    assertEquals(2, second.revisionNumber());
    assertEquals("base-plan", second.rootPlanId());
    assertEquals("revision-1", second.previousRevisionId());
    assertEquals("2026-10-23", second.days().get(0).date());
  }

  @Test
  public void rejectsUnknownEntryInvalidDateAndSameDateMoves() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.reschedule(original, "missing", "2026-10-22", "revision-1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.reschedule(original, "work-a", "2026-02-30", "revision-1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.reschedule(original, "work-a", "2026-10-20", "revision-1"));
  }

  @Test
  public void rejectsMalformedV2RevisionMetadata() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);
    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.reschedule(original, "work-a", "2026-10-22", "revision-1");
    String badSequence = revision.sourceJson().replace("\"number\":1", "\"number\":0");
    String badParent =
        revision
            .sourceJson()
            .replace("\"rootPlanId\":\"base-plan\"", "\"rootPlanId\":\"revision-1\"");
    String unknownMetadata =
        revision.sourceJson().replace("\"number\":1", "\"number\":1,\"future\":true");

    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(badSequence));
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(badParent));
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(unknownMetadata));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RelvenPlanImporter.parse(SOURCE.replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
  }

  @Test
  public void addWorkoutCreatesOneNewEntryAndLeavesSourceUntouched() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);

    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.addWorkout(original, "2026-10-22", "easy", "work-c", "revision-1");

    assertEquals(3, original.days().size());
    assertEquals(SOURCE, original.sourceJson());
    assertEquals(4, revision.days().size());
    assertEquals(2, revision.schemaVersion());
    assertEquals("base-plan", revision.rootPlanId());
    assertEquals("base-plan", revision.previousRevisionId());
    assertEquals(1, revision.revisionNumber());
    assertEquals("2026-10-22", revision.days().get(3).date());
    assertEquals("easy", revision.days().get(3).workoutId());
  }

  @Test
  public void addRestCreatesOneNewEntryAndAllowsNextRevision() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);

    RelvenPlanImporter.Plan first =
        RelvenPlanRevision.addRest(original, "2026-10-22", "rest-b", "revision-1");
    RelvenPlanImporter.Plan second =
        RelvenPlanRevision.addWorkout(first, "2026-10-23", "easy", "work-c", "revision-2");

    assertEquals("rest", first.days().get(3).kind());
    assertEquals(2, second.revisionNumber());
    assertEquals("revision-1", second.previousRevisionId());
    assertEquals(5, second.days().size());
  }

  @Test
  public void rejectsDuplicateIdsDateConflictsAndUnknownWorkoutWhenAdding() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);

    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.addWorkout(original, "2026-10-22", "easy", "work-a", "r1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.addWorkout(original, "2026-10-22", "missing", "work-c", "r1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.addRest(original, "2026-10-20", "rest-b", "r1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.addRest(original, "2026-10-21", "rest-b", "r1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.addWorkout(original, "2026-02-30", "easy", "work-c", "r1"));
  }

  @Test
  public void removeDeletesOnlyRequestedEntryAndCannotRemoveLastDay() {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(SOURCE);

    RelvenPlanImporter.Plan revision = RelvenPlanRevision.remove(original, "rest-a", "revision-1");

    assertEquals(3, original.days().size());
    assertEquals(SOURCE, original.sourceJson());
    assertEquals(2, revision.days().size());
    assertEquals(
        List.of("work-a", "work-b"),
        revision.days().stream().map(RelvenPlanImporter.Session::id).toList());
    RelvenPlanImporter.Plan oneEntry = RelvenPlanRevision.remove(revision, "work-a", "revision-2");
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.remove(oneEntry, "work-b", "revision-3"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanRevision.remove(original, "missing", "revision-1"));
  }
}
