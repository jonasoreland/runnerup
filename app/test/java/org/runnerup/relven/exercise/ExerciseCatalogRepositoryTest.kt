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
