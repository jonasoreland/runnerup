package org.runnerup.relven.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
}
