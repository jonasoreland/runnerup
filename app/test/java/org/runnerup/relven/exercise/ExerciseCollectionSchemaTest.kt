package org.runnerup.relven.exercise

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCollectionSchemaTest {
  @Test
  fun checkedInSchemaDescribesTheExportedCollectionContract() {
    val schemaFile = File("../schemas/exercise-collection-v1.schema.json")
    assertTrue("Collection schema is missing at ${schemaFile.absolutePath}", schemaFile.isFile)
    val schema = JSONObject(schemaFile.readText())
    val rootProperties = schema.getJSONObject("properties")
    val exercise = schema.getJSONObject("\$defs").getJSONObject("exercise")

    assertEquals("relven.exercise-collection", rootProperties.getJSONObject("format").getString("const"))
    assertEquals(1, rootProperties.getJSONObject("schemaVersion").getInt("const"))
    assertFalse(schema.getBoolean("additionalProperties"))
    assertEquals(
        exercise.getJSONArray("required").length(),
        exercise.getJSONObject("properties").length())
    val required = exercise.getJSONArray("required")
    val expectedFields =
        setOf(
            "exerciseId",
            "revision",
            "archived",
            "nameEnglish",
            "nameSwedish",
            "category",
            "movementPattern",
            "muscleGroups",
            "equipment",
            "instructionsEnglish",
            "instructionsSwedish")
    assertEquals(expectedFields, (0 until required.length()).map { required.getString(it) }.toSet())
    assertFalse(schema.getJSONObject("properties").has("media"))
  }
}
