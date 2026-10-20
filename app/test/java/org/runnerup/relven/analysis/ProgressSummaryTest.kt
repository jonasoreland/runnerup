package org.runnerup.relven.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressSummaryTest {
  private companion object {
    const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    const val SEVEN_DAYS_MILLIS = 7 * DAY_MILLIS
    const val TWENTY_EIGHT_DAYS_MILLIS = 28 * DAY_MILLIS
  }

  private val nowMillis = 1_800_000_000_000L

  @Test
  fun countsExactSevenAndTwentyEightDayWindows() {
    val summary =
        summarizeActivities(
            listOf(
                activity("today", nowMillis, 600, 2_000.0),
                activity("seven-days", nowMillis - SEVEN_DAYS_MILLIS, 300, 1_000.0),
                activity("eight-days", nowMillis - 8 * DAY_MILLIS, 900, 3_000.0),
                activity("twenty-eight-days", nowMillis - TWENTY_EIGHT_DAYS_MILLIS, 120, 500.0),
                activity("older", nowMillis - 29 * DAY_MILLIS, 120, 500.0)),
            nowMillis)

    assertEquals(ActivityWindowSummary(2, 900, 3_000.0, 2), summary.sevenDays)
    assertEquals(ActivityWindowSummary(4, 1_920, 6_500.0, 4), summary.twentyEightDays)
  }

  @Test
  fun countsDuplicateActivityIdsOnce() {
    val row = activity("same", nowMillis, 600, 2_000.0)

    val summary = summarizeActivities(listOf(row, row.copy(durationSeconds = 900)), nowMillis)

    assertEquals(ActivityWindowSummary(1, 600, 2_000.0, 1), summary.sevenDays)
  }

  @Test
  fun doesNotLetAnInvalidDuplicateHideAValidActivity() {
    val invalid = activity("same", nowMillis, -1, 100.0)
    val valid = activity("same", nowMillis, 120, 400.0)

    val summary = summarizeActivities(listOf(invalid, valid), nowMillis)

    assertEquals(ActivityWindowSummary(1, 120, 400.0, 1), summary.sevenDays)
  }

  @Test
  fun skipsInvalidStartsAndDurationsButKeepsValidTimeWhenDistanceIsInvalid() {
    val summary =
        summarizeActivities(
            listOf(
                activity("valid", nowMillis, 60, Double.NaN),
                activity("future", nowMillis + 1, 90, 100.0),
                activity("zero-start", 0, 90, 100.0),
                activity("negative-duration", nowMillis, -1, 100.0),
                activity("empty-id", nowMillis, 100, 100.0).copy(id = "")),
            nowMillis)

    assertEquals(1, summary.sevenDays.sessionCount)
    assertEquals(60, summary.sevenDays.durationSeconds)
    assertNull(summary.sevenDays.distanceMeters)
    assertEquals(0, summary.sevenDays.distanceSessionCount)
  }

  @Test
  fun emptyRowsProduceZeroTotalsAndUnknownDistance() {
    val summary = summarizeActivities(emptyList(), nowMillis)

    assertEquals(ActivityWindowSummary(0, 0, null, 0), summary.sevenDays)
    assertEquals(ActivityWindowSummary(0, 0, null, 0), summary.twentyEightDays)
  }

  @Test
  fun zeroDistanceDoesNotCountAsARecordedDistance() {
    val summary = summarizeActivities(listOf(activity("zero", nowMillis, 60, 0.0)), nowMillis)

    assertEquals(1, summary.sevenDays.sessionCount)
    assertEquals(60, summary.sevenDays.durationSeconds)
    assertNull(summary.sevenDays.distanceMeters)
    assertEquals(0, summary.sevenDays.distanceSessionCount)
  }

  @Test
  fun distanceOverflowKeepsCoverageCountButHidesInvalidTotal() {
    val summary =
        summarizeActivities(
            listOf(
                activity("large-1", nowMillis, 60, Double.MAX_VALUE),
                activity("large-2", nowMillis, 60, Double.MAX_VALUE)),
            nowMillis)

    assertNull(summary.sevenDays.distanceMeters)
    assertEquals(2, summary.sevenDays.distanceSessionCount)
  }

  private fun activity(
      id: String,
      startedAtMillis: Long,
      durationSeconds: Long?,
      distanceMeters: Double?
  ) = RecordedActivity(id, startedAtMillis, durationSeconds, distanceMeters)
}
