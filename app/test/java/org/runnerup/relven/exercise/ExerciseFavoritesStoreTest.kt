package org.runnerup.relven.exercise

import org.junit.Assert.assertEquals
import org.junit.Test
import android.content.SharedPreferences
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn

class ExerciseFavoritesStoreTest {
  private val firstId = "3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0"
  private val secondId = "09a2a7b3-0798-4ef8-98a8-02a446068ee0"

  @Test
  fun favoritesPersistByStableExerciseIdAndCanBeRemoved() {
    val preferences = Mockito.mock(SharedPreferences::class.java)
    val editor = Mockito.mock(SharedPreferences.Editor::class.java)
    var stored = emptySet<String>()
    doAnswer { stored.toSet() }.`when`(preferences).getStringSet("favorite_ids", emptySet())
    doReturn(editor).`when`(preferences).edit()
    doAnswer { invocation ->
      stored = invocation.getArgument<Set<String>>(1).toSet()
      editor
    }.`when`(editor).putStringSet(Mockito.eq("favorite_ids"), Mockito.anySet())
    val store = ExerciseFavoritesStore(preferences)

    store.setFavorite(firstId, true)
    store.setFavorite(secondId, true)

    assertEquals(setOf(firstId, secondId), store.read())
    store.setFavorite(firstId, false)
    assertEquals(setOf(secondId), store.read())
  }

  @Test
  fun ignoresMalformedStoredIds() {
    val preferences = Mockito.mock(SharedPreferences::class.java)
    doReturn(setOf(firstId, "bad-id")).`when`(preferences).getStringSet("favorite_ids", emptySet())

    assertEquals(setOf(firstId), ExerciseFavoritesStore(preferences).read())
  }
}
