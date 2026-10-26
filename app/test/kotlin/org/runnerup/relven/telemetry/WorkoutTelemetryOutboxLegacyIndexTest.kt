package org.runnerup.relven.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.runnerup.common.wear.WorkoutTelemetryOutbox
import org.runnerup.common.wear.WorkoutTelemetryOutboxStore
import org.runnerup.common.wear.WorkoutTelemetryProtocol

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkoutTelemetryOutboxLegacyIndexTest {
  @Test
  fun discoversPendingOutboxFilesFromBeforeSessionIndexWasIntroduced() {
    val context = RuntimeEnvironment.getApplication()
    val sessionId = "legacy-index-${System.nanoTime()}"
    val directory = java.io.File(context.filesDir, "workout-telemetry")
    directory.deleteRecursively()
    val store = WorkoutTelemetryOutboxStore(context)
    val sample =
        WorkoutTelemetryProtocol.Sample(
            sessionId,
            1,
            1_000_000_000,
            System.currentTimeMillis(),
            59.33,
            18.06,
            5.0,
            null,
            null,
            140.0)

    assertEquals(WorkoutTelemetryOutbox.AppendResult.APPENDED, store.append(sessionId, sample))
    java.io.File(directory, "sessions.index").delete()

    val recovered = WorkoutTelemetryOutboxStore(context)

    assertEquals(listOf(sessionId), recovered.pendingSessionIds())
    assertTrue(java.io.File(directory, "sessions.index").exists())
    assertEquals(sample, recovered.pendingBatch(sessionId, 10, 32 * 1024)!!.samples.single())
  }
}
