package org.runnerup.relven.exercise

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.json.JSONArray
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
  fun archiveAndRestorePreserveStableIdAndDefinitionRevision() {
    val id = store.create(validInput())
    val original = store.load("en").single()

    store.setArchived(id, true)
    val archived = store.load("en").single()

    assertEquals(id, archived.exerciseId)
    assertEquals(original.revision, archived.revision)
    assertTrue(archived.isArchived)

    store.setArchived(id, false)
    val restored = store.load("en").single()
    assertEquals(id, restored.exerciseId)
    assertEquals(original.revision, restored.revision)
    assertFalse(restored.isArchived)
  }

  @Test
  fun legacyRecordsWithoutArchiveFlagRemainActive() {
    store.create(validInput())
    val root = JSONObject(file.readText())
    root.getJSONArray("exercises").getJSONObject(0).remove("archived")
    file.writeText(root.toString())

    assertFalse(store.load("en").single().isArchived)
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

  @Test
  fun collectionExportPreservesEveryCustomFieldAndArchiveState() {
    val id = store.create(validInput())
    store.update(id, validInput().copy(nameEnglish = "Wall squat"))
    store.setArchived(id, true)

    val root = JSONObject(String(store.exportCollection(), StandardCharsets.UTF_8))
    val record = root.getJSONArray("exercises").getJSONObject(0)

    assertEquals("relven.exercise-collection", root.getString("format"))
    assertEquals(1, root.getInt("schemaVersion"))
    assertEquals(id, record.getString("exerciseId"))
    assertEquals(2, record.getInt("revision"))
    assertTrue(record.getBoolean("archived"))
    assertEquals("Wall squat", record.getString("nameEnglish"))
    assertEquals("Väggsittning", record.getString("nameSwedish"))
    assertEquals("lower_body", record.getString("category"))
    assertEquals("isometric_squat", record.getString("movementPattern"))
    assertEquals("quadriceps", record.getJSONArray("muscleGroups").getString(0))
    assertEquals("wall", record.getJSONArray("equipment").getString(0))
    assertEquals("Lean against a wall.", record.getJSONArray("instructionsEnglish").getString(0))
    assertEquals("Luta dig mot en vägg.", record.getJSONArray("instructionsSwedish").getString(0))
  }

  @Test
  fun collectionImportMergesAndExactReimportIsIdempotent() {
    val id = store.create(validInput())
    store.update(id, validInput().copy(nameEnglish = "Wall squat"))
    store.setArchived(id, true)
    val exported = store.exportCollection()
    val destination = CustomExerciseStore(File(directory, "destination.json"))
    destination.create(validInput().copy(nameEnglish = "Bridge", nameSwedish = "Brygga"))

    val first = destination.importCollection(exported, emptySet())
    val second = destination.importCollection(exported, emptySet())

    assertEquals(1, first.addedCount)
    assertEquals(0, first.unchangedCount)
    assertEquals(0, second.addedCount)
    assertEquals(1, second.unchangedCount)
    val imported = destination.load("en").single { it.exerciseId == id }
    assertEquals("Wall squat", imported.name)
    assertEquals(2, imported.revision)
    assertTrue(imported.isArchived)
    assertEquals(2, destination.load("en").size)
  }

  @Test
  fun conflictingRecordRejectsWholeCollectionWithoutWriting() {
    val id = store.create(validInput())
    val original = file.readBytes()
    val incoming = recordFrom(validInput().copy(nameEnglish = "Different"), id)
    val newRecord = recordFrom(validInput().copy(nameEnglish = "New", nameSwedish = "Ny"),
        "00000000-0000-0000-0000-000000000002")

    assertIllegalArgument { store.importCollection(collection(newRecord, incoming), emptySet()) }

    assertTrue(original.contentEquals(file.readBytes()))
    assertEquals(1, store.load("en").size)
  }

  @Test
  fun bundledIdCollisionRejectsWholeCollectionWithoutWriting() {
    val record = recordFrom(validInput(), "00000000-0000-0000-0000-000000000003")
    val original = if (file.exists()) file.readBytes() else null

    assertIllegalArgument {
      store.importCollection(collection(record), setOf("00000000-0000-0000-0000-000000000003"))
    }

    assertEquals(original == null, !file.exists())
    if (original != null) assertTrue(original.contentEquals(file.readBytes()))
  }

  @Test
  fun duplicateIdsInsideCollectionRejectWithoutWriting() {
    val record = recordFrom(validInput(), "00000000-0000-0000-0000-000000000004")

    assertIllegalArgument { store.importCollection(collection(record, JSONObject(record.toString())), emptySet()) }

    assertFalse(file.exists())
  }

  @Test
  fun oversizedCollectionRejectsWithoutWriting() {
    assertIllegalArgument { store.importCollection(ByteArray(1_048_577), emptySet()) }
    assertFalse(file.exists())
  }

  @Test
  fun unsupportedVersionAndUnknownFieldsRejectWithoutWriting() {
    val record = recordFrom(validInput(), "00000000-0000-0000-0000-000000000006")
    val wrongVersion = JSONObject(String(collection(record), StandardCharsets.UTF_8)).put("schemaVersion", 2)
    val unknownField = JSONObject(record.toString()).put("mediaUrl", "https://example.test/video")

    assertIllegalArgument { store.importCollection(wrongVersion.toString().toByteArray(), emptySet()) }
    assertIllegalArgument { store.importCollection(collection(unknownField), emptySet()) }

    assertFalse(file.exists())
  }

  @Test
  fun collectionRejectsCoercedAndFractionalJsonTypes() {
    val record = recordFrom(validInput(), "00000000-0000-0000-0000-000000000007")
    val stringRevision = JSONObject(record.toString()).put("revision", "1")
    val fractionalSchemaVersion = JSONObject(String(collection(record), StandardCharsets.UTF_8))
        .put("schemaVersion", 1.5)
    val numericName = JSONObject(record.toString()).put("nameEnglish", 12)

    assertIllegalArgument { store.importCollection(collection(stringRevision), emptySet()) }
    assertIllegalArgument { store.importCollection(fractionalSchemaVersion.toString().toByteArray(), emptySet()) }
    assertIllegalArgument { store.importCollection(collection(numericName), emptySet()) }

    assertFalse(file.exists())
  }

  @Test
  fun collectionRejectsPaddedTextInsteadOfSilentlyNormalizingIt() {
    val record = recordFrom(validInput(), "00000000-0000-0000-0000-000000000008")
    val paddedName = JSONObject(record.toString()).put("nameEnglish", " Wall sit ")
    val paddedEquipment = JSONObject(record.toString()).put("equipment", JSONArray().put(" wall "))
    val paddedInstruction = JSONObject(record.toString())
        .put("instructionsEnglish", JSONArray().put(" Lean against a wall. "))

    assertIllegalArgument { store.importCollection(collection(paddedName), emptySet()) }
    assertIllegalArgument { store.importCollection(collection(paddedEquipment), emptySet()) }
    assertIllegalArgument { store.importCollection(collection(paddedInstruction), emptySet()) }

    assertFalse(file.exists())
  }

  @Test
  fun importCapacityOverflowRejectsWithoutChangingExistingRecords() {
    repeat(200) { index ->
      store.create(validInput().copy(nameEnglish = "Exercise $index", nameSwedish = "Övning $index"))
    }
    val original = file.readBytes()
    val incoming = collection(recordFrom(validInput().copy(nameEnglish = "Extra", nameSwedish = "Extra"),
        "00000000-0000-0000-0000-000000000005"))

    assertIllegalArgument { store.importCollection(incoming, emptySet()) }

    assertTrue(original.contentEquals(file.readBytes()))
    assertEquals(200, store.load("en").size)
  }

  private fun recordFrom(input: CustomExerciseInput, id: String): JSONObject {
    val temporary = CustomExerciseStore(File(directory, "temporary-$id.json"))
    temporary.create(input)
    val record = JSONObject(temporary.exportCollection().toString(StandardCharsets.UTF_8))
        .getJSONArray("exercises").getJSONObject(0)
    record.put("exerciseId", id)
    return record
  }

  private fun collection(vararg records: JSONObject): ByteArray =
      JSONObject()
          .put("format", "relven.exercise-collection")
          .put("schemaVersion", 1)
          .put("exercises", JSONArray().also { array -> records.forEach(array::put) })
          .toString()
          .toByteArray(StandardCharsets.UTF_8)

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
