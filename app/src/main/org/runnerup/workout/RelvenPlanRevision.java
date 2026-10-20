package org.runnerup.workout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Produces an immutable full-package snapshot when a scheduled session is moved. */
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

  private static String findDate(RelvenPlanImporter.Plan plan, String sessionId) {
    for (RelvenPlanImporter.Session day : plan.days()) {
      if (day.id().equals(sessionId)) return day.date();
    }
    throw new IllegalArgumentException("Unknown plan session");
  }
}
