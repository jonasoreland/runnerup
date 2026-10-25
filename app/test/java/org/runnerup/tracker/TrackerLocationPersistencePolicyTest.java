package org.runnerup.tracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.runnerup.common.tracker.TrackerState;

public class TrackerLocationPersistencePolicyTest {
  @Test
  public void preparationFixesAreNotPersistedAsWorkoutRoute() {
    assertFalse(TrackerLocationPersistencePolicy.shouldPersist(false, TrackerState.CONNECTED));
    assertFalse(TrackerLocationPersistencePolicy.shouldPersist(false, TrackerState.INITIALIZED));
    assertFalse(TrackerLocationPersistencePolicy.shouldPersist(false, TrackerState.PAUSED));
  }

  @Test
  public void acceptedFixesArePersistedOnlyAfterWorkoutStart() {
    assertTrue(TrackerLocationPersistencePolicy.shouldPersist(false, TrackerState.STARTED));
    assertTrue(TrackerLocationPersistencePolicy.shouldPersist(true, TrackerState.CONNECTED));
  }
}
