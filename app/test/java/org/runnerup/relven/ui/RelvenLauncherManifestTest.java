package org.runnerup.relven.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.runnerup.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class RelvenLauncherManifestTest {
  @Test
  public void productionLauncherOpensRelvenHome() {
    Context context = RuntimeEnvironment.getApplication();

    Intent launcher =
        context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());

    assertNotNull(launcher);
    assertEquals(
        "org.runnerup.relven.ui.RelvenHomeActivity", launcher.getComponent().getClassName());
  }

  @Test
  public void productionLauncherUsesRelvenStartingTheme() throws Exception {
    Context context = RuntimeEnvironment.getApplication();

    Intent launcher =
        context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());

    assertNotNull(launcher);
    assertEquals(
        R.style.RelvenStartingTheme,
        context.getPackageManager().getActivityInfo(launcher.getComponent(), 0).theme);
  }

  @Test
  public void everyAppActivityIsLockedToPortrait() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    PackageInfo appPackage =
        context
            .getPackageManager()
            .getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES);

    assertNotNull(appPackage.activities);
    for (ActivityInfo activity : appPackage.activities) {
      if (!activity.name.startsWith("org.runnerup.")) continue;
      assertEquals(
          activity.name + " should be locked to portrait",
          ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
          activity.screenOrientation);
    }
  }

  @Test
  public void activityMapIsRegisteredAsAnInternalDestination() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    Intent mapIntent = ActivityMapActivity.Companion.intent(context, 42L);

    assertEquals(
        "org.runnerup.relven.ui.ActivityMapActivity", mapIntent.getComponent().getClassName());
    assertEquals(42L, mapIntent.getLongExtra("org.runnerup.relven.ui.activity_map_id", -1L));
    assertFalse(
        context
            .getPackageManager()
            .getActivityInfo(
                new ComponentName(context, "org.runnerup.relven.ui.ActivityMapActivity"), 0)
            .exported);
  }

  @Test
  public void workoutManagerUsesRelvenScreenTheme() throws Exception {
    Context context = RuntimeEnvironment.getApplication();

    ActivityInfo workoutManager =
        context
            .getPackageManager()
            .getActivityInfo(
                new ComponentName(context, "org.runnerup.view.ManageWorkoutsActivity"), 0);

    assertEquals(R.style.RelvenScreenTheme, workoutManager.theme);
  }

  @Test
  public void workoutManagerUsesRelvenAlertDialogs() {
    Context context = RuntimeEnvironment.getApplication();
    TypedArray theme =
        context.obtainStyledAttributes(
            R.style.RelvenScreenTheme, new int[] {androidx.appcompat.R.attr.alertDialogTheme});
    try {
      assertEquals(R.style.RelvenStartDialogTheme, theme.getResourceId(0, 0));
    } finally {
      theme.recycle();
    }
  }

  @Test
  public void activityUploadHandlesRotationWithoutRestartingItsSyncManager() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    ActivityInfo uploadActivity =
        context
            .getPackageManager()
            .getActivityInfo(
                new ComponentName(context, "org.runnerup.relven.ui.ActivityUploadActivity"), 0);

    assertTrue((uploadActivity.configChanges & ActivityInfo.CONFIG_ORIENTATION) != 0);
    assertTrue((uploadActivity.configChanges & ActivityInfo.CONFIG_SCREEN_SIZE) != 0);
    assertTrue((uploadActivity.configChanges & ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE) != 0);
  }
}
