package org.runnerup.relven.strength

import android.content.Context
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.runnerup.common.wear.StrengthSessionAuthorityProtocol

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StrengthAuthorityCommandStoreTest {
  private val context: Context = RuntimeEnvironment.getApplication()

  @Test
  fun pendingCommandSurvivesReopenRejectsReplacementAndAcknowledgesById() {
    context.getSharedPreferences("wear_strength_authority_outbox", Context.MODE_PRIVATE).edit().clear().commit()
    val store = StrengthAuthorityCommandStore(context)
    val command = StrengthSessionAuthorityProtocol.Command(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        0L,
        StrengthSessionAuthorityProtocol.Action.BEGIN)
    val conflicting = command.copy(requestId = UUID.randomUUID().toString())

    assertTrue(store.enqueue(command))
    assertEquals(command, StrengthAuthorityCommandStore(context).pending())
    assertFalse(store.enqueue(conflicting))
    assertFalse(store.acknowledge(conflicting.requestId))
    assertTrue(store.acknowledge(command.requestId))
    assertEquals(null, store.pending())
  }
}
