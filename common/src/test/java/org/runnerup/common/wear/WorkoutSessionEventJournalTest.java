package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorkoutSessionEventJournalTest {
  private static final String SESSION = "99f7f1de-73ad-4a52-8d22-3df6a5b8b75a";

  @Test
  public void acceptedEventsSurviveReopenAndReplayInSequence() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    WorkoutSessionEventJournalStore store = new WorkoutSessionEventJournalStore(storage);
    WorkoutSessionProtocol.Command start =
        command(
            1, "fef77b0a-7012-4dd7-a374-cf7a1dd6f083", WorkoutSessionProtocol.Action.START, 10_000);
    WorkoutSessionProtocol.Command pause =
        command(2, "671a25fc-cdb2-4d62-a3a2-175f2826040b", WorkoutSessionProtocol.Action.PAUSE, 0);

    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.APPENDED,
        store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, start));
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.APPENDED,
        store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, pause));

    WorkoutSessionEventJournalStore reopened = new WorkoutSessionEventJournalStore(storage);
    WorkoutSessionEventJournal journal = reopened.load(SESSION);
    assertNotNull(journal);
    assertEquals(2L, journal.lastSequence());
    assertEquals(2, journal.pendingEvents().size());
    assertEquals(WorkoutSessionProtocol.Phase.PAUSED, journal.replayState().getPhase());
  }

  @Test
  public void terminalCompletionSurvivesRestartAndRemainsIdempotentUntilAcknowledged() {
    MemoryStorage storage = new MemoryStorage();
    WorkoutSessionEventJournalStore store = new WorkoutSessionEventJournalStore(storage);
    WorkoutSessionProtocol.Command start =
        command(
            1, "fef77b0a-7012-4dd7-a374-cf7a1dd6f083", WorkoutSessionProtocol.Action.START, 10_000);
    WorkoutSessionProtocol.Command finish =
        command(2, "23c507e2-97fc-4daa-92f4-bdd36ddad9e5", WorkoutSessionProtocol.Action.FINISH, 0);

    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.APPENDED,
        store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, start));
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.APPENDED,
        store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, finish));

    WorkoutSessionEventJournalStore reopened = new WorkoutSessionEventJournalStore(storage);
    WorkoutSessionEventJournal restored = reopened.load(SESSION);
    assertNotNull(restored);
    assertEquals(WorkoutSessionProtocol.Phase.COMPLETED, restored.replayState().getPhase());
    assertEquals(finish.getCommandId(), restored.pendingEvents().get(1).getCommandId());
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.DUPLICATE,
        reopened.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, finish));
    assertTrue(reopened.acknowledge(SESSION, 1, start.getCommandId()));
    assertTrue(reopened.acknowledge(SESSION, 2, finish.getCommandId()));

    WorkoutSessionEventJournal afterAcknowledgement =
        new WorkoutSessionEventJournalStore(storage).load(SESSION);
    assertNotNull(afterAcknowledgement);
    assertTrue(afterAcknowledgement.pendingEvents().isEmpty());
    assertEquals(
        WorkoutSessionProtocol.Phase.COMPLETED, afterAcknowledgement.replayState().getPhase());
  }

  @Test
  public void retriesAreIdempotentButConflictingAndOutOfOrderEventsAreRejected() throws Exception {
    WorkoutSessionEventJournalStore store =
        new WorkoutSessionEventJournalStore(new MemoryStorage());
    WorkoutSessionProtocol.Command first =
        command(
            1, "fef77b0a-7012-4dd7-a374-cf7a1dd6f083", WorkoutSessionProtocol.Action.START, 10_000);
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.APPENDED,
        store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, first));
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.DUPLICATE,
        store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, first));
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.CONFLICT,
        store.append(
            SESSION,
            WorkoutSessionProtocol.Actor.WATCH,
            command(
                1,
                "4cf3f1f3-e067-4e4e-9b1c-9f0f480eac59",
                WorkoutSessionProtocol.Action.START,
                10_000)));
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.OUT_OF_ORDER,
        store.append(
            SESSION,
            WorkoutSessionProtocol.Actor.WATCH,
            command(
                3,
                "671a25fc-cdb2-4d62-a3a2-175f2826040b",
                WorkoutSessionProtocol.Action.PAUSE,
                0)));
  }

  @Test
  public void acknowledgementRemovesOnlyTheMatchingHeadAndSurvivesReopen() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    WorkoutSessionEventJournalStore store = new WorkoutSessionEventJournalStore(storage);
    WorkoutSessionProtocol.Command start =
        command(
            1, "fef77b0a-7012-4dd7-a374-cf7a1dd6f083", WorkoutSessionProtocol.Action.START, 10_000);
    WorkoutSessionProtocol.Command pause =
        command(2, "671a25fc-cdb2-4d62-a3a2-175f2826040b", WorkoutSessionProtocol.Action.PAUSE, 0);
    store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, start);
    store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, pause);

    assertFalse(store.acknowledge(SESSION, 2, pause.getCommandId()));
    assertTrue(store.acknowledge(SESSION, 1, start.getCommandId()));

    WorkoutSessionEventJournal journal = new WorkoutSessionEventJournalStore(storage).load(SESSION);
    assertNotNull(journal);
    assertEquals(1, journal.pendingEvents().size());
    assertEquals(pause.getCommandId(), journal.pendingEvents().get(0).getCommandId());
    assertTrue(
        new WorkoutSessionEventJournalStore(storage).acknowledge(SESSION, 2, pause.getCommandId()));
    assertTrue(
        new WorkoutSessionEventJournalStore(storage).load(SESSION).pendingEvents().isEmpty());
  }

  @Test
  public void rejectedUnstartedStartCanBeRolledBackAfterItsProjectionWasAcknowledged() {
    MemoryStorage storage = new MemoryStorage();
    WorkoutSessionEventJournalStore store = new WorkoutSessionEventJournalStore(storage);
    WorkoutSessionProtocol.Command start =
        command(
            1, "fef77b0a-7012-4dd7-a374-cf7a1dd6f083", WorkoutSessionProtocol.Action.START, 10_000);
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.APPENDED,
        store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, start));
    assertTrue(store.acknowledge(SESSION, 1, start.getCommandId()));

    assertEquals(
        WorkoutSessionEventJournalStore.RejectionRollbackResult.ROLLED_BACK,
        store.rejectUnstartedStart(SESSION, start.getCommandId()));
    WorkoutSessionEventJournal restored =
        new WorkoutSessionEventJournalStore(storage).load(SESSION);
    assertNotNull(restored);
    assertEquals(0L, restored.lastSequence());
    assertEquals(WorkoutSessionProtocol.Phase.READY, restored.replayState().getPhase());
  }

  @Test
  public void rejectedStartReportsStorageFailureInsteadOfClaimingRollback() {
    MemoryStorage storage = new MemoryStorage();
    WorkoutSessionEventJournalStore store = new WorkoutSessionEventJournalStore(storage);
    WorkoutSessionProtocol.Command start =
        command(
            1, "fef77b0a-7012-4dd7-a374-cf7a1dd6f083", WorkoutSessionProtocol.Action.START, 10_000);
    store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, start);
    storage.failWrites = true;

    assertEquals(
        WorkoutSessionEventJournalStore.RejectionRollbackResult.STORAGE_ERROR,
        store.rejectUnstartedStart(SESSION, start.getCommandId()));
    assertNotNull(store.load(SESSION));
  }

  @Test
  public void rejectionRollbackDistinguishesMissingJournalFromMismatchedEvent() {
    WorkoutSessionEventJournalStore store =
        new WorkoutSessionEventJournalStore(new MemoryStorage());
    assertEquals(
        WorkoutSessionEventJournalStore.RejectionRollbackResult.NO_JOURNAL,
        store.rejectUnstartedStart(SESSION, "fef77b0a-7012-4dd7-a374-cf7a1dd6f083"));

    WorkoutSessionProtocol.Command start =
        command(
            1, "fef77b0a-7012-4dd7-a374-cf7a1dd6f083", WorkoutSessionProtocol.Action.START, 10_000);
    store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, start);
    assertEquals(
        WorkoutSessionEventJournalStore.RejectionRollbackResult.NO_MATCH,
        store.rejectUnstartedStart(SESSION, "671a25fc-cdb2-4d62-a3a2-175f2826040b"));
  }

  @Test
  public void corruptAndMismatchedJournalPayloadIsNotRestored() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    WorkoutSessionEventJournalStore store = new WorkoutSessionEventJournalStore(storage);
    store.append(
        SESSION,
        WorkoutSessionProtocol.Actor.WATCH,
        command(
            1,
            "fef77b0a-7012-4dd7-a374-cf7a1dd6f083",
            WorkoutSessionProtocol.Action.START,
            10_000));
    assertNull(store.load("323e2adc-5dab-4d7b-9e4b-e111510ddc7b"));
    storage.bytes = new byte[] {1, 2, 3};
    assertNull(store.load(SESSION));
    assertEquals(3, storage.bytes.length);
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.STORAGE_ERROR,
        store.append(
            SESSION,
            WorkoutSessionProtocol.Actor.WATCH,
            command(
                1,
                "fef77b0a-7012-4dd7-a374-cf7a1dd6f083",
                WorkoutSessionProtocol.Action.START,
                10_000)));
    assertEquals("Unreadable journal bytes must not be replaced", 3, storage.bytes.length);
  }

  @Test
  public void unavailableStorageDoesNotOverwriteAnUnreadableJournal() {
    FailingReadStorage storage = new FailingReadStorage();
    WorkoutSessionEventJournalStore store = new WorkoutSessionEventJournalStore(storage);
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.STORAGE_ERROR,
        store.append(
            SESSION,
            WorkoutSessionProtocol.Actor.WATCH,
            command(
                1,
                "fef77b0a-7012-4dd7-a374-cf7a1dd6f083",
                WorkoutSessionProtocol.Action.START,
                10_000)));
    assertFalse(storage.writeAttempted);
  }

  @Test
  public void differentAuthorityCannotAppendToAnExistingSessionJournal() {
    WorkoutSessionEventJournalStore store =
        new WorkoutSessionEventJournalStore(new MemoryStorage());
    WorkoutSessionProtocol.Command start =
        command(
            1, "fef77b0a-7012-4dd7-a374-cf7a1dd6f083", WorkoutSessionProtocol.Action.START, 10_000);
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.APPENDED,
        store.append(SESSION, WorkoutSessionProtocol.Actor.WATCH, start));
    assertEquals(
        WorkoutSessionEventJournalStore.AppendResult.CONFLICT,
        store.append(
            SESSION,
            WorkoutSessionProtocol.Actor.PHONE,
            new WorkoutSessionProtocol.Command(
                SESSION,
                "4cf3f1f3-e067-4e4e-9b1c-9f0f480eac59",
                2,
                WorkoutSessionProtocol.Actor.PHONE,
                WorkoutSessionProtocol.Action.PAUSE,
                11_000,
                0)));
  }

  private static WorkoutSessionProtocol.Command command(
      long sequence, String commandId, WorkoutSessionProtocol.Action action, long plannedStart) {
    long issuedAt = action == WorkoutSessionProtocol.Action.START ? 1_000L : 11_000L;
    return new WorkoutSessionProtocol.Command(
        SESSION,
        commandId,
        sequence,
        WorkoutSessionProtocol.Actor.WATCH,
        action,
        issuedAt,
        plannedStart);
  }

  private static final class MemoryStorage implements WorkoutSessionEventJournalStore.Storage {
    byte[] bytes;
    boolean failWrites;

    @Override
    public byte[] read(String sessionId) {
      return bytes == null ? null : bytes.clone();
    }

    @Override
    public boolean write(String sessionId, byte[] encoded) {
      if (failWrites) return false;
      bytes = encoded.clone();
      return true;
    }

    @Override
    public boolean delete(String sessionId) {
      bytes = null;
      return true;
    }
  }

  private static final class FailingReadStorage implements WorkoutSessionEventJournalStore.Storage {
    boolean writeAttempted;

    @Override
    public byte[] read(String sessionId) {
      throw new IllegalStateException("Storage unavailable");
    }

    @Override
    public boolean write(String sessionId, byte[] encoded) {
      writeAttempted = true;
      return true;
    }

    @Override
    public boolean delete(String sessionId) {
      return false;
    }
  }
}
