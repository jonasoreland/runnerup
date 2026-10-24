package org.runnerup.relven.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RelvenSettingsOverview(
    route: SettingsRoute,
    onRouteChanged: (SettingsRoute) -> Unit,
    settings: org.runnerup.relven.settings.RelvenSettingsSnapshot,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onAutoStartGpsChanged: (Boolean) -> Unit,
    onAutoPauseChanged: (Boolean) -> Unit,
    onAutoLapChanged: (Boolean) -> Unit,
    onAutoLapIntervalsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier) {
  BackHandler(enabled = route is SettingsRoute.Page) {
    onRouteChanged(SettingsRoute.Overview)
  }
  Column(
      modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Spacer(Modifier.height(8.dp))
    when (route) {
      SettingsRoute.Overview -> {
        Text("Inställningar", fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Text("Hantera Relven och dina träningsinställningar", color = RelvenColors.MutedText)
        SettingsGroup.entries.forEach { group ->
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(group.title(), color = RelvenColors.Orange, fontWeight = FontWeight.SemiBold)
            Surface(
                color = RelvenColors.Surface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))) {
              Column {
                SettingsPage.entries.filter { it.group == group }.forEachIndexed { index, page ->
                  SettingsEntry(page = page, onClick = { onRouteChanged(SettingsRoute.Page(page)) })
                  if (index < SettingsPage.entries.count { it.group == group } - 1) {
                    androidx.compose.material3.HorizontalDivider(
                        color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 18.dp))
                  }
                }
              }
            }
          }
        }
      }
      is SettingsRoute.Page -> {
        val context = LocalContext.current
        TextButton(onClick = { onRouteChanged(SettingsRoute.Overview) }) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          Text("Inställningar", modifier = Modifier.padding(start = 8.dp))
        }
        Text(route.page.title(), fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Text(route.page.intro(), color = RelvenColors.MutedText, lineHeight = 22.sp)
        when (route.page) {
          SettingsPage.Workout -> SettingsCard {
            SettingsSwitch(
                title = "Håll skärmen tänd under pass",
                summary = "Gäller nästa träningspass",
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOnChanged)
            SettingsSwitch(
                title = "Automatisk paus",
                summary = "Pausa registreringen när du står stilla",
                checked = settings.autoPause,
                onCheckedChange = onAutoPauseChanged)
            SettingsSwitch(
                title = "Automatiska varv för fria pass",
                summary = "Registrera varv enligt din befintliga varvlängd",
                checked = settings.autoLap,
                onCheckedChange = onAutoLapChanged)
            SettingsSwitch(
                title = "Automatiska varv för intervallpass",
                summary = "Registrera varv för intervallpass",
                checked = settings.autoLapIntervals,
                onCheckedChange = onAutoLapIntervalsChanged)
          }
          SettingsPage.GpsSensors -> SettingsCard {
            SettingsSwitch(
                title = "Starta GPS automatiskt",
                summary = "Förbered GPS när du öppnar träningsvyn",
                checked = settings.autoStartGps,
                onCheckedChange = onAutoStartGpsChanged)
          }
          else -> Surface(
              color = RelvenColors.Surface,
              shape = RoundedCornerShape(22.dp),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))) {
            Text(
                route.page.detail(),
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                color = RelvenColors.MutedText,
                lineHeight = 21.sp)
          }
        }
        if (route.page == SettingsPage.Voice) {
          Button(
              onClick = {
                context.startActivity(
                    android.content.Intent(
                        context, org.runnerup.view.AudioCueSettingsActivity::class.java))
              },
              colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
              modifier = Modifier.fillMaxWidth()) {
            Text("Öppna röstinställningar")
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
  Surface(
      color = RelvenColors.Surface,
      shape = RoundedCornerShape(22.dp),
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))) {
    Column(content = content)
  }
}

@Composable
private fun SettingsSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth()
          .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
          .padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(summary, color = RelvenColors.MutedText, fontSize = 13.sp, lineHeight = 18.sp)
    }
    Switch(checked = checked, onCheckedChange = null)
  }
}

@Composable
private fun SettingsEntry(page: SettingsPage, onClick: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
          .padding(horizontal = 18.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(page.title(), fontWeight = FontWeight.SemiBold)
      Text(page.intro(), color = RelvenColors.MutedText, fontSize = 13.sp)
    }
    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RelvenColors.MutedText)
  }
}

private fun SettingsGroup.title(): String = when (this) {
  SettingsGroup.Devices -> "Enheter och anslutningar"
  SettingsGroup.Training -> "Träning"
  SettingsGroup.App -> "App"
  SettingsGroup.About -> "Om"
}

private fun SettingsPage.title(): String = when (this) {
  SettingsPage.Watch -> "Klocka"
  SettingsPage.HealthConnect -> "Health Connect"
  SettingsPage.Workout -> "Träningsinställningar"
  SettingsPage.Voice -> "Ljud och röstcoachning"
  SettingsPage.GpsSensors -> "GPS och sensorer"
  SettingsPage.Appearance -> "Utseende"
  SettingsPage.LanguageUnits -> "Språk och enheter"
  SettingsPage.Notifications -> "Notiser"
  SettingsPage.DataExport -> "Data och export"
  SettingsPage.About -> "Om Relven"
}

private fun SettingsPage.intro(): String = when (this) {
  SettingsPage.Watch -> "Anslutning och synkronisering med Wear OS"
  SettingsPage.HealthConnect -> "Dela Relvens träningspass med andra hälsoappar"
  SettingsPage.Workout -> "Timers, återhämtning och träningsbeteende"
  SettingsPage.Voice -> "Röstcoachning och träningsmeddelanden"
  SettingsPage.GpsSensors -> "GPS, puls och tillgängliga sensorer"
  SettingsPage.Appearance -> "Tema och visning"
  SettingsPage.LanguageUnits -> "Språk, distans och tempo"
  SettingsPage.Notifications -> "Påminnelser och träningsnotiser"
  SettingsPage.DataExport -> "Import, export och lokal träningsdata"
  SettingsPage.About -> "Appversion och information"
}

private fun SettingsPage.detail(): String = when (this) {
  SettingsPage.HealthConnect -> "Export till Health Connect är ännu inte tillgänglig. Dina träningspass sparas fortsatt i Relven."
  SettingsPage.Watch -> "Wear OS-anslutningen fortsätter att hantera träningssynkronisering. Klockans anslutningsstatus visas inte här ännu."
  SettingsPage.Voice -> "Röstcoachning använder Relvens befintliga talinställningar och fungerar utan en molntjänst."
  SettingsPage.Appearance -> "Relven använder sitt mörka tema. Temaväxling är inte tillgänglig ännu."
  SettingsPage.LanguageUnits -> "Enhetsvalen använder fortfarande de befintliga appinställningarna. Dina sparade träningsvärden ändras inte."
  SettingsPage.Notifications -> "Befintliga Android-notiser fortsätter att fungera. Separata notisval finns inte ännu."
  SettingsPage.DataExport -> "Import och export är tillgängliga från respektive tränings- och planvyer."
  SettingsPage.About -> "Relven – din träning, sparad lokalt på enheten."
  SettingsPage.Workout, SettingsPage.GpsSensors -> ""
}
