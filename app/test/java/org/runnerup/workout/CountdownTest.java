package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import android.content.res.Resources;
import org.junit.Test;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;

public class CountdownTest {

  @Test
  public void testDefaultCountdownWhenActiveWithoutExplicitTime() {
    Resources res = mock(Resources.class);
    SharedPreferences prefs = mock(SharedPreferences.class);

    when(res.getString(R.string.pref_sport)).thenReturn("pref_sport");
    when(res.getString(R.string.pref_countdown_active)).thenReturn("pref_countdown_active");
    when(res.getString(R.string.pref_countdown_time)).thenReturn("pref_countdown_time");

    when(prefs.getInt(eq("pref_sport"), anyInt())).thenReturn(DB.ACTIVITY.SPORT_RUNNING);
    // User activated countdown, but countdown_time is not yet set in prefs
    when(prefs.getBoolean(eq("pref_countdown_active"), eq(false))).thenReturn(true);
    when(prefs.getString(eq("pref_countdown_time"), eq("15"))).thenReturn("15");

    Workout workout = WorkoutBuilder.createDefaultWorkout(res, prefs, null);
    // Should have 2 steps: countdown rest step (15s) and active step
    assertEquals(2, workout.steps.size());
    Step countdownStep = workout.steps.get(0);
    assertTrue(countdownStep.isPauseStep());
    assertEquals(Dimension.TIME, countdownStep.durationType);
    assertEquals(15.0, countdownStep.durationValue, 0.001);
  }

  @Test
  public void testNoCountdownWhenInactive() {
    Resources res = mock(Resources.class);
    SharedPreferences prefs = mock(SharedPreferences.class);

    when(res.getString(R.string.pref_sport)).thenReturn("pref_sport");
    when(res.getString(R.string.pref_countdown_active)).thenReturn("pref_countdown_active");

    when(prefs.getInt(eq("pref_sport"), anyInt())).thenReturn(DB.ACTIVITY.SPORT_RUNNING);
    when(prefs.getBoolean(eq("pref_countdown_active"), eq(false))).thenReturn(false);

    Workout workout = WorkoutBuilder.createDefaultWorkout(res, prefs, null);
    assertEquals(1, workout.steps.size());
    assertFalse(workout.steps.get(0).isPauseStep());
  }
}
