package org.runnerup.relven.exercise

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

@Throws(IOException::class)
internal fun readExerciseCollection(input: InputStream): ByteArray {
  val output = ByteArrayOutputStream()
  val buffer = ByteArray(8192)
  var total = 0
  while (true) {
    val count = input.read(buffer)
    if (count < 0) break
    total += count
    if (total > MAX_EXERCISE_COLLECTION_BYTES) {
      throw IOException("Exercise collection is too large")
    }
    output.write(buffer, 0, count)
  }
  return output.toByteArray()
}
