package org.runnerup.workout;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stores validated plans atomically without changing active workouts or activity history. */
public final class RelvenPlanStore {
  private RelvenPlanStore() {}

  public static synchronized void save(Context context, RelvenPlanImporter.Plan plan)
      throws IOException {
    RelvenPlanImporter.Plan verified = RelvenPlanImporter.parse(plan.sourceJson());
    if (!verified.id().equals(plan.id())) throw new IOException("Plan ID changed before saving");
    if (plan.sourceJson().getBytes(StandardCharsets.UTF_8).length > 4_194_304) {
      throw new IOException("Plan file is too large");
    }
    File directory = context.getDir("relven-plans", Context.MODE_PRIVATE);
    File destination = new File(directory, hashId(plan.id()) + ".json");
    if (destination.exists()) throw new IOException("Plan already imported: " + plan.name());

    AtomicFile file = new AtomicFile(destination);
    FileOutputStream stream = null;
    try {
      stream = file.startWrite();
      stream.write(plan.sourceJson().getBytes(StandardCharsets.UTF_8));
      file.finishWrite(stream);
    } catch (IOException error) {
      if (stream != null) file.failWrite(stream);
      throw error;
    }
  }

  private static String hashId(String id) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        result.append(Character.forDigit((value >> 4) & 0xF, 16));
        result.append(Character.forDigit(value & 0xF, 16));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }
}
