package org.runnerup.relven.ui

import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.runnerup.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RelvenStartDialogThemeTest {
  @Test
  fun startDialogsUseRelvenAccentAndSurface() {
    val context =
      ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.RelvenStartDialogTheme)
    val attributes =
      context.theme.obtainStyledAttributes(
        intArrayOf(
          androidx.appcompat.R.attr.colorAccent,
          androidx.appcompat.R.attr.colorBackgroundFloating,
        ),
      )

    try {
      assertEquals(ContextCompat.getColor(context, R.color.relven_orange), attributes.getColor(0, 0))
      assertEquals(ContextCompat.getColor(context, R.color.relven_surface), attributes.getColor(1, 0))
    } finally {
      attributes.recycle()
    }
  }
}
