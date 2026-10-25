package org.runnerup.service

/** Holds the single idempotent authority command while the paired phone node ID loads. */
internal class DeferredStrengthAuthorityCommand {
  data class Pending(val sourceNodeId: String, val payload: ByteArray)

  private var pending: Pending? = null

  @Synchronized
  fun defer(sourceNodeId: String, payload: ByteArray): Boolean {
    if (sourceNodeId.isBlank() || payload.isEmpty() || payload.size > MAX_PAYLOAD_BYTES) return false
    val current = pending
    if (current == null) {
      pending = Pending(sourceNodeId, payload.copyOf())
      return true
    }
    return current.sourceNodeId == sourceNodeId && current.payload.contentEquals(payload)
  }

  /** Consumes the pending request only after identity resolution, regardless of its source. */
  @Synchronized
  fun takeFor(phoneNodeId: String): Pending? {
    val current = pending ?: return null
    pending = null
    return current.takeIf { it.sourceNodeId == phoneNodeId }
  }

  private companion object {
    const val MAX_PAYLOAD_BYTES = 96 * 1024
  }
}
