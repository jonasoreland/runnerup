package org.runnerup.workout;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.runnerup.R;
import org.runnerup.util.Formatter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BasicWorkoutGoalTest {
  private Context context;
  private SharedPreferences preferences;

  @Before
  public void setUp() {
    context = RuntimeEnvironment.getApplication();
    preferences = PreferenceManager.getDefaultSharedPreferences(context);
    preferences.edit().clear().commit();
  }

  @Test
  public void timeGoalEndsTheSimpleWorkoutAfterTheSelectedDuration() {
    preferences
        .edit()
        .putString("basic_goal_type", "time")
        .putString("basic_goal_time", "00:35:00")
        .putInt(context.getString(R.string.pref_basic_target_type), -1)
        .commit();

    Step activeStep = createBasicWorkout().steps.get(0);

    assertEquals(Dimension.TIME, activeStep.getDurationType());
    assertEquals(35 * 60.0, activeStep.getDurationValue(), 0.0);
  }

  @Test
  public void zeroTimeGoalFallsBackToSafeDefaultInsteadOfAnUnboundedWorkout() {
    preferences
        .edit()
        .putString("basic_goal_type", "time")
        .putString("basic_goal_time", "00:00:00")
        .putInt(context.getString(R.string.pref_basic_target_type), -1)
        .commit();

    Step activeStep = createBasicWorkout().steps.get(0);

    assertEquals(Dimension.TIME, activeStep.getDurationType());
    assertEquals(30 * 60.0, activeStep.getDurationValue(), 0.0);
  }

  @Test
  public void distanceGoalUsesThePreferredDistanceUnit() {
    preferences
        .edit()
        .putString("basic_goal_type", "distance")
        .putString("basic_goal_distance", "5")
        .putString(context.getString(R.string.pref_unit), "km")
        .putInt(context.getString(R.string.pref_basic_target_type), -1)
        .commit();

    Step activeStep = createBasicWorkout().steps.get(0);

    assertEquals(Dimension.DISTANCE, activeStep.getDurationType());
    assertEquals(
        5 * Formatter.getUnitMeters(context.getResources(), preferences),
        activeStep.getDurationValue(),
        0.0);
  }

  @Test
  public void legacyPaceTargetStillAppliesWhenNoGoalModeWasSaved() {
    preferences
        .edit()
        .putInt(context.getString(R.string.pref_basic_target_type), Dimension.PACE.getValue())
        .commit();

    Step activeStep = createBasicWorkout().steps.get(0);

    assertEquals(Dimension.PACE, activeStep.getTargetType());
    assertEquals(null, activeStep.getDurationType());
  }

  private Workout createBasicWorkout() {
    return WorkoutBuilder.createDefaultWorkout(
        context.getResources(),
        preferences,
        Dimension.valueOf(
            preferences.getInt(context.getString(R.string.pref_basic_target_type), -1)));
  }
}
