package org.runnerup.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutBuilder;
import org.runnerup.workout.WorkoutSerializer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = TrackerServiceRecoveryTest.ImmediateTextToSpeechShadow.class)
public class TrackerServiceRecoveryTest {
  @Test
  public void serviceRestoresAnActiveWorkoutFromItsDurableCheckpoint() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    File checkpointPath = new File(context.getFilesDir(), "active-workout-checkpoint.json");
    ActiveWorkoutCheckpointStore checkpointStore = new ActiveWorkoutCheckpointStore(checkpointPath);
    checkpointStore.clear();

    SQLiteDatabase database = DBHelper.getWritableDatabase(context);
    ContentValues activityValues = new ContentValues();
    activityValues.put(Constants.DB.ACTIVITY.SPORT, Constants.DB.ACTIVITY.SPORT_RUNNING);
    long activityId = database.insert(Constants.DB.ACTIVITY.TABLE, null, activityValues);
    assertTruePositive(activityId);

    Workout sourceWorkout = createRunningWorkout(context);
    sourceWorkout.setTracker(mock(Tracker.class));
    sourceWorkout.onInit(sourceWorkout);
    sourceWorkout.onStart(Scope.ACTIVITY, sourceWorkout);
    StringWriter definition = new StringWriter();
    WorkoutSerializer.writeJSON(definition, sourceWorkout);
    ActiveWorkoutCheckpoint checkpoint =
        new ActiveWorkoutCheckpoint(
            activityId,
            TrackerState.STARTED,
            17_000_000_000L,
            128.5,
            0,
            0,
            0,
            definition.toString(),
            sourceWorkout.toRuntimeCheckpoint().toString(),
            0,
            System.currentTimeMillis(),
            1,
            Constants.DB.ACTIVITY.SPORT_RUNNING,
            Constants.WORKOUT_TYPE.BASIC,
            false,
            true);
    checkpointStore.save(checkpoint);

    android.content.SharedPreferences preferences =
        PreferenceManager.getDefaultSharedPreferences(context);
    preferences
        .edit()
        .putBoolean(context.getString(org.runnerup.R.string.pref_autopause_active), true)
        .commit();
    WorkoutBuilder.getAudioCuePreferences(
            context, preferences, context.getString(org.runnerup.R.string.pref_basic_audio))
        .edit()
        .putBoolean(context.getString(org.runnerup.R.string.pref_mute_bool), !checkpoint.isMute())
        .commit();

    ServiceController<Tracker> controller = Robolectric.buildService(Tracker.class).create();
    Tracker restoredTracker = controller.get();
    try {
      restoredTracker.onStartCommand(new Intent(context, Tracker.class), 0, 1);
      Shadows.shadowOf(Looper.getMainLooper()).idle();

      assertEquals(TrackerState.STARTED, restoredTracker.getState());
      assertEquals(activityId, restoredTracker.getActivityId());
      assertEquals(128.5, restoredTracker.getDistance(), 0.0);
      assertNotNull(restoredTracker.getWorkout());
      assertFalse(restoredTracker.getWorkout().isPaused());
      assertEquals(checkpoint.isMute(), restoredTracker.getWorkout().getMute());
      assertNotNull(restoredTracker.getWorkout().getCurrentStep());
    } finally {
      controller.destroy();
      awaitCheckpointWrites(restoredTracker);
      checkpointStore.clear();
      database.delete(
          Constants.DB.ACTIVITY.TABLE, "_id = ?", new String[] {Long.toString(activityId)});
    }
  }

  private static Workout createRunningWorkout(Context context) {
    android.content.SharedPreferences preferences =
        PreferenceManager.getDefaultSharedPreferences(context);
    Workout workout =
        WorkoutBuilder.createDefaultWorkout(context.getResources(), preferences, null);
    WorkoutBuilder.prepareWorkout(context.getResources(), preferences, workout);
    android.content.SharedPreferences audioPreferences =
        WorkoutBuilder.getAudioCuePreferences(
            context, preferences, context.getString(org.runnerup.R.string.pref_basic_audio));
    WorkoutBuilder.addAudioCuesToWorkout(
        context.getResources(), workout, audioPreferences, preferences);
    return workout;
  }

  private static void assertTruePositive(long activityId) {
    if (activityId <= 0) throw new AssertionError("An open activity row was not created");
  }

  private static void awaitCheckpointWrites(Tracker tracker) throws Exception {
    Field executorField = Tracker.class.getDeclaredField("checkpointExecutor");
    executorField.setAccessible(true);
    ExecutorService executor = (ExecutorService) executorField.get(tracker);
    assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
  }

  @Implements(TextToSpeech.class)
  public static class ImmediateTextToSpeechShadow {
    @Implementation
    protected void __constructor__(Context context, TextToSpeech.OnInitListener listener) {
      listener.onInit(TextToSpeech.SUCCESS);
    }

    @Implementation
    protected void shutdown() {}
  }
}
