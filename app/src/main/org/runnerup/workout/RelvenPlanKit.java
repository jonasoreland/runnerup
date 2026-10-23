package org.runnerup.workout;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.runnerup.relven.exercise.RelvenPlanKitExerciseData;

/** Provides the versioned offline AI Plan Kit bundled with the phone app. */
public final class RelvenPlanKit {
  public static final String ASSET_PATH = "relven/relven-ai-plan-kit-v2.zip";
  private static final String KIT_PREFIX = "relven-ai-plan-kit/";
  private static final String CUSTOM_COLLECTION_ENTRY =
      KIT_PREFIX + "user-data/custom-exercise-collection-v1.json";
  private static final int MAX_ARCHIVE_BYTES = 4 * 1_048_576;

  private RelvenPlanKit() {}

  public static String readInstructions(Context context) throws IOException {
    ByteArrayOutputStream bundle = new ByteArrayOutputStream();
    copyArchive(context, bundle);
    StringBuilder prompt = new StringBuilder();
    try (ZipInputStream archive =
        new ZipInputStream(
            new java.io.ByteArrayInputStream(bundle.toByteArray()), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      byte[] buffer = new byte[8192];
      while ((entry = archive.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        String name = entry.getName();
        if (name.startsWith(KIT_PREFIX)) name = name.substring(KIT_PREFIX.length());
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        int total = 0;
        int count;
        while ((count = archive.read(buffer)) != -1) {
          if (count > MAX_ARCHIVE_BYTES - total) {
            throw new IOException("AI Plan Kit text is too large to copy");
          }
          contents.write(buffer, 0, count);
          total += count;
        }
        if (prompt.length() > 0) prompt.append("\n\n");
        prompt.append("===== FILE: ").append(name).append(" =====\n");
        prompt.append(new String(contents.toByteArray(), StandardCharsets.UTF_8));
      }
    }
    if (prompt.length() == 0) throw new IOException("AI Plan Kit is empty");
    return prompt.toString();
  }

  public static void copyArchive(Context context, OutputStream destination) throws IOException {
    try (InputStream input = context.getAssets().open(ASSET_PATH);
        ZipInputStream archive = new ZipInputStream(input, StandardCharsets.UTF_8)) {
      ZipOutputStream output = new ZipOutputStream(destination, StandardCharsets.UTF_8);
      ZipEntry entry;
      byte[] buffer = new byte[8192];
      int total = 0;
      while ((entry = archive.getNextEntry()) != null) {
        ZipEntry copy = new ZipEntry(entry.getName());
        output.putNextEntry(copy);
        int count;
        while ((count = archive.read(buffer)) != -1) {
          if (count > MAX_ARCHIVE_BYTES - total) {
            throw new IOException("Bundled AI Plan Kit is too large");
          }
          output.write(buffer, 0, count);
          total += count;
        }
        output.closeEntry();
      }
      ZipEntry customCollection = new ZipEntry(CUSTOM_COLLECTION_ENTRY);
      customCollection.setTime(0L);
      output.putNextEntry(customCollection);
      byte[] customData = RelvenPlanKitExerciseData.exportCustomCollection(context);
      if (customData.length > MAX_ARCHIVE_BYTES - total) {
        throw new IOException("Custom exercise collection is too large for the AI Plan Kit");
      }
      output.write(customData);
      output.closeEntry();
      output.finish();
      output.flush();
    }
  }

  public static File createShareFile(Context context) throws IOException {
    File directory = new File(context.getCacheDir(), "relven-ai-plan-kit");
    if (!directory.isDirectory() && !directory.mkdirs()) {
      throw new IOException("Could not create AI Plan Kit share cache");
    }
    File archive = new File(directory, "relven-ai-plan-kit-v2.zip");
    try (FileOutputStream output = new FileOutputStream(archive)) {
      copyArchive(context, output);
    }
    return archive;
  }
}
