package org.runnerup.workout;

import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Produces immutable full-package snapshots for active-plan schedule changes. */
public final class RelvenPlanRevision {
  private RelvenPlanRevision() {}

  public static RelvenPlanImporter.Plan reschedule(
      RelvenPlanImporter.Plan source, String sessionId, String targetDate, String revisionId) {
    return reschedule(
        source,
        sessionId,
        targetDate,
        revisionId,
        source == null ? 0 : source.revisionNumber() + 1);
  }

  static RelvenPlanImporter.Plan reschedule(
      RelvenPlanImporter.Plan source,
      String sessionId,
      String targetDate,
      String revisionId,
      int revisionNumber) {
    if (source == null || sessionId == null || targetDate == null || revisionId == null) {
      throw new IllegalArgumentException("Missing reschedule input");
    }
    if (revisionNumber <= source.revisionNumber()) {
      throw new IllegalArgumentException("Revision number must increase");
    }
    if (targetDate.equals(findDate(source, sessionId))) {
      throw new IllegalArgumentException("Session is already scheduled on that date");
    }

    try {
      JSONObject root = new JSONObject(source.sourceJson());
      JSONArray days = root.getJSONArray("days");
      boolean found = false;
      for (int index = 0; index < days.length(); index++) {
        JSONObject day = days.getJSONObject(index);
        if (!sessionId.equals(day.getString("id"))) continue;
        day.put("date", targetDate);
        found = true;
        break;
      }
      if (!found) throw new IllegalArgumentException("Unknown plan session");

      root.put("schemaVersion", 2);
      root.put("id", revisionId);
      JSONObject revision = new JSONObject();
      revision.put("rootPlanId", source.rootPlanId());
      revision.put("number", revisionNumber);
      revision.put("previousRevisionId", source.id());
      root.put("revision", revision);
      return RelvenPlanImporter.parse(root.toString());
    } catch (JSONException error) {
      throw new IllegalArgumentException("Invalid source plan", error);
    }
  }

  public static RelvenPlanImporter.Plan addWorkout(
      RelvenPlanImporter.Plan source,
      String date,
      String workoutId,
      String sessionId,
      String revisionId) {
    return addSession(
        source,
        date,
        "workout",
        workoutId,
        sessionId,
        revisionId,
        source == null ? 0 : source.revisionNumber() + 1);
  }

  static RelvenPlanImporter.Plan addWorkout(
      RelvenPlanImporter.Plan source,
      String date,
      String workoutId,
      String sessionId,
      String revisionId,
      int revisionNumber) {
    return addSession(source, date, "workout", workoutId, sessionId, revisionId, revisionNumber);
  }

  public static RelvenPlanImporter.Plan addRest(
      RelvenPlanImporter.Plan source, String date, String sessionId, String revisionId) {
    return addSession(
        source,
        date,
        "rest",
        null,
        sessionId,
        revisionId,
        source == null ? 0 : source.revisionNumber() + 1);
  }

  static RelvenPlanImporter.Plan addRest(
      RelvenPlanImporter.Plan source,
      String date,
      String sessionId,
      String revisionId,
      int revisionNumber) {
    return addSession(source, date, "rest", null, sessionId, revisionId, revisionNumber);
  }

  public static RelvenPlanImporter.Plan rename(
      RelvenPlanImporter.Plan source, String name, String revisionId) {
    return rename(source, name, revisionId, source == null ? 0 : source.revisionNumber() + 1);
  }

  static RelvenPlanImporter.Plan rename(
      RelvenPlanImporter.Plan source, String name, String revisionId, int revisionNumber) {
    if (source == null || revisionId == null || name == null) {
      throw new IllegalArgumentException("Missing rename input");
    }
    if (revisionNumber <= source.revisionNumber()) {
      throw new IllegalArgumentException("Revision number must increase");
    }
    String normalizedName = normalizePlanName(name);
    if (normalizedName.isEmpty() || normalizedName.length() > 160) {
      throw new IllegalArgumentException("Invalid plan name");
    }
    if (normalizedName.equals(source.name())) {
      throw new IllegalArgumentException("Plan name is unchanged");
    }
    try {
      JSONObject root = revisionRoot(source, revisionId, revisionNumber);
      root.put("name", normalizedName);
      return RelvenPlanImporter.parse(root.toString());
    } catch (JSONException error) {
      throw new IllegalArgumentException("Invalid source plan", error);
    }
  }

  public static RelvenPlanImporter.Plan renameWorkout(
      RelvenPlanImporter.Plan source, String workoutId, String name, String revisionId) {
    return renameWorkout(
        source, workoutId, name, revisionId, source == null ? 0 : source.revisionNumber() + 1);
  }

  static RelvenPlanImporter.Plan renameWorkout(
      RelvenPlanImporter.Plan source,
      String workoutId,
      String name,
      String revisionId,
      int revisionNumber) {
    requireRevisionInput(source, workoutId, revisionId, revisionNumber);
    if (name == null) throw new IllegalArgumentException("Missing workout name");
    String normalizedName = normalizePlanName(name);
    if (normalizedName.isEmpty() || normalizedName.length() > 160) {
      throw new IllegalArgumentException("Invalid workout name");
    }
    String currentName = source.workoutNames().get(workoutId);
    if (currentName == null) throw new IllegalArgumentException("Unknown workout");
    if (normalizedName.equals(currentName)) {
      throw new IllegalArgumentException("Workout name is unchanged");
    }
    try {
      JSONObject root = revisionRoot(source, revisionId, revisionNumber);
      JSONArray workouts = root.getJSONArray("workouts");
      for (int index = 0; index < workouts.length(); index++) {
        JSONObject workout = workouts.getJSONObject(index);
        if (workoutId.equals(workout.getString("id"))) {
          workout.put("name", normalizedName);
          return RelvenPlanImporter.parse(root.toString());
        }
      }
      throw new IllegalArgumentException("Unknown workout");
    } catch (JSONException error) {
      throw new IllegalArgumentException("Invalid source plan", error);
    }
  }

  public static RelvenPlanImporter.Plan remove(
      RelvenPlanImporter.Plan source, String sessionId, String revisionId) {
    return remove(source, sessionId, revisionId, source == null ? 0 : source.revisionNumber() + 1);
  }

  static RelvenPlanImporter.Plan remove(
      RelvenPlanImporter.Plan source, String sessionId, String revisionId, int revisionNumber) {
    requireRevisionInput(source, sessionId, revisionId, revisionNumber);
    if (source.days().size() <= 1)
      throw new IllegalArgumentException("Cannot remove the last entry");
    try {
      JSONObject root = revisionRoot(source, revisionId, revisionNumber);
      JSONArray days = root.getJSONArray("days");
      boolean found = false;
      for (int index = 0; index < days.length(); index++) {
        if (sessionId.equals(days.getJSONObject(index).getString("id"))) {
          days.remove(index);
          found = true;
          break;
        }
      }
      if (!found) throw new IllegalArgumentException("Unknown plan session");
      return RelvenPlanImporter.parse(root.toString());
    } catch (JSONException error) {
      throw new IllegalArgumentException("Invalid source plan", error);
    }
  }

  private static RelvenPlanImporter.Plan addSession(
      RelvenPlanImporter.Plan source,
      String date,
      String kind,
      String workoutId,
      String sessionId,
      String revisionId,
      int revisionNumber) {
    requireRevisionInput(source, sessionId, revisionId, revisionNumber);
    if (source.days().size() >= 2000) throw new IllegalArgumentException("Plan day limit reached");
    if (sessionId.isBlank()) throw new IllegalArgumentException("Missing session ID");
    Set<String> ids = new HashSet<>();
    boolean hasWorkout = false;
    boolean hasRest = false;
    for (RelvenPlanImporter.Session day : source.days()) {
      ids.add(day.id());
      if (day.date().equals(date)) {
        if (day.kind().equals("workout")) hasWorkout = true;
        if (day.kind().equals("rest")) hasRest = true;
      }
    }
    if (!ids.add(sessionId)) throw new IllegalArgumentException("Duplicate session ID");
    if ("workout".equals(kind)) {
      if (!source.workouts().containsKey(workoutId) || hasRest) {
        throw new IllegalArgumentException("Invalid workout entry");
      }
    } else if ("rest".equals(kind)) {
      if (hasWorkout || hasRest)
        throw new IllegalArgumentException("Rest date is already scheduled");
    } else {
      throw new IllegalArgumentException("Invalid schedule entry kind");
    }
    try {
      JSONObject root = revisionRoot(source, revisionId, revisionNumber);
      JSONObject day = new JSONObject();
      day.put("id", sessionId);
      day.put("date", date);
      day.put("kind", kind);
      if (workoutId != null) day.put("workoutId", workoutId);
      root.getJSONArray("days").put(day);
      return RelvenPlanImporter.parse(root.toString());
    } catch (JSONException error) {
      throw new IllegalArgumentException("Invalid source plan", error);
    }
  }

  private static void requireRevisionInput(
      RelvenPlanImporter.Plan source, String sessionId, String revisionId, int revisionNumber) {
    if (source == null || sessionId == null || revisionId == null) {
      throw new IllegalArgumentException("Missing revision input");
    }
    if (revisionNumber <= source.revisionNumber()) {
      throw new IllegalArgumentException("Revision number must increase");
    }
  }

  private static JSONObject revisionRoot(
      RelvenPlanImporter.Plan source, String revisionId, int revisionNumber) throws JSONException {
    JSONObject root = new JSONObject(source.sourceJson());
    root.put("schemaVersion", 2);
    root.put("id", revisionId);
    JSONObject revision = new JSONObject();
    revision.put("rootPlanId", source.rootPlanId());
    revision.put("number", revisionNumber);
    revision.put("previousRevisionId", source.id());
    root.put("revision", revision);
    return root;
  }

  static String normalizePlanName(String name) {
    int start = 0;
    int end = name.length();
    while (start < end && Character.isWhitespace(name.charAt(start))) start++;
    while (end > start && Character.isWhitespace(name.charAt(end - 1))) end--;
    return name.substring(start, end);
  }

  private static String findDate(RelvenPlanImporter.Plan plan, String sessionId) {
    for (RelvenPlanImporter.Session day : plan.days()) {
      if (day.id().equals(sessionId)) return day.date();
    }
    throw new IllegalArgumentException("Unknown plan session");
  }
}
