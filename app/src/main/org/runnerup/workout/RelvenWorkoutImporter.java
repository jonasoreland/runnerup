package org.runnerup.workout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants;

/** Converts the executable subset of Relven v3 running plans to the existing workout engine. */
public final class RelvenWorkoutImporter {
  private static final int MAX_NODES = 256;
  private static final int MAX_DEPTH = 16;
  private static final long MAX_EXPANDED_STEPS = 10_000;

  private RelvenWorkoutImporter() {}

  public static Workout parse(String json) {
    try {
      JSONObject document = new JSONObject(json);
      if (document.getInt("schemaVersion") != 3) throw unsupported("schema version");
      if (!"running".equals(document.getString("sport"))) throw unsupported("sport");
      requireName(document, "id");
      requireName(document, "name");
      JSONArray nodes = document.getJSONArray("steps");
      if (nodes.length() == 0 || nodes.length() > MAX_NODES) throw unsupported("step count");

      ParseState state = new ParseState();
      List<Step> steps = parseNodes(nodes, state, 0, 1);
      Workout workout = new Workout();
      workout.sport = Constants.DB.ACTIVITY.SPORT_RUNNING;
      workout.setWorkoutType(Constants.WORKOUT_TYPE.ADVANCED);
      for (Step step : steps) workout.addStep(step);
      return workout;
    } catch (JSONException error) {
      throw new IllegalArgumentException("Invalid Relven workout JSON", error);
    }
  }

  private static List<Step> parseNodes(
      JSONArray nodes, ParseState state, int depth, long repeatMultiplier) throws JSONException {
    if (depth > MAX_DEPTH || nodes.length() == 0) throw unsupported("nesting or empty steps");
    List<Step> result = new ArrayList<>();
    for (int i = 0; i < nodes.length(); i++) {
      if (++state.nodeCount > MAX_NODES) throw unsupported("step count");
      JSONObject node = nodes.getJSONObject(i);
      String id = requireName(node, "id");
      if (!state.ids.add(id)) throw unsupported("duplicate step id: " + id);
      switch (node.getString("kind")) {
        case "segment":
          if (repeatMultiplier > MAX_EXPANDED_STEPS - state.expandedStepCount) {
            throw unsupported("expanded step count");
          }
          state.expandedStepCount += repeatMultiplier;
          result.add(parseSegment(node));
          break;
        case "sequence":
          result.addAll(parseNodes(node.getJSONArray("steps"), state, depth + 1, repeatMultiplier));
          break;
        case "repeat":
          int count = node.getInt("count");
          if (count < 1 || count > 100) throw unsupported("repeat count");
          if (repeatMultiplier > MAX_EXPANDED_STEPS / count) {
            throw unsupported("expanded step count");
          }
          RepeatStep repeat = new RepeatStep();
          repeat.setRepeatCount(count);
          repeat
              .getSteps()
              .addAll(
                  parseNodes(
                      node.getJSONArray("steps"), state, depth + 1, repeatMultiplier * count));
          result.add(repeat);
          break;
        default:
          throw unsupported("step kind");
      }
    }
    return result;
  }

  private static Step parseSegment(JSONObject node) throws JSONException {
    if (node.has("exercise")) throw unsupported("strength exercise");
    Step step = new Step();
    if (node.has("name") && !node.isNull("name")) step.setName(requireName(node, "name"));
    switch (node.getString("phase")) {
      case "warmup":
        step.setIntensity(Intensity.WARMUP);
        break;
      case "work":
        step.setIntensity(Intensity.ACTIVE);
        break;
      case "recovery":
        step.setIntensity(Intensity.RECOVERY);
        break;
      case "cooldown":
        step.setIntensity(Intensity.COOLDOWN);
        break;
      default:
        throw unsupported("phase");
    }

    JSONObject end = node.getJSONObject("end");
    switch (end.getString("type")) {
      case "time":
        step.setDurationType(Dimension.TIME);
        step.setDurationValue(positive(end, "seconds"));
        break;
      case "distance":
        step.setDurationType(Dimension.DISTANCE);
        step.setDurationValue(positive(end, "meters"));
        break;
      case "manual":
        step.setDurationType(null);
        break;
      default:
        throw unsupported("end condition: " + end.getString("type"));
    }

    if (node.has("target") && !node.isNull("target")) {
      JSONObject target = node.getJSONObject("target");
      double min;
      double max;
      switch (target.getString("type")) {
        case "pace":
          step.setTargetType(Dimension.PACE);
          min = positive(target, "minSecondsPerKm") / 1000;
          max = positive(target, "maxSecondsPerKm") / 1000;
          break;
        case "speed":
          step.setTargetType(Dimension.SPEED);
          min = positive(target, "minMetersPerSecond");
          max = positive(target, "maxMetersPerSecond");
          break;
        case "heart_rate":
          step.setTargetType(Dimension.HR);
          min = target.getDouble("minBpm");
          max = target.getDouble("maxBpm");
          if (min < 30 || max > 240) throw unsupported("heart rate target");
          break;
        default:
          throw unsupported("target: " + target.getString("type"));
      }
      if (min > max) throw unsupported("reversed target range");
      step.setTargetValue(min, max);
    }
    return step;
  }

  private static double positive(JSONObject object, String key) throws JSONException {
    double value = object.getDouble(key);
    if (!Double.isFinite(value) || value <= 0 || value > 1_000_000_000) {
      throw unsupported(key);
    }
    return value;
  }

  private static String requireName(JSONObject object, String key) throws JSONException {
    String value = object.getString(key);
    if (value.isBlank() || value.length() > 160) throw unsupported(key);
    return value;
  }

  private static IllegalArgumentException unsupported(String field) {
    return new IllegalArgumentException("Unsupported Relven workout " + field);
  }

  private static final class ParseState {
    int nodeCount;
    long expandedStepCount;
    final Set<String> ids = new HashSet<>();
  }
}
