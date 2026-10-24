package org.runnerup.relven.ui

import android.content.res.Configuration
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeDashboardLocalizationTest {
  private val application = RuntimeEnvironment.getApplication()

  @Test
  fun homeDashboardCopyIsTranslatedInEnglishAndSwedish() {
    val expected =
        mapOf(
            "Home_today_brand_subtitle" to ("Your training" to "Din träning"),
            "Home_today_active_session_open" to ("Open workout" to "Öppna pass"),
            "Home_today_title" to ("Today" to "Idag"),
            "Home_today_subtitle" to ("Ready for your next workout?" to "Redo för nästa pass?"),
            "Home_today_running_title" to ("Running" to "Löpning"),
            "Home_today_running_description" to
                ("Choose a free run or build intervals in the training view." to
                    "Välj ett fritt pass eller bygg intervaller i träningsvyn."),
            "Home_today_running_open" to ("Open training view" to "Öppna träningsvyn"),
            "Home_today_recent_title" to ("Latest workout" to "Senaste passet"),
            "Home_today_recent_distance" to ("%1\$.2f km" to "%1\$.2f km"),
            "Home_today_recent_duration_minutes" to ("%1\$d min" to "%1\$d min"),
            "Home_today_recent_summary" to ("%1\$s · %2\$s" to "%1\$s · %2\$s"),
            "Home_today_unavailable_value" to ("—" to "—"),
            "Home_session_active" to ("Workout in progress" to "Pass pågår"),
            "Home_session_paused" to ("Workout paused" to "Pass pausat"))

    for ((name, translations) in expected) {
      assertEquals(name, translations.first, localizedString(name, "en-US"))
      assertEquals(name, translations.second, localizedString(name, "sv-SE"))
    }
  }

  @Test
  fun recentActivityDistanceUsesLocaleSpecificDecimalSeparator() {
    assertEquals("1.25 km", formattedString("Home_today_recent_distance", "en-US", 1.25))
    assertEquals("1,25 km", formattedString("Home_today_recent_distance", "sv-SE", 1.25))
  }

  private fun localizedString(name: String, languageTag: String): String {
    return configuredContext(languageTag).getString(resourceId(name))
  }

  private fun formattedString(name: String, languageTag: String, vararg arguments: Any): String =
      configuredContext(languageTag).getString(resourceId(name), *arguments)

  private fun configuredContext(languageTag: String) =
      application.createConfigurationContext(
          Configuration(application.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageTag))
          })

  private fun resourceId(name: String): Int {
    val id = application.resources.getIdentifier(name, "string", application.packageName)
    assertNotEquals("Missing localized string resource $name", 0, id)
    return id
  }
}
