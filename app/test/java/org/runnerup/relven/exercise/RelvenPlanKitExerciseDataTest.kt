package org.runnerup.relven.exercise

import android.content.Context
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.runnerup.workout.RelvenPlanKit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RelvenPlanKitExerciseDataTest {
  @Test
  fun zipContainsTheCurrentCustomCollectionIncludingArchivedEntries() {
    val context: Context = RuntimeEnvironment.getApplication()
    val repository = ExerciseCatalogRepository(context)
    val activeId = repository.createCustomExercise(customExercise("Custom bird dog"))
    val archivedId = repository.createCustomExercise(customExercise("Archived wall hold"))
    repository.setCustomExerciseArchived(archivedId, true)
    val output = ByteArrayOutputStream()

    RelvenPlanKit.copyArchive(context, output)

    val collectionJson = readEntry(output.toByteArray(), "user-data/custom-exercise-collection-v1.json")
    val collection = JSONObject(collectionJson)
    assertEquals("relven.exercise-collection", collection.getString("format"))
    val records = collection.getJSONArray("exercises")
    val active = (0 until records.length()).map { records.getJSONObject(it) }
        .single { it.getString("exerciseId") == activeId }
    val archived = (0 until records.length()).map { records.getJSONObject(it) }
        .single { it.getString("exerciseId") == archivedId }
    assertEquals("Custom bird dog", active.getString("nameEnglish"))
    assertEquals(false, active.getBoolean("archived"))
    assertEquals("Archived wall hold", archived.getString("nameEnglish"))
    assertTrue(archived.getBoolean("archived"))
    assertEquals("Väggpress", archived.getString("nameSwedish"))
  }

  @Test
  fun copiedPromptEmbedsSchemasBothCatalogsAndTheLiveCustomSnapshot() {
    val context: Context = RuntimeEnvironment.getApplication()
    val repository = ExerciseCatalogRepository(context)
    val id = repository.createCustomExercise(customExercise("Prompt-only custom exercise"))

    val copied = RelvenPlanKit.readInstructions(context)

    assertTrue(copied.contains("schemas/plan-v1.schema.json"))
    assertTrue(copied.contains("schemas/workout-v3.schema.json"))
    assertTrue(copied.contains("schemas/exercise-collection-v1.schema.json"))
    assertTrue(copied.contains("catalog/exercise-library-v1.json"))
    assertTrue(copied.contains("user-data/custom-exercise-collection-v1.json"))
    assertTrue(copied.contains("Prompt-only custom exercise"))
    assertTrue(copied.contains("Väggpress"))
    assertTrue(copied.contains(id))
    assertTrue(copied.contains("minSecondsPerKm"))
  }

  private fun customExercise(name: String) =
      CustomExerciseInput(
          nameEnglish = name,
          nameSwedish = "Väggpress",
          category = ExerciseCategory.CORE,
          movementPattern = "anti_rotation",
          muscleGroups = listOf("obliques"),
          equipment = emptyList(),
          instructionsEnglish = listOf("Start on the floor.", "Return with control."),
          instructionsSwedish = listOf("Börja på golvet.", "Återgå kontrollerat."))

  private fun readEntry(bytes: ByteArray, path: String): String =
      ZipInputStream(bytes.inputStream(), StandardCharsets.UTF_8).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
          if (entry.name.endsWith(path)) return zip.readBytes().toString(StandardCharsets.UTF_8)
          entry = zip.nextEntry
        }
        error("Missing ZIP entry: $path")
      }
}
