package org.runnerup.relven.ui;

import android.content.Intent;
import android.net.Uri;
import org.runnerup.common.util.PreparedWorkoutLaunchRoute;
import org.runnerup.common.wear.StrengthPreparationAttempt;
import org.runnerup.common.wear.WorkoutPreparationAttempt;

/** Builds the user-visible watch continuation only after a correlated preparation ACK. */
public final class PreparedWorkoutUiLauncher {
  public interface RemoteActivityStarter {
    void start(String nodeId, Intent intent);
  }

  private PreparedWorkoutUiLauncher() {}

  public static void afterWorkoutPreparation(
      WorkoutPreparationAttempt.Result result, String nodeId, RemoteActivityStarter starter) {
    if (result != WorkoutPreparationAttempt.Result.PREPARED) return;
    start(nodeId, starter);
  }

  public static void afterStrengthPreparation(
      StrengthPreparationAttempt.Result result, String nodeId, RemoteActivityStarter starter) {
    if (result != StrengthPreparationAttempt.Result.PREPARED) return;
    start(nodeId, starter);
  }

  private static void start(String nodeId, RemoteActivityStarter starter) {
    if (nodeId == null || nodeId.trim().isEmpty() || starter == null) return;
    Intent targetIntent =
        new Intent(Intent.ACTION_VIEW, Uri.parse(PreparedWorkoutLaunchRoute.URI_STRING))
            .addCategory(Intent.CATEGORY_BROWSABLE);
    starter.start(nodeId, targetIntent);
  }
}
