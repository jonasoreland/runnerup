package org.runnerup.relven.exercise

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

internal class ExerciseCatalogRepository(context: Context) {
  private val appContext = context.applicationContext

  @Throws(IOException::class, IllegalArgumentException::class)
  fun load(languageTag: String): ExerciseCatalog {
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
