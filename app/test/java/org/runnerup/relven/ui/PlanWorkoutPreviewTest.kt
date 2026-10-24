package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.runnerup.relven.strength.CompletedStrengthSession
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.workout.RelvenStrengthWorkoutParser

class PlanWorkoutPreviewTest {
  @Test
  fun estimatesRepeatedTimedRunningSegmentsExactly() {
    val estimate =
        estimateRunningWorkoutDuration(
            listOf(
                segment("warmup", "time", 600.0),
                segment("work", "time", 180.0, repeatCount = 5),
                segment("recovery", "time", 120.0, repeatCount = 5),
                segment("cooldown", "time", 600.0)))

    assertEquals(DurationEstimate(2_700L, 2_700L), estimate)
  }

  @Test
  fun estimatesDistanceSegmentFromPaceTarget() {
    val estimate =
        estimateRunningWorkoutDuration(
            listOf(
                RunningSegmentSummary(
                    "distance",
                    null,
                    "work",
                    RunningSegmentEnd("distance", 1_000.0),
                    RunningSegmentTarget("pace", 300.0, 360.0),
                    1)))

    assertEquals(DurationEstimate(300L, 360L, approximate = true), estimate)
  }

  @Test
  fun estimatesDistanceSegmentFromSpeedTargetInReverseOrder() {
    val estimate =
        estimateRunningWorkoutDuration(
            listOf(
                RunningSegmentSummary(
                    "distance",
                    null,
                    "work",
                    RunningSegmentEnd("distance", 1_000.0),
                    RunningSegmentTarget("speed", 2.5, 3.0),
                    1)))

    assertEquals(DurationEstimate(333L, 400L, approximate = true), estimate)
  }

  @Test
  fun doesNotEstimateManualOrUnboundedDistanceSegments() {
    assertNull(estimateRunningWorkoutDuration(listOf(segment("manual", "manual", null))))
    assertNull(
        estimateRunningWorkoutDuration(
            listOf(segment("distance", "distance", 1_000.0))))
  }

  @Test
  fun estimatesRepetitionWorkoutAsAnApproximateRangeIncludingRest() {
    val estimate = estimateStrengthWorkoutDuration(strengthWorkout())

    assertEquals(DurationEstimate(180L, 264L, approximate = true), estimate)
  }

  @Test
  fun usesTimedPrescriptionAndRestForTimedStrengthWork() {
    val workout =
        RelvenStrengthWorkoutParser.parse(
            strengthJson(
                end = "\"type\":\"time\",\"seconds\":30",
                exercise =
                    "\"sets\":2,\"minRepetitions\":1,\"maxRepetitions\":1,\"restSeconds\":30"))

    assertEquals(DurationEstimate(90L, 90L, approximate = true), estimateStrengthWorkoutDuration(workout))
  }

  @Test
  fun previewShowsCategoryAndUpcomingTodayOrPastStatus() {
    val entry = strengthEntry()

    assertEquals(
        PlanWorkoutPreview(WorkoutCategory.STRENGTH, DurationEstimate(180L, 264L, true), PlanWorkoutStatus.UPCOMING),
        planWorkoutPreview(entry, "2026-10-25", "2026-10-24"))
    assertEquals(
        PlanWorkoutStatus.TODAY,
        planWorkoutPreview(entry, "2026-10-24", "2026-10-24").status)
    assertEquals(
        PlanWorkoutStatus.PAST,
        planWorkoutPreview(entry, "2026-10-23", "2026-10-24").status)
  }

  @Test
  fun completedStrengthSessionStatusTakesPrecedenceOverItsDate() {
    val workout = strengthWorkout()
    var session = StrengthSession.start("plan", "session", workout)
    session = session.completeRepetitions(8).skipRest()
        .completeRepetitions(9).skipRest().completeRepetitions(10)
    val entry = strengthEntry(CompletedStrengthSession(session, 1_792_449_000_000L))

    assertEquals(
        PlanWorkoutStatus.COMPLETED,
        planWorkoutPreview(entry, "2026-10-25", "2026-10-24").status)
  }

  @Test
  fun prehabUsesItsOwnCategory() {
    val workout = RelvenStrengthWorkoutParser.parse(strengthJson(sport = "prehab"))
    val entry = PlanCalendarEntry.Workout("session", "prehab", "Mobility", "", strengthDetails = workout)

    assertEquals(
        WorkoutCategory.PREHAB,
        planWorkoutPreview(entry, "2026-10-24", "2026-10-24").category)
  }

  private fun segment(
      id: String,
      type: String,
      value: Double?,
      repeatCount: Int = 1
  ) = RunningSegmentSummary(
      id, null, "work", RunningSegmentEnd(type, value), null, repeatCount)

  private fun strengthEntry(completion: CompletedStrengthSession? = null) =
      PlanCalendarEntry.Workout(
          "session", "strength", "Strength", "", strengthDetails = strengthWorkout(), completion = completion)

  private fun strengthWorkout() = RelvenStrengthWorkoutParser.parse(strengthJson())

  private fun strengthJson(
      sport: String = "strength",
      end: String = "\"type\":\"manual\"",
      exercise: String = "\"sets\":3,\"minRepetitions\":8,\"maxRepetitions\":12,\"restSeconds\":60"
  ) =
      """{"schemaVersion":3,"id":"strength","name":"Strength","sport":"$sport","steps":[{"id":"squat","kind":"segment","phase":"work","end":{$end},"exercise":{"exerciseId":"3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0","libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Squat","instructions":"Move with control.","equipment":[],"images":[]},$exercise}}]}"""
}
