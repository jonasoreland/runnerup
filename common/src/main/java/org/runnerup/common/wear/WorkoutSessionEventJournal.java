package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Durable ordered record of accepted authority events plus the unacknowledged replay suffix. */
public final class WorkoutSessionEventJournal {
  private static final int MAGIC = 0x57534A31; // WSJ1
  private static final int VERSION = 1;
  private static final int MAX_BYTES = 1_048_576;
  private static final int MAX_EVENTS = 4096;

  public enum AppendResult {
    APPENDED,
    DUPLICATE,
    CONFLICT,
    OUT_OF_ORDER
  }

  private final String sessionId;
  private final WorkoutSessionProtocol.Actor authority;
  private final List<WorkoutSessionProtocol.Command> events = new ArrayList<>();
  private long acknowledgedThrough;

  private WorkoutSessionEventJournal(
      String sessionId, WorkoutSessionProtocol.Actor authority, long acknowledgedThrough) {
    WorkoutSessionProtocol.SessionState.ready(sessionId, authority);
    if (acknowledgedThrough < 0) throw new IllegalArgumentException("Invalid acknowledgment");
    this.sessionId = sessionId;
    this.authority = authority;
    this.acknowledgedThrough = acknowledgedThrough;
  }

  public static WorkoutSessionEventJournal empty(
      String sessionId, WorkoutSessionProtocol.Actor authority) {
    if (authority == null) throw new IllegalArgumentException("Authority is required");
    return new WorkoutSessionEventJournal(sessionId, authority, 0L);
  }

  public AppendResult append(WorkoutSessionProtocol.Command command) {
    if (command == null
        || !sessionId.equals(command.getSessionId())
        || command.getActor() != authority) return AppendResult.CONFLICT;
    long expectedSequence = lastSequence() + 1L;
    if (command.getSequence() < expectedSequence) {
      WorkoutSessionProtocol.Command existing = eventAt(command.getSequence());
      return sameCommand(existing, command) ? AppendResult.DUPLICATE : AppendResult.CONFLICT;
    }
    if (command.getSequence() != expectedSequence) return AppendResult.OUT_OF_ORDER;
    if (events.size() >= MAX_EVENTS) return AppendResult.CONFLICT;

    List<WorkoutSessionProtocol.Command> candidate = new ArrayList<>(events);
    candidate.add(command);
    if (!isValidReplay(candidate)) return AppendResult.CONFLICT;
    events.add(command);
    return AppendResult.APPENDED;
  }

  /** Acknowledgments are ordered and idempotent; they can only retire a matching event. */
  public boolean acknowledge(long sequence, String commandId) {
    if (sequence <= acknowledgedThrough) {
      WorkoutSessionProtocol.Command existing = eventAt(sequence);
      return existing != null && existing.getCommandId().equals(commandId);
    }
    if (sequence != acknowledgedThrough + 1L) return false;
    WorkoutSessionProtocol.Command event = eventAt(sequence);
    if (event == null || !event.getCommandId().equals(commandId)) return false;
    acknowledgedThrough = sequence;
    return true;
  }

  /** Rolls back a start that the phone rejected before RunnerUp began recording. */
  public boolean rejectUnstartedStart(String commandId) {
    if (events.isEmpty()) return false;
    WorkoutSessionProtocol.Command last = events.get(events.size() - 1);
    if (last.getAction() != WorkoutSessionProtocol.Action.START
        || !last.getCommandId().equals(commandId)) return false;
    events.remove(events.size() - 1);
    acknowledgedThrough = Math.min(acknowledgedThrough, events.size());
    return true;
  }

  public String getSessionId() {
    return sessionId;
  }

  public WorkoutSessionProtocol.Actor getAuthority() {
    return authority;
  }

  public long lastSequence() {
    return events.size();
  }

  public long acknowledgedThrough() {
    return acknowledgedThrough;
  }

  public List<WorkoutSessionProtocol.Command> events() {
    return Collections.unmodifiableList(new ArrayList<>(events));
  }

  public List<WorkoutSessionProtocol.Command> pendingEvents() {
    return Collections.unmodifiableList(
        new ArrayList<>(events.subList((int) acknowledgedThrough, events.size())));
  }

  public WorkoutSessionProtocol.SessionState replayState() {
    WorkoutSessionProtocol.SessionState state =
        WorkoutSessionProtocol.SessionState.ready(sessionId, authority);
    for (WorkoutSessionProtocol.Command event : events) {
      WorkoutSessionProtocol.Transition advanced =
          WorkoutSessionProtocol.advance(state, event.getIssuedAtElapsedRealtimeMs());
      if (advanced.getResult() == WorkoutSessionProtocol.Result.ACCEPTED)
        state = advanced.getState();
      WorkoutSessionProtocol.Transition transition =
          WorkoutSessionProtocol.applyAtAuthority(state, authority, event);
      if (transition.getResult() != WorkoutSessionProtocol.Result.ACCEPTED) {
        throw new IllegalStateException("Journal contains an invalid session transition");
      }
      state = transition.getState();
    }
    return state;
  }

  public byte[] encode() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeByte(VERSION);
      output.writeUTF(sessionId);
      output.writeByte(authority.ordinal());
      output.writeLong(acknowledgedThrough);
      output.writeInt(events.size());
      for (WorkoutSessionProtocol.Command event : events) {
        byte[] encoded = WorkoutSessionProtocol.encodeCommand(event);
        output.writeShort(encoded.length);
        output.write(encoded);
      }
    }
    byte[] encoded = bytes.toByteArray();
    if (encoded.length > MAX_BYTES) throw new IOException("Workout session journal is too large");
    return encoded;
  }

  public static WorkoutSessionEventJournal decode(byte[] payload) throws IOException {
    if (payload == null || payload.length == 0 || payload.length > MAX_BYTES) {
      throw new IOException("Invalid workout session journal size");
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (input.readInt() != MAGIC || input.readUnsignedByte() != VERSION) {
        throw new IOException("Unsupported workout session journal");
      }
      String sessionId = input.readUTF();
      int actor = input.readUnsignedByte();
      if (actor >= WorkoutSessionProtocol.Actor.values().length) {
        throw new IOException("Invalid workout session authority");
      }
      long acknowledgedThrough = input.readLong();
      int count = input.readInt();
      if (count < 0
          || count > MAX_EVENTS
          || acknowledgedThrough < 0
          || acknowledgedThrough > count) {
        throw new IOException("Invalid workout session journal metadata");
      }
      WorkoutSessionEventJournal journal =
          new WorkoutSessionEventJournal(
              sessionId, WorkoutSessionProtocol.Actor.values()[actor], acknowledgedThrough);
      for (int index = 0; index < count; index++) {
        int length = input.readUnsignedShort();
        if (length == 0 || length > WorkoutSessionProtocol.MAX_MESSAGE_BYTES) {
          throw new IOException("Invalid workout session event size");
        }
        byte[] eventBytes = new byte[length];
        input.readFully(eventBytes);
        WorkoutSessionProtocol.Command command = WorkoutSessionProtocol.decodeCommand(eventBytes);
        if (journal.append(command) != AppendResult.APPENDED) {
          throw new IOException("Invalid workout session event ordering");
        }
      }
      if (input.available() != 0) throw new IOException("Trailing workout session journal bytes");
      return journal;
    } catch (IllegalArgumentException error) {
      throw new IOException("Invalid workout session journal", error);
    }
  }

  private WorkoutSessionProtocol.Command eventAt(long sequence) {
    if (sequence <= 0 || sequence > events.size()) return null;
    return events.get((int) sequence - 1);
  }

  private boolean isValidReplay(List<WorkoutSessionProtocol.Command> candidate) {
    WorkoutSessionProtocol.SessionState state =
        WorkoutSessionProtocol.SessionState.ready(sessionId, authority);
    for (WorkoutSessionProtocol.Command event : candidate) {
      WorkoutSessionProtocol.Transition advanced =
          WorkoutSessionProtocol.advance(state, event.getIssuedAtElapsedRealtimeMs());
      if (advanced.getResult() == WorkoutSessionProtocol.Result.ACCEPTED)
        state = advanced.getState();
      WorkoutSessionProtocol.Transition transition =
          WorkoutSessionProtocol.applyAtAuthority(state, authority, event);
      if (transition.getResult() != WorkoutSessionProtocol.Result.ACCEPTED) return false;
      state = transition.getState();
    }
    return true;
  }

  private static boolean sameCommand(
      WorkoutSessionProtocol.Command first, WorkoutSessionProtocol.Command second) {
    return first != null
        && first.getSessionId().equals(second.getSessionId())
        && first.getCommandId().equals(second.getCommandId())
        && first.getSequence() == second.getSequence()
        && first.getActor() == second.getActor()
        && first.getAction() == second.getAction()
        && first.getIssuedAtElapsedRealtimeMs() == second.getIssuedAtElapsedRealtimeMs()
        && first.getPlannedStartElapsedRealtimeMs() == second.getPlannedStartElapsedRealtimeMs();
  }
}
