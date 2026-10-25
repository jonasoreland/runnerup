package org.runnerup.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.Arrays;
import org.runnerup.common.wear.GpsPreparationProtocol;
import org.runnerup.common.wear.WorkoutPreparationProtocol;

/** Durable single-slot cache of GPS readiness for the workout currently prepared on Wear. */
@SuppressLint("ApplySharedPref")
public final class PreparedGpsReadinessStore {
  private static final String KEY = "prepared_gps_readiness_v1";
  public static final String PREFERENCES_NAME = PreparedWorkoutStore.PREFERENCES_NAME;
  public static final String READINESS_KEY = KEY;

  private final Storage storage;
  private final PreparedWorkoutStore preparedWorkoutStore;

  public PreparedGpsReadinessStore(Context context) {
    Context appContext = context.getApplicationContext();
    SharedPreferences preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
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
    preparedWorkoutStore = new PreparedWorkoutStore(appContext);
  }

  PreparedGpsReadinessStore(Storage storage, PreparedWorkoutStore preparedWorkoutStore) {
    this.storage = storage;
    this.preparedWorkoutStore = preparedWorkoutStore;
  }

  /** Accepts only the current prepared workout and ignores stale revisions and exact retries. */
  public synchronized boolean save(byte[] payload) {
    final GpsPreparationProtocol.Readiness incoming;
    try {
      incoming = GpsPreparationProtocol.decode(payload);
    } catch (IOException invalid) {
      return false;
    }
    if (!isCurrentlyPrepared(incoming.getSessionId())) return false;

    GpsPreparationProtocol.Readiness existing = readStoredReadiness();
    byte[] existingPayload = readPayload();
    if (existing != null && incoming.getSessionId().equals(existing.getSessionId())) {
      if (incoming.getRevision() < existing.getRevision()) return false;
      if (incoming.getRevision() == existing.getRevision()) {
        return Arrays.equals(payload, existingPayload);
      }
    }
    return storage.write(KEY, toHex(payload));
  }

  /** Returns no state for a different or no-longer-prepared session. */
  public synchronized GpsPreparationProtocol.Readiness load(String sessionId) {
    if (!isCurrentlyPrepared(sessionId)) return null;
    GpsPreparationProtocol.Readiness readiness = readStoredReadiness();
    return readiness != null && sessionId.equals(readiness.getSessionId()) ? readiness : null;
  }

  /** Removes only the matching workout's cached state. */
  public synchronized boolean clear(String sessionId) {
    GpsPreparationProtocol.Readiness readiness = readStoredReadiness();
    if (readiness == null || !sessionId.equals(readiness.getSessionId())) return false;
    storage.remove(KEY);
    return true;
  }

  /** Clears an orphaned status when a different workout replaces the prepared workout. */
  public synchronized void clearForDifferentSession(String sessionId) {
    GpsPreparationProtocol.Readiness readiness = readStoredReadiness();
    if (readiness != null && !sessionId.equals(readiness.getSessionId())) storage.remove(KEY);
  }

  private boolean isCurrentlyPrepared(String sessionId) {
    if (sessionId == null) return false;
    byte[] preparedPayload = preparedWorkoutStore.load();
    if (preparedPayload == null) return false;
    try {
      WorkoutPreparationProtocol.PrepareMessage prepared =
          WorkoutPreparationProtocol.decodePrepare(preparedPayload);
      return sessionId.equals(prepared.getSessionId());
    } catch (IOException invalidPreparedWorkout) {
      return false;
    }
  }

  private GpsPreparationProtocol.Readiness readStoredReadiness() {
    byte[] payload = readPayload();
    if (payload == null) return null;
    try {
      return GpsPreparationProtocol.decode(payload);
    } catch (IOException invalid) {
      storage.remove(KEY);
      return null;
    }
  }

  private byte[] readPayload() {
    String encoded = storage.read(KEY);
    if (encoded == null) return null;
    try {
      if (encoded.length() > 128 || encoded.length() % 2 != 0) {
        throw new IOException("Invalid GPS readiness cache size");
      }
      return fromHex(encoded);
    } catch (IOException invalid) {
      storage.remove(KEY);
      return null;
    }
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
      if (high < 0 || low < 0) throw new IOException("Invalid GPS readiness cache encoding");
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
