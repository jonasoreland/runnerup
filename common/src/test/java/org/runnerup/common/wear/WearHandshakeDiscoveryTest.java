package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class WearHandshakeDiscoveryTest {
  @Test
  public void continuesAfterFirstNodeTimesOutAndVerifiesLaterRelvenNode() {
    List<String> attemptedNodes = new ArrayList<>();
    List<WearHandshakeDiscovery.Result> results = new ArrayList<>();

    WearHandshakeDiscovery.discover(
        Arrays.asList("watch-without-relven", "relven-watch"),
        (nodeId, callback) -> {
          attemptedNodes.add(nodeId);
          if ("watch-without-relven".equals(nodeId)) {
            callback.onResult(WearHandshakeDiscovery.Status.TIMEOUT, 5_000L);
          } else {
            callback.onResult(WearHandshakeDiscovery.Status.VERIFIED, 24L);
          }
        },
        results::add);

    assertEquals(Arrays.asList("watch-without-relven", "relven-watch"), attemptedNodes);
    assertEquals(1, results.size());
    assertEquals(WearHandshakeDiscovery.Status.VERIFIED, results.get(0).status);
    assertEquals("relven-watch", results.get(0).nodeId);
    assertEquals(24L, results.get(0).roundTripTimeMillis);
  }

  @Test
  public void reportsNoWatchWhenThereAreNoConnectedCandidates() {
    List<WearHandshakeDiscovery.Result> results = new ArrayList<>();

    WearHandshakeDiscovery.discover(
        Collections.emptyList(),
        (nodeId, callback) -> {
          throw new AssertionError("No ping should be sent without a connected candidate");
        },
        results::add);

    assertEquals(1, results.size());
    assertEquals(WearHandshakeDiscovery.Status.NO_WATCH, results.get(0).status);
    assertNull(results.get(0).nodeId);
  }

  @Test
  public void preservesProtocolMismatchWhenNoCandidateVerifies() {
    List<WearHandshakeDiscovery.Result> results = new ArrayList<>();

    WearHandshakeDiscovery.discover(
        Arrays.asList("old-relven-watch", "unavailable-watch"),
        (nodeId, callback) ->
            callback.onResult(
                "old-relven-watch".equals(nodeId)
                    ? WearHandshakeDiscovery.Status.PROTOCOL_MISMATCH
                    : WearHandshakeDiscovery.Status.TIMEOUT,
                -1L),
        results::add);

    assertEquals(1, results.size());
    assertEquals(WearHandshakeDiscovery.Status.PROTOCOL_MISMATCH, results.get(0).status);
    assertNull(results.get(0).nodeId);
  }

  @Test
  public void ignoresDuplicateAndLateResultsFromAnEarlierCandidate() {
    List<WearHandshakeDiscovery.PingCallback> pending = new ArrayList<>();
    List<WearHandshakeDiscovery.Result> results = new ArrayList<>();

    WearHandshakeDiscovery.discover(
        Arrays.asList("first-watch", "second-watch"),
        (nodeId, callback) -> pending.add(callback),
        results::add);

    assertEquals(1, pending.size());
    pending.get(0).onResult(WearHandshakeDiscovery.Status.TIMEOUT, 5_000L);
    assertEquals(2, pending.size());
    pending.get(1).onResult(WearHandshakeDiscovery.Status.VERIFIED, 19L);
    pending.get(0).onResult(WearHandshakeDiscovery.Status.PROTOCOL_MISMATCH, -1L);

    assertEquals(1, results.size());
    assertEquals(WearHandshakeDiscovery.Status.VERIFIED, results.get(0).status);
    assertEquals("second-watch", results.get(0).nodeId);
  }
}
