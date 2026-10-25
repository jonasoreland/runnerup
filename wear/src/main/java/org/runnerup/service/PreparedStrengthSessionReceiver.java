package org.runnerup.service;

import java.io.IOException;
import org.runnerup.common.wear.StrengthExecutionProtocol;

/** Writes a validated strength preparation durably before returning its correlated ACK. */
public final class PreparedStrengthSessionReceiver {
  private final PreparedStrengthSessionStore store;

  public PreparedStrengthSessionReceiver(PreparedStrengthSessionStore store) {
    this.store = store;
  }

  /** Returns null for malformed payloads that cannot be safely correlated. */
  public byte[] receive(byte[] payload) {
    final StrengthExecutionProtocol.PrepareMessage message;
    try {
      message = StrengthExecutionProtocol.decodePrepare(payload);
    } catch (IOException invalid) {
      return null;
    }
    boolean accepted;
    try {
      accepted = store.save(payload);
    } catch (RuntimeException unavailable) {
      accepted = false;
    }
    try {
      return StrengthExecutionProtocol.encodeAcknowledgement(
          message.getSessionId(), message.getRequestId(), accepted);
    } catch (IOException invalidAfterValidation) {
      return null;
    }
  }

  public boolean cancel(byte[] payload) {
    try {
      return store.cancel(StrengthExecutionProtocol.decodeCancellation(payload).getSessionId());
    } catch (IOException malformed) {
      return false;
    }
  }
}
