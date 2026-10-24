package org.runnerup.relven.ui

/** Ensures delayed Settings callbacks cannot replace results from a newer refresh or test. */
internal class WearHandshakeRequestGate {
  private var generation = 0L

  @Synchronized
  fun begin(): Long = ++generation

  @Synchronized
  fun isCurrent(request: Long): Boolean = generation == request
}
