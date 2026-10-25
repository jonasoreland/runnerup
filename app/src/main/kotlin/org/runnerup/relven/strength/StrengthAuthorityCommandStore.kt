package org.runnerup.relven.strength

import android.content.Context
import android.content.SharedPreferences
import android.annotation.SuppressLint
import org.runnerup.common.wear.StrengthSessionAuthorityProtocol

/** Durable one-at-a-time phone command outbox; retries always reuse the same command ID. */
@SuppressLint("ApplySharedPref", "UseKtx")
internal class StrengthAuthorityCommandStore private constructor(
    private val preferences: SharedPreferences) {
  constructor(context: Context) : this(
      context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))

  @Synchronized
  fun enqueue(command: StrengthSessionAuthorityProtocol.Command): Boolean {
    val encoded = StrengthSessionAuthorityProtocol.encodeCommand(command).toHex()
    val existing = pending()
    if (existing != null) return existing == command
    return preferences.edit().putString(PENDING, encoded).commit()
  }

  @Synchronized
  fun pending(): StrengthSessionAuthorityProtocol.Command? {
    val value = preferences.getString(PENDING, null) ?: return null
    return runCatching { StrengthSessionAuthorityProtocol.decodeCommand(value.fromHex()) }.getOrNull()
  }

  @Synchronized
  fun acknowledge(requestId: String): Boolean {
    val current = pending() ?: return false
    if (current.requestId != requestId) return false
    return preferences.edit().remove(PENDING).commit()
  }

  private fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    return buildString(size * 2) {
      for (byte in this@toHex) {
        val value = byte.toInt() and 0xff
        append(digits[value ushr 4])
        append(digits[value and 0xf])
      }
    }
  }

  private fun String.fromHex(): ByteArray {
    require(length % 2 == 0 && length <= StrengthSessionAuthorityProtocol.MAX_MESSAGE_BYTES * 2)
    return ByteArray(length / 2) { index ->
      val high = Character.digit(this[index * 2], 16).also { require(it >= 0) }
      val low = Character.digit(this[index * 2 + 1], 16).also { require(it >= 0) }
      ((high shl 4) or low).toByte()
    }
  }

  private companion object {
    const val PREFERENCES = "wear_strength_authority_outbox"
    const val PENDING = "pending_command_hex"
  }
}
