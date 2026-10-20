package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class RelvenPlanStoreTest {
  private static final String JSON =
      "{\"format\":\"relven.plan\",\"schemaVersion\":1,\"id\":\"store-test\","
          + "\"name\":\"Store test\",\"timezone\":\"Europe/Stockholm\","
          + "\"workouts\":[{\"schemaVersion\":3,\"id\":\"run-a\",\"name\":\"Easy run\","
          + "\"sport\":\"running\",\"steps\":[{\"id\":\"step-a\",\"kind\":\"segment\","
          + "\"phase\":\"work\",\"end\":{\"type\":\"time\",\"seconds\":1800}}]}],"
          + "\"days\":[{\"id\":\"session-a\",\"date\":\"2026-10-20\",\"kind\":\"workout\","
          + "\"workoutId\":\"run-a\"}]}";
  private static final String STRENGTH_JSON =
      "{\"format\":\"relven.plan\",\"schemaVersion\":1,\"id\":\"strength-store\","
          + "\"name\":\"Strength week\",\"timezone\":\"Europe/Stockholm\",\"workouts\":[{"
          + "\"schemaVersion\":3,\"id\":\"strength-a\",\"name\":\"Bodyweight basics\","
          + "\"sport\":\"strength\",\"steps\":[{\"id\":\"squat-step\",\"kind\":\"segment\","
          + "\"phase\":\"work\",\"end\":{\"type\":\"manual\"},\"exercise\":{"
          + "\"exerciseId\":\"3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0\",\"libraryRevision\":1,"
          + "\"exerciseRevision\":1,\"snapshot\":{\"name\":\"Bodyweight squat\","
          + "\"instructions\":\"Stand with your feet about shoulder-width apart.\\n"
          + "Bend your knees and hips as far as comfortable while keeping your chest lifted.\\n"
          + "Press through your feet to stand tall again.\","
          + "\"equipment\":[],\"images\":[]},\"sets\":3,\"minRepetitions\":8,"
          + "\"maxRepetitions\":12,\"restSeconds\":60}}]}],\"days\":[{\"id\":\"strength-day\","
          + "\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"strength-a\"}]}";

  private Context context;
  private File directory;

  @Before
  public void setUp() throws Exception {
    context = RuntimeEnvironment.getApplication();
    RelvenPlanStore.clearActivePlan(context);
    directory = context.getDir("relven-plans", Context.MODE_PRIVATE);
    File[] existing = directory.listFiles();
    if (existing != null) {
      for (File file : existing) assertTrue(file.delete());
    }
  }

  @Test
  public void savesValidatedPlanAndRejectsDuplicateId() throws Exception {
    RelvenPlanImporter.Plan plan = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, plan);

    File[] saved = directory.listFiles();
    assertTrue(saved != null);
    assertEquals(1, saved.length);
    assertEquals(JSON, Files.readString(saved[0].toPath()));
    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.save(context, plan));
    assertEquals(1, directory.listFiles().length);
  }

  @Test
  public void savesAndRevisesStrengthPlanWithoutRunnerUpWorkoutConversion() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(STRENGTH_JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanStore.activate(context, original);

    RelvenPlanImporter.Plan revision =
        RelvenPlanStore.addRestToActivePlan(context, original, "2026-10-21", "rest-day");

    assertTrue(original.workouts().isEmpty());
    assertEquals(
        "Bodyweight squat",
        revision.strengthWorkouts().get("strength-a").exercises().get(0).name());
    assertEquals("strength-a", revision.days().get(0).workoutId());
    assertEquals(2, RelvenPlanStore.list(context).size());
  }

  @Test
  public void importsPublishedStrengthAndPrehabExampleAgainstTheBundledCatalog() throws Exception {
    String example = Files.readString(new File("../schemas/examples/plan-strength.json").toPath());

    RelvenPlanImporter.Plan plan = RelvenPlanStore.parse(context, example);

    assertEquals(2, plan.strengthWorkouts().size());
    assertEquals(
        "Bodyweight squat", plan.strengthWorkouts().get("strength-a").exercises().get(0).name());
    assertEquals("Glute bridge", plan.strengthWorkouts().get("prehab-a").exercises().get(0).name());
  }

  @Test
  public void invalidSourceWritesNothing() {
    RelvenPlanImporter.Plan forged =
        new RelvenPlanImporter.Plan(
            "forged", "Forged", "Europe/Stockholm", List.of(), Map.of(), Map.of(), "{}");
    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.save(context, forged));
    assertEquals(0, directory.listFiles().length);
  }

  @Test
  public void listsPreviouslySavedPlansByName() throws Exception {
    String later = JSON.replace("store-test", "store-z").replace("Store test", "Zulu plan");
    String earlier = JSON.replace("store-test", "store-a").replace("Store test", "Alpha plan");
    RelvenPlanStore.save(context, RelvenPlanImporter.parse(later));
    RelvenPlanStore.save(context, RelvenPlanImporter.parse(earlier));

    List<RelvenPlanImporter.Plan> plans = RelvenPlanStore.list(context);

    assertEquals(2, plans.size());
    assertEquals("store-a", plans.get(0).id());
    assertEquals("store-z", plans.get(1).id());
  }

  @Test
  public void activatesOnlySavedPlansAndCanSwitchPrimaryPlan() throws Exception {
    RelvenPlanImporter.Plan first = RelvenPlanImporter.parse(JSON);
    String secondJson = JSON.replace("store-test", "store-second").replace("Store test", "Second");
    RelvenPlanImporter.Plan second = RelvenPlanImporter.parse(secondJson);
    RelvenPlanStore.save(context, first);
    RelvenPlanStore.save(context, second);

    assertNull(RelvenPlanStore.activePlan(context));
    RelvenPlanStore.activate(context, first);
    assertEquals("store-test", RelvenPlanStore.activePlan(context).id());
    RelvenPlanStore.activate(context, second);
    assertEquals("store-second", RelvenPlanStore.activePlan(context).id());
  }

  @Test
  public void rejectsActivatingAnUnstoredPlanAndCanClearActivePlan() throws Exception {
    RelvenPlanImporter.Plan plan = RelvenPlanImporter.parse(JSON);
    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.activate(context, plan));

    RelvenPlanStore.save(context, plan);
    RelvenPlanStore.activate(context, plan);
    RelvenPlanStore.clearActivePlan(context);

    assertNull(RelvenPlanStore.activePlan(context));
    assertEquals(1, RelvenPlanStore.list(context).size());
  }

  @Test
  public void reschedulingCreatesAndActivatesRevisionWithoutChangingOriginal() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanStore.activate(context, original);
    File originalFile = directory.listFiles()[0];

    RelvenPlanImporter.Plan revision =
        RelvenPlanStore.rescheduleActivePlan(context, original, "session-a", "2026-10-22");

    assertEquals(2, RelvenPlanStore.list(context).size());
    assertEquals(JSON, Files.readString(originalFile.toPath()));
    assertEquals(revision.id(), RelvenPlanStore.activePlan(context).id());
    assertEquals("store-test", revision.rootPlanId());
    assertEquals(1, revision.revisionNumber());
    assertEquals("store-test", revision.previousRevisionId());
    assertEquals("2026-10-22", revision.days().get(0).date());
  }

  @Test
  public void rejectsReschedulingWhenPlanIsNotTheCurrentSavedActivePackage() throws Exception {
    RelvenPlanImporter.Plan plan = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, plan);

    assertThrows(
        java.io.IOException.class,
        () -> RelvenPlanStore.rescheduleActivePlan(context, plan, "session-a", "2026-10-22"));
    assertEquals(1, RelvenPlanStore.list(context).size());
    assertNull(RelvenPlanStore.activePlan(context));
  }

  @Test
  public void rejectsConflictWithoutWritingOrChangingActiveRevision() throws Exception {
    String withRest =
        JSON.replace(
            "{\"id\":\"session-a\",\"date\":\"2026-10-20\",\"kind\":\"workout\","
                + "\"workoutId\":\"run-a\"}",
            "{\"id\":\"session-a\",\"date\":\"2026-10-20\",\"kind\":\"workout\","
                + "\"workoutId\":\"run-a\"},{\"id\":\"rest-a\","
                + "\"date\":\"2026-10-21\",\"kind\":\"rest\"}");
    RelvenPlanImporter.Plan plan = RelvenPlanImporter.parse(withRest);
    RelvenPlanStore.save(context, plan);
    RelvenPlanStore.activate(context, plan);

    assertThrows(
        IllegalArgumentException.class,
        () -> RelvenPlanStore.rescheduleActivePlan(context, plan, "session-a", "2026-10-21"));
    assertEquals(plan.id(), RelvenPlanStore.activePlan(context).id());
    assertEquals(1, RelvenPlanStore.list(context).size());
  }

  @Test
  public void rejectsCreatingRevisionFromAnEarlierActiveSnapshot() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanStore.activate(context, original);
    RelvenPlanImporter.Plan first =
        RelvenPlanStore.rescheduleActivePlan(context, original, "session-a", "2026-10-22");
    RelvenPlanStore.activate(context, original);

    assertThrows(
        java.io.IOException.class,
        () -> RelvenPlanStore.rescheduleActivePlan(context, original, "session-a", "2026-10-23"));
    assertEquals("2026-10-22", first.days().get(0).date());
    assertEquals(original.id(), RelvenPlanStore.activePlan(context).id());
    assertEquals(2, RelvenPlanStore.list(context).size());
  }

  @Test
  public void rejectsRevisionWithoutNextSequenceNumber() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanImporter.Plan skippedRevision =
        RelvenPlanRevision.reschedule(original, "session-a", "2026-10-22", "revision-2", 2);

    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.save(context, skippedRevision));
    assertEquals(1, RelvenPlanStore.list(context).size());
  }

  @Test
  public void addsAndRemovesScheduleEntriesAsActivatedImmutableRevisions() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanStore.activate(context, original);
    File originalFile = directory.listFiles()[0];

    RelvenPlanImporter.Plan withWorkout =
        RelvenPlanStore.addWorkoutToActivePlan(
            context, original, "2026-10-22", "run-a", "session-b");
    RelvenPlanImporter.Plan withRest =
        RelvenPlanStore.addRestToActivePlan(context, withWorkout, "2026-10-21", "rest-b");
    RelvenPlanImporter.Plan withoutWorkout =
        RelvenPlanStore.removeActivePlanEntry(context, withRest, "session-b");

    assertEquals(JSON, Files.readString(originalFile.toPath()));
    assertEquals(1, withWorkout.revisionNumber());
    assertEquals(2, withRest.revisionNumber());
    assertEquals(3, withoutWorkout.revisionNumber());
    assertEquals(withoutWorkout.id(), RelvenPlanStore.activePlan(context).id());
    assertEquals(2, withoutWorkout.days().size());
    assertEquals("rest-b", withoutWorkout.days().get(1).id());
    assertEquals(4, RelvenPlanStore.list(context).size());
  }

  @Test
  public void rejectsRevisionAddingMultipleEntriesOrChangingExistingEntry() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanImporter.Plan oneAddition =
        RelvenPlanRevision.addWorkout(original, "2026-10-22", "run-a", "session-b", "revision-1");
    JSONObject forgedJson = new JSONObject(oneAddition.sourceJson());
    JSONObject secondEntry = new JSONObject();
    secondEntry.put("id", "session-c");
    secondEntry.put("date", "2026-10-23");
    secondEntry.put("kind", "workout");
    secondEntry.put("workoutId", "run-a");
    forgedJson.getJSONArray("days").put(secondEntry);
    RelvenPlanImporter.Plan twoAdditions = RelvenPlanImporter.parse(forgedJson.toString());
    RelvenPlanImporter.Plan alteredExisting =
        RelvenPlanImporter.parse(oneAddition.sourceJson().replace("2026-10-20", "2026-10-21"));

    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.save(context, twoAdditions));
    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.save(context, alteredExisting));
    assertEquals(1, RelvenPlanStore.list(context).size());
  }

  @Test
  public void renameCreatesAndActivatesNameOnlyImmutableRevision() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanStore.activate(context, original);
    File originalFile = directory.listFiles()[0];

    RelvenPlanImporter.Plan renamed =
        RelvenPlanStore.renameActivePlan(context, original, "Evening plan");

    assertEquals(JSON, Files.readString(originalFile.toPath()));
    assertEquals("Evening plan", renamed.name());
    assertEquals("store-test", renamed.previousRevisionId());
    assertEquals(1, renamed.revisionNumber());
    assertEquals(renamed.id(), RelvenPlanStore.activePlan(context).id());
    assertEquals(original.days(), renamed.days());
    assertEquals(2, RelvenPlanStore.list(context).size());
  }

  @Test
  public void renameWorkoutCreatesAndActivatesImmutableRevision() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanStore.activate(context, original);
    File originalFile = directory.listFiles()[0];

    RelvenPlanImporter.Plan renamed =
        RelvenPlanStore.renameWorkoutInActivePlan(context, original, "run-a", "Tempo run");

    assertEquals(JSON, Files.readString(originalFile.toPath()));
    assertEquals("Tempo run", renamed.workoutNames().get("run-a"));
    assertEquals("store-test", renamed.previousRevisionId());
    assertEquals(1, renamed.revisionNumber());
    assertEquals(renamed.id(), RelvenPlanStore.activePlan(context).id());
    assertEquals(original.days(), renamed.days());
    assertEquals(2, RelvenPlanStore.list(context).size());
  }

  @Test
  public void rejectsRenamingWhenPlanIsNotCurrentActiveSnapshot() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanStore.activate(context, original);
    RelvenPlanImporter.Plan moved =
        RelvenPlanStore.rescheduleActivePlan(context, original, "session-a", "2026-10-22");

    assertThrows(
        java.io.IOException.class,
        () -> RelvenPlanStore.renameActivePlan(context, original, "Stale rename"));
    assertEquals(moved.id(), RelvenPlanStore.activePlan(context).id());
    assertEquals(2, RelvenPlanStore.list(context).size());
  }

  @Test
  public void rejectsRevisionCombiningRenameAndScheduleChange() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanImporter.Plan moved =
        RelvenPlanRevision.reschedule(original, "session-a", "2026-10-22", "revision-1");
    String mixedJson =
        moved.sourceJson().replace("\"name\":\"Store test\"", "\"name\":\"Renamed\"");
    RelvenPlanImporter.Plan mixed = RelvenPlanImporter.parse(mixedJson);

    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.save(context, mixed));
    assertEquals(1, RelvenPlanStore.list(context).size());
  }

  @Test
  public void rejectsRevisionWhosePreviousSnapshotIsNotSaved() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanImporter.Plan first =
        RelvenPlanRevision.reschedule(original, "session-a", "2026-10-22", "revision-1");
    RelvenPlanStore.save(context, first);
    RelvenPlanImporter.Plan second =
        RelvenPlanRevision.reschedule(first, "session-a", "2026-10-23", "revision-2");
    RelvenPlanImporter.Plan missingPredecessor =
        RelvenPlanImporter.parse(
            second
                .sourceJson()
                .replace(
                    "\"previousRevisionId\":\"revision-1\"",
                    "\"previousRevisionId\":\"missing-revision\""));

    assertThrows(
        java.io.IOException.class, () -> RelvenPlanStore.save(context, missingPredecessor));
    assertEquals(2, RelvenPlanStore.list(context).size());
  }

  @Test
  public void listsRevisionsInStableSequenceOrder() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanImporter.Plan first =
        RelvenPlanRevision.reschedule(original, "session-a", "2026-10-22", "revision-1");
    RelvenPlanStore.save(context, first);
    RelvenPlanImporter.Plan second =
        RelvenPlanRevision.reschedule(first, "session-a", "2026-10-23", "revision-2");
    RelvenPlanStore.save(context, second);

    List<RelvenPlanImporter.Plan> plans = RelvenPlanStore.list(context);

    assertEquals(
        List.of("store-test", "revision-1", "revision-2"),
        plans.stream().map(RelvenPlanImporter.Plan::id).toList());
  }

  @Test
  public void rejectsRevisionWhosePredecessorIsNotTheLineageTip() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanImporter.Plan first =
        RelvenPlanRevision.reschedule(original, "session-a", "2026-10-22", "revision-1");
    RelvenPlanStore.save(context, first);
    RelvenPlanImporter.Plan second =
        RelvenPlanRevision.reschedule(first, "session-a", "2026-10-23", "revision-2");
    RelvenPlanStore.save(context, second);
    RelvenPlanImporter.Plan skippedParent =
        RelvenPlanRevision.reschedule(original, "session-a", "2026-10-24", "revision-3", 3);

    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.save(context, skippedParent));
    assertEquals(3, RelvenPlanStore.list(context).size());
  }

  @Test
  public void rejectsRevisionThatChangesWorkoutStepsOrAddsAnotherScheduleEntry() throws Exception {
    RelvenPlanImporter.Plan original = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, original);
    RelvenPlanImporter.Plan rescheduled =
        RelvenPlanRevision.reschedule(original, "session-a", "2026-10-22", "revision-1");
    RelvenPlanImporter.Plan changedWorkout =
        RelvenPlanImporter.parse(rescheduled.sourceJson().replace("1800", "900"));
    JSONObject expandedJson = new JSONObject(rescheduled.sourceJson());
    expandedJson
        .getJSONArray("days")
        .put(
            new JSONObject().put("id", "extra-rest").put("date", "2026-10-23").put("kind", "rest"));
    RelvenPlanImporter.Plan addedSession = RelvenPlanImporter.parse(expandedJson.toString());

    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.save(context, changedWorkout));
    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.save(context, addedSession));
    assertEquals(1, RelvenPlanStore.list(context).size());
    assertEquals("store-test", original.id());
  }
}
