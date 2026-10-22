package org.runnerup.relven.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.runnerup.workout.RelvenExerciseReference

internal enum class PlanWorkoutType {
  Easy,
  Intervals,
  LongRun,
  Strength,
  Prehab
}

internal data class PlanDraftSession(val dayOfWeek: Int, val workoutType: PlanWorkoutType)

internal data class PlanDraftStrengthExercise(
    val exerciseId: String,
    val sets: Int = 3,
    val minRepetitions: Int = 8,
    val maxRepetitions: Int = 12,
    val restSeconds: Int = 60,
    val durationSeconds: Int? = null)

internal data class PlanWorkoutCopy(
    val name: String,
    val warmup: String,
    val easy: String,
    val effort: String,
    val recovery: String,
    val cooldown: String,
    val longRun: String)

/** Creates a schema-v1 running plan from the in-app plan builder fields. */
internal object PlanDraftBuilder {
  private const val MAX_NAME_LENGTH = 80
  private const val MAX_WEEKS = 16

  fun create(
      name: String,
      startDate: String,
      weeks: Int,
      sessions: List<PlanDraftSession>,
      timezone: String,
      nextId: () -> String = { UUID.randomUUID().toString() },
      workoutCopy: Map<PlanWorkoutType, PlanWorkoutCopy> = defaultWorkoutCopy(),
      strengthExercises: List<PlanDraftStrengthExercise> = emptyList(),
      exerciseReferences: Map<String, RelvenExerciseReference> = emptyMap()): String {
    val normalizedName = name.trim()
    require(normalizedName.isNotEmpty() && normalizedName.length <= MAX_NAME_LENGTH) {
      "Plan name must contain 1–$MAX_NAME_LENGTH characters"
    }
    require(weeks in 1..MAX_WEEKS) { "Plan duration must be between 1 and $MAX_WEEKS weeks" }
    require(sessions.isNotEmpty()) { "Choose at least one training day" }
    require(sessions.size <= 7 && sessions.all { it.dayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY }) {
      "Training days must be unique weekdays"
    }
    require(sessions.map { it.dayOfWeek }.distinct().size == sessions.size) {
      "Choose each weekday at most once"
    }
    val usesStrengthExercises = sessions.any {
      it.workoutType == PlanWorkoutType.Strength || it.workoutType == PlanWorkoutType.Prehab
    }
    val authoredExercises = if (usesStrengthExercises) strengthExercises else emptyList()
    if (usesStrengthExercises) {
      require(strengthExercises.isNotEmpty()) { "Choose at least one strength exercise" }
    }
    require(authoredExercises.map { it.exerciseId }.distinct().size == authoredExercises.size) {
      "Choose each strength exercise at most once"
    }
    authoredExercises.forEach { exercise ->
      require(exercise.sets in 1..100 && exercise.minRepetitions in 1..500 &&
          exercise.maxRepetitions in exercise.minRepetitions..500 && exercise.restSeconds in 0..3600 &&
          (exercise.durationSeconds?.let { it in 1..86_400 } ?: true)) {
        "Invalid strength exercise prescription"
      }
      val reference = exerciseReferences[exercise.exerciseId]
          ?: throw IllegalArgumentException("Unknown strength exercise")
      require(reference.libraryRevision > 0 && reference.exerciseRevision > 0 &&
          reference.equipment.isEmpty() && reference.name.isNotBlank() && reference.instructions.isNotBlank()) {
        "Strength exercises must use a valid no-equipment catalog reference"
      }
    }
    require(TimeZone.getAvailableIDs().contains(timezone)) { "Choose a valid timezone" }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
      isLenient = false
      timeZone = TimeZone.getTimeZone(timezone)
    }
    require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(startDate)) { "Choose a valid start date" }
    val start = try {
      dateFormat.parse(startDate)
    } catch (error: java.text.ParseException) {
      throw IllegalArgumentException("Choose a valid start date", error)
    } ?: throw IllegalArgumentException("Choose a valid start date")
    require(dateFormat.format(start) == startDate) { "Choose a valid start date" }

    val ids = mutableSetOf<String>()
    fun id(): String = nextId().also {
      require(it.isNotBlank() && it.length <= 160 && ids.add(it)) { "Generated identifiers must be unique" }
    }

    val planId = id()
    val workoutIds = linkedMapOf<PlanWorkoutType, String>()
    sessions.map { it.workoutType }.distinct().forEach { type -> workoutIds[type] = id() }
    val workouts = JSONArray()
    workoutIds.forEach { (type, workoutId) ->
      workouts.put(
          if (type == PlanWorkoutType.Strength || type == PlanWorkoutType.Prehab) {
            strengthWorkoutDocument(
                workoutId, type, workoutCopy.getValue(type), authoredExercises, exerciseReferences, ::id)
          } else {
            workoutDocument(workoutId, type, workoutCopy.getValue(type), ::id)
          })
    }

    val days = JSONArray()
    val startCalendar = Calendar.getInstance(TimeZone.getTimeZone(timezone), Locale.ROOT).apply {
      time = start
      set(Calendar.HOUR_OF_DAY, 12)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val scheduleEnd = startCalendar.clone() as Calendar
    scheduleEnd.add(Calendar.DAY_OF_YEAR, weeks * 7)
    sessions.sortedBy { it.dayOfWeek }.forEach { session ->
      val first = startCalendar.clone() as Calendar
      val offset = (session.dayOfWeek - first.get(Calendar.DAY_OF_WEEK) + 7) % 7
      first.add(Calendar.DAY_OF_YEAR, offset)
      var date = first
      while (date.before(scheduleEnd)) {
        days.put(
            JSONObject()
                .put("id", id())
                .put("date", dateFormat.format(date.time))
                .put("kind", "workout")
                .put("workoutId", workoutIds.getValue(session.workoutType)))
        date = (date.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
      }
    }

    return JSONObject()
        .put("format", "relven.plan")
        .put("schemaVersion", 1)
        .put("id", planId)
        .put("name", normalizedName)
        .put("timezone", timezone)
        .put("workouts", workouts)
        .put("days", days)
        .toString()
  }

  private fun workoutDocument(
      workoutId: String,
      type: PlanWorkoutType,
      copy: PlanWorkoutCopy,
      nextId: () -> String): JSONObject {
    val steps = JSONArray()
    fun segment(name: String, phase: String, seconds: Int) =
        JSONObject()
            .put("id", nextId())
            .put("kind", "segment")
            .put("name", name)
            .put("phase", phase)
            .put("end", JSONObject().put("type", "time").put("seconds", seconds))
    when (type) {
      PlanWorkoutType.Easy -> steps.put(segment(copy.easy, "work", 1_800))
      PlanWorkoutType.Intervals -> {
        steps.put(segment(copy.warmup, "warmup", 600))
        steps.put(
            JSONObject()
                .put("id", nextId())
                .put("kind", "repeat")
                .put("name", copy.name)
                .put("count", 4)
                .put(
                    "steps",
                    JSONArray()
                        .put(segment(copy.effort, "work", 240))
                        .put(segment(copy.recovery, "recovery", 180))))
        steps.put(segment(copy.cooldown, "cooldown", 300))
      }
      PlanWorkoutType.LongRun -> steps.put(segment(copy.longRun, "work", 3_000))
      PlanWorkoutType.Strength, PlanWorkoutType.Prehab -> error("Strength workout needs catalog exercises")
    }
    return JSONObject()
        .put("schemaVersion", 3)
        .put("id", workoutId)
        .put("name", copy.name)
        .put("sport", "running")
        .put("steps", steps)
  }

  private fun strengthWorkoutDocument(
      workoutId: String,
      type: PlanWorkoutType,
      copy: PlanWorkoutCopy,
      exercises: List<PlanDraftStrengthExercise>,
      references: Map<String, RelvenExerciseReference>,
      nextId: () -> String
  ): JSONObject {
    val steps = JSONArray()
    exercises.forEach { prescription ->
      val reference = references.getValue(prescription.exerciseId)
      steps.put(
          JSONObject()
              .put("id", nextId())
              .put("kind", "segment")
              .put("name", reference.name)
              .put("phase", "work")
              .put("end", prescription.durationSeconds?.let {
                JSONObject().put("type", "time").put("seconds", it)
              } ?: JSONObject().put("type", "manual"))
              .put(
                  "exercise",
                  JSONObject()
                      .put("exerciseId", prescription.exerciseId)
                      .put("libraryRevision", reference.libraryRevision)
                      .put("exerciseRevision", reference.exerciseRevision)
                      .put(
                          "snapshot",
                          JSONObject()
                              .put("name", reference.name)
                              .put("instructions", reference.instructions)
                              .put("equipment", JSONArray())
                              .put("images", JSONArray()))
                      .put("sets", prescription.sets)
                      .put("minRepetitions", prescription.minRepetitions)
                      .put("maxRepetitions", prescription.maxRepetitions)
                      .put("restSeconds", prescription.restSeconds)))
    }
    return JSONObject()
        .put("schemaVersion", 3)
        .put("id", workoutId)
        .put("name", copy.name)
        .put("sport", if (type == PlanWorkoutType.Prehab) "prehab" else "strength")
        .put("steps", steps)
  }

  private fun defaultWorkoutCopy() = mapOf(
      PlanWorkoutType.Easy to PlanWorkoutCopy(
          "Easy run · 30 min", "Warm up", "Easy running", "Fast interval", "Recovery jog", "Cool down", "Long easy run"),
      PlanWorkoutType.Intervals to PlanWorkoutCopy(
          "Intervals · 4 × 4 min", "Warm up", "Easy running", "Fast interval", "Recovery jog", "Cool down", "Long easy run"),
      PlanWorkoutType.LongRun to PlanWorkoutCopy(
          "Long run · 50 min", "Warm up", "Easy running", "Fast interval", "Recovery jog", "Cool down", "Long easy run"),
      PlanWorkoutType.Strength to PlanWorkoutCopy(
          "Bodyweight strength", "Warm up", "Easy running", "Fast interval", "Recovery jog", "Cool down", "Long easy run"),
      PlanWorkoutType.Prehab to PlanWorkoutCopy(
          "Prehab", "Warm up", "Easy running", "Fast interval", "Recovery jog", "Cool down", "Long easy run"))
}
