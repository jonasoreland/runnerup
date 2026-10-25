package org.runnerup.service;

import java.util.List;

/** Maps actual connected Wear nodes to the phone-peer transport state. */
public final class WearPeerAvailability {
  private WearPeerAvailability() {}

  public static boolean isPhonePeerConnected(
      String expectedPhoneNodeId, List<String> connectedIds) {
    if (connectedIds == null || connectedIds.isEmpty()) return false;
    if (expectedPhoneNodeId == null || expectedPhoneNodeId.isBlank()) return true;
    return connectedIds.contains(expectedPhoneNodeId);
  }

  public static WearConnectionStatus status(
      boolean wearableApiConnected, boolean phonePeerConnected) {
    return wearableApiConnected && phonePeerConnected
        ? WearConnectionStatus.CONNECTED
        : WearConnectionStatus.DISCONNECTED;
  }
}
