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
import java.util.Objects;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.relven.exercise.ExerciseCatalogRepository;

/** Stores validated plans atomically without changing active workouts or activity history. */
public final class RelvenPlanStore {
  private static final String ACTIVE_PLAN_PREFERENCES = "relven-plan-state";
  private static final String ACTIVE_PLAN_ID = "active_plan_id";
  private static final int MAX_ACTIVE_PLAN_ID_BYTES = 640;

  private RelvenPlanStore() {}

  /** Parses a plan for preview and validates strength snapshots against the bundled catalog. */
  public static RelvenPlanImporter.Plan parse(Context context, String sourceJson)
      throws IOException {
    try {
      return RelvenPlanImporter.parse(sourceJson, installedExerciseReferences(context));
    } catch (IllegalArgumentException error) {
      throw new IOException("Plan package is invalid", error);
    }
  }

  public static synchronized void save(Context context, RelvenPlanImporter.Plan plan)
      throws IOException {
    RelvenPlanImporter.Plan verified;
    try {
      verified = RelvenPlanImporter.parse(plan.sourceJson());
    } catch (IllegalArgumentException error) {
      throw new IOException("Plan package is invalid", error);
    }
    if (verified.schemaVersion() == 1) {
      verified = parse(context, plan.sourceJson());
    }
    if (!verified.id().equals(plan.id())) throw new IOException("Plan ID changed before saving");
    if (plan.sourceJson().getBytes(StandardCharsets.UTF_8).length > 4_194_304) {
      throw new IOException("Plan file is too large");
    }
    if (verified.schemaVersion() == 2) validateRevisionLineage(context, verified);
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
    plans.sort(
        Comparator.comparing(RelvenPlanImporter.Plan::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(RelvenPlanImporter.Plan::revisionNumber)
            .thenComparing(RelvenPlanImporter.Plan::id));
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

  /** Creates a new immutable schedule revision and activates it only after its package is saved. */
  public static synchronized RelvenPlanImporter.Plan rescheduleActivePlan(
      Context context, RelvenPlanImporter.Plan activePlan, String sessionId, String targetDate)
      throws IOException {
    RelvenPlanImporter.Plan current = activePlan(context);
    if (current == null
        || activePlan == null
        || !current.id().equals(activePlan.id())
        || !current.sourceJson().equals(activePlan.sourceJson())) {
      throw new IOException("Plan changed before rescheduling");
    }

    int nextRevisionNumber = 1;
    for (RelvenPlanImporter.Plan saved : list(context)) {
      if (saved.rootPlanId().equals(current.rootPlanId())) {
        if (saved.revisionNumber() == Integer.MAX_VALUE) {
          throw new IOException("Plan revision limit reached");
        }
        nextRevisionNumber = Math.max(nextRevisionNumber, saved.revisionNumber() + 1);
      }
    }

    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.reschedule(
            current, sessionId, targetDate, UUID.randomUUID().toString(), nextRevisionNumber);
    save(context, revision);
    activate(context, revision);
    return revision;
  }

  /** Adds one planned workout to the active plan and activates its immutable revision. */
  public static synchronized RelvenPlanImporter.Plan addWorkoutToActivePlan(
      Context context,
      RelvenPlanImporter.Plan activePlan,
      String date,
      String workoutId,
      String sessionId)
      throws IOException {
    RelvenPlanImporter.Plan current = requireCurrentActivePlan(context, activePlan);
    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.addWorkout(
            current,
            date,
            workoutId,
            sessionId,
            UUID.randomUUID().toString(),
            nextRevisionNumber(context, current));
    save(context, revision);
    activate(context, revision);
    return revision;
  }

  /** Adds one rest day to the active plan and activates its immutable revision. */
  public static synchronized RelvenPlanImporter.Plan addRestToActivePlan(
      Context context, RelvenPlanImporter.Plan activePlan, String date, String sessionId)
      throws IOException {
    RelvenPlanImporter.Plan current = requireCurrentActivePlan(context, activePlan);
    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.addRest(
            current,
            date,
            sessionId,
            UUID.randomUUID().toString(),
            nextRevisionNumber(context, current));
    save(context, revision);
    activate(context, revision);
    return revision;
  }

  /** Removes exactly one scheduled item and activates the resulting immutable revision. */
  public static synchronized RelvenPlanImporter.Plan removeActivePlanEntry(
      Context context, RelvenPlanImporter.Plan activePlan, String sessionId) throws IOException {
    RelvenPlanImporter.Plan current = requireCurrentActivePlan(context, activePlan);
    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.remove(
            current, sessionId, UUID.randomUUID().toString(), nextRevisionNumber(context, current));
    save(context, revision);
    activate(context, revision);
    return revision;
  }

  /** Renames the active plan by saving and activating an immutable revision. */
  public static synchronized RelvenPlanImporter.Plan renameActivePlan(
      Context context, RelvenPlanImporter.Plan activePlan, String name) throws IOException {
    RelvenPlanImporter.Plan current = requireCurrentActivePlan(context, activePlan);
    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.rename(
            current, name, UUID.randomUUID().toString(), nextRevisionNumber(context, current));
    save(context, revision);
    activate(context, revision);
    return revision;
  }

  /** Renames one workout definition and activates a new immutable plan revision. */
  public static synchronized RelvenPlanImporter.Plan renameWorkoutInActivePlan(
      Context context, RelvenPlanImporter.Plan activePlan, String workoutId, String name)
      throws IOException {
    RelvenPlanImporter.Plan current = requireCurrentActivePlan(context, activePlan);
    RelvenPlanImporter.Plan revision =
        RelvenPlanRevision.renameWorkout(
            current,
            workoutId,
            name,
            UUID.randomUUID().toString(),
            nextRevisionNumber(context, current));
    save(context, revision);
    activate(context, revision);
    return revision;
  }

  private static RelvenPlanImporter.Plan requireCurrentActivePlan(
      Context context, RelvenPlanImporter.Plan activePlan) throws IOException {
    RelvenPlanImporter.Plan current = activePlan(context);
    if (current == null
        || activePlan == null
        || !current.id().equals(activePlan.id())
        || !current.sourceJson().equals(activePlan.sourceJson())) {
      throw new IOException("Plan changed before editing");
    }
    return current;
  }

  private static java.util.Map<String, RelvenExerciseReference> installedExerciseReferences(
      Context context) throws IOException {
    try {
      return new ExerciseCatalogRepository(context).exerciseReferences();
    } catch (IllegalArgumentException error) {
      throw new IOException("Bundled exercise catalog is invalid", error);
    }
  }

  private static int nextRevisionNumber(Context context, RelvenPlanImporter.Plan current)
      throws IOException {
    int nextRevisionNumber = 1;
    for (RelvenPlanImporter.Plan saved : list(context)) {
      if (saved.rootPlanId().equals(current.rootPlanId())) {
        if (saved.revisionNumber() == Integer.MAX_VALUE) {
          throw new IOException("Plan revision limit reached");
        }
        nextRevisionNumber = Math.max(nextRevisionNumber, saved.revisionNumber() + 1);
      }
    }
    return nextRevisionNumber;
  }

  private static void validateRevisionLineage(Context context, RelvenPlanImporter.Plan revision)
      throws IOException {
    RelvenPlanImporter.Plan root = null;
    RelvenPlanImporter.Plan previous = null;
    int highestRevision = 0;
    for (RelvenPlanImporter.Plan saved : list(context)) {
      if (saved.id().equals(revision.rootPlanId())) root = saved;
      if (saved.id().equals(revision.previousRevisionId())) previous = saved;
      if (saved.rootPlanId().equals(revision.rootPlanId())) {
        highestRevision = Math.max(highestRevision, saved.revisionNumber());
      }
    }
    if (root == null
        || root.schemaVersion() != 1
        || !root.id().equals(root.rootPlanId())
        || previous == null
        || !previous.rootPlanId().equals(revision.rootPlanId())
        || highestRevision == Integer.MAX_VALUE
        || revision.revisionNumber() != highestRevision + 1
        || revision.revisionNumber() != previous.revisionNumber() + 1) {
      throw new IOException("Plan revision does not follow a saved lineage");
    }
    validateRevisionSnapshot(previous, revision);
  }

  private static void validateRevisionSnapshot(
      RelvenPlanImporter.Plan previous, RelvenPlanImporter.Plan revision) throws IOException {
    try {
      JSONObject previousJson = new JSONObject(previous.sourceJson());
      JSONObject revisionJson = new JSONObject(revision.sourceJson());
      if (!previous.timezone().equals(revision.timezone())) {
        throw new IOException("Plan revision changed immutable plan content");
      }
      boolean sameWorkouts =
          canonicalJson(previousJson.getJSONArray("workouts"))
              .equals(canonicalJson(revisionJson.getJSONArray("workouts")));
      boolean oneWorkoutNameChange = sameWorkoutsExceptOneName(previousJson, revisionJson);

      List<RelvenPlanImporter.Session> oldDays = previous.days();
      List<RelvenPlanImporter.Session> newDays = revision.days();
      if (Math.abs(oldDays.size() - newDays.size()) > 1) {
        throw new IOException("Plan revision changed the schedule");
      }
      int changedDates = 0;
      int removedDays = 0;
      for (RelvenPlanImporter.Session oldDay : oldDays) {
        RelvenPlanImporter.Session newDay = null;
        for (RelvenPlanImporter.Session candidate : newDays) {
          if (candidate.id().equals(oldDay.id())) {
            newDay = candidate;
            break;
          }
        }
        if (newDay == null) {
          removedDays++;
          continue;
        }
        if (!oldDay.kind().equals(newDay.kind())
            || !Objects.equals(oldDay.workoutId(), newDay.workoutId())
            || !oldDay.notes().equals(newDay.notes())) {
          throw new IOException("Plan revision changed an existing schedule entry");
        }
        if (!oldDay.date().equals(newDay.date())) changedDates++;
      }
      int addedDays = 0;
      for (RelvenPlanImporter.Session newDay : newDays) {
        boolean found = false;
        for (RelvenPlanImporter.Session oldDay : oldDays) {
          if (oldDay.id().equals(newDay.id())) {
            found = true;
            break;
          }
        }
        if (!found) addedDays++;
      }
      boolean oneReschedule =
          sameWorkouts
              && previous.name().equals(revision.name())
              && oldDays.size() == newDays.size()
              && changedDates == 1
              && removedDays == 0;
      boolean oneAddition =
          sameWorkouts
              && previous.name().equals(revision.name())
              && newDays.size() == oldDays.size() + 1
              && addedDays == 1
              && changedDates == 0
              && removedDays == 0;
      boolean oneRemoval =
          sameWorkouts
              && previous.name().equals(revision.name())
              && oldDays.size() == newDays.size() + 1
              && removedDays == 1
              && addedDays == 0
              && changedDates == 0;
      boolean nameOnly =
          sameWorkouts
              && !previous.name().equals(revision.name())
              && revision.name().equals(RelvenPlanRevision.normalizePlanName(revision.name()))
              && oldDays.size() == newDays.size()
              && addedDays == 0
              && removedDays == 0
              && changedDates == 0;
      boolean workoutNameOnly =
          previous.name().equals(revision.name())
              && !sameWorkouts
              && oneWorkoutNameChange
              && oldDays.size() == newDays.size()
              && addedDays == 0
              && removedDays == 0
              && changedDates == 0;
      if (!oneReschedule && !oneAddition && !oneRemoval && !nameOnly && !workoutNameOnly) {
        throw new IOException("Plan revision must contain exactly one supported edit");
      }
    } catch (JSONException error) {
      throw new IOException("Plan revision snapshot is invalid", error);
    }
  }

  private static boolean sameWorkoutsExceptOneName(JSONObject previous, JSONObject revision)
      throws JSONException {
    JSONArray oldWorkouts = previous.getJSONArray("workouts");
    JSONArray newWorkouts = revision.getJSONArray("workouts");
    if (oldWorkouts.length() != newWorkouts.length()) return false;
    int changedNames = 0;
    for (int index = 0; index < oldWorkouts.length(); index++) {
      JSONObject oldWorkout = new JSONObject(oldWorkouts.getJSONObject(index).toString());
      JSONObject newWorkout = new JSONObject(newWorkouts.getJSONObject(index).toString());
      String oldName = oldWorkout.getString("name");
      String newName = newWorkout.getString("name");
      oldWorkout.remove("name");
      newWorkout.remove("name");
      if (!canonicalJson(oldWorkout).equals(canonicalJson(newWorkout))) return false;
      if (!oldName.equals(newName)) {
        changedNames++;
        if (newName.isBlank() || newName.length() > 160) return false;
      }
    }
    return changedNames == 1;
  }

  private static String canonicalJson(Object value) throws JSONException {
    if (value instanceof JSONObject object) {
      List<String> keys = new ArrayList<>();
      for (java.util.Iterator<String> iterator = object.keys(); iterator.hasNext(); ) {
        keys.add(iterator.next());
      }
      Collections.sort(keys);
      JSONObject canonical = new JSONObject();
      for (String key : keys) canonical.put(key, canonicalValue(object.get(key)));
      return canonical.toString();
    }
    if (value instanceof JSONArray array) {
      JSONArray canonical = new JSONArray();
      for (int index = 0; index < array.length(); index++) {
        canonical.put(canonicalValue(array.get(index)));
      }
      return canonical.toString();
    }
    if (value instanceof String string) return JSONObject.quote(string);
    return value.toString();
  }

  private static Object canonicalValue(Object value) throws JSONException {
    if (value instanceof JSONObject object) return new JSONObject(canonicalJson(object));
    if (value instanceof JSONArray array) return new JSONArray(canonicalJson(array));
    return value;
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
