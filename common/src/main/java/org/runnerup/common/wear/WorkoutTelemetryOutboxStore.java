package org.runnerup.common.wear;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Durable per-session telemetry outbox with atomic file replacement. */
public final class WorkoutTelemetryOutboxStore {
  public interface Storage {
    byte[] read(String sessionId) throws IOException;

    boolean write(String sessionId, byte[] payload) throws IOException;

    boolean delete(String sessionId) throws IOException;
  }

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

  public synchronized WorkoutTelemetryOutbox.AppendResult append(
      String sessionId, WorkoutTelemetryProtocol.Sample sample) {
    WorkoutTelemetryOutbox outbox;
    try {
      outbox = load(sessionId);
    } catch (RuntimeException corruptOrUnavailable) {
      return WorkoutTelemetryOutbox.AppendResult.STORAGE_ERROR;
    }
    if (outbox == null) outbox = WorkoutTelemetryOutbox.empty(sessionId);
    WorkoutTelemetryOutbox.AppendResult result = outbox.append(sample);
    if (result != WorkoutTelemetryOutbox.AppendResult.APPENDED) return result;
    return persist(sessionId, outbox) ? result : WorkoutTelemetryOutbox.AppendResult.STORAGE_ERROR;
  }

  public synchronized WorkoutTelemetryProtocol.Batch pendingBatch(
      String sessionId, int maxSamples, int maxBytes) {
    WorkoutTelemetryOutbox outbox = load(sessionId);
    return outbox == null ? null : outbox.pendingBatch(maxSamples, maxBytes);
  }

  public synchronized boolean acknowledge(WorkoutTelemetryProtocol.Ack ack) {
    if (ack == null) return false;
    WorkoutTelemetryOutbox outbox;
    try {
      outbox = load(ack.sessionId);
    } catch (RuntimeException corruptOrUnavailable) {
      return false;
    }
    if (outbox == null || !outbox.acknowledge(ack)) return false;
    return persist(ack.sessionId, outbox);
  }

  public synchronized long acknowledgedThrough(String sessionId) {
    WorkoutTelemetryOutbox outbox = load(sessionId);
    return outbox == null ? 0 : outbox.acknowledgedThrough();
  }

  /**
   * Returns the last allocated sample sequence, including acknowledged samples, for safe restart.
   */
  public synchronized long lastSequence(String sessionId) {
    WorkoutTelemetryOutbox outbox = load(sessionId);
    return outbox == null ? 0 : outbox.lastSequence();
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
