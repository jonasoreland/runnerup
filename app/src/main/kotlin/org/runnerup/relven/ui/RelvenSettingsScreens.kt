package org.runnerup.relven.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.BuildConfig
import org.runnerup.relven.settings.RelvenSettingsSnapshot
import org.runnerup.view.AudioCueSettingsActivity
import org.runnerup.view.HRSettingsActivity

internal enum class WearConnectionState {
  Loading,
  TransportConnected,
  Disconnected,
  Unavailable,
  Error
}

internal enum class WearHandshakeState {
  NotTested,
  Testing,
  Verified,
  NoWatch,
  TimedOut,
  ProtocolMismatch,
  Failed,
  Unsupported
}

internal fun wearConnectionState(
    supported: Boolean,
    requestSucceeded: Boolean,
    connectedNodes: List<String>?
): WearConnectionState = when {
  !supported -> WearConnectionState.Unavailable
  !requestSucceeded -> WearConnectionState.Error
  connectedNodes.isNullOrEmpty() -> WearConnectionState.Disconnected
  else -> WearConnectionState.TransportConnected
}

internal fun wearHandshakeState(status: String?): WearHandshakeState = when (status) {
  "VERIFIED" -> WearHandshakeState.Verified
  "TIMEOUT" -> WearHandshakeState.TimedOut
  "PROTOCOL_MISMATCH" -> WearHandshakeState.ProtocolMismatch
  "UNSUPPORTED" -> WearHandshakeState.Unsupported
  "NO_WATCH" -> WearHandshakeState.NoWatch
  "TRANSPORT_ERROR" -> WearHandshakeState.Failed
  else -> WearHandshakeState.NotTested
}

internal data class RelvenSettingsDeviceState(
    val locationPermissionGranted: Boolean = false,
    val gpsProviderEnabled: Boolean = false,
    val cadenceSensorAvailable: Boolean = false,
    val temperatureSensorAvailable: Boolean = false,
    val pressureSensorAvailable: Boolean = false,
    val activityRecognitionPermissionGranted: Boolean = true,
    val notificationsEnabled: Boolean = false,
    val localeDisplayName: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val wearState: WearConnectionState = WearConnectionState.Loading,
    val connectedWatchNames: List<String> = emptyList(),
    val wearHandshakeState: WearHandshakeState = WearHandshakeState.NotTested,
    val wearHandshakeRoundTripTimeMillis: Long? = null
)

internal data class SettingsPageActions(
    val onRefreshWatch: () -> Unit = {},
    val onCadenceSensorChanged: (Boolean) -> Unit = {},
    val onTemperatureSensorChanged: (Boolean) -> Unit = {},
    val onPressureSensorChanged: (Boolean) -> Unit = {},
    val onImportCustomExercises: () -> Unit = {},
    val onExportCustomExercises: () -> Unit = {},
    val onOpenHistory: () -> Unit = {},
    val onOpenPlans: () -> Unit = {},
    val onOpenExercises: () -> Unit = {},
    val onOpenHealthConnect: () -> Unit = {},
    val onDistanceUnitChanged: (String) -> Unit = {},
    val onSpeedUnitChanged: (String) -> Unit = {}
)

@Composable
internal fun RelvenSettingsPageContent(
    page: SettingsPage,
    settings: RelvenSettingsSnapshot,
    deviceState: RelvenSettingsDeviceState,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onAutoStartGpsChanged: (Boolean) -> Unit,
    onAutoPauseChanged: (Boolean) -> Unit,
    onAutoLapChanged: (Boolean) -> Unit,
    onAutoLapIntervalsChanged: (Boolean) -> Unit,
    actions: SettingsPageActions
) {
  val context = LocalContext.current
  when (page) {
    SettingsPage.Workout -> SettingsCard {
      SettingsSwitch("Håll skärmen tänd under pass", "Gäller nästa träningspass", settings.keepScreenOn, onKeepScreenOnChanged)
      SettingsSwitch("Automatisk paus", "Pausa registreringen när du står stilla", settings.autoPause, onAutoPauseChanged)
      SettingsSwitch("Automatiska varv för fria pass", "Använder din befintliga varvlängd", settings.autoLap, onAutoLapChanged)
      SettingsSwitch("Automatiska varv för intervallpass", "Registrera varv för intervallpass", settings.autoLapIntervals, onAutoLapIntervalsChanged)
    }
    SettingsPage.Voice -> SettingsCard {
      Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Röstinställningarna använder Relvens befintliga talmotor och sparade val.", color = RelvenColors.MutedText, lineHeight = 21.sp)
        SettingsPrimaryButton("Öppna röstinställningar") {
          context.startActivity(Intent(context, AudioCueSettingsActivity::class.java))
        }
      }
    }
    SettingsPage.GpsSensors -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
      SettingsCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("GPS", fontWeight = FontWeight.SemiBold)
          Text("Platstillstånd: ${if (deviceState.locationPermissionGranted) "Tillåtet" else "Saknas"}", color = statusColor(deviceState.locationPermissionGranted))
          Text("GPS på enheten: ${if (deviceState.gpsProviderEnabled) "På" else "Av eller otillgänglig"}", color = RelvenColors.MutedText)
          SettingsSwitch("Starta GPS automatiskt", "Förbered GPS när du öppnar träningsvyn", settings.autoStartGps, onAutoStartGpsChanged)
          SettingsInlineAction("Öppna appbehörigheter") {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)))
          }
          SettingsInlineAction("Öppna GPS-inställningar") {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
          }
        }
      }
      SettingsCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Sensorer", fontWeight = FontWeight.SemiBold)
          SensorSetting(
              "Stegräknare",
              deviceState.cadenceSensorAvailable,
              settings.useCadenceSensor,
              actions.onCadenceSensorChanged,
              if (deviceState.activityRecognitionPermissionGranted) null else "Behörighet för rörelsedata saknas")
          SensorSetting("Temperatur", deviceState.temperatureSensorAvailable, settings.useTemperatureSensor, actions.onTemperatureSensorChanged)
          SensorSetting("Lufttryck och höjd", deviceState.pressureSensorAvailable, settings.usePressureSensor, actions.onPressureSensorChanged)
          Text("Sensorer visas som tillgängliga bara när Android hittar motsvarande hårdvara.", color = RelvenColors.MutedText, fontSize = 13.sp, lineHeight = 18.sp)
          SettingsInlineAction("Puls och pulszoner") {
            context.startActivity(Intent(context, HRSettingsActivity::class.java))
          }
        }
      }
    }
    SettingsPage.Watch -> SettingsCard {
      Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Anslutna klockor", fontWeight = FontWeight.SemiBold)
        Text(wearStateLabel(deviceState.wearState), color = RelvenColors.MutedText)
        deviceState.connectedWatchNames.forEach { Text(it, fontWeight = FontWeight.Medium) }
        Text(handshakeStateLabel(deviceState.wearHandshakeState, deviceState.wearHandshakeRoundTripTimeMillis), color = RelvenColors.MutedText)
        Text("Klocknärvaro visar bara transportanslutning. Bekräftad appkontakt kräver svar från Relven på klockan.", color = RelvenColors.MutedText, fontSize = 13.sp, lineHeight = 18.sp)
        SettingsInlineAction(
            label = if (deviceState.wearHandshakeState == WearHandshakeState.Testing) "Testar anslutningen…" else "Testa Relven på klockan",
            onClick = actions.onRefreshWatch,
            enabled = deviceState.wearHandshakeState != WearHandshakeState.Testing)
      }
    }
    SettingsPage.Appearance -> SettingsCard {
      Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Relven använder mörkt tema", fontWeight = FontWeight.SemiBold)
        Text("Ett separat ljust tema eller systemstyrt temaval finns inte i appen ännu.", color = RelvenColors.MutedText, lineHeight = 21.sp)
      }
    }
    SettingsPage.LanguageUnits -> SettingsCard {
      Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Språk", fontWeight = FontWeight.SemiBold)
        Text("Följer enhetens språk: ${deviceState.localeDisplayName.ifBlank { "okänt" }}", color = RelvenColors.MutedText)
        Text("Appens språk väljs i Androids appinställningar.", color = RelvenColors.MutedText, fontSize = 13.sp)
        androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Text("Distans", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SettingsChoice("Kilometer", settings.distanceUnit == "km", "settings-distance-km") { actions.onDistanceUnitChanged("km") }
          SettingsChoice("Mile (mi)", settings.distanceUnit == "mi", "settings-distance-mi") { actions.onDistanceUnitChanged("mi") }
        }
        Text("Tempo och hastighet", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SettingsChoice("Tempo", settings.speedUnit == "pace", "settings-speed-pace") { actions.onSpeedUnitChanged("pace") }
          SettingsChoice("Hastighet", settings.speedUnit == "speed", "settings-speed-speed") { actions.onSpeedUnitChanged("speed") }
        }
        Text("Ändringarna gäller visning. Sparade distanser behåller sina ursprungliga värden.", color = RelvenColors.MutedText, fontSize = 13.sp, lineHeight = 18.sp)
      }
    }
    SettingsPage.Notifications -> SettingsCard {
      Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (deviceState.notificationsEnabled) "Notiser är tillåtna i Android" else "Notiser är avstängda i Android", fontWeight = FontWeight.SemiBold)
        Text("Relven har inga separata påminnelseval. Appens notistillstånd styrs av Android.", color = RelvenColors.MutedText, lineHeight = 21.sp)
        SettingsPrimaryButton("Öppna notisinställningar") {
          val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
          } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null))
          }
          context.startActivity(intent)
        }
      }
    }
    SettingsPage.DataExport -> SettingsCard {
      Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Träningsdata på enheten", fontWeight = FontWeight.SemiBold)
        Text("Relven sparar träningsdata lokalt. Health Connect är en frivillig extern export och ersätter inte en säkerhetskopia.", color = RelvenColors.MutedText, lineHeight = 21.sp)
        SettingsInlineAction("Öppna träningshistorik", actions.onOpenHistory)
        SettingsInlineAction("Öppna planer och pass", actions.onOpenPlans)
        SettingsInlineAction("Öppna övningsbibliotek", actions.onOpenExercises)
        SettingsInlineAction("Importera egna övningar", actions.onImportCustomExercises)
        SettingsInlineAction("Exportera egna övningar", actions.onExportCustomExercises)
        SettingsInlineAction("Health Connect-export", actions.onOpenHealthConnect)
        Text("Aktiviteter och styrkepass kan exporteras från respektive detaljvy.", color = RelvenColors.MutedText, fontSize = 13.sp, lineHeight = 18.sp)
      }
    }
    SettingsPage.About -> SettingsCard {
      Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Relven", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Version ${deviceState.appVersion}", color = RelvenColors.MutedText)
        Text("Din träning sparas lokalt på enheten. Health Connect-export är valfri.", color = RelvenColors.MutedText, lineHeight = 21.sp)
      }
    }
    SettingsPage.HealthConnect -> Unit
  }
}

@Composable
private fun SensorSetting(
    label: String,
    available: Boolean,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit,
    statusNote: String? = null
) {
  if (available) {
    SettingsSwitch(
        label,
        statusNote ?: "Sensor hittad på enheten",
        enabled,
        onChanged)
  } else {
    Text("$label: ingen sådan sensor hittades", color = RelvenColors.MutedText, modifier = Modifier.padding(vertical = 8.dp))
  }
}

private fun statusColor(available: Boolean) = if (available) RelvenColors.MutedText else RelvenColors.Orange

private fun wearStateLabel(state: WearConnectionState): String = when (state) {
  WearConnectionState.Loading -> "Kontrollerar Wear OS-anslutning…"
  WearConnectionState.TransportConnected -> "Wear OS-klocka hittad via Data Layer"
  WearConnectionState.Disconnected -> "Ingen ansluten Wear OS-klocka hittades"
  WearConnectionState.Unavailable -> "Wear OS Data Layer är inte tillgängligt i den här versionen"
  WearConnectionState.Error -> "Klockans anslutning kunde inte kontrolleras"
}

private fun handshakeStateLabel(state: WearHandshakeState, roundTripMillis: Long?): String = when (state) {
  WearHandshakeState.NotTested -> "Relven-kontakt: inte testad"
  WearHandshakeState.Testing -> "Testar svar från Relven på klockan…"
  WearHandshakeState.Verified -> roundTripMillis?.let { "Relven svarar ($it ms)" } ?: "Relven svarar"
  WearHandshakeState.NoWatch -> "Ingen ansluten klocka att testa"
  WearHandshakeState.TimedOut -> "Inget app-svar inom 5 sekunder"
  WearHandshakeState.ProtocolMismatch -> "Relven svarade, men protokollversionen stöds inte"
  WearHandshakeState.Failed -> "Anslutningstestet misslyckades"
  WearHandshakeState.Unsupported -> "Anslutningstest stöds inte i den här appversionen"
}

@Composable
private fun SettingsPrimaryButton(label: String, onClick: () -> Unit) {
  Button(
      onClick = onClick,
      colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

@Composable
private fun SettingsInlineAction(label: String, onClick: () -> Unit) {
  SettingsInlineAction(label = label, enabled = true, onClick = onClick)
}

@Composable
private fun SettingsInlineAction(label: String, enabled: Boolean, onClick: () -> Unit) {
  TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
    Text(label, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun SettingsChoice(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
  androidx.compose.material3.FilterChip(
      selected = selected,
      onClick = onClick,
      modifier = Modifier.testTag(tag),
      label = { Text(label) },
      colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
          selectedContainerColor = RelvenColors.Orange,
          selectedLabelColor = RelvenColors.Background))
}
