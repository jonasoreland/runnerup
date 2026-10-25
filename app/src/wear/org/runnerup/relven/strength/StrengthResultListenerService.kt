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
import org.runnerup.relven.ui.WearConnectionProvider

/** Background phone endpoint for durable phone↔watch strength-result delivery. */
class StrengthResultListenerService : WearableListenerService() {
  private val worker = Executors.newSingleThreadExecutor()

  override fun onPeerConnected(peer: Node) {
    super.onPeerConnected(peer)
    worker.execute {
      if (isConnectedPeer(peer.id)) {
        WearConnectionProvider.sendPendingStrengthResult(applicationContext, peer.id)
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
        }
      } catch (error: Exception) {
        Log.w(TAG, "Unable to process Wear strength result message", error)
      }
    }
  }

  private fun receiveResult(event: MessageEvent) {
    val result = StrengthResultProtocol.decodeResult(event.data)
    val outcome = StrengthSessionStore.applySynchronizedResult(applicationContext, result)
    if (outcome == StrengthSessionStore.SynchronizedResultApplyResult.COMMITTED) {
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
