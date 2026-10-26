package org.runnerup.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.runnerup.common.wear.WorkoutSessionProtocol;

public class WearWorkoutRecorderControllerTest {
  private static final String SESSION = "b6bc0461-8785-4c09-90f5-55bdc361592f";

  @Test
  public void startDeadlineIsTheFirstRecordableInstant() {
    WearWorkoutRecorderController controller = new WearWorkoutRecorderController();

    assertEquals(
        WearWorkoutRecorderController.Effect.START,
        controller.accept(command(1, WorkoutSessionProtocol.Action.START, 3_000), 0));
    assertFalse(controller.shouldRecord(SESSION, 2_999_999_999L));
    controller.advanceTo(3_000);
    assertTrue(controller.shouldRecord(SESSION, 3_000_000_000L));
  }

  @Test
  public void pauseResumeAndFinishControlSampleAcceptance() {
    WearWorkoutRecorderController controller = activeController();

    assertEquals(
        WearWorkoutRecorderController.Effect.PAUSE,
        controller.accept(command(2, WorkoutSessionProtocol.Action.PAUSE, 0), 4_000));
    assertFalse(controller.shouldRecord(SESSION, 4_000_000_000L));
    assertEquals(
        WearWorkoutRecorderController.Effect.RESUME,
        controller.accept(command(3, WorkoutSessionProtocol.Action.RESUME, 0), 5_000));
    assertTrue(controller.shouldRecord(SESSION, 5_000_000_000L));
    assertFalse(controller.shouldRecord(SESSION, 4_999_999_999L));
    assertEquals(
        WearWorkoutRecorderController.Effect.FINISH,
        controller.accept(command(4, WorkoutSessionProtocol.Action.FINISH, 0), 6_000));
    assertFalse(controller.shouldRecord(SESSION, 6_000_000_000L));
  }

  @Test
  public void duplicateAndOutOfOrderAcceptedProjectionsDoNotRepeatEffects() {
    WearWorkoutRecorderController controller = activeController();

    assertEquals(
        WearWorkoutRecorderController.Effect.NONE,
        controller.accept(command(1, WorkoutSessionProtocol.Action.START, 3_000), 3_000));
    assertEquals(
        WearWorkoutRecorderController.Effect.PAUSE,
        controller.accept(command(2, WorkoutSessionProtocol.Action.PAUSE, 0), 4_000));
    assertEquals(
        WearWorkoutRecorderController.Effect.NONE,
        controller.accept(command(2, WorkoutSessionProtocol.Action.PAUSE, 0), 4_000));
    assertEquals(
        WearWorkoutRecorderController.Effect.RESUME,
        controller.accept(command(3, WorkoutSessionProtocol.Action.RESUME, 0), 5_000));
    assertEquals(
        WearWorkoutRecorderController.Effect.NONE,
        controller.accept(command(3, WorkoutSessionProtocol.Action.RESUME, 0), 5_000));
    assertTrue(controller.shouldRecord(SESSION, 5_000_000_000L));
  }

  @Test
  public void aSequenceGapCannotAdvanceRecorderState() {
    WearWorkoutRecorderController controller = activeController();

    assertEquals(
        WearWorkoutRecorderController.Effect.NONE,
        controller.accept(command(3, WorkoutSessionProtocol.Action.PAUSE, 0), 4_000));
    assertTrue(controller.shouldRecord(SESSION, 4_000_000_000L));
    assertEquals(
        WearWorkoutRecorderController.Effect.PAUSE,
        controller.accept(command(2, WorkoutSessionProtocol.Action.PAUSE, 0), 4_000));
  }

  @Test
  public void restoredPausedSessionKeepsSequenceAndRejectsSamplesUntilResume() {
    WearWorkoutRecorderController original = activeController();
    original.accept(command(2, WorkoutSessionProtocol.Action.PAUSE, 0), 4_000);

    WearWorkoutRecorderController restored = new WearWorkoutRecorderController();
    restored.restore(original.snapshot());

    assertFalse(restored.shouldRecord(SESSION, 5_000_000_000L));
    assertEquals(
        WearWorkoutRecorderController.Effect.NONE,
        restored.accept(command(2, WorkoutSessionProtocol.Action.PAUSE, 0), 5_000));
    assertEquals(
        WearWorkoutRecorderController.Effect.RESUME,
        restored.accept(command(3, WorkoutSessionProtocol.Action.RESUME, 0), 5_000));
    assertTrue(restored.shouldRecord(SESSION, 5_000_000_000L));
  }

  private static WearWorkoutRecorderController activeController() {
    WearWorkoutRecorderController controller = new WearWorkoutRecorderController();
    controller.accept(command(1, WorkoutSessionProtocol.Action.START, 3_000), 0);
    controller.advanceTo(3_000);
    return controller;
  }

  private static WorkoutSessionProtocol.Command command(
      long sequence, WorkoutSessionProtocol.Action action, long plannedStart) {
    return new WorkoutSessionProtocol.Command(
        SESSION,
        "00000000-0000-4000-8000-" + String.format("%012d", sequence),
        sequence,
        WorkoutSessionProtocol.Actor.WATCH,
        action,
        0,
        plannedStart);
  }
}
