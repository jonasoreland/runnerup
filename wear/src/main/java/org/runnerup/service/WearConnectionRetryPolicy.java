package org.runnerup.service;

/** Small deterministic policy for recovering the Wear Data Layer connection. */
public final class WearConnectionRetryPolicy {
  private static final long INITIAL_DELAY_MS = 1_000L;
  private static final long MAX_DELAY_MS = 16_000L;

  private WearConnectionRetryPolicy() {}

  public static long delayMillis(int failedAttempts) {
    int exponent = Math.max(0, Math.min(failedAttempts, 4));
    return Math.min(MAX_DELAY_MS, INITIAL_DELAY_MS << exponent);
  }

  public static boolean shouldRetry(boolean connected, boolean connecting) {
    return !connected && !connecting;
  }
}
