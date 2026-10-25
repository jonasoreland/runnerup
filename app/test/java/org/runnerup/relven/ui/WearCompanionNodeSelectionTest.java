package org.runnerup.relven.ui;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;

public class WearCompanionNodeSelectionTest {
  @Test
  public void onlyConnectedNodesAdvertisingRelvenCapabilityAreSelected() {
    assertEquals(
        Arrays.asList("watch-b"),
        WearConnectionProvider.selectRelvenCompanionNodeIds(
            Arrays.asList("watch-a", "watch-b"),
            new HashSet<>(Arrays.asList("watch-b", "old-watch"))));
  }

  @Test
  public void emptyCapabilitySetSelectsNoRelvenCompanions() {
    assertEquals(
        Arrays.asList(),
        WearConnectionProvider.selectRelvenCompanionNodeIds(
            Arrays.asList("watch-a"), new HashSet<>()));
  }
}
