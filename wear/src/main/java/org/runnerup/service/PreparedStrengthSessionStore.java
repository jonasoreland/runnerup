package org.runnerup.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.runnerup.common.wear.StrengthExecutionProtocol;

/** Durable single-slot store for the strength execution most recently prepared on the watch. */
@SuppressLint("ApplySharedPref") // A positive preparation ACK requires a synchronous durable write.
public final class PreparedStrengthSessionStore {
  private static final String KEY = "prepared_strength_session_v1";
  private static final String CANCELLED_SESSION_KEY = "cancelled_strength_sessions";
  public static final String PREFERENCES_NAME = "wear_strength_preparation";
  public static final String PREPARED_SESSION_KEY = KEY;
  private static final int MAX_CANCELLED_SESSIONS = 512;
  private final Storage storage;

  public PreparedStrengthSessionStore(Context context) {
    SharedPreferences preferences =
        context
            .getApplicationContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    storage =
        new Storage() {
          @Override
          public String read(String key) {
            return preferences.getString(key, null);
          }

          @Override
          public boolean writeState(String preparedSession, String cancelledSessions) {
            SharedPreferences.Editor editor = preferences.edit();
            if (preparedSession == null) editor.remove(KEY);
            else editor.putString(KEY, preparedSession);
            return editor.putString(CANCELLED_SESSION_KEY, cancelledSessions).commit();
          }
        };
  }

  PreparedStrengthSessionStore(Storage storage) {
    this.storage = storage;
  }

  /** Returns false for malformed/conflicting input or if the durable write fails. */
  public synchronized boolean save(byte[] payload) {
    final StrengthExecutionProtocol.PrepareMessage incoming;
    try {
      incoming = StrengthExecutionProtocol.decodePrepare(payload);
    } catch (IOException invalid) {
      return false;
    }
    if (cancelledSessions().contains(incoming.getSessionId())) return false;
    byte[] existingPayload = load();
    StrengthExecutionProtocol.PrepareMessage existing = null;
    if (existingPayload != null) {
      try {
        existing = StrengthExecutionProtocol.decodePrepare(existingPayload);
      } catch (IOException invalidStoredPayload) {
        storage.writeState(null, serialize(cancelledSessions()));
      }
    }
    StrengthExecutionProtocol.StorageDecision decision =
        StrengthExecutionProtocol.assessReplacement(existing, incoming);
    if (decision == StrengthExecutionProtocol.StorageDecision.CONFLICT) return false;
    if (decision == StrengthExecutionProtocol.StorageDecision.DUPLICATE) return true;
    Set<String> cancelled = cancelledSessions();
    if (existing != null) {
      cancelled.add(existing.getSessionId());
      if (cancelled.size() > MAX_CANCELLED_SESSIONS) return false;
    }
    return storage.writeState(toHex(payload), serialize(cancelled));
  }

  public synchronized byte[] load() {
    String encoded = storage.read(KEY);
    if (encoded == null) return null;
    try {
      if (encoded.length() > StrengthExecutionProtocol.MAX_ENCODED_BYTES * 2
          || encoded.length() % 2 != 0)
        throw new IOException("Invalid saved strength session size");
      byte[] payload = fromHex(encoded);
      StrengthExecutionProtocol.PrepareMessage prepared =
          StrengthExecutionProtocol.decodePrepare(payload);
      if (cancelledSessions().contains(prepared.getSessionId())) {
        storage.writeState(null, serialize(cancelledSessions()));
        return null;
      }
      return payload;
    } catch (IllegalArgumentException | IOException invalid) {
      storage.writeState(null, serialize(cancelledSessions()));
      return null;
    }
  }

  public synchronized void clear() {
    storage.writeState(null, "");
  }

  /** Leaves a cancellation tombstone so a delayed prepare retry cannot resurrect this session. */
  public synchronized boolean cancel(String sessionId) {
    try {
      StrengthExecutionProtocol.encodeCancellation(sessionId);
    } catch (IOException invalid) {
      return false;
    }
    byte[] current = load();
    Set<String> cancelled = cancelledSessions();
    boolean newlyCancelled = cancelled.add(sessionId);
    if (newlyCancelled && cancelled.size() > MAX_CANCELLED_SESSIONS) return false;
    boolean keepCurrent = true;
    if (current != null) {
      try {
        if (sessionId.equals(StrengthExecutionProtocol.decodePrepare(current).getSessionId())) {
          keepCurrent = false;
        }
      } catch (IOException invalidStoredPayload) {
        keepCurrent = false;
      }
    }
    return storage.writeState(
        keepCurrent && current != null ? toHex(current) : null, serialize(cancelled));
  }

  private Set<String> cancelledSessions() {
    Set<String> sessions = new HashSet<>();
    String encoded = storage.read(CANCELLED_SESSION_KEY);
    if (encoded != null && !encoded.isEmpty()) {
      for (String session : encoded.split("\n")) {
        if (!session.isEmpty()) sessions.add(session);
      }
    }
    return sessions;
  }

  private static String serialize(Set<String> sessions) {
    return String.join("\n", sessions);
  }

  private static String toHex(byte[] bytes) {
    char[] encoded = new char[bytes.length * 2];
    char[] digits = "0123456789abcdef".toCharArray();
    for (int index = 0; index < bytes.length; index++) {
      int value = bytes[index] & 0xFF;
      encoded[index * 2] = digits[value >>> 4];
      encoded[index * 2 + 1] = digits[value & 0x0F];
    }
    return new String(encoded);
  }

  private static byte[] fromHex(String encoded) throws IOException {
    byte[] bytes = new byte[encoded.length() / 2];
    for (int index = 0; index < bytes.length; index++) {
      int high = Character.digit(encoded.charAt(index * 2), 16);
      int low = Character.digit(encoded.charAt(index * 2 + 1), 16);
      if (high < 0 || low < 0) throw new IOException("Invalid saved strength session encoding");
      bytes[index] = (byte) ((high << 4) | low);
    }
    return bytes;
  }

  interface Storage {
    String read(String key);

    boolean writeState(String preparedSession, String cancelledSessions);
  }
}
