package org.runnerup.common.wear

import java.util.UUID
import org.json.JSONObject
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.relven.strength.StrengthSessionCodec

/** Bounded, versioned commands and durable snapshots for watch-authoritative strength sessions. */
object StrengthSessionAuthorityProtocol {
  const val CURRENT_VERSION = 1
  const val MAX_MESSAGE_BYTES = 96 * 1024

  enum class Action { BEGIN, PAUSE, RESUME }
  enum class Outcome { COMMITTED, DUPLICATE, STALE, CONFLICT, INVALID }

  data class Transition(val outcome: Outcome, val session: StrengthSession)

  data class Command(
      val sessionId: String,
      val requestId: String,
      val expectedRevision: Long,
      val action: Action) {
    init {
      requireUuid(sessionId, "session")
      requireUuid(requestId, "request")
      require(expectedRevision >= 0L) { "Invalid expected revision" }
    }
  }

  data class Projection(
      val sessionId: String,
      val requestId: String,
      val revision: Long,
      val projectedAtElapsedRealtimeMillis: Long,
      val sessionJson: String) {
    init {
      requireUuid(sessionId, "session")
      requireUuid(requestId, "request")
      require(revision > 0L) { "Invalid projection revision" }
      require(projectedAtElapsedRealtimeMillis > 0L) { "Invalid projection clock" }
      require(sessionJson.isNotBlank() && sessionJson.toByteArray(Charsets.UTF_8).size <= MAX_MESSAGE_BYTES) {
        "Invalid projected session size"
      }
      val (session, _) = StrengthSessionCodec.decode(sessionJson)
      require(session.executionId == sessionId) { "Projection session identity mismatch" }
      require(session.authorityRevision == revision) { "Projection revision mismatch" }
      require(session.lastAuthorityCommandId == requestId) { "Projection command identity mismatch" }
    }
  }

  data class Acknowledgement(
      val sessionId: String,
      val requestId: String,
      val revision: Long,
      val outcome: Outcome) {
    init {
      requireUuid(sessionId, "session")
      requireUuid(requestId, "request")
      require(revision >= 0L) { "Invalid acknowledgement revision" }
    }
  }

  /** Applies a control only on the watch, where the single live authority is hosted. */
  @JvmStatic
  @JvmOverloads
  fun applyAtWatch(
      current: StrengthSession,
      command: Command,
      nowElapsedRealtimeMillis: Long,
      nowWallClockMillis: Long? = null): Transition {
    if (current.executionId != command.sessionId) return Transition(Outcome.CONFLICT, current)
    if (current.lastAuthorityCommandId == command.requestId) {
      return Transition(Outcome.DUPLICATE, current)
    }
    if (current.authorityRevision != command.expectedRevision) {
      return Transition(Outcome.STALE, current)
    }
    if (current.authorityRevision == Long.MAX_VALUE) return Transition(Outcome.CONFLICT, current)
    if (command.action == Action.PAUSE && current.phase == StrengthSession.Phase.PREPARING) {
      return Transition(Outcome.INVALID, current)
    }
    val changed = try {
      when (command.action) {
        Action.BEGIN -> current.begin(nowElapsedRealtimeMillis, nowWallClockMillis)
        Action.PAUSE -> current.pause(nowElapsedRealtimeMillis)
        Action.RESUME -> current.resume(nowElapsedRealtimeMillis)
      }
    } catch (_: IllegalArgumentException) {
      return Transition(Outcome.INVALID, current)
    } catch (_: IllegalStateException) {
      return Transition(Outcome.INVALID, current)
    }
    return Transition(
        Outcome.COMMITTED,
        changed.copy(
            authorityRevision = current.authorityRevision + 1L,
            lastAuthorityCommandId = command.requestId))
  }

  @JvmStatic
  fun encodeCommand(command: Command): ByteArray = encode(
      JSONObject()
          .put("type", "command")
          .put("sessionId", command.sessionId)
          .put("requestId", command.requestId)
          .put("expectedRevision", command.expectedRevision)
          .put("action", command.action.name))

  @JvmStatic
  fun decodeCommand(payload: ByteArray): Command {
    val json = decode(payload, "command")
    return try {
      Command(
          json.getString("sessionId"),
          json.getString("requestId"),
          json.getLong("expectedRevision"),
          Action.valueOf(json.getString("action")))
    } catch (invalid: Exception) {
      throw IllegalArgumentException("Invalid strength command", invalid)
    }
  }

  @JvmStatic
  fun encodeProjection(
      session: StrengthSession,
      requestId: String,
      projectedAtElapsedRealtimeMillis: Long): ByteArray {
    val sessionJson = StrengthSessionCodec.encode(session, null)
    val projection = Projection(
        session.executionId,
        requestId,
        session.authorityRevision,
        projectedAtElapsedRealtimeMillis,
        sessionJson)
    return encode(
        JSONObject()
            .put("type", "projection")
            .put("sessionId", projection.sessionId)
            .put("requestId", projection.requestId)
            .put("revision", projection.revision)
            .put("projectedAtElapsedRealtimeMillis", projection.projectedAtElapsedRealtimeMillis)
            .put("sessionJson", projection.sessionJson))
  }

  @JvmStatic
  fun decodeProjection(payload: ByteArray): Projection {
    val json = decode(payload, "projection")
    return try {
      Projection(
          json.getString("sessionId"),
          json.getString("requestId"),
          json.getLong("revision"),
          json.getLong("projectedAtElapsedRealtimeMillis"),
          json.getString("sessionJson"))
    } catch (invalid: Exception) {
      throw IllegalArgumentException("Invalid strength projection", invalid)
    }
  }

  @JvmStatic
  fun encodeAcknowledgement(acknowledgement: Acknowledgement): ByteArray = encode(
      JSONObject()
          .put("type", "acknowledgement")
          .put("sessionId", acknowledgement.sessionId)
          .put("requestId", acknowledgement.requestId)
          .put("revision", acknowledgement.revision)
          .put("outcome", acknowledgement.outcome.name))

  @JvmStatic
  fun decodeAcknowledgement(payload: ByteArray): Acknowledgement {
    val json = decode(payload, "acknowledgement")
    return try {
      Acknowledgement(
          json.getString("sessionId"),
          json.getString("requestId"),
          json.getLong("revision"),
          Outcome.valueOf(json.getString("outcome")))
    } catch (invalid: Exception) {
      throw IllegalArgumentException("Invalid strength acknowledgement", invalid)
    }
  }

  private fun encode(json: JSONObject): ByteArray {
    val payload = JSONObject().put("version", CURRENT_VERSION).put("message", json).toString()
        .toByteArray(Charsets.UTF_8)
    require(payload.size <= MAX_MESSAGE_BYTES) { "Strength authority message too large" }
    return payload
  }

  private fun decode(payload: ByteArray, expectedType: String): JSONObject {
    require(payload.isNotEmpty() && payload.size <= MAX_MESSAGE_BYTES) { "Invalid strength message size" }
    val envelope = try { JSONObject(payload.toString(Charsets.UTF_8)) }
    catch (invalid: Exception) { throw IllegalArgumentException("Invalid strength message", invalid) }
    require(envelope.keys().asSequence().toSet() == setOf("version", "message")) {
      "Unexpected strength envelope fields"
    }
    require(envelope.getInt("version") == CURRENT_VERSION) { "Unsupported strength protocol" }
    val message = envelope.getJSONObject("message")
    require(message.getString("type") == expectedType) { "Unexpected strength message type" }
    val expectedKeys = when (expectedType) {
      "command" -> setOf("type", "sessionId", "requestId", "expectedRevision", "action")
      "projection" -> setOf(
          "type", "sessionId", "requestId", "revision", "projectedAtElapsedRealtimeMillis", "sessionJson")
      "acknowledgement" -> setOf("type", "sessionId", "requestId", "revision", "outcome")
      else -> error("Unsupported strength message type")
    }
    require(message.keys().asSequence().toSet() == expectedKeys) { "Unexpected strength message fields" }
    return message
  }

  private fun requireUuid(value: String, label: String) {
    require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
      "Invalid $label ID"
    }
  }
}
