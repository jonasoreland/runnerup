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
  public void planWorkoutDetailsLabelsAreLocalizedInEnglishAndSwedish() {
    Context context = RuntimeEnvironment.getApplication();
    Context swedish = localizedContext(context, "sv");

    assertEquals(
        "Workout details",
        context.getString(org.runnerup.common.R.string.Plan_calendar_workout_details_title));
    assertEquals(
        "View workout details",
        context.getString(org.runnerup.common.R.string.Plan_calendar_open_workout_details));
    assertEquals(
        "Passdetaljer",
        swedish.getString(org.runnerup.common.R.string.Plan_calendar_workout_details_title));
    assertEquals(
        "Visa passdetaljer",
        swedish.getString(org.runnerup.common.R.string.Plan_calendar_open_workout_details));
  }

  @Test
  public void planWorkoutMoreActionsLabelIsLocalizedInEnglishAndSwedish() {
    Context context = RuntimeEnvironment.getApplication();
    Context swedish = localizedContext(context, "sv");

    assertEquals(
        "More workout actions",
        context.getString(org.runnerup.common.R.string.Plan_calendar_workout_more_actions));
    assertEquals(
        "Fler passåtgärder",
        swedish.getString(org.runnerup.common.R.string.Plan_calendar_workout_more_actions));
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

  @Test
  public void remainingLocalizedLabelsUseRelvenBranding() {
    Context context = RuntimeEnvironment.getApplication();
    Context persian = localizedContext(context, "fa");
    Context finnish = localizedContext(context, "fi");
    Context turkish = localizedContext(context, "tr");

    assertEquals(
        "امتیاز دادن به Relven", persian.getString(org.runnerup.common.R.string.Rate_RunnerUp));
    assertEquals("درباره ی Relven", persian.getString(org.runnerup.common.R.string.About_RunnerUp));
    assertEquals(
        "فعالیت Relven شروع شد",
        persian.getString(org.runnerup.common.R.string.RunnerUp_activity_started));
    assertEquals(
        "اعلانات GPS و فعالیت Relven",
        persian.getString(org.runnerup.common.R.string.channel_notification_ongoing));
    assertEquals(
        "Relven GPS- ja harjoitusilmoitukset",
        finnish.getString(org.runnerup.common.R.string.channel_notification_ongoing));
    assertEquals("Relven Hakkında", turkish.getString(org.runnerup.common.R.string.About_RunnerUp));
  }

  private static Context localizedContext(Context context, String languageTag) {
    Configuration configuration = new Configuration(context.getResources().getConfiguration());
    configuration.setLocale(java.util.Locale.forLanguageTag(languageTag));
    return context.createConfigurationContext(configuration);
  }
}
