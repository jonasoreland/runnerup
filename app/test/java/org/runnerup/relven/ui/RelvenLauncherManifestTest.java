package org.runnerup.relven.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

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
}
