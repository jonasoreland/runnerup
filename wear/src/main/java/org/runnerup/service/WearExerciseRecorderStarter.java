package org.runnerup.service;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;
import org.runnerup.common.wear.WorkoutSessionProtocol;

/** Avoids loading Health Services classes on Wear OS versions below the library's API floor. */
public final class WearExerciseRecorderStarter {
  private static final String TAG = "WearExerciseRecorderStarter";
  private static final String SERVICE_NAME = "org.runnerup.service.WearExerciseRecorderService";
  private static final String EXTRA_COMMAND = "org.runnerup.extra.WORKOUT_SESSION_COMMAND";

  private WearExerciseRecorderStarter() {}

  public static void dispatch(Context context, WorkoutSessionProtocol.Command command) {
    if (Build.VERSION.SDK_INT < 30 || context == null || command == null) return;
    if (command.getAction() != WorkoutSessionProtocol.Action.START
        && command.getAction() != WorkoutSessionProtocol.Action.PAUSE
        && command.getAction() != WorkoutSessionProtocol.Action.RESUME
        && command.getAction() != WorkoutSessionProtocol.Action.FINISH
        && command.getAction() != WorkoutSessionProtocol.Action.CANCEL) return;
    if (Build.VERSION.SDK_INT >= 34
        && context.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")
            != PackageManager.PERMISSION_GRANTED
        && !hasHeartRatePermission(context)) return;
    try {
      Intent intent =
          new Intent()
              .setClassName(context, SERVICE_NAME)
              .putExtra(EXTRA_COMMAND, WorkoutSessionProtocol.encodeCommand(command));
      ContextCompat.startForegroundService(context, intent);
    } catch (java.io.IOException | RuntimeException unavailable) {
      Log.w(TAG, "Health Services recorder is unavailable on this watch", unavailable);
    }
  }

  private static boolean hasHeartRatePermission(Context context) {
    String permission =
        Build.VERSION.SDK_INT >= 36
            ? "android.permission.health.READ_HEART_RATE"
            : "android.permission.BODY_SENSORS";
    if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
    String backgroundPermission =
        Build.VERSION.SDK_INT >= 36
            ? "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
            : Build.VERSION.SDK_INT >= 33 ? "android.permission.BODY_SENSORS_BACKGROUND" : null;
    return backgroundPermission == null
        || context.checkSelfPermission(backgroundPermission) == PackageManager.PERMISSION_GRANTED;
  }
}
