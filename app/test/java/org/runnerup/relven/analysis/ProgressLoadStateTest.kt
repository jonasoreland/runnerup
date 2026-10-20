package org.runnerup.relven.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressLoadStateTest {
  @Test
  fun nullResultIsLoading() {
    assertEquals(ProgressLoadState.Loading, progressLoadState(null))
  }

  @Test
  fun successContainsRecordedSummary() {
    val expected =
        ProgressSummary(ActivityWindowSummary(0, 0, null, 0), ActivityWindowSummary(1, 90, 300.0, 1))

    assertEquals(ProgressLoadState.Loaded(expected), progressLoadState(Result.success(expected)))
  }

  @Test
  fun failureIsUnavailable() {
    assertEquals(ProgressLoadState.Unavailable, progressLoadState(Result.failure(IllegalStateException())))
  }

  @Test
  fun onlyLatestProgressRequestCanCommit() {
    val requests = ProgressRequestVersion()

    val resumeRequest = requests.begin()
    val retryRequest = requests.begin()

    assertEquals(false, requests.isCurrent(resumeRequest))
    assertEquals(true, requests.isCurrent(retryRequest))
  }
}
