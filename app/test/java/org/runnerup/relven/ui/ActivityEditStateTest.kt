package org.runnerup.relven.ui

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.runnerup.common.util.Constants.DB
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ActivityEditStateTest {
  private lateinit var database: SQLiteDatabase

  @Before
  fun setUp() {
    database = SQLiteDatabase.create(null)
    database.execSQL(
        """CREATE TABLE ${DB.ACTIVITY.TABLE} (
          _id INTEGER PRIMARY KEY,
          ${DB.ACTIVITY.DELETED} INTEGER NOT NULL DEFAULT 0,
          ${DB.ACTIVITY.SPORT} INTEGER NOT NULL,
          ${DB.ACTIVITY.COMMENT} TEXT,
          ${DB.ACTIVITY.DISTANCE} REAL
        )""".trimIndent())
    database.insertOrThrow(
        DB.ACTIVITY.TABLE,
        null,
        ContentValues().apply {
          put("_id", 7L)
          put(DB.ACTIVITY.DELETED, 0)
          put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_RUNNING)
          put(DB.ACTIVITY.COMMENT, "before")
          put(DB.ACTIVITY.DISTANCE, 5200.0)
        })
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun parsesPreferredUnitDistanceAndLocaleDecimal() {
    assertEquals(1500.0, activityEditDistanceMeters("1,5", 1000.0)!!, 0.0)
    assertEquals(1609.34, activityEditDistanceMeters("1", 1609.34)!!, 0.0)
  }

  @Test
  fun preservesOriginalPrecisionWhenDisplayedManualDistanceIsUnchanged() {
    assertEquals(
        389.456,
        activityEditDistanceMeters("0.242", 1609.34, "0.242", 389.456)!!,
        0.0)
    assertEquals(
        400.72566,
        activityEditDistanceMeters("0.249", 1609.34, "0.242", 389.456)!!,
        0.00001)
  }

  @Test
  fun rejectsInvalidDistance() {
    assertNull(activityEditDistanceMeters("", 1000.0))
    assertNull(activityEditDistanceMeters("-1", 1000.0))
    assertNull(activityEditDistanceMeters("NaN", 1000.0))
    assertNull(activityEditDistanceMeters("1", 0.0))
  }

  @Test
  fun updatesSportAndCommentWithoutChangingGpsDistance() {
    val saved =
        saveHistoryActivityEdit(
            database,
            ActivityEditCommand(
                activityId = 7L,
                sport = DB.ACTIVITY.SPORT_WALKING,
                manualDistanceMeters = null,
                comment = "updated"))

    assertTrue(saved)
    database.query(
        DB.ACTIVITY.TABLE,
        arrayOf(DB.ACTIVITY.SPORT, DB.ACTIVITY.COMMENT, DB.ACTIVITY.DISTANCE),
        "_id = ?",
        arrayOf("7"),
        null,
        null,
        null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(DB.ACTIVITY.SPORT_WALKING, cursor.getInt(0))
      assertEquals("updated", cursor.getString(1))
      assertEquals(5200.0, cursor.getDouble(2), 0.0)
    }
  }

  @Test
  fun savesManualDistanceInMeters() {
    assertTrue(
        saveHistoryActivityEdit(
            database,
            ActivityEditCommand(
                activityId = 7L,
                sport = DB.ACTIVITY.SPORT_TREADMILL,
                manualDistanceMeters = 3218.68,
                comment = "treadmill")))

    database.query(
        DB.ACTIVITY.TABLE,
        arrayOf(DB.ACTIVITY.DISTANCE),
        "_id = ?",
        arrayOf("7"),
        null,
        null,
        null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(3218.68, cursor.getDouble(0), 0.0)
    }
  }

  @Test
  fun doesNotUpdateDeletedOrMissingActivity() {
    database.execSQL("UPDATE ${DB.ACTIVITY.TABLE} SET ${DB.ACTIVITY.DELETED} = 1 WHERE _id = 7")
    assertFalse(
        saveHistoryActivityEdit(
            database,
            ActivityEditCommand(7L, DB.ACTIVITY.SPORT_RUNNING, null, "changed")))
    assertFalse(
        saveHistoryActivityEdit(
            database,
            ActivityEditCommand(88L, DB.ACTIVITY.SPORT_RUNNING, null, "changed")))
  }
}
