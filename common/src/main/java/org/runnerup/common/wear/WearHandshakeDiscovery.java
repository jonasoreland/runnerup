package org.runnerup.common.wear;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Tries each connected Wear node until one proves it runs a compatible Relven companion. */
public final class WearHandshakeDiscovery {
  public enum Status {
    VERIFIED,
    NO_WATCH,
    TIMEOUT,
    PROTOCOL_MISMATCH,
    TRANSPORT_ERROR,
    UNSUPPORTED
  }

  public static final class Result {
    public final Status status;
    public final String nodeId;
    public final long roundTripTimeMillis;

    private Result(Status status, String nodeId, long roundTripTimeMillis) {
      this.status = status;
      this.nodeId = nodeId;
      this.roundTripTimeMillis = roundTripTimeMillis;
    }
  }

  public interface PingCallback {
    void onResult(Status status, long roundTripTimeMillis);
  }

  public interface Pinger {
    void ping(String nodeId, PingCallback callback);
  }

  public interface Callback {
    void onResult(Result result);
  }

  private WearHandshakeDiscovery() {}

  public static void discover(List<String> connectedNodeIds, Pinger pinger, Callback callback) {
    List<String> candidates = new ArrayList<>();
    if (connectedNodeIds != null) {
      for (String nodeId : new LinkedHashSet<>(connectedNodeIds)) {
        if (nodeId != null && !nodeId.trim().isEmpty()) candidates.add(nodeId);
      }
    }
    new Attempt(candidates, pinger, callback).next();
  }

  private static final class Attempt {
    private final List<String> candidates;
    private final Pinger pinger;
    private final Callback callback;
    private int nextCandidate;
    private Status strongestFailure = Status.TRANSPORT_ERROR;
    private boolean sawTimeout;
    private boolean completed;

    private Attempt(List<String> candidates, Pinger pinger, Callback callback) {
      this.candidates = candidates;
      this.pinger = pinger;
      this.callback = callback;
    }

    private void next() {
      final String nodeId;
      Result terminalResult = null;
      synchronized (this) {
        if (completed) return;
        if (nextCandidate >= candidates.size()) {
          completed = true;
          Status status =
              candidates.isEmpty()
                  ? Status.NO_WATCH
                  : strongestFailure == Status.PROTOCOL_MISMATCH
                      ? Status.PROTOCOL_MISMATCH
                      : sawTimeout ? Status.TIMEOUT : Status.TRANSPORT_ERROR;
          terminalResult = new Result(status, null, -1L);
          nodeId = null;
        } else {
          nodeId = candidates.get(nextCandidate++);
        }
      }
      if (terminalResult != null) {
        callback.onResult(terminalResult);
        return;
      }

      java.util.concurrent.atomic.AtomicBoolean candidateCompleted =
          new java.util.concurrent.atomic.AtomicBoolean();
      try {
        pinger.ping(
            nodeId,
            (status, roundTripTimeMillis) -> {
              if (!candidateCompleted.compareAndSet(false, true)) return;
              Status safeStatus = status == null ? Status.TRANSPORT_ERROR : status;
              if (safeStatus == Status.VERIFIED) {
                complete(new Result(Status.VERIFIED, nodeId, roundTripTimeMillis));
                return;
              }
              synchronized (Attempt.this) {
                if (completed) return;
                if (safeStatus == Status.PROTOCOL_MISMATCH) {
                  strongestFailure = Status.PROTOCOL_MISMATCH;
                }
                if (safeStatus == Status.TIMEOUT) sawTimeout = true;
              }
              next();
            });
      } catch (RuntimeException transportFailure) {
        if (candidateCompleted.compareAndSet(false, true)) next();
      }
    }

    private void complete(Result result) {
      synchronized (this) {
        if (completed) return;
        completed = true;
      }
      callback.onResult(result);
    }
  }
}
