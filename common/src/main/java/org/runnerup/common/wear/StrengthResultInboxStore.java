package org.runnerup.common.wear;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Bounded durable receive-side replica for confirmed results; received records are never echoed.
 */
public final class StrengthResultInboxStore {
  private static final String KEY = "received_results_v1";
  private static final int STORE_VERSION = 1;
  private final Storage storage;

  public enum AcceptResult {
    STORED,
    DUPLICATE,
    CONFLICT,
    LIMIT_REACHED,
    STORAGE_ERROR
  }

  public StrengthResultInboxStore(Context context) {
    SharedPreferences preferences =
        context
            .getApplicationContext()
            .getSharedPreferences(StrengthResultOutboxStore.PREFERENCES_NAME, Context.MODE_PRIVATE);
    storage =
        new Storage() {
          @Override
          public String read() {
            return preferences.getString(KEY, null);
          }

          @Override
          public boolean write(String content) {
            return preferences.edit().putString(KEY, content).commit();
          }
        };
  }

  StrengthResultInboxStore(Storage storage) {
    this.storage = storage;
  }

  public synchronized AcceptResult accept(StrengthResultProtocol.Result result) {
    final byte[] encoded;
    try {
      encoded = StrengthResultProtocol.encodeResult(result);
    } catch (IOException | RuntimeException invalid) {
      return AcceptResult.CONFLICT;
    }
    final List<byte[]> received;
    try {
      received = readReceived();
    } catch (RuntimeException unavailable) {
      return AcceptResult.STORAGE_ERROR;
    }
    for (byte[] existing : received) {
      try {
        StrengthResultProtocol.Result previous = StrengthResultProtocol.decodeResult(existing);
        if (previous.getResultId().equals(result.getResultId())) {
          return Arrays.equals(existing, encoded) ? AcceptResult.DUPLICATE : AcceptResult.CONFLICT;
        }
      } catch (IOException corrupt) {
        return AcceptResult.STORAGE_ERROR;
      }
    }
    while (!received.isEmpty()
        && (received.size() >= StrengthResultOutboxStore.MAX_PENDING_RESULTS
            || byteCount(received) + encoded.length > StrengthResultOutboxStore.MAX_OUTBOX_BYTES)) {
      received.remove(0);
    }
    if (encoded.length > StrengthResultOutboxStore.MAX_OUTBOX_BYTES)
      return AcceptResult.LIMIT_REACHED;
    received.add(encoded);
    return writeReceived(received) ? AcceptResult.STORED : AcceptResult.STORAGE_ERROR;
  }

  public synchronized List<StrengthResultProtocol.Result> received() {
    List<StrengthResultProtocol.Result> results = new ArrayList<>();
    try {
      for (byte[] value : readReceived()) results.add(StrengthResultProtocol.decodeResult(value));
    } catch (IOException | RuntimeException corrupt) {
      throw new IllegalStateException("Unable to read strength result inbox", corrupt);
    }
    return results;
  }

  private List<byte[]> readReceived() {
    String encoded = storage.read();
    if (encoded == null || encoded.isEmpty()) return new ArrayList<>();
    if (encoded.length() > StrengthResultOutboxStore.MAX_OUTBOX_BYTES * 2) {
      throw new IllegalStateException("Strength result inbox too large");
    }
    try {
      JSONObject root = new JSONObject(encoded);
      if (root.getInt("schemaVersion") != STORE_VERSION) {
        throw new IllegalStateException("Unsupported strength result inbox version");
      }
      JSONArray items = root.getJSONArray("results");
      if (items.length() > StrengthResultOutboxStore.MAX_PENDING_RESULTS) {
        throw new IllegalStateException("Too many received results");
      }
      List<byte[]> results = new ArrayList<>(items.length());
      int byteCount = 0;
      for (int index = 0; index < items.length(); index++) {
        byte[] value = fromHex(items.getString(index));
        StrengthResultProtocol.decodeResult(value);
        byteCount += value.length;
        if (byteCount > StrengthResultOutboxStore.MAX_OUTBOX_BYTES) {
          throw new IllegalStateException("Strength result inbox too large");
        }
        results.add(value);
      }
      return results;
    } catch (JSONException | IOException | IllegalArgumentException invalid) {
      throw new IllegalStateException("Invalid persisted strength result inbox", invalid);
    }
  }

  private boolean writeReceived(List<byte[]> received) {
    JSONArray values = new JSONArray();
    int byteCount = 0;
    for (byte[] value : received) {
      byteCount += value.length;
      if (byteCount > StrengthResultOutboxStore.MAX_OUTBOX_BYTES
          || received.size() > StrengthResultOutboxStore.MAX_PENDING_RESULTS) return false;
      values.put(toHex(value));
    }
    try {
      return storage.write(
          new JSONObject().put("schemaVersion", STORE_VERSION).put("results", values).toString());
    } catch (JSONException | RuntimeException unavailable) {
      return false;
    }
  }

  private static int byteCount(List<byte[]> values) {
    int total = 0;
    for (byte[] value : values) total += value.length;
    return total;
  }

  interface Storage {
    String read();

    boolean write(String content);
  }

  private static String toHex(byte[] bytes) {
    char[] result = new char[bytes.length * 2];
    char[] digits = "0123456789abcdef".toCharArray();
    for (int index = 0; index < bytes.length; index++) {
      int value = bytes[index] & 0xff;
      result[index * 2] = digits[value >>> 4];
      result[index * 2 + 1] = digits[value & 0x0f];
    }
    return new String(result);
  }

  private static byte[] fromHex(String value) throws IOException {
    if (value.length() % 2 != 0) throw new IOException("Invalid result encoding");
    byte[] bytes = new byte[value.length() / 2];
    for (int index = 0; index < bytes.length; index++) {
      int high = Character.digit(value.charAt(index * 2), 16);
      int low = Character.digit(value.charAt(index * 2 + 1), 16);
      if (high < 0 || low < 0) throw new IOException("Invalid result encoding");
      bytes[index] = (byte) ((high << 4) | low);
    }
    return bytes;
  }
}
