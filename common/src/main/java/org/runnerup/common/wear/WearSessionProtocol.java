package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Small bounded protocol envelope for application-level Wear OS requests and acknowledgements. */
public final class WearSessionProtocol {
  public static final int CURRENT_VERSION = 2;
  public static final int PING = 1;
  public static final int PONG = 2;
  private static final int MAGIC =
      0x52564E31; // RVN1 wire format; CURRENT_VERSION negotiates app capabilities.
  private static final int MAX_PAYLOAD_BYTES = 512;
  private static final int MAX_REQUEST_ID_BYTES = 128;

  private WearSessionProtocol() {}

  public static byte[] encodePing(String requestId, int requestedVersion) throws IOException {
    return encode(PING, requestId, requestedVersion, 0);
  }

  public static byte[] encodePong(String requestId, int requestedVersion, int supportedVersion)
      throws IOException {
    return encode(PONG, requestId, requestedVersion, supportedVersion);
  }

  /** Returns a pong for a valid ping, including requests from a different protocol version. */
  public static byte[] respondToPing(byte[] payload) throws IOException {
    Message request = decode(payload);
    if (request.type != PING) throw new IOException("Expected ping message");
    return encodePong(request.requestId, request.requestedVersion, CURRENT_VERSION);
  }

  private static byte[] encode(
      int type, String requestId, int requestedVersion, int supportedVersion) throws IOException {
    validateRequestId(requestId);
    if (requestedVersion <= 0 || (type == PONG && supportedVersion <= 0)) {
      throw new IOException("Invalid protocol version");
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeByte(type);
      output.writeInt(requestedVersion);
      if (type == PONG) output.writeInt(supportedVersion);
      output.writeUTF(requestId);
    }
    if (bytes.size() > MAX_PAYLOAD_BYTES) throw new IOException("Protocol payload too large");
    return bytes.toByteArray();
  }

  public static Message decode(byte[] payload) throws IOException {
    if (payload == null || payload.length < 11 || payload.length > MAX_PAYLOAD_BYTES) {
      throw new IOException("Invalid protocol payload size");
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (input.readInt() != MAGIC) throw new IOException("Invalid protocol payload header");
      int type = input.readUnsignedByte();
      if (type != PING && type != PONG) throw new IOException("Invalid protocol message type");
      int requestedVersion = input.readInt();
      if (requestedVersion <= 0) throw new IOException("Invalid protocol version");
      int supportedVersion = type == PONG ? input.readInt() : 0;
      if (type == PONG && supportedVersion <= 0) throw new IOException("Invalid protocol version");
      String requestId = input.readUTF();
      validateRequestId(requestId);
      if (input.available() != 0) throw new IOException("Trailing protocol payload data");
      return new Message(type, requestId, requestedVersion, supportedVersion);
    }
  }

  private static void validateRequestId(String requestId) throws IOException {
    if (requestId == null
        || requestId.trim().isEmpty()
        || requestId.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_ID_BYTES) {
      throw new IOException("Invalid request id");
    }
  }

  public static boolean isSuccessfulHandshake(Message ping, Message pong) {
    return ping != null
        && pong != null
        && ping.type == PING
        && pong.type == PONG
        && ping.requestedVersion == CURRENT_VERSION
        && ping.requestId.equals(pong.requestId)
        && ping.requestedVersion == pong.requestedVersion
        && pong.supportedVersion == CURRENT_VERSION;
  }

  public static final class Message {
    private final int type;
    private final String requestId;
    private final int requestedVersion;
    private final int supportedVersion;

    private Message(int type, String requestId, int requestedVersion, int supportedVersion) {
      this.type = type;
      this.requestId = requestId;
      this.requestedVersion = requestedVersion;
      this.supportedVersion = supportedVersion;
    }

    public int getType() {
      return type;
    }

    public String getRequestId() {
      return requestId;
    }

    public int getRequestedVersion() {
      return requestedVersion;
    }

    public int getSupportedVersion() {
      return supportedVersion;
    }
  }
}
