package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/** Small NTP-style Wear clock sample: watch sends, phone timestamps receive and reply. */
public final class WearClockSyncProtocol {
  private static final int MAGIC = 0x52574331; // RWC1
  private static final int VERSION = 1;
  private static final int REQUEST = 1;
  private static final int REPLY = 2;
  private static final int MAX_BYTES = 128;

  private WearClockSyncProtocol() {}

  public static byte[] encodeRequest(String requestId, long watchSentAt) throws IOException {
    validateRequest(requestId, watchSentAt);
    return encode(REQUEST, requestId, watchSentAt, 0, 0);
  }

  public static Request decodeRequest(byte[] payload) throws IOException {
    Message message = decode(payload, REQUEST);
    validateRequest(message.requestId, message.first);
    return new Request(message.requestId, message.first);
  }

  public static byte[] encodeReply(
      String requestId, long watchSentAt, long phoneReceivedAt, long phoneSentAt)
      throws IOException {
    validateRequest(requestId, watchSentAt);
    if (phoneReceivedAt < 0 || phoneSentAt < phoneReceivedAt) {
      throw new IOException("Invalid phone clock sample");
    }
    return encode(REPLY, requestId, watchSentAt, phoneReceivedAt, phoneSentAt);
  }

  public static Reply decodeReply(byte[] payload) throws IOException {
    Message message = decode(payload, REPLY);
    validateRequest(message.requestId, message.first);
    if (message.second < 0 || message.third < message.second) {
      throw new IOException("Invalid phone clock sample");
    }
    return new Reply(message.requestId, message.first, message.second, message.third);
  }

  private static byte[] encode(int type, String id, long first, long second, long third)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeByte(VERSION);
      output.writeByte(type);
      output.writeUTF(id);
      output.writeLong(first);
      if (type == REPLY) {
        output.writeLong(second);
        output.writeLong(third);
      }
    }
    if (bytes.size() > MAX_BYTES) throw new IOException("Clock message too large");
    return bytes.toByteArray();
  }

  private static Message decode(byte[] payload, int expectedType) throws IOException {
    if (payload == null || payload.length < 19 || payload.length > MAX_BYTES) {
      throw new IOException("Invalid clock message size");
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (input.readInt() != MAGIC) throw new IOException("Invalid clock message header");
      if (input.readUnsignedByte() != VERSION) throw new IOException("Unsupported clock version");
      if (input.readUnsignedByte() != expectedType)
        throw new IOException("Unexpected clock message type");
      String id = input.readUTF();
      long first = input.readLong();
      long second = expectedType == REPLY ? input.readLong() : 0;
      long third = expectedType == REPLY ? input.readLong() : 0;
      if (input.available() != 0) throw new IOException("Trailing clock message data");
      return new Message(id, first, second, third);
    }
  }

  private static void validateRequest(String requestId, long timestamp) throws IOException {
    try {
      if (requestId == null || !UUID.fromString(requestId).toString().equals(requestId)) {
        throw new IOException("Invalid clock request ID");
      }
    } catch (IllegalArgumentException invalid) {
      throw new IOException("Invalid clock request ID", invalid);
    }
    if (timestamp < 0) throw new IOException("Invalid clock timestamp");
  }

  private static final class Message {
    final String requestId;
    final long first;
    final long second;
    final long third;

    Message(String requestId, long first, long second, long third) {
      this.requestId = requestId;
      this.first = first;
      this.second = second;
      this.third = third;
    }
  }

  public static final class Request {
    private final String requestId;
    private final long watchSentAtElapsedRealtimeMs;

    private Request(String requestId, long sent) {
      this.requestId = requestId;
      this.watchSentAtElapsedRealtimeMs = sent;
    }

    public String getRequestId() {
      return requestId;
    }

    public long getWatchSentAtElapsedRealtimeMs() {
      return watchSentAtElapsedRealtimeMs;
    }
  }

  public static final class Reply {
    private final String requestId;
    private final long watchSentAtElapsedRealtimeMs;
    private final long phoneReceivedAtElapsedRealtimeMs;
    private final long phoneSentAtElapsedRealtimeMs;

    private Reply(String id, long watchSent, long phoneReceived, long phoneSent) {
      requestId = id;
      watchSentAtElapsedRealtimeMs = watchSent;
      phoneReceivedAtElapsedRealtimeMs = phoneReceived;
      phoneSentAtElapsedRealtimeMs = phoneSent;
    }

    public String getRequestId() {
      return requestId;
    }

    public long getWatchSentAtElapsedRealtimeMs() {
      return watchSentAtElapsedRealtimeMs;
    }

    public long getPhoneReceivedAtElapsedRealtimeMs() {
      return phoneReceivedAtElapsedRealtimeMs;
    }

    public long getPhoneSentAtElapsedRealtimeMs() {
      return phoneSentAtElapsedRealtimeMs;
    }
  }
}
