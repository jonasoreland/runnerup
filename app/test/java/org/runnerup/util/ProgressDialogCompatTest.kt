package org.runnerup.util

import android.content.DialogInterface
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ProgressDialogCompatTest {
  @Test
  fun hiddenDialogCanBeDrivenWithoutShowingLegacyModal() {
    val progress = ProgressDialogCompat(RuntimeEnvironment.getApplication(), false)

    progress.setTitle("Uploading")
    progress.setMessage("Sending activity")
    progress.setCancelable(false)
    progress.setCanceledOnTouchOutside(false)
    progress.setMax(3)
    progress.setProgress(1)
    progress.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", null)
    progress.show()

    assertNull(progress.getButton(DialogInterface.BUTTON_NEGATIVE))
    assertFalse(progress.isShowing())
    progress.cancel()
    progress.dismiss()
  }
}
