package org.runnerup.common.wear;

/** Keeps terminal session projection behind the watch's durable set-result outbox. */
public final class WorkoutSessionTerminalDeliveryPolicy {
  private WorkoutSessionTerminalDeliveryPolicy() {}

  public static boolean canProject(
      WorkoutSessionProtocol.Command command, boolean outboxReadable, int pendingResults) {
    if (command == null || pendingResults < 0) return false;
    if (command.getAction() != WorkoutSessionProtocol.Action.FINISH) return true;
    return outboxReadable && pendingResults == 0;
  }
}
