package org.runnerup.relven.exercise

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.runnerup.relven.ui.PlanDraftBuilder
import org.runnerup.relven.ui.PlanDraftSession
import org.runnerup.relven.ui.PlanDraftStrengthExercise
import org.runnerup.relven.ui.PlanWorkoutType
import org.runnerup.workout.RelvenStrengthWorkoutParser
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExerciseCatalogRepositoryTest {
  private val context: Context = RuntimeEnvironment.getApplication()
  private val customDirectory = File(context.filesDir, "relven")
  private var temporaryFile: File? = null

  @Before
  fun clearCustomCatalog() {
    customDirectory.deleteRecursively()
  }

  @After
  fun removeTemporaryFiles() {
    customDirectory.deleteRecursively()
    temporaryFile?.delete()
  }

  @Test
  fun repositoryRoundTripsOnlyCustomExercisesAndRetainsArchiveRevision() {
    val repository = ExerciseCatalogRepository(context)
    val id = repository.createCustomExercise(validInput())
    repository.updateCustomExercise(id, validInput().copy(nameEnglish = "Wall squat"))
    repository.setCustomExerciseArchived(id, true)
    val exported = repository.exportCustomExercises()
    val file = File(customDirectory, "custom-exercises.json")

    val collection = JSONObject(String(exported, StandardCharsets.UTF_8))
    assertEquals(1, collection.getJSONArray("exercises").length())
    customDirectory.deleteRecursively()
    val imported = repository.importCustomExercises(exported)
    val entry = repository.load("sv").exercises.single { it.exerciseId == id }

    assertEquals(1, imported.addedCount)
    assertEquals("Väggsittning", entry.name)
    assertEquals(2, entry.revision)
    assertTrue(entry.isArchived)
    assertTrue(file.isFile)
  }

  @Test
  fun repositoryRejectsBundledExerciseIdsWithoutCreatingCustomStore() {
    val bundledId =
        context.assets.open("relven/exercise-library-v1.json").bufferedReader().use { reader ->
          ExerciseCatalog.parse(reader.readText()).exercises.first().exerciseId
        }
    temporaryFile = File(context.cacheDir, "exercise-collection-$bundledId.json")
    val temporary = CustomExerciseStore(requireNotNull(temporaryFile))
    temporary.create(validInput())
    val root = JSONObject(String(temporary.exportCollection(), StandardCharsets.UTF_8))
    val record = root.getJSONArray("exercises").getJSONObject(0)
    record.put("exerciseId", bundledId)
    val bundledCollection =
        JSONObject()
            .put("format", "relven.exercise-collection")
            .put("schemaVersion", 1)
            .put("exercises", JSONArray().put(record))
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

    try {
      ExerciseCatalogRepository(context).importCustomExercises(bundledCollection)
      throw AssertionError("Expected bundled ID collision")
    } catch (_: IllegalArgumentException) {
      assertFalse(File(customDirectory, "custom-exercises.json").exists())
    }
  }

  @Test
  fun expandedCatalogLoadsOfflineSearchesSwedishAndPinsV3Snapshots() {
    val repository = ExerciseCatalogRepository(context)
    val swedish = repository.load("sv-SE")
    val english = repository.load("en-US")

    assertEquals(116, swedish.exercises.size)
    assertTrue(swedish.exercises.all { it.equipment.isEmpty() })
    assertTrue(
        swedish.filter("tåhävning", ExerciseCategory.LOWER_BODY)
            .any { it.name == "Tåhävning med böjt knä" })

    val selected = english.exercises.single { it.name == "Single-leg glute bridge" }
    val references = repository.exerciseReferences()
    val json =
        PlanDraftBuilder.create(
            name = "Snapshot compatibility check",
            startDate = "2026-01-05",
            weeks = 1,
            sessions = listOf(PlanDraftSession(Calendar.MONDAY, PlanWorkoutType.Strength)),
            timezone = "Europe/Stockholm",
            strengthExercises = listOf(PlanDraftStrengthExercise(selected.exerciseId)),
            exerciseReferences = references)
    val workout = JSONObject(json).getJSONArray("workouts").getJSONObject(0)
    val prescription =
        workout.getJSONArray("steps").getJSONObject(0).getJSONObject("exercise")
    val snapshot = prescription.getJSONObject("snapshot")
    assertEquals(selected.exerciseId, prescription.getString("exerciseId"))
    assertEquals(2, prescription.getInt("libraryRevision"))
    assertEquals(1, prescription.getInt("exerciseRevision"))
    assertEquals(selected.name, snapshot.getString("name"))
    assertEquals(selected.instructions.joinToString("\n"), snapshot.getString("instructions"))
    assertEquals(0, snapshot.getJSONArray("equipment").length())
    assertEquals(0, snapshot.getJSONArray("images").length())
    assertEquals(1, RelvenStrengthWorkoutParser.parse(workout.toString(), references).exercises().size)
  }

  private fun validInput() =
      CustomExerciseInput(
          nameEnglish = "Wall sit",
          nameSwedish = "Väggsittning",
          category = ExerciseCategory.LOWER_BODY,
          movementPattern = "isometric_squat",
          muscleGroups = listOf("quadriceps", "glutes"),
          equipment = listOf("wall"),
          instructionsEnglish = listOf("Lean against a wall.", "Hold the seated position."),
          instructionsSwedish = listOf("Luta dig mot en vägg.", "Håll sittpositionen."))
}
