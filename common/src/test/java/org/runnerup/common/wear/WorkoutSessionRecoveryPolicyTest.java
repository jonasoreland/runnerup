package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.runnerup.common.tracker.TrackerState;

public class WorkoutSessionRecoveryPolicyTest {
  private static final String SESSION = "99f7f1de-73ad-4a52-8d22-3df6a5b8b75a";
  private static final String START_ID = "fef77b0a-7012-4dd7-a374-cf7a1dd6f083";

  @Test
  public void restoresUnfinishedStartOnlyWhileTrackerIsPrepared() {
    WorkoutSessionEventJournal journal = startJournal();

    assertEquals(
        START_ID,
        WorkoutSessionRecoveryPolicy.startToRecover(journal, TrackerState.CONNECTED, 5_000L)
            .getCommandId());
    assertNull(WorkoutSessionRecoveryPolicy.startToRecover(journal, TrackerState.STARTED, 5_000L));
    assertNull(
        WorkoutSessionRecoveryPolicy.startToRecover(journal, TrackerState.CONNECTED, 10_000L));
  }

  @Test
  public void doesNotRestoreStartAfterLaterSessionEvent() {
    WorkoutSessionEventJournal journal = startJournal();
    assertEquals(
        WorkoutSessionEventJournal.AppendResult.APPENDED,
        journal.append(
            new WorkoutSessionProtocol.Command(
                SESSION,
                "671a25fc-cdb2-4d62-a3a2-175f2826040b",
                2,
                WorkoutSessionProtocol.Actor.WATCH,
                WorkoutSessionProtocol.Action.PAUSE,
                11_000L,
                0L)));

    assertNull(
        WorkoutSessionRecoveryPolicy.startToRecover(journal, TrackerState.CONNECTED, 5_000L));
  }

  @Test
  public void replaysStateControlsOnlyWhenTrackerShowsEffectIsMissing() {
    assertEquals(
        true,
        WorkoutSessionRecoveryPolicy.shouldApplyControl(
            WorkoutSessionProtocol.Action.PAUSE, TrackerState.STARTED));
    assertEquals(
        false,
        WorkoutSessionRecoveryPolicy.shouldApplyControl(
            WorkoutSessionProtocol.Action.PAUSE, TrackerState.PAUSED));
    assertEquals(
        true,
        WorkoutSessionRecoveryPolicy.shouldApplyControl(
            WorkoutSessionProtocol.Action.RESUME, TrackerState.PAUSED));
    assertEquals(
        false,
        WorkoutSessionRecoveryPolicy.shouldApplyControl(
            WorkoutSessionProtocol.Action.RESUME, TrackerState.STARTED));
    assertEquals(
        true,
        WorkoutSessionRecoveryPolicy.shouldApplyControl(
            WorkoutSessionProtocol.Action.FINISH, TrackerState.STARTED));
    assertEquals(
        false,
        WorkoutSessionRecoveryPolicy.shouldApplyControl(
            WorkoutSessionProtocol.Action.FINISH, TrackerState.STOPPED));
  }

  private static WorkoutSessionEventJournal startJournal() {
    WorkoutSessionEventJournal journal =
        WorkoutSessionEventJournal.empty(SESSION, WorkoutSessionProtocol.Actor.WATCH);
    assertEquals(
        WorkoutSessionEventJournal.AppendResult.APPENDED,
        journal.append(
            new WorkoutSessionProtocol.Command(
                SESSION,
                START_ID,
                1,
                WorkoutSessionProtocol.Actor.WATCH,
                WorkoutSessionProtocol.Action.START,
                1_000L,
                10_000L)));
    return journal;
  }
}
