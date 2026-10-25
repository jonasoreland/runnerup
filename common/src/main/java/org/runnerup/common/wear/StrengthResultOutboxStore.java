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

/** Synchronously persisted ordered outbox for confirmed strength set results. */
public final class StrengthResultOutboxStore {
  public static final int MAX_PENDING_RESULTS = 256;
  public static final int MAX_OUTBOX_BYTES = 1_048_576;
  public static final String PREFERENCES_NAME = "strength_result_sync";
  private static final String KEY = "pending_results_v1";
  private static final int STORE_VERSION = 1;
  private final Storage storage;

  public enum EnqueueResult {
    ENQUEUED,
    DUPLICATE,
    CONFLICT,
    LIMIT_REACHED,
    STORAGE_ERROR
  }

  public enum AcknowledgeResult {
    REMOVED,
    DUPLICATE,
    MISMATCH,
    NOT_COMMITTED,
    STORAGE_ERROR
  }

  public StrengthResultOutboxStore(Context context) {
    SharedPreferences preferences =
        context
            .getApplicationContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
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

  StrengthResultOutboxStore(Storage storage) {
    this.storage = storage;
  }

  public synchronized EnqueueResult enqueue(StrengthResultProtocol.Result result) {
    final byte[] encoded;
    try {
      encoded = StrengthResultProtocol.encodeResult(result);
    } catch (IOException invalid) {
      return EnqueueResult.CONFLICT;
    }
    final List<byte[]> pending;
    try {
      pending = readPending();
    } catch (RuntimeException unavailable) {
      return EnqueueResult.STORAGE_ERROR;
    }
    for (byte[] existing : pending) {
      try {
        StrengthResultProtocol.Result old = StrengthResultProtocol.decodeResult(existing);
        if (old.getResultId().equals(result.getResultId())) {
          return Arrays.equals(existing, encoded)
              ? EnqueueResult.DUPLICATE
              : EnqueueResult.CONFLICT;
        }
      } catch (IOException corrupt) {
        return EnqueueResult.STORAGE_ERROR;
      }
    }
    if (pending.size() >= MAX_PENDING_RESULTS) return EnqueueResult.LIMIT_REACHED;
    pending.add(encoded);
    return writePending(pending) ? EnqueueResult.ENQUEUED : EnqueueResult.STORAGE_ERROR;
  }

  /** Returns pending events in their original durable order. */
  public synchronized List<StrengthResultProtocol.Result> pending(int maxCount) {
    if (maxCount < 0) throw new IllegalArgumentException("Invalid result batch size");
    List<StrengthResultProtocol.Result> results = new ArrayList<>();
    try {
      for (byte[] value : readPending()) {
        if (results.size() >= maxCount) break;
        results.add(StrengthResultProtocol.decodeResult(value));
      }
    } catch (IOException | RuntimeException corrupt) {
      throw new IllegalStateException("Unable to read strength result outbox", corrupt);
    }
    return results;
  }

  /** Removes only a result with a matching stable identity and a durable receiver commit. */
  public synchronized AcknowledgeResult acknowledge(
      StrengthResultProtocol.Acknowledgement acknowledgement) {
    if (acknowledgement == null) return AcknowledgeResult.MISMATCH;
    if (acknowledgement.getOutcome() != StrengthResultProtocol.AckOutcome.COMMITTED
        && acknowledgement.getOutcome() != StrengthResultProtocol.AckOutcome.DUPLICATE) {
      return AcknowledgeResult.NOT_COMMITTED;
    }
    final List<byte[]> pending;
    try {
      pending = readPending();
    } catch (RuntimeException unavailable) {
      return AcknowledgeResult.STORAGE_ERROR;
    }
    int index = -1;
    for (int i = 0; i < pending.size(); i++) {
      try {
        StrengthResultProtocol.Result result = StrengthResultProtocol.decodeResult(pending.get(i));
        if (result.getResultId().equals(acknowledgement.getResultId())) {
          if (!result.getExecutionId().equals(acknowledgement.getExecutionId()))
            return AcknowledgeResult.MISMATCH;
          index = i;
          break;
        }
      } catch (IOException corrupt) {
        return AcknowledgeResult.STORAGE_ERROR;
      }
    }
    if (index < 0) return AcknowledgeResult.DUPLICATE;
    pending.remove(index);
    return writePending(pending) ? AcknowledgeResult.REMOVED : AcknowledgeResult.STORAGE_ERROR;
  }

  private List<byte[]> readPending() {
    String encoded = storage.read();
    if (encoded == null || encoded.isEmpty()) return new ArrayList<>();
    if (encoded.length() > MAX_OUTBOX_BYTES * 2)
      throw new IllegalStateException("Strength outbox too large");
    try {
      JSONObject root = new JSONObject(encoded);
      if (root.getInt("schemaVersion") != STORE_VERSION)
        throw new IllegalStateException("Unsupported strength outbox version");
      JSONArray items = root.getJSONArray("results");
      if (items.length() > MAX_PENDING_RESULTS)
        throw new IllegalStateException("Too many pending results");
      List<byte[]> results = new ArrayList<>(items.length());
      int byteCount = 0;
      for (int index = 0; index < items.length(); index++) {
        byte[] value = fromHex(items.getString(index));
        StrengthResultProtocol.decodeResult(value);
        byteCount += value.length;
        if (byteCount > MAX_OUTBOX_BYTES)
          throw new IllegalStateException("Strength outbox too large");
        results.add(value);
      }
      return results;
    } catch (JSONException | IOException | IllegalArgumentException invalid) {
      throw new IllegalStateException("Invalid persisted strength result outbox", invalid);
    }
  }

  private boolean writePending(List<byte[]> pending) {
    int byteCount = 0;
    JSONArray results = new JSONArray();
    for (byte[] value : pending) {
      byteCount += value.length;
      if (byteCount > MAX_OUTBOX_BYTES || pending.size() > MAX_PENDING_RESULTS) return false;
      results.put(toHex(value));
    }
    try {
      return storage.write(
          new JSONObject().put("schemaVersion", STORE_VERSION).put("results", results).toString());
    } catch (JSONException | RuntimeException unavailable) {
      return false;
    }
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
