package org.runnerup.relven.ui

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class StartWorkoutTabsStateTest {
  @Test
  fun selectionUpdatesStateAndNotifiesFragmentOnce() {
    val selected = AtomicInteger(-1)
    val state = StartWorkoutTabsStateHolder(0, selected::set)

    state.select(2)
    state.select(2)

    assertEquals(2, state.selectedIndex)
    assertEquals(2, selected.get())
  }

  @Test
  fun restoredSelectionIsAvailableWithoutCallingSelectionCallback() {
    val selected = AtomicInteger(-1)
    val state = StartWorkoutTabsStateHolder(1, selected::set)

    assertEquals(1, state.selectedIndex)
    assertEquals(-1, selected.get())
  }
}
