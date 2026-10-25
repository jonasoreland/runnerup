package org.runnerup.common.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreparedWorkoutLaunchRouteTest {
  @Test
  public void acceptsOnlyBrowsablePreparedWorkoutViewRoute() {
    assertTrue(
        PreparedWorkoutLaunchRoute.matches(
            "android.intent.action.VIEW", true, PreparedWorkoutLaunchRoute.URI_STRING));
    assertFalse(
        PreparedWorkoutLaunchRoute.matches(
            "android.intent.action.SEND", true, "relven://workout/prepared"));
    assertFalse(
        PreparedWorkoutLaunchRoute.matches(
            "android.intent.action.VIEW", false, "relven://workout/prepared"));
    assertFalse(
        PreparedWorkoutLaunchRoute.matches(
            "android.intent.action.VIEW", true, "relven://workout/other"));
    assertFalse(
        PreparedWorkoutLaunchRoute.matches("android.intent.action.VIEW", true, "not a uri"));
  }
}
