package org.runnerup.service

import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredStrengthAuthorityCommandTest {
  @Test
  fun retainsOneDuplicateUntilPhoneIdentityLoadsThenReleasesToTheMatchingPeer() {
    val buffer = DeferredStrengthAuthorityCommand()
    val phoneId = "phone-${UUID.randomUUID()}"
    val request = byteArrayOf(1, 2, 3)

    assertTrue(buffer.defer(phoneId, request))
    assertTrue(buffer.defer(phoneId, request.copyOf()))
    assertArrayEquals(request, buffer.takeFor(phoneId)?.payload)
    assertNull(buffer.takeFor(phoneId))
  }

  @Test
  fun dropsCommandFromPeerThatDoesNotMatchResolvedPhoneIdentity() {
    val buffer = DeferredStrengthAuthorityCommand()
    val request = byteArrayOf(1)

    assertTrue(buffer.defer("unverified-peer", request))
    assertNull(buffer.takeFor("paired-phone"))
    assertNull(buffer.takeFor("unverified-peer"))
  }

  @Test
  fun doesNotReplaceADeferredCommandWithAnotherRequest() {
    val buffer = DeferredStrengthAuthorityCommand()
    val phoneId = "phone-${UUID.randomUUID()}"
    val first = byteArrayOf(1)

    assertTrue(buffer.defer(phoneId, first))
    assertEquals(false, buffer.defer(phoneId, byteArrayOf(2)))
    assertArrayEquals(first, buffer.takeFor(phoneId)?.payload)
  }
}
