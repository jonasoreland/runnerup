package org.runnerup.relven.health

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectExportStoreTest {
  private lateinit var context: Context
  private val start = Instant.parse("2026-10-24T08:00:00Z")

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteSharedPreferences("relven-health-connect-outbox")
    context.getDir("relven-health-connect-outbox", Context.MODE_PRIVATE).deleteRecursively()
  }

  @Test
  fun storesAndRestoresWorkoutPayloadAndRetryStatus() {
    val queued = HealthConnectExportStore.enqueueUpsert(context, workout(), 100)
    assertTrue(HealthConnectExportStore.markStatus(
        context, queued.sessionId, queued.version, HealthConnectExportStatus.RETRYABLE_ERROR,
        error = "temporary", nowMillis = 200))

    val reopened = HealthConnectExportStore.get(context, queued.sessionId)!!
    assertEquals(HealthConnectExportStatus.RETRYABLE_ERROR, reopened.status)
    assertEquals(1, reopened.attemptCount)
    val decoded = HealthConnectWorkoutCodec.decode(reopened.payload!!)
    assertEquals(queued.version, decoded.version)
    assertEquals(workout(), decoded)
    assertEquals(queued.sessionId, HealthConnectExportStore.pending(context).single().sessionId)
  }

  @Test
  fun identicalUpsertIsIdempotentAndChangedSourceIncrementsVersion() {
    val first = HealthConnectExportStore.enqueueUpsert(context, workout(), 100)
    assertTrue(HealthConnectExportStore.markExported(
        context, first.sessionId, first.version, setOf(HealthConnectRecordKind.SESSION), emptySet(), 200))
    val saved = HealthConnectExportStore.get(context, first.sessionId)!!
    assertEquals(HealthConnectExportStatus.EXPORTED, saved.status)
    assertEquals(saved.payload, HealthConnectWorkoutCodec.encode(workout().copy(version = saved.version)))
    val duplicate = HealthConnectExportStore.enqueueUpsert(context, workout(), 300)

    assertEquals(first.version, duplicate.version)
    assertEquals(HealthConnectExportStatus.EXPORTED, duplicate.status)
    assertFalse(HealthConnectExportStore.pending(context).isNotEmpty())

    val edited = HealthConnectExportStore.enqueueUpsert(context, workout().copy(notes = "edited"), 400)
    assertEquals(first.version + 1, edited.version)
    assertEquals(setOf(HealthConnectRecordKind.SESSION), edited.ownedRecordKinds)
    assertEquals(HealthConnectExportStatus.PENDING, edited.status)
  }

  @Test
  fun deleteQueuesPreviouslyExportedAndInFlightRelvenRecords() {
    val neverExported = HealthConnectExportStore.enqueueUpsert(context, workout(), 100)
    val inFlightDelete = HealthConnectExportStore.enqueueDelete(context, neverExported.sessionId, 200)!!
    assertEquals(HealthConnectExportOperation.DELETE, inFlightDelete.operation)
    assertEquals(setOf(HealthConnectRecordKind.SESSION, HealthConnectRecordKind.DISTANCE,
        HealthConnectRecordKind.HEART_RATE, HealthConnectRecordKind.CADENCE), inFlightDelete.ownedRecordKinds)

    val withoutOutbox = "activity-no-outbox"
    assertNull(HealthConnectExportStore.enqueueDelete(context, withoutOutbox, 250))

    val exported = HealthConnectExportStore.enqueueUpsert(context, workout().copy(sessionId = "activity-10"), 300)
    assertTrue(HealthConnectExportStore.markExported(
        context, exported.sessionId, exported.version,
        setOf(HealthConnectRecordKind.SESSION, HealthConnectRecordKind.DISTANCE), emptySet(), 400))
    val deletion = HealthConnectExportStore.enqueueDelete(context, exported.sessionId, 500)!!
    assertEquals(HealthConnectExportOperation.DELETE, deletion.operation)
    assertEquals(setOf(HealthConnectRecordKind.SESSION, HealthConnectRecordKind.DISTANCE), deletion.ownedRecordKinds)
    assertTrue(HealthConnectExportStore.markDeleted(context, exported.sessionId, 600))
    assertEquals(HealthConnectExportStatus.DELETED, HealthConnectExportStore.get(context, exported.sessionId)!!.status)
    assertFalse(HealthConnectExportStore.pending(context).any { it.sessionId == exported.sessionId })
  }

  private fun workout() = RecordedWorkoutForHealthConnect(
      sessionId = "activity-9",
      version = 0,
      activity = HealthConnectActivity.Running,
      title = "Morning run",
      notes = null,
      startTime = start,
      endTime = start.plusSeconds(600),
      distanceMeters = 1_200.0,
      route = listOf(
          HealthConnectRouteSample(start.plusSeconds(1), 59.0, 18.0, cadencePerMinute = 160.0),
          HealthConnectRouteSample(start.plusSeconds(2), 59.001, 18.001, heartRateBpm = 140.0)))
}
