package org.runnerup.relven.ui

/** Returns the exact portion of the root viewport occupied below the floating home navigation. */
internal fun homeNavigationContentObstructionPx(rootHeightPx: Int, navigationTopPx: Int?): Int {
  if (rootHeightPx <= 0 || navigationTopPx == null || navigationTopPx !in 0..rootHeightPx) return 0
  return rootHeightPx - navigationTopPx
}
