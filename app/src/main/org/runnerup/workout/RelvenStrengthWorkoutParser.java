package org.runnerup.workout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Parses offline strength/prehab details without creating RunnerUp execution objects. */
public final class RelvenStrengthWorkoutParser {
  private static final int MAX_NODES = 256;
  private static final int MAX_DEPTH = 16;
  private static final long MAX_EXPANDED_EXERCISES = 10_000;
  private static final Pattern UUID =
      Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

  public record Exercise(
      String stepId,
      String exerciseId,
      int libraryRevision,
      int exerciseRevision,
      String name,
      String instructions,
      int sets,
      int minRepetitions,
      int maxRepetitions,
      int restSeconds,
      Integer durationSeconds) {}

  public record Workout(
      String id, String name, String sport, List<Exercise> exercises, String sourceJson) {}

  private RelvenStrengthWorkoutParser() {}

  public static Workout parse(String json) {
    return parse(json, null);
  }

  public static Workout parse(String json, java.util.Map<String, RelvenExerciseReference> catalog) {
    try {
      RelvenJsonLimits.validate(json);
      JSONObject root = new JSONObject(json);
      allowOnly(
          root, "schemaVersion", "id", "name", "sport", "warmupExempt", "cooldownExempt", "steps");
      if (integer(root, "schemaVersion") != 3) throw invalid("workout schema version");
      String sport = text(root, "sport", 1, 16);
      if (!sport.equals("strength") && !sport.equals("prehab")) throw invalid("workout sport");
      validateOptionalBoolean(root, "warmupExempt");
      validateOptionalBoolean(root, "cooldownExempt");

      ParseState state = new ParseState();
      List<Exercise> exercises = parseNodes(root.getJSONArray("steps"), 0, 1, state, catalog);
      if (exercises.isEmpty()) throw invalid("no exercises");
      return new Workout(
          text(root, "id", 1, 160),
          text(root, "name", 1, 160),
          sport,
          List.copyOf(exercises),
          json);
    } catch (JSONException error) {
      throw new IllegalArgumentException("Invalid strength workout JSON", error);
    }
  }

  private static List<Exercise> parseNodes(
      JSONArray nodes,
      int depth,
      int multiplier,
      ParseState state,
      java.util.Map<String, RelvenExerciseReference> catalog)
      throws JSONException {
    if (depth > MAX_DEPTH || nodes.length() == 0 || multiplier > MAX_EXPANDED_EXERCISES) {
      throw invalid("strength workout nesting or expansion limit");
    }
    List<Exercise> result = new ArrayList<>();
    for (int index = 0; index < nodes.length(); index++) {
      if (++state.nodeCount > MAX_NODES) throw invalid("strength workout node limit");
      JSONObject node = nodes.getJSONObject(index);
      String kind = text(node, "kind", 1, 16);
      String stepId = text(node, "id", 1, 160);
      if (!state.stepIds.add(stepId)) throw invalid("duplicate step ID");
      switch (kind) {
        case "segment" -> {
          allowOnly(node, "id", "kind", "name", "phase", "end", "target", "exercise");
          String phase = text(node, "phase", 1, 16);
          if (!Set.of("warmup", "work", "recovery", "cooldown").contains(phase)) {
            throw invalid("strength step phase");
          }
          if (node.has("name")) text(node, "name", 1, 160);
          if (node.has("target") && !node.isNull("target")) throw invalid("strength target");
          Integer duration = parseEnd(node.getJSONObject("end"));
          Exercise exercise =
              parseExercise(node.getJSONObject("exercise"), stepId, duration, catalog);
          for (int copy = 0; copy < multiplier; copy++) {
            if (++state.expandedExercises > MAX_EXPANDED_EXERCISES) {
              throw invalid("strength workout expansion limit");
            }
            result.add(exercise);
          }
        }
        case "sequence" -> {
          allowOnly(node, "id", "kind", "name", "steps");
          if (node.has("name")) text(node, "name", 1, 160);
          result.addAll(
              parseNodes(node.getJSONArray("steps"), depth + 1, multiplier, state, catalog));
        }
        case "repeat" -> {
          allowOnly(node, "id", "kind", "name", "count", "steps");
          if (node.has("name")) text(node, "name", 1, 160);
          int count = integer(node, "count");
          if (count < 1 || count > 100 || (long) multiplier * count > MAX_EXPANDED_EXERCISES) {
            throw invalid("strength repeat count");
          }
          result.addAll(
              parseNodes(
                  node.getJSONArray("steps"), depth + 1, multiplier * count, state, catalog));
        }
        default -> throw invalid("strength step kind");
      }
    }
    return result;
  }

  private static Exercise parseExercise(
      JSONObject exercise,
      String stepId,
      Integer duration,
      java.util.Map<String, RelvenExerciseReference> catalog)
      throws JSONException {
    allowOnly(
        exercise,
        "exerciseId",
        "libraryRevision",
        "exerciseRevision",
        "snapshot",
        "sets",
        "minRepetitions",
        "maxRepetitions",
        "restSeconds");
    String exerciseId = text(exercise, "exerciseId", 36, 36);
    if (!UUID.matcher(exerciseId).matches()) throw invalid("exercise UUID");
    RelvenExerciseReference reference = catalog == null ? null : catalog.get(exerciseId);
    if (catalog != null && reference == null) {
      throw invalid("unknown exercise ID");
    }
    int libraryRevision = integer(exercise, "libraryRevision");
    int exerciseRevision = integer(exercise, "exerciseRevision");
    int sets = integer(exercise, "sets");
    int minRepetitions = integer(exercise, "minRepetitions");
    int maxRepetitions = integer(exercise, "maxRepetitions");
    int restSeconds = integer(exercise, "restSeconds");
    if (libraryRevision < 1
        || exerciseRevision < 1
        || sets < 1
        || sets > 100
        || minRepetitions < 1
        || maxRepetitions < minRepetitions
        || maxRepetitions > 500
        || restSeconds < 0
        || restSeconds > 3600) {
      throw invalid("exercise prescription");
    }
    JSONObject snapshot = exercise.getJSONObject("snapshot");
    allowOnly(snapshot, "name", "instructions", "equipment", "images");
    String name = text(snapshot, "name", 1, 120);
    String instructions = text(snapshot, "instructions", 1, 1000);
    JSONArray equipment = snapshot.getJSONArray("equipment");
    if (equipment.length() != 0) throw invalid("strength equipment is not supported");
    if (reference != null
        && (libraryRevision > reference.getLibraryRevision()
            || reference.getExerciseRevision() != exerciseRevision
            || !reference.getName().equals(name)
            || !reference.getInstructions().equals(instructions)
            || !reference.getEquipment().equals(List.of()))) {
      throw invalid("exercise snapshot does not match its canonical exercise revision");
    }
    validateImages(snapshot.getJSONArray("images"));
    return new Exercise(
        stepId,
        exerciseId,
        libraryRevision,
        exerciseRevision,
        name,
        instructions,
        sets,
        minRepetitions,
        maxRepetitions,
        restSeconds,
        duration);
  }

  private static Integer parseEnd(JSONObject end) throws JSONException {
    String type = text(end, "type", 1, 16);
    return switch (type) {
      case "manual" -> {
        allowOnly(end, "type");
        yield null;
      }
      case "time" -> {
        allowOnly(end, "type", "seconds");
        int seconds = integer(end, "seconds");
        if (seconds < 1 || seconds > 86_400) throw invalid("strength duration");
        yield seconds;
      }
      default -> throw invalid("unsupported strength end condition");
    };
  }

  private static void validateImages(JSONArray images) throws JSONException {
    if (images.length() > 8) throw invalid("exercise images");
    for (int index = 0; index < images.length(); index++) {
      JSONObject image = images.getJSONObject(index);
      allowOnly(image, "assetId", "role");
      String assetId = text(image, "assetId", 36, 36);
      if (!UUID.matcher(assetId).matches()) throw invalid("image asset UUID");
      String role = text(image, "role", 1, 32);
      if (!Set.of("start", "finish", "demonstration").contains(role)) {
        throw invalid("image role");
      }
    }
  }

  private static void validateOptionalBoolean(JSONObject object, String field)
      throws JSONException {
    if (object.has(field) && !(object.get(field) instanceof Boolean)) throw invalid(field);
  }

  private static void allowOnly(JSONObject object, String... allowed) throws JSONException {
    Set<String> keys = Set.of(allowed);
    for (java.util.Iterator<String> iterator = object.keys(); iterator.hasNext(); ) {
      if (!keys.contains(iterator.next())) throw invalid("unknown field");
    }
  }

  private static String text(JSONObject object, String key, int min, int max) throws JSONException {
    Object raw = object.get(key);
    if (!(raw instanceof String value)
        || value.isBlank()
        || value.length() < min
        || value.length() > max) {
      throw invalid(key);
    }
    return value;
  }

  private static int integer(JSONObject object, String key) throws JSONException {
    Object raw = object.get(key);
    if (!(raw instanceof Number number) || number.doubleValue() != number.intValue()) {
      throw invalid(key);
    }
    return number.intValue();
  }

  private static IllegalArgumentException invalid(String message) {
    return new IllegalArgumentException(message);
  }

  private static final class ParseState {
    private final Set<String> stepIds = new HashSet<>();
    private int nodeCount;
    private long expandedExercises;
  }
}
