package org.runnerup.service

import java.util.UUID
import org.runnerup.common.wear.StrengthResultProtocol
import org.runnerup.relven.strength.StrengthSession

/** Applies reducer changes on Wear, durably committing each authority revision before UI publication. */
class WearStrengthExecutionController @JvmOverloads constructor(
    private val store: WearStrengthExecutionStore,
    private val elapsedRealtimeMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val wallClockMillis: () -> Long = { System.currentTimeMillis() }) {
  data class Update(
      val session: StrengthSession?,
      val committed: Boolean,
      val commitResult: WearStrengthExecutionStore.CommitResult?,
      val syncableResults: List<StrengthResultProtocol.Result> = emptyList())

  fun update(transform: (StrengthSession) -> StrengthSession): Update {
    val current = store.load() ?: return Update(null, false, WearStrengthExecutionStore.CommitResult.CONFLICT)
    val changed = transform(current).withStableResultIds()
    if (changed == current) return Update(current, false, WearStrengthExecutionStore.CommitResult.DUPLICATE)
    return commit(current, changed)
  }

  /** Refreshes deadline-derived display time; only timer transitions are committed and synchronized. */
  fun tick(): Update {
    val current = store.load() ?: return Update(null, false, WearStrengthExecutionStore.CommitResult.CONFLICT)
    if (current.phase != StrengthSession.Phase.REST && current.phase != StrengthSession.Phase.TIMED_WORK) {
      return Update(current, false, WearStrengthExecutionStore.CommitResult.DUPLICATE)
    }
    val refreshed = current.tickAt(elapsedRealtimeMillis(), wallClockMillis())
    if (!executionChanged(current, refreshed)) {
      return Update(refreshed, false, WearStrengthExecutionStore.CommitResult.DUPLICATE)
    }
    return commit(current, refreshed)
  }

  private fun commit(current: StrengthSession, changed: StrengthSession): Update {
    check(current.authorityRevision < Long.MAX_VALUE) { "Strength authority revision exhausted" }
    val next =
        changed.copy(
            authorityRevision = current.authorityRevision + 1L,
            lastAuthorityCommandId = UUID.randomUUID().toString())
    val result = store.commit(next)
    if (result != WearStrengthExecutionStore.CommitResult.COMMITTED) {
      return Update(store.load(), false, result)
    }
    val existingResultIds = current.results.mapNotNullTo(mutableSetOf()) { it.sourceResultId }
    val syncable = next.syncableResults().filterNot { it.resultId in existingResultIds }
    return Update(next, true, result, syncable)
  }

  private fun executionChanged(before: StrengthSession, after: StrengthSession): Boolean =
      before.phase != after.phase ||
          before.exerciseIndex != after.exerciseIndex ||
          before.setNumber != after.setNumber ||
          before.results != after.results ||
          before.pendingResults != after.pendingResults ||
          before.timerDeadlineElapsedRealtimeMillis != after.timerDeadlineElapsedRealtimeMillis ||
          before.resumePhase != after.resumePhase
}
