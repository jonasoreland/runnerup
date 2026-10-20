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

/** Provides the versioned offline AI Plan Kit bundled with the phone app. */
public final class RelvenPlanKit {
  public static final String ASSET_PATH = "relven/relven-ai-plan-kit-v1.zip";
  private static final String INSTRUCTIONS_ENTRY = "relven-ai-plan-kit/INSTRUCTIONS.md";
  private static final int MAX_INSTRUCTIONS_BYTES = 1_048_576;

  private RelvenPlanKit() {}

  public static String readInstructions(Context context) throws IOException {
    try (InputStream input = context.getAssets().open(ASSET_PATH);
        ZipInputStream archive = new ZipInputStream(input, StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = archive.getNextEntry()) != null) {
        if (!INSTRUCTIONS_ENTRY.equals(entry.getName())) continue;
        ByteArrayOutputStream instructions = new ByteArrayOutputStream();
        copy(archive, instructions, MAX_INSTRUCTIONS_BYTES);
        return new String(instructions.toByteArray(), StandardCharsets.UTF_8);
      }
    }
    throw new IOException("AI Plan Kit instructions are missing");
  }

  public static void copyArchive(Context context, OutputStream destination) throws IOException {
    try (InputStream archive = context.getAssets().open(ASSET_PATH)) {
      copy(archive, destination, Integer.MAX_VALUE);
    }
  }

  public static File createShareFile(Context context) throws IOException {
    File directory = new File(context.getCacheDir(), "relven-ai-plan-kit");
    if (!directory.isDirectory() && !directory.mkdirs()) {
      throw new IOException("Could not create AI Plan Kit share cache");
    }
    File archive = new File(directory, "relven-ai-plan-kit-v1.zip");
    try (FileOutputStream output = new FileOutputStream(archive)) {
      copyArchive(context, output);
    }
    return archive;
  }

  private static void copy(InputStream input, OutputStream output, int maxBytes)
      throws IOException {
    byte[] buffer = new byte[8192];
    int total = 0;
    int count;
    while ((count = input.read(buffer)) != -1) {
      if (count > maxBytes - total) throw new IOException("AI Plan Kit entry is too large");
      output.write(buffer, 0, count);
      total += count;
    }
  }
}
