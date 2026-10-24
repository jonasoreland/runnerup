package org.runnerup.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import org.runnerup.common.wear.WorkoutPreparationProtocol;

/** Durable single-slot store for the workout most recently prepared on the watch. */
@SuppressLint(
    "ApplySharedPref") // The listener acknowledges only after the watch has durably stored it.
public final class PreparedWorkoutStore {
  private static final String KEY = "prepared_workout_envelope";
  private static final String CANCELLED_SESSION_KEY = "cancelled_workout_session";
  private final Storage storage;

  public PreparedWorkoutStore(Context context) {
    SharedPreferences preferences =
        context
            .getApplicationContext()
            .getSharedPreferences("wear_workout_preparation", Context.MODE_PRIVATE);
    storage =
        new Storage() {
          @Override
          public String read(String key) {
            return preferences.getString(key, null);
          }

          @Override
          public boolean write(String key, String value) {
            return preferences.edit().putString(key, value).commit();
          }

          @Override
          public void remove(String key) {
            preferences.edit().remove(key).commit();
          }
        };
  }

  PreparedWorkoutStore(Storage storage) {
    this.storage = storage;
  }

  /** Returns false for invalid/conflicting input or a failed durable write. */
  public boolean save(byte[] payload) {
    final WorkoutPreparationProtocol.PrepareMessage incoming;
    try {
      incoming = WorkoutPreparationProtocol.decodePrepare(payload);
    } catch (IOException invalid) {
      return false;
    }
    String cancelledSession = storage.read(CANCELLED_SESSION_KEY);
    if (incoming.getSessionId().equals(cancelledSession)) return false;
    if (cancelledSession != null) storage.remove(CANCELLED_SESSION_KEY);
    byte[] existingPayload = load();
    WorkoutPreparationProtocol.PrepareMessage existing = null;
    if (existingPayload != null) {
      try {
        existing = WorkoutPreparationProtocol.decodePrepare(existingPayload);
      } catch (IOException invalidStoredPayload) {
        storage.remove(KEY);
      }
    }
    if (WorkoutPreparationProtocol.assessReplacement(existing, incoming)
        == WorkoutPreparationProtocol.StorageDecision.CONFLICT) return false;
    return storage.write(KEY, toHex(payload));
  }

  public byte[] load() {
    String encoded = storage.read(KEY);
    if (encoded == null) return null;
    try {
      if (encoded.length() > WorkoutPreparationProtocol.MAX_ENCODED_BYTES * 2
          || encoded.length() % 2 != 0) throw new IOException("Invalid saved workout size");
      byte[] payload = fromHex(encoded);
      WorkoutPreparationProtocol.decodePrepare(payload);
      return payload;
    } catch (IllegalArgumentException | IOException invalid) {
      storage.remove(KEY);
      return null;
    }
  }

  public void clear() {
    storage.remove(KEY);
    storage.remove(CANCELLED_SESSION_KEY);
  }

  /** Records a cancellation tombstone so a late prepare retry cannot resurrect that session. */
  public boolean cancel(String sessionId) {
    try {
      WorkoutPreparationProtocol.encodeCancellation(sessionId);
    } catch (IOException invalid) {
      return false;
    }
    if (!storage.write(CANCELLED_SESSION_KEY, sessionId)) return false;
    byte[] current = load();
    if (current != null) {
      try {
        if (sessionId.equals(WorkoutPreparationProtocol.decodePrepare(current).getSessionId())) {
          storage.remove(KEY);
        }
      } catch (IOException invalidStoredPayload) {
        storage.remove(KEY);
      }
    }
    return true;
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
      if (high < 0 || low < 0) throw new IOException("Invalid saved workout encoding");
      bytes[index] = (byte) ((high << 4) | low);
    }
    return bytes;
  }

  interface Storage {
    String read(String key);

    boolean write(String key, String value);

    void remove(String key);
  }
}
