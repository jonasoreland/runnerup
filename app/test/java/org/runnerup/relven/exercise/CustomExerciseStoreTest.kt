package org.runnerup.relven.exercise

import android.content.Context
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomExerciseStoreTest {
  private val context: Context = RuntimeEnvironment.getApplication()
  private val directory = File(context.cacheDir, "custom-exercise-store-test")
  private val file = File(directory, "custom.json")
  private val store = CustomExerciseStore(file)

  @Before
  fun clearStore() {
    directory.deleteRecursively()
  }

  @Test
  fun createPersistsStableIdAndBothLanguages() {
    val id = store.create(validInput())

    val english = store.load("en").single()
    val swedish = store.load("sv").single()
    assertTrue(id.matches(Regex("^[0-9a-f-]{36}$")))
    assertEquals(id, english.exerciseId)
    assertEquals(id, swedish.exerciseId)
    assertEquals("Wall sit", english.name)
    assertEquals("Väggsittning", swedish.name)
    assertTrue(english.isCustom)
  }

  @Test
  fun editPreservesIdAndIncrementsRevision() {
    val id = store.create(validInput())
    val first = store.load("en").single()

    store.update(id, validInput().copy(nameEnglish = "Wall squat"))

    val updated = store.load("en").single()
    assertEquals(id, updated.exerciseId)
    assertEquals(first.revision + 1, updated.revision)
    assertEquals("Wall squat", updated.name)
  }

  @Test
  fun invalidEditDoesNotChangeExistingRecord() {
    val id = store.create(validInput())
    val original = file.readBytes()

    assertIllegalArgument { store.update(id, validInput().copy(nameEnglish = "  ")) }

    assertTrue(original.contentEquals(file.readBytes()))
    assertEquals("Wall sit", store.load("en").single().name)
  }

  @Test
  fun rejectsCorruptAndOversizedStoreWithoutDeletingSource() {
    directory.mkdirs()
    file.writeText("not json")

    assertIllegalArgument { store.load("en") }
    assertEquals("not json", file.readText())

    file.writeBytes(ByteArray(1_048_577))
    assertIllegalArgument { store.load("en") }
    assertTrue(file.exists())
  }

  @Test
  fun updatingUnknownIdIsRejected() {
    assertIllegalArgument { store.update("00000000-0000-0000-0000-000000000001", validInput()) }
    assertFalse(file.exists())
  }

  @Test
  fun rejectsDuplicateIdsInStoredCustomData() {
    store.create(validInput())
    val root = JSONObject(file.readText())
    val first = root.getJSONArray("exercises").getJSONObject(0)
    root.getJSONArray("exercises").put(JSONObject(first.toString()))
    file.writeText(root.toString())

    assertIllegalArgument { store.load("en") }
    assertEquals(2, JSONObject(file.readText()).getJSONArray("exercises").length())
  }

  @Test
  fun recoversPreviousDataAfterInterruptedReplacement() {
    store.create(validInput())
    val backup = File(directory, "custom.json.bak")
    assertTrue(file.renameTo(backup))

    val recovered = store.load("en").single()

    assertEquals("Wall sit", recovered.name)
    assertTrue(file.exists())
    assertFalse(backup.exists())
  }

  @Test
  fun failedReplacementRestoresTheExistingFile() {
    val id = store.create(validInput())
    val original = file.readBytes()
    val failingStore =
        CustomExerciseStore(file) { source, destination ->
          if (destination == file && source.name.endsWith(".tmp")) false
          else source.renameTo(destination)
        }

    try {
      failingStore.update(id, validInput().copy(nameEnglish = "Updated wall sit"))
      throw AssertionError("Expected IOException")
    } catch (_: IOException) {
      // Expected: replacement is rejected and the backup is restored.
    }

    assertTrue(original.contentEquals(file.readBytes()))
    assertEquals("Wall sit", store.load("en").single().name)
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

  private fun assertIllegalArgument(block: () -> Unit) {
    try {
      block()
      throw AssertionError("Expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
      // Expected.
    }
  }
}
