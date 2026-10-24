package org.runnerup.relven.health

import android.content.Context
import androidx.health.connect.client.records.Record
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectExportProcessorTest {
  private lateinit var context: Context
  private val start = Instant.parse("2026-10-24T08:00:00Z")

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getDir("relven-health-connect-outbox", Context.MODE_PRIVATE).deleteRecursively()
  }

  @Test
  fun upsertWaitsForExercisePermissionWithoutWriting() = runBlocking {
    val entry = enqueue()
    val gateway = RecordingGateway()

    val outcome = process(entry, emptySet(), gateway)

    assertEquals(HealthConnectExportOutcome.WAITING_FOR_PERMISSION, outcome)
    assertTrue(gateway.inserted.isEmpty())
    assertEquals(HealthConnectExportStatus.WAITING_PERMISSION,
        HealthConnectExportStore.get(context, entry.sessionId)!!.status)
  }

  @Test
  fun successfulUpsertPersistsOwnedRecordKindsAndStableVersion() = runBlocking {
    val entry = enqueue()
    val gateway = RecordingGateway()

    val outcome = process(entry, setOf("android.permission.health.WRITE_EXERCISE"), gateway)

    assertEquals(HealthConnectExportOutcome.COMPLETE, outcome)
    assertEquals(1, gateway.inserted.size)
    val saved = HealthConnectExportStore.get(context, entry.sessionId)!!
    assertEquals(HealthConnectExportOperation.DONE, saved.operation)
    assertEquals(HealthConnectExportStatus.EXPORTED, saved.status)
    assertEquals(setOf(HealthConnectRecordKind.SESSION), saved.ownedRecordKinds)
  }

  @Test
  fun missingOptionalRoutePermissionExportsSessionAndRecordsOmission() = runBlocking {
    val entry = HealthConnectExportStore.enqueueUpsert(context,
        RecordedWorkoutForHealthConnect(
            sessionId = "activity-43", version = 0, activity = HealthConnectActivity.Running,
            title = null, notes = null, startTime = start, endTime = start.plusSeconds(60),
            route = listOf(
                HealthConnectRouteSample(start.plusSeconds(1), 59.0, 18.0),
                HealthConnectRouteSample(start.plusSeconds(2), 59.001, 18.001))), 10)
    val gateway = RecordingGateway()

    val outcome = process(entry, setOf("android.permission.health.WRITE_EXERCISE"), gateway)

    assertEquals(HealthConnectExportOutcome.COMPLETE, outcome)
    val saved = HealthConnectExportStore.get(context, entry.sessionId)!!
    assertEquals(HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS, saved.status)
    assertEquals(setOf("android.permission.health.WRITE_EXERCISE_ROUTE"), saved.omittedPermissions)
    assertEquals(1, gateway.inserted.size)
  }

  @Test
  fun revokedPermissionRetainsOldRecordOwnershipAndReportsStaleDataCleanupOmission() = runBlocking {
    val entry = enqueue()
    assertTrue(HealthConnectExportStore.markExported(context, entry.sessionId, entry.version,
        setOf(HealthConnectRecordKind.SESSION, HealthConnectRecordKind.DISTANCE,
            HealthConnectRecordKind.HEART_RATE), emptySet(), 20))
    val edited = HealthConnectExportStore.enqueueUpsert(context,
        RecordedWorkoutForHealthConnect(
            sessionId = entry.sessionId, version = 0, activity = HealthConnectActivity.Running,
            title = "Edited run", notes = null, startTime = start, endTime = start.plusSeconds(60)), 30)
    assertEquals(setOf(HealthConnectRecordKind.SESSION, HealthConnectRecordKind.DISTANCE,
        HealthConnectRecordKind.HEART_RATE), edited.ownedRecordKinds)

    val outcome = process(edited, setOf("android.permission.health.WRITE_EXERCISE"), RecordingGateway())

    assertEquals(HealthConnectExportOutcome.COMPLETE, outcome)
    val saved = HealthConnectExportStore.get(context, edited.sessionId)!!
    assertEquals(HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS, saved.status)
    assertEquals(setOf(HealthConnectRecordKind.SESSION, HealthConnectRecordKind.DISTANCE,
        HealthConnectRecordKind.HEART_RATE), saved.ownedRecordKinds)
    assertEquals(setOf("android.permission.health.WRITE_DISTANCE", "android.permission.health.WRITE_HEART_RATE"),
        saved.omittedPermissions)
  }

  @Test
  fun transientWriteFailureLeavesRetryableEntry() = runBlocking {
    val entry = enqueue()
    val gateway = RecordingGateway().apply { insertFailure = IOException("temporary") }

    val outcome = process(entry, setOf("android.permission.health.WRITE_EXERCISE"), gateway)

    assertEquals(HealthConnectExportOutcome.RETRY, outcome)
    val saved = HealthConnectExportStore.get(context, entry.sessionId)!!
    assertEquals(HealthConnectExportStatus.RETRYABLE_ERROR, saved.status)
    assertEquals(1, saved.attemptCount)
  }

  @Test
  fun workerCancellationIsPropagatedWithoutChangingOutboxStatus() = runBlocking {
    val entry = enqueue()
    val gateway = RecordingGateway().apply { insertFailure = CancellationException("stopped") }

    try {
      process(entry, setOf("android.permission.health.WRITE_EXERCISE"), gateway)
      throw AssertionError("Expected worker cancellation to propagate")
    } catch (expected: CancellationException) {
      assertEquals("stopped", expected.message)
    }

    val saved = HealthConnectExportStore.get(context, entry.sessionId)!!
    assertEquals(HealthConnectExportStatus.PENDING, saved.status)
    assertEquals(0, saved.attemptCount)
  }

  @Test
  fun deleteOnlyDeletesOwnedKindsAndCompletesTombstone() = runBlocking {
    val entry = enqueue()
    HealthConnectExportStore.markExported(context, entry.sessionId, entry.version,
        setOf(HealthConnectRecordKind.SESSION, HealthConnectRecordKind.DISTANCE), emptySet(), 20)
    val deletion = HealthConnectExportStore.enqueueDelete(context, entry.sessionId, 30)!!
    val gateway = RecordingGateway()

    val outcome = process(deletion,
        setOf("android.permission.health.WRITE_EXERCISE", "android.permission.health.WRITE_DISTANCE"), gateway)

    assertEquals(HealthConnectExportOutcome.COMPLETE, outcome)
    assertEquals(setOf(HealthConnectRecordKind.SESSION, HealthConnectRecordKind.DISTANCE), gateway.deletedKinds)
    assertTrue(gateway.deletedIds.all { it.startsWith("relven-${entry.sessionId}-") })
    val saved = HealthConnectExportStore.get(context, entry.sessionId)!!
    assertEquals(HealthConnectExportOperation.DONE, saved.operation)
    assertEquals(HealthConnectExportStatus.DELETED, saved.status)
    assertFalse(HealthConnectExportStore.pending(context).any { it.sessionId == entry.sessionId })
  }

  @Test
  fun deleteWaitsForAnInFlightInsertAndRemovesItsStableIds() = runBlocking {
    val entry = enqueue()
    val gateway = RecordingGateway().apply { holdInsert = true }
    val permissions = setOf(
        "android.permission.health.WRITE_EXERCISE",
        "android.permission.health.WRITE_EXERCISE_ROUTE",
        "android.permission.health.WRITE_DISTANCE",
        "android.permission.health.WRITE_HEART_RATE",
        "android.permission.health.WRITE_SPEED",
        "android.permission.health.WRITE_STEPS")
    val upsert = async {
      process(entry, permissions, gateway)
    }
    gateway.insertEntered.await()
    val deletion = HealthConnectExportStore.enqueueDelete(context, entry.sessionId, 30)!!
    val delete = async {
      HealthConnectExportProcessor.process(context, deletion, permissions, gateway,
          exportOptedIn = true, availability = HealthConnectAvailabilityStatus.AVAILABLE, nowMillis = 40)
    }

    yield()
    assertTrue("delete must wait until the in-flight insert returns", gateway.deletedKinds.isEmpty())
    gateway.allowInsert.complete(Unit)
    assertEquals(HealthConnectExportOutcome.COMPLETE, upsert.await())
    assertEquals(HealthConnectExportOutcome.COMPLETE, delete.await())
    assertTrue(gateway.inserted.isNotEmpty())
    assertEquals(deletion.ownedRecordKinds, gateway.deletedKinds)
    assertTrue(gateway.deletedIds.all { it.startsWith("relven-${entry.sessionId}-") })
  }

  private fun enqueue(): HealthConnectExportEntry = HealthConnectExportStore.enqueueUpsert(context,
      RecordedWorkoutForHealthConnect(
          sessionId = "activity-42", version = 0, activity = HealthConnectActivity.Running,
          title = "Run", notes = null, startTime = start, endTime = start.plusSeconds(60)), 10)

  private suspend fun process(
      entry: HealthConnectExportEntry,
      permissions: Set<String>,
      gateway: HealthConnectExportGateway) = HealthConnectExportProcessor.process(
      context, entry, permissions, gateway, exportOptedIn = true,
      availability = HealthConnectAvailabilityStatus.AVAILABLE, nowMillis = 40)

  private class RecordingGateway : HealthConnectExportGateway {
    val inserted = mutableListOf<Record>()
    val deletedKinds = mutableSetOf<HealthConnectRecordKind>()
    val deletedIds = mutableListOf<String>()
    val insertEntered = CompletableDeferred<Unit>()
    val allowInsert = CompletableDeferred<Unit>()
    var holdInsert = false
    var insertFailure: Exception? = null

    override suspend fun insertRecords(records: List<Record>) {
      insertFailure?.let { throw it }
      if (holdInsert) {
        insertEntered.complete(Unit)
        allowInsert.await()
      }
      inserted += records
    }

    override suspend fun deleteRecords(kind: HealthConnectRecordKind, clientRecordId: String) {
      deletedKinds += kind
      deletedIds += clientRecordId
    }
  }
}
