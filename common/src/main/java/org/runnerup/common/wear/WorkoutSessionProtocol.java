package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/** Versioned, bounded control envelope and reducer for one phone/watch workout session. */
public final class WorkoutSessionProtocol {
  public static final int CURRENT_VERSION = 2;
  public static final int MAX_MESSAGE_BYTES = 256;
  private static final int MAGIC = 0x52575332; // RWS2
  private static final int COMMAND = 1;
  private static final long MAX_CLOCK_RTT_MS = 2_000;

  private WorkoutSessionProtocol() {}

  /** Converts an accepted phone command into the phone-authority projection sent to the watch. */
  public static Command phoneProjection(Command acceptedCommand) {
    if (acceptedCommand == null || acceptedCommand.getActor() != Actor.PHONE) {
      throw new IllegalArgumentException("Only accepted phone commands can be projected");
    }
    return new Command(
        acceptedCommand.getSessionId(),
        acceptedCommand.getCommandId(),
        acceptedCommand.getSequence(),
        Actor.PHONE,
        acceptedCommand.getAction(),
        acceptedCommand.getIssuedAtElapsedRealtimeMs(),
        acceptedCommand.getPlannedStartElapsedRealtimeMs());
  }

  /** Rebases a committed authority command onto a recipient's elapsed-realtime clock. */
  public static Command rebaseCommandClock(
      Command committedCommand,
      long projectedIssuedAtElapsedRealtimeMs,
      long projectedPlannedStartElapsedRealtimeMs) {
    if (committedCommand == null) throw new IllegalArgumentException("Command is required");
    return new Command(
        committedCommand.getSessionId(),
        committedCommand.getCommandId(),
        committedCommand.getSequence(),
        committedCommand.getActor(),
        committedCommand.getAction(),
        projectedIssuedAtElapsedRealtimeMs,
        projectedPlannedStartElapsedRealtimeMs);
  }

  /** A delayed start projection is stale once its shared monotonic deadline has passed. */
  public static boolean isStartDeadlineInFuture(Command command, long nowElapsedRealtimeMs) {
    return command != null
        && command.getAction() == Action.START
        && command.getPlannedStartElapsedRealtimeMs() > nowElapsedRealtimeMs;
  }

  public enum Actor {
    PHONE,
    WATCH
  }

  public enum Action {
    START,
    PAUSE,
    RESUME,
    LAP,
    SKIP,
    FINISH,
    CANCEL
  }

  public enum Phase {
    READY,
    COUNTDOWN,
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED
  }

  public enum Result {
    ACCEPTED,
    DUPLICATE,
    REJECTED
  }

  public static final class Command {
    private final String sessionId;
    private final String commandId;
    private final long sequence;
    private final Actor actor;
    private final Action action;
    private final long issuedAtElapsedRealtimeMs;
    private final long plannedStartElapsedRealtimeMs;

    public Command(
        String sessionId,
        String commandId,
        long sequence,
        Actor actor,
        Action action,
        long issuedAtElapsedRealtimeMs,
        long plannedStartElapsedRealtimeMs) {
      validateUuid(sessionId, "session");
      validateUuid(commandId, "command");
      if (sequence <= 0) throw new IllegalArgumentException("Invalid sequence");
      if (actor == null || action == null) throw new IllegalArgumentException("Invalid command");
      if (issuedAtElapsedRealtimeMs < 0 || plannedStartElapsedRealtimeMs < 0) {
        throw new IllegalArgumentException("Invalid monotonic time");
      }
      this.sessionId = sessionId;
      this.commandId = commandId;
      this.sequence = sequence;
      this.actor = actor;
      this.action = action;
      this.issuedAtElapsedRealtimeMs = issuedAtElapsedRealtimeMs;
      this.plannedStartElapsedRealtimeMs = plannedStartElapsedRealtimeMs;
    }

    public String getSessionId() {
      return sessionId;
    }

    public String getCommandId() {
      return commandId;
    }

    public long getSequence() {
      return sequence;
    }

    public Actor getActor() {
      return actor;
    }

    public Action getAction() {
      return action;
    }

    public long getIssuedAtElapsedRealtimeMs() {
      return issuedAtElapsedRealtimeMs;
    }

    public long getPlannedStartElapsedRealtimeMs() {
      return plannedStartElapsedRealtimeMs;
    }
  }

  public static final class SessionState {
    private final String sessionId;
    private final Actor authority;
    private final Phase phase;
    private final long revision;
    private final long lastSequence;
    private final String lastCommandId;
    private final long plannedStartElapsedRealtimeMs;

    private SessionState(
        String sessionId,
        Actor authority,
        Phase phase,
        long revision,
        long lastSequence,
        String lastCommandId,
        long plannedStartElapsedRealtimeMs) {
      this.sessionId = sessionId;
      this.authority = authority;
      this.phase = phase;
      this.revision = revision;
      this.lastSequence = lastSequence;
      this.lastCommandId = lastCommandId;
      this.plannedStartElapsedRealtimeMs = plannedStartElapsedRealtimeMs;
    }

    public static SessionState ready(String sessionId, Actor authority) {
      validateUuid(sessionId, "session");
      if (authority == null) throw new IllegalArgumentException("Authority is required");
      return new SessionState(sessionId, authority, Phase.READY, 0, 0, null, 0);
    }

    /** Creates a local UI mirror after the authoritative coordinator accepted a start. */
    public static SessionState projectAcceptedStart(
        String sessionId, Actor authority, Command command) {
      validateUuid(sessionId, "session");
      if (authority == null
          || command == null
          || !sessionId.equals(command.sessionId)
          || command.action != Action.START
          || command.sequence <= 0
          || command.plannedStartElapsedRealtimeMs <= command.issuedAtElapsedRealtimeMs
          || command.plannedStartElapsedRealtimeMs - command.issuedAtElapsedRealtimeMs > 10_000L) {
        throw new IllegalArgumentException("Invalid accepted start projection");
      }
      return new SessionState(
          sessionId,
          authority,
          Phase.COUNTDOWN,
          1L,
          command.sequence,
          command.commandId,
          command.plannedStartElapsedRealtimeMs);
    }

    public String getSessionId() {
      return sessionId;
    }

    public Actor getAuthority() {
      return authority;
    }

    public Phase getPhase() {
      return phase;
    }

    public long getRevision() {
      return revision;
    }

    public long getLastSequence() {
      return lastSequence;
    }

    public String getLastCommandId() {
      return lastCommandId;
    }

    public long getPlannedStartElapsedRealtimeMs() {
      return plannedStartElapsedRealtimeMs;
    }

    private SessionState copy(Phase nextPhase, Command command, long plannedStart) {
      return new SessionState(
          sessionId,
          authority,
          nextPhase,
          revision + 1,
          command.getSequence(),
          command.getCommandId(),
          plannedStart);
    }
  }

  public static final class Transition {
    private final Result result;
    private final SessionState state;
    private final String reason;

    private Transition(Result result, SessionState state, String reason) {
      this.result = result;
      this.state = state;
      this.reason = reason;
    }

    public Result getResult() {
      return result;
    }

    public SessionState getState() {
      return state;
    }

    public String getReason() {
      return reason;
    }
  }

  public static byte[] encodeCommand(Command command) throws IOException {
    if (command == null) throw new IOException("Command is required");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeByte(CURRENT_VERSION);
      output.writeByte(COMMAND);
      output.writeUTF(command.sessionId);
      output.writeUTF(command.commandId);
      output.writeLong(command.sequence);
      output.writeByte(command.actor.ordinal());
      output.writeByte(command.action.ordinal());
      output.writeLong(command.issuedAtElapsedRealtimeMs);
      output.writeLong(command.plannedStartElapsedRealtimeMs);
    }
    if (bytes.size() > MAX_MESSAGE_BYTES) throw new IOException("Session command too large");
    return bytes.toByteArray();
  }

  public static Command decodeCommand(byte[] payload) throws IOException {
    if (payload == null || payload.length < 16 || payload.length > MAX_MESSAGE_BYTES) {
      throw new IOException("Invalid session message size");
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (input.readInt() != MAGIC) throw new IOException("Invalid session message header");
      if (input.readUnsignedByte() != CURRENT_VERSION)
        throw new IOException("Unsupported session protocol");
      if (input.readUnsignedByte() != COMMAND)
        throw new IOException("Unexpected session message type");
      String sessionId = input.readUTF();
      String commandId = input.readUTF();
      long sequence = input.readLong();
      int actorIndex = input.readUnsignedByte();
      int actionIndex = input.readUnsignedByte();
      long issuedAt = input.readLong();
      long startAt = input.readLong();
      if (input.available() != 0) throw new IOException("Trailing session message data");
      if (actorIndex >= Actor.values().length || actionIndex >= Action.values().length) {
        throw new IOException("Invalid session command enum");
      }
      try {
        return new Command(
            sessionId,
            commandId,
            sequence,
            Actor.values()[actorIndex],
            Action.values()[actionIndex],
            issuedAt,
            startAt);
      } catch (IllegalArgumentException invalid) {
        throw new IOException("Invalid session command", invalid);
      }
    }
  }

  /**
   * Apply a validated command at the single session coordinator. The actor is the request source.
   */
  public static Transition apply(SessionState state, Command command) {
    if (state == null || command == null || !state.sessionId.equals(command.sessionId)) {
      return rejected(state, "Session identity mismatch");
    }
    if (command.commandId.equals(state.lastCommandId)) {
      return new Transition(Result.DUPLICATE, state, "Already applied");
    }
    if (command.sequence <= state.lastSequence
        || ((state.lastSequence == Long.MAX_VALUE || command.sequence != state.lastSequence + 1L)
            && command.action != Action.START)) {
      return rejected(state, "Out-of-order command sequence");
    }
    Phase next;
    long plannedStart = state.plannedStartElapsedRealtimeMs;
    switch (command.action) {
      case START:
        if (state.phase != Phase.READY
            || command.plannedStartElapsedRealtimeMs <= command.issuedAtElapsedRealtimeMs
            || command.plannedStartElapsedRealtimeMs - command.issuedAtElapsedRealtimeMs > 10_000) {
          return rejected(state, "Start is not valid in this session state");
        }
        plannedStart = command.plannedStartElapsedRealtimeMs;
        next = Phase.COUNTDOWN;
        break;
      case PAUSE:
        if (state.phase != Phase.ACTIVE) return rejected(state, "Pause requires active session");
        next = Phase.PAUSED;
        break;
      case RESUME:
        if (state.phase != Phase.PAUSED) return rejected(state, "Resume requires paused session");
        next = Phase.ACTIVE;
        break;
      case LAP:
      case SKIP:
        if (state.phase != Phase.ACTIVE) return rejected(state, "Control requires active session");
        next = Phase.ACTIVE;
        break;
      case FINISH:
        if (state.phase != Phase.ACTIVE && state.phase != Phase.PAUSED) {
          return rejected(state, "Finish requires active or paused session");
        }
        next = Phase.COMPLETED;
        break;
      case CANCEL:
        if (state.phase != Phase.COUNTDOWN) {
          return rejected(state, "Cancel requires a pending countdown");
        }
        plannedStart = 0L;
        next = Phase.READY;
        break;
      default:
        return rejected(state, "Unsupported session command");
    }
    return new Transition(Result.ACCEPTED, state.copy(next, command, plannedStart), null);
  }

  /** Validates that only the recorded session authority commits a request to shared state. */
  public static Transition applyAtAuthority(
      SessionState state, Actor committingAuthority, Command command) {
    if (state == null || committingAuthority == null || state.authority != committingAuthority) {
      return rejected(state, "Wrong session authority");
    }
    return apply(state, command);
  }

  /** Applies an event projection only when it came from the mirror's declared authority. */
  public static Transition applyProjection(SessionState state, Command command) {
    if (state == null
        || command == null
        || state.getAuthority() != Actor.WATCH
        || command.getActor() != state.getAuthority()) {
      return rejected(state, "Projection did not come from the session authority");
    }
    return apply(state, command);
  }

  /** Advances only from an absolute monotonic deadline; callers schedule ticks as presentation. */
  public static Transition advance(SessionState state, long nowElapsedRealtimeMs) {
    if (state == null
        || state.phase != Phase.COUNTDOWN
        || nowElapsedRealtimeMs < state.plannedStartElapsedRealtimeMs) {
      return new Transition(Result.REJECTED, state, "Countdown has not reached its deadline");
    }
    return new Transition(
        Result.ACCEPTED,
        new SessionState(
            state.sessionId,
            state.authority,
            Phase.ACTIVE,
            state.revision + 1,
            state.lastSequence,
            state.lastCommandId,
            state.plannedStartElapsedRealtimeMs),
        null);
  }

  public static ClockOffset estimateClockOffset(
      long phoneSent, long watchReceived, long watchSent, long phoneReceived, long maxRttMs) {
    long rtt = (phoneReceived - phoneSent) - (watchSent - watchReceived);
    if (phoneSent < 0
        || watchReceived < 0
        || watchSent < watchReceived
        || phoneReceived < phoneSent
        || rtt < 0
        || rtt > Math.min(MAX_CLOCK_RTT_MS, Math.max(0, maxRttMs))) {
      return new ClockOffset(false, 0, rtt);
    }
    long offset = ((watchReceived - phoneSent) + (watchSent - phoneReceived)) / 2;
    return new ClockOffset(true, offset, rtt);
  }

  /** Estimates watch-minus-phone offset for a watch-originated request and phone reply. */
  public static ClockOffset estimateWatchInitiatedClockOffset(
      long watchSent, long phoneReceived, long phoneSent, long watchReceived, long maxRttMs) {
    long rtt = (watchReceived - watchSent) - (phoneSent - phoneReceived);
    if (watchSent < 0
        || phoneReceived < 0
        || phoneSent < phoneReceived
        || watchReceived < watchSent
        || rtt < 0
        || rtt > Math.min(MAX_CLOCK_RTT_MS, Math.max(0, maxRttMs))) {
      return new ClockOffset(false, 0, rtt);
    }
    long offset = ((watchSent - phoneReceived) + (watchReceived - phoneSent)) / 2;
    return new ClockOffset(true, offset, rtt);
  }

  private static Transition rejected(SessionState state, String reason) {
    return new Transition(Result.REJECTED, state, reason);
  }

  private static void validateUuid(String value, String label) {
    try {
      if (value == null || !UUID.fromString(value).toString().equals(value)) {
        throw new IllegalArgumentException("Invalid " + label + " ID");
      }
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("Invalid " + label + " ID", invalid);
    }
  }

  public static final class ClockOffset {
    private final boolean valid;
    private final long watchMinusPhoneOffsetMs;
    private final long roundTripTimeMs;

    private ClockOffset(boolean valid, long offset, long rtt) {
      this.valid = valid;
      this.watchMinusPhoneOffsetMs = offset;
      this.roundTripTimeMs = rtt;
    }

    public boolean isValid() {
      return valid;
    }

    public long getWatchMinusPhoneOffsetMs() {
      return watchMinusPhoneOffsetMs;
    }

    public long getRoundTripTimeMs() {
      return roundTripTimeMs;
    }

    public long toWatchElapsedRealtime(long phoneElapsedRealtimeMs) {
      if (!valid) throw new IllegalStateException("Clock offset is not valid");
      return phoneElapsedRealtimeMs + watchMinusPhoneOffsetMs;
    }

    public long toPhoneElapsedRealtime(long watchElapsedRealtimeMs) {
      if (!valid) throw new IllegalStateException("Clock offset is not valid");
      return watchElapsedRealtimeMs - watchMinusPhoneOffsetMs;
    }
  }
}
