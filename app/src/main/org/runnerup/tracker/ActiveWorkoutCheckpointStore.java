package org.runnerup.tracker;

import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

/** Crash-safe private-file storage for the one active RunnerUp workout checkpoint. */
public final class ActiveWorkoutCheckpointStore {
  private final AtomicFile file;

  public ActiveWorkoutCheckpointStore(File path) {
    file = new AtomicFile(path);
  }

  public synchronized void save(ActiveWorkoutCheckpoint checkpoint) throws IOException {
    FileOutputStream output = null;
    try {
      output = file.startWrite();
      output.write(checkpoint.toJson().toString().getBytes(StandardCharsets.UTF_8));
      file.finishWrite(output);
    } catch (IOException | JSONException exception) {
      if (output != null) file.failWrite(output);
      if (exception instanceof IOException) throw (IOException) exception;
      throw new IOException("Unable to encode active workout checkpoint", exception);
    }
  }

  /** Returns null when no valid supported checkpoint is available. */
  public synchronized ActiveWorkoutCheckpoint load() {
    try (FileInputStream input = file.openRead();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int count;
      while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
      return ActiveWorkoutCheckpoint.fromJson(
          new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
    } catch (IOException | JSONException exception) {
      return null;
    }
  }

  public synchronized void clear() {
    file.delete();
  }
}
