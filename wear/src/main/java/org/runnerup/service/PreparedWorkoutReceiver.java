package org.runnerup.service;

import java.io.IOException;
import org.runnerup.common.wear.WorkoutPreparationProtocol;

/** Persists a received workout before returning its correlated preparation acknowledgement. */
public final class PreparedWorkoutReceiver {
  private final PreparedWorkoutStore store;

  public PreparedWorkoutReceiver(PreparedWorkoutStore store) {
    this.store = store;
  }

  /** Returns null for malformed messages that cannot be safely correlated. */
  public byte[] receive(byte[] payload) {
    try {
      WorkoutPreparationProtocol.PrepareMessage message =
          WorkoutPreparationProtocol.decodePrepare(payload);
      boolean accepted = store.save(payload);
      return WorkoutPreparationProtocol.encodeAcknowledgement(
          message.getSessionId(), message.getRequestId(), accepted);
    } catch (IOException invalidMessage) {
      return null;
    }
  }

  /** Applies a retry-safe cancellation without clearing a different prepared session. */
  public boolean cancel(byte[] payload) {
    try {
      return store.cancel(WorkoutPreparationProtocol.decodeCancellation(payload).getSessionId());
    } catch (IOException malformed) {
      return false;
    }
  }
}
