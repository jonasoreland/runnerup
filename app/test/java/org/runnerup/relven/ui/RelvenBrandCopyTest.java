package org.runnerup.relven.ui;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.res.Configuration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class RelvenBrandCopyTest {
  @Test
  public void defaultSettingsLabelsUseRelvenBranding() {
    Context context = RuntimeEnvironment.getApplication();

    assertEquals(
        "Relven Live address",
        context.getString(org.runnerup.common.R.string.RunnerUp_live_address));
    assertEquals(
        "Relven on Wear OS",
        context.getString(org.runnerup.common.R.string.RunnerUp_Wear_OS_devices));
  }

  @Test
  public void swedishLiveAddressUsesRelvenBranding() {
    Context context = RuntimeEnvironment.getApplication();
    Configuration configuration = new Configuration(context.getResources().getConfiguration());
    configuration.setLocale(java.util.Locale.forLanguageTag("sv"));

    Context swedish = context.createConfigurationContext(configuration);

    assertEquals(
        "Relven Live-adress",
        swedish.getString(org.runnerup.common.R.string.RunnerUp_live_address));
  }
}
