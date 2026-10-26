package org.runnerup.common.wear;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Durable per-session telemetry outbox with atomic file replacement. */
public final class WorkoutTelemetryOutboxStore {
  public interface Storage {
    byte[] read(String sessionId) throws IOException;

    boolean write(String sessionId, byte[] payload) throws IOException;

    boolean delete(String sessionId) throws IOException;

    default boolean addSession(String sessionId) throws IOException {
      return true;
    }

    default boolean removeSession(String sessionId) throws IOException {
      return true;
    }

    default List<String> sessionIds() throws IOException {
      return Collections.emptyList();
    }
  }

  private static final Object STORE_LOCK = new Object();
  private final Storage storage;

  public WorkoutTelemetryOutboxStore(Context context) {
    File directory = new File(context.getApplicationContext().getFilesDir(), "workout-telemetry");
    if (!directory.exists() && !directory.mkdirs())
      throw new IllegalStateException("Unable to create telemetry directory");
    storage = new FileStorage(directory);
  }

  public WorkoutTelemetryOutboxStore(Storage storage) {
    if (storage == null) throw new IllegalArgumentException("Storage is required");
    this.storage = storage;
  }

  public WorkoutTelemetryOutbox.AppendResult append(
      String sessionId, WorkoutTelemetryProtocol.Sample sample) {
    synchronized (STORE_LOCK) {
      WorkoutTelemetryOutbox outbox;
      try {
        outbox = load(sessionId);
      } catch (RuntimeException corruptOrUnavailable) {
        return WorkoutTelemetryOutbox.AppendResult.STORAGE_ERROR;
      }
      if (outbox == null) outbox = WorkoutTelemetryOutbox.empty(sessionId);
      WorkoutTelemetryOutbox.AppendResult result = outbox.append(sample);
      if (result != WorkoutTelemetryOutbox.AppendResult.APPENDED) return result;
      try {
        if (!storage.addSession(sessionId))
          return WorkoutTelemetryOutbox.AppendResult.STORAGE_ERROR;
      } catch (IOException | RuntimeException unavailable) {
        return WorkoutTelemetryOutbox.AppendResult.STORAGE_ERROR;
      }
      return persist(sessionId, outbox)
          ? result
          : WorkoutTelemetryOutbox.AppendResult.STORAGE_ERROR;
    }
  }

  public WorkoutTelemetryProtocol.Batch pendingBatch(
      String sessionId, int maxSamples, int maxBytes) {
    synchronized (STORE_LOCK) {
      WorkoutTelemetryOutbox outbox = load(sessionId);
      return outbox == null ? null : outbox.pendingBatch(maxSamples, maxBytes);
    }
  }

  public boolean acknowledge(WorkoutTelemetryProtocol.Ack ack) {
    synchronized (STORE_LOCK) {
      if (ack == null) return false;
      WorkoutTelemetryOutbox outbox;
      try {
        outbox = load(ack.sessionId);
      } catch (RuntimeException corruptOrUnavailable) {
        return false;
      }
      if (outbox == null || !outbox.acknowledge(ack)) return false;
      if (!persist(ack.sessionId, outbox)) return false;
      if (outbox.pendingSamples().isEmpty()) {
        try {
          return storage.removeSession(ack.sessionId);
        } catch (IOException | RuntimeException unavailable) {
          return false;
        }
      }
      return true;
    }
  }

  /** Returns sessions with unacknowledged samples in durable insertion order. */
  public List<String> pendingSessionIds() {
    synchronized (STORE_LOCK) {
      try {
        List<String> sessions = storage.sessionIds();
        if (sessions == null || sessions.isEmpty()) return Collections.emptyList();
        List<String> pending = new ArrayList<>();
        for (String sessionId : sessions) {
          try {
            WorkoutTelemetryOutbox outbox = load(sessionId);
            if (outbox != null && !outbox.pendingSamples().isEmpty()) pending.add(sessionId);
          } catch (RuntimeException invalidEntry) {
            // A damaged session must not block delivery for other intact sessions.
          }
        }
        return Collections.unmodifiableList(pending);
      } catch (IOException | RuntimeException unavailable) {
        return Collections.emptyList();
      }
    }
  }

  public long acknowledgedThrough(String sessionId) {
    synchronized (STORE_LOCK) {
      WorkoutTelemetryOutbox outbox = load(sessionId);
      return outbox == null ? 0 : outbox.acknowledgedThrough();
    }
  }

  /**
   * Returns the last allocated sample sequence, including acknowledged samples, for safe restart.
   */
  public long lastSequence(String sessionId) {
    synchronized (STORE_LOCK) {
      WorkoutTelemetryOutbox outbox = load(sessionId);
      return outbox == null ? 0 : outbox.lastSequence();
    }
  }

  private WorkoutTelemetryOutbox load(String sessionId) {
    if (sessionId == null || sessionId.trim().isEmpty()) return null;
    try {
      byte[] bytes = storage.read(sessionId);
      return bytes == null ? null : WorkoutTelemetryOutbox.decode(sessionId, bytes);
    } catch (IOException | RuntimeException invalidOrUnavailable) {
      throw new IllegalStateException(
          "Unable to load telemetry outbox without risking data loss", invalidOrUnavailable);
    }
  }

  private boolean persist(String sessionId, WorkoutTelemetryOutbox outbox) {
    try {
      return storage.write(sessionId, outbox.encode());
    } catch (IOException | RuntimeException unavailable) {
      return false;
    }
  }

  private static final class FileStorage implements Storage {
    private static final int SESSION_INDEX_MAGIC = 0x57544931; // WTI1
    private static final int SESSION_INDEX_VERSION = 1;
    private static final int MAX_INDEXED_SESSIONS = 256;
    private static final Object SESSION_INDEX_LOCK = new Object();
    private final File directory;

    FileStorage(File directory) {
      this.directory = directory;
    }

    @Override
    public byte[] read(String sessionId) throws IOException {
      AtomicFile file = atomicFile(sessionId);
      FileInputStream input;
      try {
        input = file.openRead();
      } catch (java.io.FileNotFoundException missing) {
        return null;
      }
      try (FileInputStream stream = input;
          ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
          if (bytes.size() + read > WorkoutTelemetryOutbox.MAX_STORAGE_BYTES)
            throw new IOException("Telemetry outbox exceeds limit");
          bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
      }
    }

    @Override
    public boolean write(String sessionId, byte[] payload) throws IOException {
      if (payload == null || payload.length > WorkoutTelemetryOutbox.MAX_STORAGE_BYTES)
        return false;
      AtomicFile file = atomicFile(sessionId);
      FileOutputStream output = file.startWrite();
      try {
        output.write(payload);
        file.finishWrite(output);
        return true;
      } catch (IOException failure) {
        file.failWrite(output);
        throw failure;
      }
    }

    @Override
    public boolean delete(String sessionId) {
      atomicFile(sessionId).delete();
      return true;
    }

    @Override
    public boolean addSession(String sessionId) throws IOException {
      synchronized (SESSION_INDEX_LOCK) {
        Set<String> sessions = readSessionIndex();
        if (sessions.contains(sessionId)) return true;
        if (sessions.size() >= MAX_INDEXED_SESSIONS) return false;
        sessions.add(sessionId);
        return writeSessionIndex(sessions);
      }
    }

    @Override
    public boolean removeSession(String sessionId) throws IOException {
      synchronized (SESSION_INDEX_LOCK) {
        Set<String> sessions = readSessionIndex();
        if (!sessions.remove(sessionId)) return true;
        return writeSessionIndex(sessions);
      }
    }

    @Override
    public List<String> sessionIds() throws IOException {
      synchronized (SESSION_INDEX_LOCK) {
        return new ArrayList<>(readSessionIndex());
      }
    }

    private Set<String> readSessionIndex() throws IOException {
      AtomicFile file = new AtomicFile(new File(directory, "sessions.index"));
      FileInputStream input;
      try {
        input = file.openRead();
      } catch (java.io.FileNotFoundException missing) {
        Set<String> recovered = discoverPendingSessions();
        if (!writeSessionIndex(recovered))
          throw new IOException("Unable to persist recovered telemetry session index");
        return recovered;
      }
      try (DataInputStream data = new DataInputStream(input)) {
        if (data.readInt() != SESSION_INDEX_MAGIC
            || data.readUnsignedByte() != SESSION_INDEX_VERSION)
          throw new IOException("Invalid telemetry session index");
        int count = data.readUnsignedShort();
        if (count > MAX_INDEXED_SESSIONS)
          throw new IOException("Telemetry session index is too large");
        Set<String> sessions = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
          String session = data.readUTF();
          if (session.trim().isEmpty()
              || session.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 128
              || !sessions.add(session)) throw new IOException("Invalid telemetry session entry");
        }
        if (data.read() != -1) throw new IOException("Trailing telemetry index data");
        return sessions;
      }
    }

    private Set<String> discoverPendingSessions() throws IOException {
      File[] files =
          directory.listFiles((parent, name) -> name.matches("session-[0-9a-f]{64}\\.bin"));
      if (files == null) throw new IOException("Unable to scan telemetry outbox directory");
      java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
      Set<String> sessions = new LinkedHashSet<>();
      for (File candidate : files) {
        String sessionId;
        WorkoutTelemetryOutbox outbox;
        try {
          byte[] payload = readBounded(new AtomicFile(candidate));
          sessionId = WorkoutTelemetryOutbox.sessionIdFromEncoded(payload);
          if (!candidate.getName().equals(fileName(sessionId))) continue;
          outbox = WorkoutTelemetryOutbox.decode(sessionId, payload);
        } catch (IOException | RuntimeException corruptEntry) {
          // Keep corrupted files untouched while allowing other valid sessions to retry.
          continue;
        }
        if (!outbox.pendingSamples().isEmpty()) sessions.add(sessionId);
        if (sessions.size() > MAX_INDEXED_SESSIONS)
          throw new IOException("Too many pending telemetry sessions to recover");
      }
      return sessions;
    }

    private byte[] readBounded(AtomicFile file) throws IOException {
      try (FileInputStream input = file.openRead();
          ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
          if (bytes.size() + count > WorkoutTelemetryOutbox.MAX_STORAGE_BYTES)
            throw new IOException("Telemetry outbox exceeds limit");
          bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
      }
    }

    private boolean writeSessionIndex(Set<String> sessions) throws IOException {
      if (sessions.size() > MAX_INDEXED_SESSIONS) return false;
      AtomicFile file = new AtomicFile(new File(directory, "sessions.index"));
      FileOutputStream output = file.startWrite();
      DataOutputStream data = new DataOutputStream(output);
      try {
        data.writeInt(SESSION_INDEX_MAGIC);
        data.writeByte(SESSION_INDEX_VERSION);
        data.writeShort(sessions.size());
        for (String session : sessions) data.writeUTF(session);
        data.flush();
        file.finishWrite(output);
        return true;
      } catch (IOException failure) {
        file.failWrite(output);
        throw failure;
      }
    }

    private AtomicFile atomicFile(String sessionId) {
      return new AtomicFile(new File(directory, fileName(sessionId)));
    }

    private static String fileName(String sessionId) {
      try {
        byte[] digest =
            MessageDigest.getInstance("SHA-256")
                .digest(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder("session-");
        for (byte value : digest)
          name.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return name + ".bin";
      } catch (NoSuchAlgorithmException impossible) {
        throw new AssertionError(impossible);
      }
    }
  }
}
