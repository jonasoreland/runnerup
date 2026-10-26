package org.runnerup.relven.strength

import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.runnerup.common.util.Constants
import org.runnerup.common.wear.StrengthResultOutboxStore
import org.runnerup.common.wear.StrengthResultProtocol
import org.runnerup.common.wear.StrengthSessionAuthorityProtocol
import org.runnerup.common.wear.WorkoutTelemetryProtocol
import org.runnerup.db.DBHelper
import org.runnerup.relven.health.HealthConnectActivitySync
import org.runnerup.relven.telemetry.WorkoutTelemetryInboxStore
import org.runnerup.relven.telemetry.WorkoutTelemetryRouteReconciler
import org.runnerup.relven.ui.WearConnectionProvider

/** Background phone endpoint for durable phone↔watch strength-result delivery. */
class StrengthResultListenerService : WearableListenerService() {
  private val worker = Executors.newSingleThreadExecutor()

  override fun onPeerConnected(peer: Node) {
    super.onPeerConnected(peer)
    worker.execute {
      if (isConnectedPeer(peer.id)) {
        WearConnectionProvider.sendPendingStrengthResult(applicationContext, peer.id)
        WearConnectionProvider.sendPendingStrengthAuthorityCommand(applicationContext, null)
      }
    }
  }

  override fun onMessageReceived(event: MessageEvent) {
    super.onMessageReceived(event)
    worker.execute {
      try {
        if (!isConnectedPeer(event.sourceNodeId)) return@execute
        when (event.path) {
          Constants.Wear.Path.MSG_STRENGTH_RESULT -> receiveResult(event)
          Constants.Wear.Path.MSG_STRENGTH_RESULT_ACK -> receiveAcknowledgement(event)
          Constants.Wear.Path.MSG_STRENGTH_AUTHORITY_PROJECTION -> receiveStrengthProjection(event)
          Constants.Wear.Path.MSG_STRENGTH_AUTHORITY_RESPONSE -> receiveStrengthAuthorityResponse(event)
          Constants.Wear.Path.MSG_WORKOUT_TELEMETRY -> receiveWorkoutTelemetry(event)
        }
      } catch (error: Exception) {
        Log.w(TAG, "Unable to process Wear strength result message", error)
      }
    }
  }

  private fun receiveStrengthProjection(event: MessageEvent) {
    val projection = StrengthSessionAuthorityProtocol.decodeProjection(event.data)
    val outcome =
        StrengthSessionStore.applyAuthoritativeProjection(applicationContext, projection)
    when (outcome) {
      StrengthSessionStore.AuthorityProjectionApplyResult.COMMITTED,
      StrengthSessionStore.AuthorityProjectionApplyResult.DUPLICATE -> {
        if (outcome == StrengthSessionStore.AuthorityProjectionApplyResult.COMMITTED) {
          sendBroadcast(
              Intent(Constants.Intents.STRENGTH_RESULT_UPDATED)
                  .setPackage(packageName)
                  .putExtra(Constants.Intents.EXTRA_STRENGTH_EXECUTION_ID, projection.sessionId))
        }
        val ack =
            StrengthSessionAuthorityProtocol.Acknowledgement(
                projection.sessionId,
                projection.requestId,
                projection.revision,
                if (outcome == StrengthSessionStore.AuthorityProjectionApplyResult.COMMITTED)
                    StrengthSessionAuthorityProtocol.Outcome.COMMITTED
                else StrengthSessionAuthorityProtocol.Outcome.DUPLICATE)
        StrengthAuthorityCommandStore(applicationContext).acknowledge(projection.requestId)
        Wearable.getMessageClient(applicationContext)
            .sendMessage(
                event.sourceNodeId,
                Constants.Wear.Path.MSG_STRENGTH_AUTHORITY_ACK,
                StrengthSessionAuthorityProtocol.encodeAcknowledgement(ack))
            .addOnFailureListener { error -> Log.w(TAG, "Strength projection ACK failed; watch will replay", error) }
      }
      else -> Log.w(TAG, "Rejected watch strength projection: $outcome")
    }
  }

  private fun receiveStrengthAuthorityResponse(event: MessageEvent) {
    val response = StrengthSessionAuthorityProtocol.decodeAcknowledgement(event.data)
    StrengthAuthorityCommandStore(applicationContext).acknowledge(response.requestId)
    Log.w(TAG, "Watch rejected strength command ${response.requestId}: ${response.outcome}")
  }

  private fun receiveResult(event: MessageEvent) {
    val result = StrengthResultProtocol.decodeResult(event.data)
    val outcome = StrengthSessionStore.applySynchronizedResult(applicationContext, result)
    if (outcome == StrengthSessionStore.SynchronizedResultApplyResult.COMMITTED) {
      HealthConnectActivitySync.onStrengthSessionSaved(applicationContext, result.executionId)
      sendBroadcast(
          Intent(Constants.Intents.STRENGTH_RESULT_UPDATED)
              .setPackage(packageName)
              .putExtra(Constants.Intents.EXTRA_STRENGTH_EXECUTION_ID, result.executionId))
    }
    val ackOutcome =
        when (outcome) {
          StrengthSessionStore.SynchronizedResultApplyResult.COMMITTED ->
            StrengthResultProtocol.AckOutcome.COMMITTED
          StrengthSessionStore.SynchronizedResultApplyResult.DUPLICATE ->
            StrengthResultProtocol.AckOutcome.DUPLICATE
          StrengthSessionStore.SynchronizedResultApplyResult.CONFLICT,
          StrengthSessionStore.SynchronizedResultApplyResult.INVALID ->
            StrengthResultProtocol.AckOutcome.CONFLICT
          StrengthSessionStore.SynchronizedResultApplyResult.SESSION_MISSING ->
            StrengthResultProtocol.AckOutcome.SESSION_MISSING
        }
    val ack = StrengthResultProtocol.Acknowledgement(result.executionId, result.resultId, ackOutcome)
    Wearable.getMessageClient(applicationContext)
        .sendMessage(
            event.sourceNodeId,
            Constants.Wear.Path.MSG_STRENGTH_RESULT_ACK,
            StrengthResultProtocol.encodeAcknowledgement(ack))
        .addOnFailureListener { error -> Log.w(TAG, "Strength result ACK send failed; sender will retry", error) }
  }

  private fun receiveAcknowledgement(event: MessageEvent) {
    val ack = StrengthResultProtocol.decodeAcknowledgement(event.data)
    val store = StrengthResultOutboxStore(applicationContext)
    val result = store.acknowledge(ack)
    if (result == StrengthResultOutboxStore.AcknowledgeResult.REMOVED) {
      WearConnectionProvider.sendPendingStrengthResult(applicationContext, event.sourceNodeId)
    }
  }

  private fun receiveWorkoutTelemetry(event: MessageEvent) {
    val batch = WorkoutTelemetryProtocol.decodeBatch(event.data)
    val database = DBHelper.getWritableDatabase(applicationContext)
    val result =
        WorkoutTelemetryInboxStore(database).commitBatch(batch, System.currentTimeMillis())
    if (!result.shouldAcknowledge) {
      Log.w(TAG, "Wear telemetry batch was not committed: ${result.status}")
      return
    }
    val routeResult = WorkoutTelemetryRouteReconciler(database).reconcilePending(batch.sessionId)
    if (!routeResult.success) {
      Log.w(TAG, "Wear telemetry is durable but route reconciliation remains pending")
      return
    }
    routeResult.updatedActivityIds.forEach { activityId ->
      HealthConnectActivitySync.onActivitySaved(applicationContext, database, activityId)
    }
    val acknowledgement =
        WorkoutTelemetryProtocol.Ack(batch.sessionId, result.committedThroughSequence)
    Wearable.getMessageClient(applicationContext)
        .sendMessage(
            event.sourceNodeId,
            Constants.Wear.Path.MSG_WORKOUT_TELEMETRY_ACK,
            WorkoutTelemetryProtocol.encodeAck(acknowledgement))
        .addOnFailureListener { error -> Log.w(TAG, "Wear telemetry ACK failed; watch will replay", error) }
  }

  private fun isConnectedPeer(nodeId: String): Boolean =
      WearConnectionProvider.isVerifiedResultPeer(applicationContext, nodeId) && runCatching {
        Tasks.await(Wearable.getNodeClient(applicationContext).connectedNodes, 5, TimeUnit.SECONDS)
            .any { it.id == nodeId }
      }.getOrDefault(false)

  override fun onDestroy() {
    worker.shutdown()
    super.onDestroy()
  }

  private companion object {
    const val TAG = "StrengthResultSync"
  }
}
