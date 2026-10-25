package org.runnerup.tracker;

import org.runnerup.common.tracker.TrackerState;

/** Defines the only RunnerUp states that may persist location samples to an activity. */
public final class TrackerLocationPersistencePolicy {
  private TrackerLocationPersistencePolicy() {}

  public static boolean shouldPersist(boolean internal, TrackerState state) {
    return internal || state == TrackerState.STARTED;
  }
}
