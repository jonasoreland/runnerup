package org.runnerup.relven.ui

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlanLibraryIntentTest {
  @Test
  fun managerRouteReturnsToHomeAndRequestsTheComposePlanLibrary() {
    val intent = RelvenHomeActivity.openPlanLibraryIntent(RuntimeEnvironment.getApplication())

    assertEquals(RelvenHomeActivity::class.java.name, intent.component?.className)
    assertTrue(intent.getBooleanExtra("relven.home.open_plan_library", false))
    assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
  }
}
