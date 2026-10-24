package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ActivityShareActionTest {
  @Test
  fun gpxShareUsesProviderUriAndGpxMimeType() {
    val target = activityShareTarget(
        authority = "se.hampusandersson.relven.debug.activity.provider",
        activityId = 42L,
        fileName = "RunnerUp_2026-10-23-07-30-00_Running.gpx",
        format = ActivityShareFormat.Gpx)

    assertEquals(
        "content://se.hampusandersson.relven.debug.activity.provider/gpx/42/RunnerUp_2026-10-23-07-30-00_Running.gpx",
        target.uri.toString())
    assertEquals("application/gpx+xml", target.mimeType)
  }

  @Test
  fun tcxShareUsesProviderUriAndTcxMimeType() {
    val target = activityShareTarget(
        authority = "se.hampusandersson.relven.activity.provider",
        activityId = 7L,
        fileName = "Run.tcx",
        format = ActivityShareFormat.Tcx)

    assertEquals(
        "content://se.hampusandersson.relven.activity.provider/tcx/7/Run.tcx",
        target.uri.toString())
    assertEquals("application/vnd.garmin.tcx+xml", target.mimeType)
  }

  @Test
  fun jsonShareUsesProviderUriAndJsonMimeType() {
    val target = activityShareTarget(
        authority = "se.hampusandersson.relven.activity.provider",
        activityId = 19L,
        fileName = "Run.json",
        format = ActivityShareFormat.Json)

    assertEquals(
        "content://se.hampusandersson.relven.activity.provider/json/19/Run.json",
        target.uri.toString())
    assertEquals("application/json", target.mimeType)
  }
}
