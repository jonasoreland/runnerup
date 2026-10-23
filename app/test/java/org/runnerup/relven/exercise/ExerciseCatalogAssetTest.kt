package org.runnerup.relven.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.json.JSONObject

class ExerciseCatalogAssetTest {
  @Test
  fun bundledLibraryContainsOneHundredNewExercisesAndPreservesTheOriginalSixteen() {
    val asset = File("assets/relven/exercise-library-v1.json")
    assertTrue("Bundled exercise library asset is missing at ${asset.absolutePath}", asset.isFile)
    val json = asset.readText()
    val baseline =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("exercise-library-v1-baseline.json"))
            .bufferedReader()
            .use { it.readText() }
    val before = JSONObject(baseline)
    val after = JSONObject(json)
    val english = ExerciseCatalog.parse(json, "en-US")
    val swedish = ExerciseCatalog.parse(json, "sv-SE")

    assertEquals(2, after.getInt("libraryRevision"))
    assertEquals(116, english.exercises.size)
    assertTrue(english.exercises.size <= 256)
    assertEquals(english.exercises.size, english.exercises.map { it.exerciseId }.toSet().size)
    val beforeById = before.getJSONArray("exercises").let { records ->
      (0 until records.length()).associate { index ->
        val record = records.getJSONObject(index)
        record.getString("exerciseId") to record.toString()
      }
    }
    val afterById = after.getJSONArray("exercises").let { records ->
      (0 until records.length()).associate { index ->
        val record = records.getJSONObject(index)
        record.getString("exerciseId") to record.toString()
      }
    }
    assertEquals(16, beforeById.size)
    beforeById.forEach { (id, record) -> assertEquals(record, afterById[id]) }
    val newIds = afterById.keys - beforeById.keys
    assertEquals(100, newIds.size)
    assertTrue(english.exercises.all { it.equipment.isEmpty() })
    assertTrue(english.exercises.all { it.name.isNotBlank() && it.instructions.isNotEmpty() })
    assertEquals(english.exercises.size, english.exercises.map { it.name.lowercase().trim() }.toSet().size)
    assertEquals(swedish.exercises.size, swedish.exercises.map { it.name.lowercase().trim() }.toSet().size)
    assertFalse(english.exercises.map { it.name }.toSet() == swedish.exercises.map { it.name }.toSet())
    assertTrue(swedish.exercises.all { it.name.isNotBlank() && it.instructions.isNotEmpty() })

    val audit =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("exercise-library-v2-focus-audit.json"))
            .bufferedReader()
            .use { JSONObject(it.readText()) }
    val auditedIds = mutableSetOf<String>()
    val expectedByFocus =
        linkedMapOf(
            "Hamstrings" to 12,
            "Groin" to 12,
            "Lower leg" to 14,
            "Functional lower body" to 16,
            "Core" to 14,
            "Shoulder" to 14,
            "Plyometrics" to 10,
            "Mobility" to 8)
    assertEquals(expectedByFocus.keys, audit.keys().asSequence().toSet())
    expectedByFocus.forEach { (focus, expectedCount) ->
      val ids = audit.getJSONArray(focus)
      assertEquals(expectedCount, ids.length())
      for (index in 0 until ids.length()) assertTrue(auditedIds.add(ids.getString(index)))
    }
    assertEquals(newIds, auditedIds)

    after.getJSONArray("exercises").let { records ->
      for (index in 0 until records.length()) {
        val record = records.getJSONObject(index)
        if (record.getString("exerciseId") in beforeById) continue
        assertEquals(1, record.getInt("revision"))
        assertTrue(
            "New exercise ID must be a stable UUIDv4",
            record.getString("exerciseId").matches(
                Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")))
        assertEquals(0, record.getJSONArray("equipment").length())
        val names = record.getJSONObject("name")
        assertTrue(names.getString("en").isNotBlank())
        assertTrue(names.getString("sv").isNotBlank())
        assertEquals(names.getString("en"), names.getString("en").trim())
        assertEquals(names.getString("sv"), names.getString("sv").trim())
        val instructions = record.getJSONObject("instructions")
        listOf("en", "sv").forEach { language ->
          val steps = instructions.getJSONArray(language)
          assertTrue(steps.length() in 3..6)
          for (step in 0 until steps.length()) {
            val text = steps.getString(step)
            assertTrue(text.isNotBlank())
            assertEquals(text, text.trim())
          }
        }
      }
    }
  }
}
