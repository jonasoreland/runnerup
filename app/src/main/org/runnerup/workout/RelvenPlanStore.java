package org.runnerup.workout;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Stores validated plans atomically without changing active workouts or activity history. */
public final class RelvenPlanStore {
  private static final String ACTIVE_PLAN_PREFERENCES = "relven-plan-state";
  private static final String ACTIVE_PLAN_ID = "active_plan_id";
  private static final int MAX_ACTIVE_PLAN_ID_BYTES = 640;

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

  public static synchronized List<RelvenPlanImporter.Plan> list(Context context)
      throws IOException {
    File directory = context.getDir("relven-plans", Context.MODE_PRIVATE);
    File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
    if (files == null) throw new IOException("Could not list saved plans");

    List<RelvenPlanImporter.Plan> plans = new ArrayList<>(files.length);
    for (File file : files) {
      try (FileInputStream input = new FileInputStream(file)) {
        plans.add(RelvenPlanImporter.parse(RelvenPlanImport.read(input)));
      } catch (IllegalArgumentException error) {
        throw new IOException("Saved plan is invalid: " + file.getName(), error);
      }
    }
    plans.sort(Comparator.comparing(RelvenPlanImporter.Plan::name, String.CASE_INSENSITIVE_ORDER));
    return Collections.unmodifiableList(new ArrayList<>(plans));
  }

  /** Selects one previously imported plan as the primary schedule without changing its contents. */
  public static synchronized void activate(Context context, RelvenPlanImporter.Plan plan)
      throws IOException {
    RelvenPlanImporter.Plan verified = RelvenPlanImporter.parse(plan.sourceJson());
    if (!verified.id().equals(plan.id()))
      throw new IOException("Plan ID changed before activation");

    RelvenPlanImporter.Plan stored = null;
    for (RelvenPlanImporter.Plan candidate : list(context)) {
      if (candidate.id().equals(plan.id())) {
        stored = candidate;
        break;
      }
    }
    if (stored == null || !stored.sourceJson().equals(plan.sourceJson())) {
      throw new IOException("Only a saved plan can be activated");
    }

    boolean saved =
        context
            .getSharedPreferences(ACTIVE_PLAN_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(ACTIVE_PLAN_ID, stored.id())
            .commit();
    if (!saved) throw new IOException("Could not activate plan");
  }

  /** Returns the active saved plan, or null when the user has not activated one. */
  public static synchronized RelvenPlanImporter.Plan activePlan(Context context)
      throws IOException {
    String planId =
        context
            .getSharedPreferences(ACTIVE_PLAN_PREFERENCES, Context.MODE_PRIVATE)
            .getString(ACTIVE_PLAN_ID, null);
    if (planId == null) return null;
    if (planId.getBytes(StandardCharsets.UTF_8).length > MAX_ACTIVE_PLAN_ID_BYTES) {
      throw new IOException("Invalid active plan reference");
    }

    for (RelvenPlanImporter.Plan plan : list(context)) {
      if (plan.id().equals(planId)) return plan;
    }
    throw new IOException("Active plan is missing from saved plans");
  }

  /** Clears the active-plan selection while retaining every imported plan. */
  public static synchronized void clearActivePlan(Context context) throws IOException {
    boolean cleared =
        context
            .getSharedPreferences(ACTIVE_PLAN_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(ACTIVE_PLAN_ID)
            .commit();
    if (!cleared) throw new IOException("Could not clear active plan");
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
