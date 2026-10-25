package org.runnerup.relven.ui

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.common.wear.StrengthExecutionProtocol
import org.runnerup.common.wear.StrengthPreparationAttempt
import org.runnerup.common.wear.WorkoutPreparationAttempt
import org.runnerup.common.wear.WorkoutPreparationProtocol
import org.runnerup.common.util.PreparedWorkoutLaunchRoute
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreparedWorkoutUiLauncherTest {
  @Test
  fun opensTheBrowsablePreparedRouteOnlyAfterCorrelatedStrengthAcknowledgement() {
    val nodeId = "verified-watch"
    val sessionId = UUID.randomUUID().toString()
    val requestId = UUID.randomUUID().toString()
    val attempt = StrengthPreparationAttempt(nodeId, sessionId, requestId)
    val ack = StrengthExecutionProtocol.encodeAcknowledgement(sessionId, requestId, true)
    val requests = mutableListOf<Pair<String, Intent>>()
    val starter = PreparedWorkoutUiLauncher.RemoteActivityStarter { targetNode, intent ->
      requests += targetNode to intent
    }

    PreparedWorkoutUiLauncher.afterStrengthPreparation(
      attempt.onMessage("different-watch", StrengthPreparationAttempt.ACK_PATH, ack),
      nodeId,
      starter)
    assertTrue(requests.isEmpty())

    val result = attempt.onMessage(nodeId, StrengthPreparationAttempt.ACK_PATH, ack)
    PreparedWorkoutUiLauncher.afterStrengthPreparation(result, nodeId, starter)

    assertEquals(1, requests.size)
    assertEquals(nodeId, requests.single().first)
    assertEquals(Intent.ACTION_VIEW, requests.single().second.action)
    assertEquals(PreparedWorkoutLaunchRoute.URI_STRING, requests.single().second.data.toString())
    assertTrue(requests.single().second.categories.contains(Intent.CATEGORY_BROWSABLE))
  }

  @Test
  fun opensTheBrowsablePreparedRouteOnlyAfterCorrelatedRunningAcknowledgement() {
    val nodeId = "verified-watch"
    val sessionId = UUID.randomUUID().toString()
    val requestId = UUID.randomUUID().toString()
    val attempt = WorkoutPreparationAttempt(nodeId, sessionId, requestId)
    val ack = WorkoutPreparationProtocol.encodeAcknowledgement(sessionId, requestId, true)
    val requests = mutableListOf<Pair<String, Intent>>()
    val starter = PreparedWorkoutUiLauncher.RemoteActivityStarter { targetNode, intent ->
      requests += targetNode to intent
    }

    val result = attempt.onMessage(nodeId, WorkoutPreparationAttempt.ACK_PATH, ack)
    PreparedWorkoutUiLauncher.afterWorkoutPreparation(result, nodeId, starter)

    assertEquals(1, requests.size)
    assertEquals(nodeId, requests.single().first)
    assertEquals(Intent.ACTION_VIEW, requests.single().second.action)
    assertEquals(PreparedWorkoutLaunchRoute.URI_STRING, requests.single().second.data.toString())
    assertTrue(requests.single().second.categories.contains(Intent.CATEGORY_BROWSABLE))
  }

  @Test
  fun doesNotOpenWatchForRejectedTimedOutOrInvalidPreparation() {
    val requests = mutableListOf<Pair<String, Intent>>()
    val starter = PreparedWorkoutUiLauncher.RemoteActivityStarter { targetNode, intent ->
      requests += targetNode to intent
    }

    PreparedWorkoutUiLauncher.afterStrengthPreparation(
      StrengthPreparationAttempt.Result.REJECTED, "watch", starter)
    PreparedWorkoutUiLauncher.afterStrengthPreparation(
      StrengthPreparationAttempt.Result.TIMEOUT, "watch", starter)
    PreparedWorkoutUiLauncher.afterStrengthPreparation(
      StrengthPreparationAttempt.Result.TRANSPORT_ERROR, "watch", starter)
    PreparedWorkoutUiLauncher.afterStrengthPreparation(
      StrengthPreparationAttempt.Result.PREPARED, " ", starter)
    PreparedWorkoutUiLauncher.afterWorkoutPreparation(
      WorkoutPreparationAttempt.Result.REJECTED, "watch", starter)
    PreparedWorkoutUiLauncher.afterWorkoutPreparation(
      WorkoutPreparationAttempt.Result.IGNORE, "watch", starter)

    assertTrue(requests.isEmpty())
  }
}
