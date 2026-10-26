package org.runnerup.service;

import org.runnerup.common.wear.WorkoutSessionProtocol;

/** Applies already-accepted session events to the Wear telemetry recorder lifecycle. */
final class WearWorkoutRecorderController {
  enum Effect {
    NONE,
    START,
    PAUSE,
    RESUME,
    FINISH
  }

  private String sessionId;
  private long lastSequence;
  private long startDeadlineElapsedRealtimeMs;
  private long activeSinceElapsedRealtimeMs;
  private Phase phase = Phase.IDLE;

  enum Phase {
    IDLE,
    COUNTDOWN,
    ACTIVE,
    PAUSED,
    FINISHED
  }

  synchronized Effect accept(WorkoutSessionProtocol.Command command, long nowElapsedRealtimeMs) {
    if (command == null || nowElapsedRealtimeMs < 0) return Effect.NONE;

    boolean newSession = !command.getSessionId().equals(sessionId);
    if (!newSession && command.getSequence() <= lastSequence) return Effect.NONE;
    if (newSession
        && (command.getAction() != WorkoutSessionProtocol.Action.START
            || command.getSequence() != 1L)) return Effect.NONE;
    if (!newSession && command.getSequence() != lastSequence + 1L) return Effect.NONE;

    if (command.getAction() == WorkoutSessionProtocol.Action.START) {
      if (!newSession && phase != Phase.IDLE && phase != Phase.FINISHED) return Effect.NONE;
      sessionId = command.getSessionId();
      lastSequence = command.getSequence();
      startDeadlineElapsedRealtimeMs = command.getPlannedStartElapsedRealtimeMs();
      phase =
          nowElapsedRealtimeMs >= startDeadlineElapsedRealtimeMs ? Phase.ACTIVE : Phase.COUNTDOWN;
      activeSinceElapsedRealtimeMs =
          phase == Phase.ACTIVE ? nowElapsedRealtimeMs : startDeadlineElapsedRealtimeMs;
      return Effect.START;
    }

    if (newSession || phase == Phase.IDLE || phase == Phase.FINISHED) return Effect.NONE;
    switch (command.getAction()) {
      case PAUSE:
        if (phase != Phase.ACTIVE) return Effect.NONE;
        phase = Phase.PAUSED;
        lastSequence = command.getSequence();
        return Effect.PAUSE;
      case RESUME:
        if (phase != Phase.PAUSED) return Effect.NONE;
        phase = Phase.ACTIVE;
        activeSinceElapsedRealtimeMs = nowElapsedRealtimeMs;
        lastSequence = command.getSequence();
        return Effect.RESUME;
      case FINISH:
      case CANCEL:
        phase = Phase.FINISHED;
        lastSequence = command.getSequence();
        return Effect.FINISH;
      case LAP:
      case SKIP:
        lastSequence = command.getSequence();
        return Effect.NONE;
      default:
        return Effect.NONE;
    }
  }

  synchronized void advanceTo(long nowElapsedRealtimeMs) {
    if (nowElapsedRealtimeMs >= startDeadlineElapsedRealtimeMs && phase == Phase.COUNTDOWN) {
      phase = Phase.ACTIVE;
      activeSinceElapsedRealtimeMs = startDeadlineElapsedRealtimeMs;
    }
  }

  synchronized boolean shouldRecord(String sampleSessionId, long sampleElapsedRealtimeNanos) {
    if (sampleSessionId == null
        || !sampleSessionId.equals(sessionId)
        || phase != Phase.ACTIVE
        || sampleElapsedRealtimeNanos < 0) return false;
    return sampleElapsedRealtimeNanos >= activeSinceElapsedRealtimeMs * 1_000_000L;
  }

  synchronized Snapshot snapshot() {
    return new Snapshot(
        sessionId,
        lastSequence,
        startDeadlineElapsedRealtimeMs,
        activeSinceElapsedRealtimeMs,
        phase);
  }

  synchronized void restore(Snapshot snapshot) {
    if (snapshot == null) throw new IllegalArgumentException("Snapshot is required");
    sessionId = snapshot.sessionId;
    lastSequence = snapshot.lastSequence;
    startDeadlineElapsedRealtimeMs = snapshot.startDeadlineElapsedRealtimeMs;
    activeSinceElapsedRealtimeMs = snapshot.activeSinceElapsedRealtimeMs;
    phase = snapshot.phase;
  }

  static final class Snapshot {
    final String sessionId;
    final long lastSequence;
    final long startDeadlineElapsedRealtimeMs;
    final long activeSinceElapsedRealtimeMs;
    final Phase phase;

    Snapshot(
        String sessionId,
        long lastSequence,
        long startDeadlineElapsedRealtimeMs,
        long activeSinceElapsedRealtimeMs,
        Phase phase) {
      this.sessionId = sessionId;
      this.lastSequence = lastSequence;
      this.startDeadlineElapsedRealtimeMs = startDeadlineElapsedRealtimeMs;
      this.activeSinceElapsedRealtimeMs = activeSinceElapsedRealtimeMs;
      this.phase = phase;
    }
  }
}
