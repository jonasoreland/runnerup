package org.runnerup.common.wear;

import java.util.List;
import org.runnerup.common.tracker.TrackerState;

/** Chooses safe RunnerUp effects to reconcile after restoring a watch-authoritative session. */
public final class WorkoutSessionRecoveryPolicy {
  private WorkoutSessionRecoveryPolicy() {}

  /** Returns a persisted start that still needs its phone Tracker start effect. */
  public static WorkoutSessionProtocol.Command startToRecover(
      WorkoutSessionEventJournal journal, TrackerState trackerState, long nowElapsedRealtimeMs) {
    if (journal == null
        || journal.getAuthority() != WorkoutSessionProtocol.Actor.WATCH
        || trackerState != TrackerState.CONNECTED) return null;
    List<WorkoutSessionProtocol.Command> events = journal.events();
    if (events.isEmpty()) return null;
    WorkoutSessionProtocol.Command last = events.get(events.size() - 1);
    return last.getAction() == WorkoutSessionProtocol.Action.START
            && WorkoutSessionProtocol.isStartDeadlineInFuture(last, nowElapsedRealtimeMs)
        ? last
        : null;
  }

  /** State-changing controls can be replayed when Tracker state proves the effect is missing. */
  public static boolean shouldApplyControl(
      WorkoutSessionProtocol.Action action, TrackerState trackerState) {
    if (action == null || trackerState == null) return false;
    switch (action) {
      case PAUSE:
        return trackerState == TrackerState.STARTED;
      case RESUME:
        return trackerState == TrackerState.PAUSED;
      case FINISH:
        return trackerState == TrackerState.STARTED || trackerState == TrackerState.PAUSED;
      default:
        return false;
    }
  }
}
