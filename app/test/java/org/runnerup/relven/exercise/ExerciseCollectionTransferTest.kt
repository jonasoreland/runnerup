package org.runnerup.relven.exercise

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCollectionTransferTest {
  @Test
  fun boundedReaderAcceptsCollectionAtLimit() {
    val bytes = ByteArray(MAX_EXERCISE_COLLECTION_BYTES) { 7 }

    assertEquals(bytes.size, readExerciseCollection(ByteArrayInputStream(bytes)).size)
  }

  @Test
  fun boundedReaderStopsWhenInputExceedsLimit() {
    val bytes = ByteArray(MAX_EXERCISE_COLLECTION_BYTES + 1) { 7 }

    try {
      readExerciseCollection(ByteArrayInputStream(bytes))
      throw AssertionError("Expected IOException")
    } catch (expected: IOException) {
      assertTrue(expected.message.orEmpty().contains("too large"))
    }
  }
}
