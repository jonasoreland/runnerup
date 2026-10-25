package org.runnerup.wear.ui

import org.runnerup.common.wear.StrengthExecutionProtocol
import org.runnerup.relven.strength.StrengthSessionCodec

data class PreparedStrengthSessionPresentation(
    val sessionId: String,
    val workoutName: String,
    val exerciseName: String,
    val prescription: String,
    val instructions: String)

object PreparedStrengthSessionPresentationFactory {
  @JvmStatic
  fun from(payload: ByteArray?): PreparedStrengthSessionPresentation? {
    if (payload == null) return null
    return runCatching {
      val request = StrengthExecutionProtocol.decodePrepare(payload)
      val session = StrengthSessionCodec.decode(request.sessionJson).first
      val workout = session.workout
      val exercise = workout.exercises()[session.exerciseIndex]
      val prescription =
          exercise.durationSeconds()?.let { "$it s • ${exercise.sets()} set" }
              ?: "${exercise.sets()} set • ${exercise.minRepetitions()}–${exercise.maxRepetitions()} reps"
      PreparedStrengthSessionPresentation(
          request.sessionId,
          workout.name(),
          exercise.name(),
          prescription,
          exercise.instructions())
    }.getOrNull()
  }
}
