package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Test
import org.runnerup.workout.RelvenPlanImporter

class RunningWorkoutDefinitionsTest {
  private val nestedPlan =
      RelvenPlanImporter.parse(
          """
          {"format":"relven.plan","schemaVersion":1,"id":"segments","name":"Intervals",
          "timezone":"Europe/Stockholm","workouts":[{"schemaVersion":3,"id":"run",
          "name":"Intervals","sport":"running","steps":[
            {"id":"warm","kind":"segment","phase":"warmup","end":{"type":"time","seconds":300}},
            {"id":"block","kind":"sequence","steps":[
              {"id":"repeat","kind":"repeat","count":3,"steps":[
                {"id":"work","kind":"segment","phase":"work","end":{"type":"distance","meters":400},
                 "target":{"type":"pace","minSecondsPerKm":300,"maxSecondsPerKm":330}},
                {"id":"recover","kind":"segment","phase":"recovery","end":{"type":"time","seconds":90}}
              ]}
            ]}
          ]}],"days":[{"id":"session","date":"2026-10-20","kind":"workout","workoutId":"run"}]}
          """.trimIndent())

  @Test
  fun segmentsKeepSourceOrderAndRepeatCount() {
    val segments = runningWorkoutSegments(nestedPlan, "run")

    assertEquals(listOf("warm", "work", "recover"), segments.map { it.id })
    assertEquals(listOf(1, 3, 3), segments.map { it.repeatCount })
    assertEquals(listOf("warmup", "work", "recovery"), segments.map { it.phase })
    assertEquals(RunningSegmentEnd("distance", 400.0), segments[1].end)
    assertEquals(RunningSegmentTarget("pace", 300.0, 330.0), segments[1].target)
    assertEquals(RunningSegmentEnd("time", 90.0), segments[2].end)
  }

  @Test
  fun manualEndsAndMissingTargetsRemainExplicit() {
    val plan = RelvenPlanImporter.parse(
        nestedPlan.sourceJson().replace(
            "\"type\":\"time\",\"seconds\":300",
            "\"type\":\"manual\""))

    val first = runningWorkoutSegments(plan, "run").first()

    assertEquals(RunningSegmentEnd("manual", null), first.end)
    assertNull(first.target)
  }

  @Test
  fun nonRunningWorkoutHasNoEditableRunningSegments() {
    assertEquals(emptyList<RunningSegmentSummary>(), runningWorkoutSegments(nestedPlan, "missing"))
  }

  @Test
  fun malformedSavedSourceIsRejected() {
    val malformed =
        RelvenPlanImporter.Plan(
            nestedPlan.schemaVersion(),
            nestedPlan.id(),
            nestedPlan.name(),
            nestedPlan.timezone(),
            nestedPlan.rootPlanId(),
            nestedPlan.revisionNumber(),
            nestedPlan.previousRevisionId(),
            nestedPlan.days(),
            nestedPlan.workouts(),
            nestedPlan.strengthWorkouts(),
            nestedPlan.workoutNames(),
            "{")

    assertThrows(IllegalArgumentException::class.java) {
      runningWorkoutSegments(malformed, "run-a")
    }
  }
}
