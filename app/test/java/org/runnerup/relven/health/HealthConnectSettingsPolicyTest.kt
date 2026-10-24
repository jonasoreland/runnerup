package org.runnerup.relven.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectSettingsPolicyTest {
  @Test
  fun exportCanOnlyBeEnabledWhenSessionWritePermissionIsGranted() {
    assertFalse(canEnableHealthConnectExport(emptySet()))
    assertFalse(canEnableHealthConnectExport(setOf("android.permission.health.WRITE_DISTANCE")))
    assertTrue(canEnableHealthConnectExport(setOf("android.permission.health.WRITE_EXERCISE")))
  }

  @Test
  fun backfillExcludesAlreadyPublishedAndDeletedSessionsButIncludesRetryableOnes() {
    val sessions = listOf("new", "exported", "omitted", "failed", "deleted", "pending")
    val entries = listOf(
        status("exported", HealthConnectExportOperation.DONE, HealthConnectExportStatus.EXPORTED),
        status("omitted", HealthConnectExportOperation.DONE, HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS),
        status("failed", HealthConnectExportOperation.UPSERT, HealthConnectExportStatus.RETRYABLE_ERROR),
        status("deleted", HealthConnectExportOperation.DONE, HealthConnectExportStatus.DELETED),
        status("pending", HealthConnectExportOperation.UPSERT, HealthConnectExportStatus.PENDING),
        status("delete-pending", HealthConnectExportOperation.DELETE, HealthConnectExportStatus.PENDING))

    assertEquals(listOf("new", "failed", "pending"),
        HealthConnectBackfillPolicy.eligibleSessionIds(sessions + "delete-pending", entries))
  }

  @Test
  fun omittedMetricSessionCanRetryOnlyAfterOneMissingPermissionIsGranted() {
    val entry = status(
        "route-session", HealthConnectExportOperation.DONE, HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS)
        .copy(omittedPermissions = setOf(
            "android.permission.health.WRITE_EXERCISE_ROUTE",
            "android.permission.health.WRITE_HEART_RATE"))

    assertFalse(HealthConnectBackfillPolicy.omissionCanRetry(entry, emptySet()))
    assertTrue(HealthConnectBackfillPolicy.omissionCanRetry(
        entry, setOf("android.permission.health.WRITE_EXERCISE_ROUTE")))
  }

  @Test
  fun databaseActivityIdIsParsedFromCanonicalSessionIdWithoutAddingAnotherPrefix() {
    assertEquals(42L, HealthConnectBackfillPolicy.databaseActivityId("activity-42"))
    assertEquals(null, HealthConnectBackfillPolicy.databaseActivityId("42"))
    assertEquals(null, HealthConnectBackfillPolicy.databaseActivityId("activity-not-a-number"))
    assertEquals(null, HealthConnectBackfillPolicy.databaseActivityId("activity-activity-42"))
  }

  private fun status(
      id: String,
      operation: HealthConnectExportOperation,
      status: HealthConnectExportStatus) = HealthConnectExportEntry(
      sessionId = id,
      version = 1,
      operation = operation,
      status = status,
      payload = null,
      ownedRecordKinds = emptySet(),
      updatedAtMillis = 1)
}
