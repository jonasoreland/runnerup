package org.runnerup.relven.ui

internal enum class PendingPlanSaveAction {
  Save,
  Reuse,
  Conflict
}

/** Avoids duplicate plan files while ensuring retries activate the exact submitted package. */
internal fun pendingPlanSaveAction(
    pendingId: String,
    pendingSourceJson: String,
    storedId: String?,
    storedSourceJson: String?): PendingPlanSaveAction = when {
  storedId == null -> PendingPlanSaveAction.Save
  storedId == pendingId && storedSourceJson == pendingSourceJson -> PendingPlanSaveAction.Reuse
  else -> PendingPlanSaveAction.Conflict
}
