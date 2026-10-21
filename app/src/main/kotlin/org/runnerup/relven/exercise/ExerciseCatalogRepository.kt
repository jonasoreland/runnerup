package org.runnerup.relven.exercise

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.runnerup.workout.RelvenExerciseReference

internal class ExerciseCatalogRepository(context: Context) {
  private val appContext = context.applicationContext
  private val customStore =
      CustomExerciseStore(File(appContext.filesDir, "relven/custom-exercises.json"))

  @Throws(IOException::class, IllegalArgumentException::class)
  fun load(languageTag: String): ExerciseCatalog {
    val bundled = loadBundledCatalog(languageTag)
    val custom = customStore.load(languageTag)
    return bundled.withCustomExercises(custom)
  }

  @Throws(IOException::class, IllegalArgumentException::class)
  fun exportCustomExercises(): ByteArray = customStore.exportCollection()

  @Throws(IOException::class, IllegalArgumentException::class)
  fun importCustomExercises(bytes: ByteArray): CollectionImportResult {
    val bundledIds = loadBundledCatalog("en").exercises.mapTo(HashSet()) { it.exerciseId }
    return customStore.importCollection(bytes, bundledIds)
  }

  @Throws(IOException::class, IllegalArgumentException::class)
  fun createCustomExercise(input: CustomExerciseInput): String = customStore.create(input)

  @Throws(IOException::class, IllegalArgumentException::class)
  fun updateCustomExercise(exerciseId: String, input: CustomExerciseInput) =
      customStore.update(exerciseId, input)

  @Throws(IOException::class, IllegalArgumentException::class)
  fun setCustomExerciseArchived(exerciseId: String, archived: Boolean) =
      customStore.setArchived(exerciseId, archived)

  fun exerciseReferences(): Map<String, RelvenExerciseReference> {
    val catalog = load("en")
    return catalog.exercises.associate { entry ->
      entry.exerciseId to
          RelvenExerciseReference(
              catalog.libraryRevision,
              entry.revision,
              entry.name,
              entry.instructions.joinToString("\n"),
              entry.equipment)
    }
  }

  private fun loadBundledCatalog(languageTag: String): ExerciseCatalog {
    val json =
        appContext.assets.open(ASSET_PATH).use { input ->
          val output = ByteArrayOutputStream()
          val buffer = ByteArray(8192)
          var total = 0
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_ASSET_BYTES) throw IOException("Exercise catalog asset is too large")
            output.write(buffer, 0, count)
          }
          String(output.toByteArray(), StandardCharsets.UTF_8)
        }
    return ExerciseCatalog.parse(json, languageTag)
  }

  private companion object {
    const val ASSET_PATH = "relven/exercise-library-v1.json"
    const val MAX_ASSET_BYTES = 1_048_576
  }
}
