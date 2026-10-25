package org.runnerup.common.wear;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorkoutSessionTerminalDeliveryPolicyTest {
  private static final String SESSION = "99f7f1de-73ad-4a52-8d22-3df6a5b8b75a";

  @Test
  public void finishWaitsForCommittedStrengthResultsAndReadableOutbox() {
    WorkoutSessionProtocol.Command finish = command(WorkoutSessionProtocol.Action.FINISH);

    assertFalse(WorkoutSessionTerminalDeliveryPolicy.canProject(finish, true, 2));
    assertFalse(WorkoutSessionTerminalDeliveryPolicy.canProject(finish, false, 0));
    assertTrue(WorkoutSessionTerminalDeliveryPolicy.canProject(finish, true, 0));
  }

  @Test
  public void nonterminalControlsDoNotWaitForStrengthResultOutbox() {
    WorkoutSessionProtocol.Command pause = command(WorkoutSessionProtocol.Action.PAUSE);

    assertTrue(WorkoutSessionTerminalDeliveryPolicy.canProject(pause, true, 2));
    assertTrue(WorkoutSessionTerminalDeliveryPolicy.canProject(pause, false, 0));
  }

  private static WorkoutSessionProtocol.Command command(WorkoutSessionProtocol.Action action) {
    return new WorkoutSessionProtocol.Command(
        SESSION,
        "23c507e2-97fc-4daa-92f4-bdd36ddad9e5",
        1,
        WorkoutSessionProtocol.Actor.WATCH,
        action,
        1_000,
        0);
  }
}
