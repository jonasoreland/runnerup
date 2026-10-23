package org.runnerup.tracker;

import org.runnerup.common.tracker.TrackerState;

/** Ensures a stopped workout can be saved or discarded only once. */
final class ActivityCompletionGate {
  private boolean completionStarted;

  void beginActivity() {
    completionStarted = false;
  }

  boolean tryBeginCompletion(TrackerState state) {
    if (completionStarted || (state != TrackerState.PAUSED && state != TrackerState.STOPPED)) {
      return false;
    }
    completionStarted = true;
    return true;
  }

  void cancelCompletion() {
    completionStarted = false;
  }
}
