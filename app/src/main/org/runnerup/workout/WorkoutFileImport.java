package org.runnerup.workout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

/** Reads a bounded selected workout document and converts supported formats to RunnerUp's model. */
public final class WorkoutFileImport {
  private static final int MAX_FILE_BYTES = 1_048_576;

  private WorkoutFileImport() {}

  public static Imported read(String fileName, InputStream input) throws Exception {
    if (input == null) throw new IOException("Failed to get input stream");
    byte[] bytes;
    try (InputStream source = input;
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int length;
      while ((length = source.read(buffer)) != -1) {
        if (output.size() + length > MAX_FILE_BYTES) {
          throw new IOException("Workout file is too large");
        }
        output.write(buffer, 0, length);
      }
      bytes = output.toByteArray();
    }

    boolean fit =
        FitWorkoutImporter.isFit(bytes)
            || fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".fit");
    if (fit) return new Imported(Format.FIT, FitWorkoutImporter.parse(bytes));

    String raw = new String(bytes, StandardCharsets.UTF_8);
    boolean relven;
    try {
      relven = new JSONObject(raw).has("schemaVersion");
    } catch (JSONException invalidJson) {
      throw new IllegalArgumentException("Invalid workout JSON", invalidJson);
    }
    Workout workout =
        relven
            ? RelvenWorkoutImporter.parse(raw)
            : WorkoutSerializer.readJSON(new StringReader(raw));
    if (workout == null) throw new IllegalArgumentException("Failed to parse content");
    return new Imported(relven ? Format.RELVEN : Format.RUNNERUP_JSON, workout);
  }

  public enum Format {
    FIT,
    RELVEN,
    RUNNERUP_JSON
  }

  public record Imported(Format format, Workout workout) {}
}
