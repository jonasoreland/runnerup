package org.runnerup.common.wear;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.io.IOException;

/** Synchronous, per-session persistence for authority events and their replay acknowledgment. */
public final class WorkoutSessionEventJournalStore {
  public enum AppendResult {
    APPENDED,
    DUPLICATE,
    CONFLICT,
    OUT_OF_ORDER,
    STORAGE_ERROR
  }

  public enum RejectionRollbackResult {
    ROLLED_BACK,
    NO_JOURNAL,
    NO_MATCH,
    STORAGE_ERROR
  }

  public static final String PREFERENCES_NAME = "workout_session_event_journals";
  private final Storage storage;

  public WorkoutSessionEventJournalStore(Context context) {
    SharedPreferences preferences =
        context
            .getApplicationContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    storage =
        new Storage() {
          @Override
          public byte[] read(String sessionId) {
            String encoded = preferences.getString(key(sessionId), null);
            return encoded == null ? null : Base64.decode(encoded, Base64.NO_WRAP);
          }

          @Override
          public boolean write(String sessionId, byte[] payload) {
            return preferences
                .edit()
                .putString(key(sessionId), Base64.encodeToString(payload, Base64.NO_WRAP))
                .commit();
          }

          @Override
          public boolean delete(String sessionId) {
            return preferences.edit().remove(key(sessionId)).commit();
          }
        };
  }

  public WorkoutSessionEventJournalStore(Storage storage) {
    this.storage = storage;
  }

  public synchronized WorkoutSessionEventJournal load(String sessionId) {
    try {
      return loadOrThrow(sessionId);
    } catch (RuntimeException unavailable) {
      return null;
    }
  }

  private WorkoutSessionEventJournal loadOrThrow(String sessionId) {
    final byte[] payload = storage.read(sessionId);
    if (payload == null) return null;
    final WorkoutSessionEventJournal journal;
    try {
      journal = WorkoutSessionEventJournal.decode(payload);
    } catch (IOException | RuntimeException invalid) {
      throw new IllegalStateException("Unable to decode workout session journal", invalid);
    }
    if (!journal.getSessionId().equals(sessionId))
      throw new IllegalStateException("Workout session journal mismatch");
    return journal;
  }

  public synchronized AppendResult append(
      String sessionId,
      WorkoutSessionProtocol.Actor authority,
      WorkoutSessionProtocol.Command command) {
    WorkoutSessionEventJournal journal;
    try {
      journal = loadOrThrow(sessionId);
    } catch (RuntimeException unavailable) {
      return AppendResult.STORAGE_ERROR;
    }
    if (journal == null) journal = WorkoutSessionEventJournal.empty(sessionId, authority);
    if (journal.getAuthority() != authority) return AppendResult.CONFLICT;
    WorkoutSessionEventJournal.AppendResult result = journal.append(command);
    if (result != WorkoutSessionEventJournal.AppendResult.APPENDED) return map(result);
    return persist(sessionId, journal) ? AppendResult.APPENDED : AppendResult.STORAGE_ERROR;
  }

  public synchronized boolean acknowledge(String sessionId, long sequence, String commandId) {
    try {
      WorkoutSessionEventJournal journal = loadOrThrow(sessionId);
      if (journal == null || !journal.acknowledge(sequence, commandId)) return false;
      return persist(sessionId, journal);
    } catch (RuntimeException unavailable) {
      return false;
    }
  }

  public synchronized RejectionRollbackResult rejectUnstartedStart(
      String sessionId, String commandId) {
    try {
      WorkoutSessionEventJournal journal = loadOrThrow(sessionId);
      if (journal == null) return RejectionRollbackResult.NO_JOURNAL;
      if (journal.getAuthority() != WorkoutSessionProtocol.Actor.WATCH
          || !journal.rejectUnstartedStart(commandId)) return RejectionRollbackResult.NO_MATCH;
      return persist(sessionId, journal)
          ? RejectionRollbackResult.ROLLED_BACK
          : RejectionRollbackResult.STORAGE_ERROR;
    } catch (RuntimeException unavailable) {
      return RejectionRollbackResult.STORAGE_ERROR;
    }
  }

  private boolean persist(String sessionId, WorkoutSessionEventJournal journal) {
    try {
      return storage.write(sessionId, journal.encode());
    } catch (IOException | RuntimeException unavailable) {
      return false;
    }
  }

  private static AppendResult map(WorkoutSessionEventJournal.AppendResult result) {
    switch (result) {
      case DUPLICATE:
        return AppendResult.DUPLICATE;
      case OUT_OF_ORDER:
        return AppendResult.OUT_OF_ORDER;
      case APPENDED:
        return AppendResult.APPENDED;
      case CONFLICT:
      default:
        return AppendResult.CONFLICT;
    }
  }

  private static String key(String sessionId) {
    return "journal_" + sessionId;
  }

  public interface Storage {
    byte[] read(String sessionId);

    boolean write(String sessionId, byte[] payload);

    boolean delete(String sessionId);
  }
}
