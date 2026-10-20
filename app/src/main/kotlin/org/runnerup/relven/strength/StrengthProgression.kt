package org.runnerup.relven.strength

internal data class ExerciseProgressSession(
    val scheduledEntryId: String,
    val finishedAtMillis: Long,
    val sets: List<StrengthSession.SetResult>)

/** Projects only recorded set results for the stable exercise identity in the saved workout. */
internal fun exerciseProgression(
    exerciseId: String,
    sessions: List<CompletedStrengthSession>,
    limit: Int = 10): List<ExerciseProgressSession> {
  require(exerciseId.isNotBlank()) { "Missing exercise identity" }
  require(limit in 1..100) { "History limit must be from 1 to 100" }
  return exerciseProgressions(sessions, limit)[exerciseId].orEmpty()
}

internal fun exerciseProgressions(
    sessions: List<CompletedStrengthSession>,
    limit: Int = 10): Map<String, List<ExerciseProgressSession>> {
  require(limit in 1..100) { "History limit must be from 1 to 100" }
  val grouped = LinkedHashMap<Pair<String, String>, MutableList<StrengthSession.SetResult>>()
  val metadata = HashMap<Pair<String, String>, CompletedStrengthSession>()
  sessions.forEach { completed ->
    completed.state.results.forEach { result ->
      val exerciseId = completed.state.workout.exercises()[result.exerciseIndex].exerciseId()
      val key = exerciseId to completed.state.scheduledEntryId
      grouped.getOrPut(key, ::ArrayList).add(result)
      metadata[key] = completed
    }
  }
  return grouped.entries
      .groupBy({ it.key.first }, { entry ->
        val completed = requireNotNull(metadata[entry.key])
        ExerciseProgressSession(
            completed.state.scheduledEntryId, completed.finishedAtMillis, entry.value.toList())
      })
      .mapValues { (_, history) ->
        history
            .sortedWith(
                compareByDescending<ExerciseProgressSession> { it.finishedAtMillis }
                    .thenBy { it.scheduledEntryId })
            .take(limit)
      }
}
