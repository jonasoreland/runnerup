package org.runnerup.relven.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseCatalogAssetTest {
  @Test
  fun bundledLibraryIsLocalizedUniqueAndBodyweightOnly() {
    val asset = File("src/main/assets/relven/exercise-library-v1.json")
    assertTrue("Bundled exercise library asset is missing at ${asset.absolutePath}", asset.isFile)
    val json = asset.readText()
    val english = ExerciseCatalog.parse(json, "en-US")
    val swedish = ExerciseCatalog.parse(json, "sv-SE")

    assertEquals(16, english.exercises.size)
    assertEquals(english.exercises.size, english.exercises.map { it.exerciseId }.toSet().size)
    assertTrue(english.exercises.all { it.equipment.isEmpty() })
    assertTrue(english.exercises.all { it.name.isNotBlank() && it.instructions.isNotEmpty() })
    assertFalse(english.exercises.map { it.name }.toSet() == swedish.exercises.map { it.name }.toSet())
    assertTrue(swedish.exercises.all { it.name.isNotBlank() && it.instructions.isNotEmpty() })
  }
}
