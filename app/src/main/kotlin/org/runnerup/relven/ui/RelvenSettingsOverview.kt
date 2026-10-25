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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import org.runnerup.relven.health.HealthConnectSettingsSnapshot
import org.runnerup.relven.health.HealthConnectSessionStatus
import org.runnerup.relven.health.HealthConnectExportStatus

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
    healthConnect: HealthConnectSettingsSnapshot,
    healthConnectBusy: Boolean,
    healthConnectMessage: String?,
    onEnableHealthConnectExport: () -> Unit,
    onDisableHealthConnectExport: () -> Unit,
    onRequestHealthConnectPermissions: () -> Unit,
    onManageHealthConnectPermissions: () -> Unit,
    onExportHistoricalWorkouts: () -> Unit,
    onSyncHealthConnectNow: () -> Unit,
    onRefreshHealthConnectStatus: () -> Unit,
    onRetryHealthConnectSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    deviceState: RelvenSettingsDeviceState = RelvenSettingsDeviceState(),
    pageActions: SettingsPageActions = SettingsPageActions(),
    voiceSettingsFragmentManager: FragmentManager? = null) {
  BackHandler(enabled = route is SettingsRoute.Page) {
    onRouteChanged(SettingsRoute.Overview)
  }
  val embeddedVoicePage = route == SettingsRoute.Page(SettingsPage.Voice) &&
      voiceSettingsFragmentManager != null
  Column(
      modifier = if (embeddedVoicePage) modifier.fillMaxSize().padding(horizontal = 24.dp)
          else modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
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
        if (route.page == SettingsPage.Voice && voiceSettingsFragmentManager != null) {
          SettingsPageHeader(route.page, onRouteChanged)
          AudioCueSettingsDestination(
              fragmentManager = voiceSettingsFragmentManager,
              modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
          SettingsPageHeader(route.page, onRouteChanged)
          if (route.page == SettingsPage.HealthConnect) {
            HealthConnectSettingsContent(
                snapshot = healthConnect,
                busy = healthConnectBusy,
                message = healthConnectMessage,
                onEnable = onEnableHealthConnectExport,
                onDisable = onDisableHealthConnectExport,
                onRequestPermissions = onRequestHealthConnectPermissions,
                onManagePermissions = onManageHealthConnectPermissions,
                onExportHistorical = onExportHistoricalWorkouts,
                onSyncNow = onSyncHealthConnectNow,
                onRefreshStatus = onRefreshHealthConnectStatus,
                onRetry = onRetryHealthConnectSession)
          } else {
            RelvenSettingsPageContent(
                page = route.page,
                settings = settings,
                deviceState = deviceState,
                onKeepScreenOnChanged = onKeepScreenOnChanged,
                onAutoStartGpsChanged = onAutoStartGpsChanged,
                onAutoPauseChanged = onAutoPauseChanged,
                onAutoLapChanged = onAutoLapChanged,
                onAutoLapIntervalsChanged = onAutoLapIntervalsChanged,
                actions = pageActions)
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun SettingsPageHeader(page: SettingsPage, onRouteChanged: (SettingsRoute) -> Unit) {
  TextButton(onClick = { onRouteChanged(SettingsRoute.Overview) }) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
    Text("Inställningar", modifier = Modifier.padding(start = 8.dp))
  }
  Text(page.title(), fontSize = 28.sp, fontWeight = FontWeight.Bold,
      modifier = Modifier.semantics { heading() })
  Text(page.intro(), color = RelvenColors.MutedText, lineHeight = 22.sp)
}

@Composable
private fun HealthConnectSettingsContent(
    snapshot: HealthConnectSettingsSnapshot,
    busy: Boolean,
    message: String?,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onRequestPermissions: () -> Unit,
    onManagePermissions: () -> Unit,
    onExportHistorical: () -> Unit,
    onSyncNow: () -> Unit,
    onRefreshStatus: () -> Unit,
    onRetry: (String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    SettingsCard {
      Column(
          Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Anslutning", fontWeight = FontWeight.SemiBold)
        Text(snapshot.availability.toSwedishLabel(), color = RelvenColors.MutedText)
        Text(
            when {
              snapshot.exportEnabled -> "Automatisk export är på för nya och ändrade pass."
              snapshot.availability == org.runnerup.relven.health.HealthConnectAvailabilityStatus.AVAILABLE ->
                "Relven kan dela sparade pass med Health Connect när du aktiverar export."
              else -> "Dina pass sparas alltid i Relven. Health Connect behövs bara för extern delning."
            },
            color = RelvenColors.MutedText,
            lineHeight = 21.sp)
        SettingsSwitch(
            title = "Exportera pass automatiskt",
            summary = if (snapshot.grantedPermissions.contains("android.permission.health.WRITE_EXERCISE")) {
              "Sparade träningspass skickas till Health Connect"
            } else "Du väljer vilka skrivbehörigheter Relven får använda",
            checked = snapshot.exportEnabled,
            onCheckedChange = { enabled -> if (enabled) onEnable() else onDisable() })
        if (snapshot.availability == org.runnerup.relven.health.HealthConnectAvailabilityStatus.AVAILABLE &&
            !snapshot.grantedPermissions.contains("android.permission.health.WRITE_EXERCISE")) {
          Button(
              onClick = onRequestPermissions,
              enabled = !busy,
              colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Anslut Health Connect")
          }
        }
        TextButton(onClick = onManagePermissions, enabled = !busy) {
          Text("Hantera behörigheter och anslutna appar")
        }
      }
    }

    SettingsCard {
      Column(
          Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Dina pass", fontWeight = FontWeight.SemiBold)
        Text("Exporterade: ${snapshot.exportedCount} · Väntande: ${snapshot.queuedCount}",
            color = RelvenColors.MutedText)
        if (snapshot.retryableCount > 0) {
          Text("${snapshot.retryableCount} exporterade pass kan kompletteras med nya behörigheter.",
              color = RelvenColors.MutedText)
        }
        Text("Tidigare pass att exportera: ${snapshot.eligibleBackfillCount}",
            color = RelvenColors.MutedText)
        Button(
            onClick = onExportHistorical,
            enabled = snapshot.exportEnabled && snapshot.eligibleBackfillCount > 0 && !busy,
            colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
          Text(if (busy) "Förbereder export…" else "Exportera tidigare pass")
        }
        TextButton(
            onClick = onSyncNow,
            enabled = snapshot.exportEnabled &&
                (snapshot.queuedCount > 0 || snapshot.retryableCount > 0) && !busy,
            modifier = Modifier.fillMaxWidth()) {
          Text(if (snapshot.retryableCount > 0) "Synkronisera och komplettera" else "Synkronisera väntande pass")
        }
        TextButton(onClick = onRefreshStatus, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
          Text("Uppdatera synkroniseringsstatus")
        }
        message?.let { Text(it, color = RelvenColors.Orange, lineHeight = 20.sp) }
        if (snapshot.sessions.isNotEmpty()) {
          Text("Synkroniseringshistorik", fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(top = 6.dp))
          snapshot.sessions.take(8).forEach { session ->
            HealthConnectHistoryRow(session, snapshot.grantedPermissions, snapshot.exportEnabled, onRetry)
          }
        } else {
          Text(
              "Ingen pass har exporterats ännu. Du väljer själv om äldre pass ska skickas.",
              color = RelvenColors.MutedText,
              lineHeight = 21.sp)
        }
      }
    }

    SettingsCard {
      Column(
          Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Samsung Health", fontWeight = FontWeight.SemiBold)
        Text(
            "Relven skickar dina träningspass till Health Connect. Andra appar, exempelvis Samsung Health, kan läsa dessa uppgifter om du har gett dem behörighet.",
            color = RelvenColors.MutedText,
            lineHeight = 21.sp)
        Text(
            "I Samsung Health öppnar du Inställningar och väljer Hälsa-anslutningar eller Health Connect. Tillåt Samsung Health att läsa träningspass. Menynamn kan skilja sig mellan versioner.",
            color = RelvenColors.MutedText,
            lineHeight = 21.sp)
      }
    }
  }
}

@Composable
private fun HealthConnectHistoryRow(
    session: HealthConnectSessionStatus,
    grantedPermissions: Set<String>,
    exportEnabled: Boolean,
    onRetry: (String) -> Unit) {
  val date = remember(session.updatedAtMillis) {
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        .format(java.util.Date(session.updatedAtMillis))
  }
  val label = when (session.status) {
    HealthConnectExportStatus.PENDING -> "Väntar på export"
    HealthConnectExportStatus.EXPORTED -> "Exporterad"
    HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS -> "Exporterad med saknade behörigheter"
    HealthConnectExportStatus.WAITING_PERMISSION -> "Behörighet saknas"
    HealthConnectExportStatus.PROVIDER_UNAVAILABLE -> "Health Connect är inte tillgängligt"
    HealthConnectExportStatus.UNSUPPORTED -> "Android-versionen stöds inte"
    HealthConnectExportStatus.RETRYABLE_ERROR -> "Tillfälligt fel"
    HealthConnectExportStatus.FAILED -> "Exporten misslyckades"
    HealthConnectExportStatus.DELETED -> "Borttagen från Health Connect"
  }
  Surface(
      color = RelvenColors.Background,
      shape = RoundedCornerShape(14.dp)) {
    Column(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text((session.workoutName?.takeIf(String::isNotBlank)
          ?: session.sessionId.removePrefix("activity-").let { "Pass $it" }) + " · " + label,
          fontWeight = FontWeight.Medium)
      Text("Senast ändrad: $date", color = RelvenColors.MutedText, fontSize = 13.sp)
      if (session.omittedPermissions.isNotEmpty()) {
        val missing = session.omittedPermissions.mapNotNull(::healthConnectPermissionLabel)
        Text(
            if (missing.isEmpty()) "Vissa mätvärden kunde inte delas. Kontrollera Health Connect-behörigheterna."
            else "Delades inte: " + missing.joinToString() + ". Ge dessa behörigheter i Health Connect.",
            color = RelvenColors.MutedText,
            fontSize = 13.sp,
            lineHeight = 17.sp)
      }
      session.lastError?.takeIf(String::isNotBlank)?.let {
        Text(it.toSwedishHealthConnectError(), color = RelvenColors.MutedText, fontSize = 13.sp, lineHeight = 18.sp)
      }
      if (exportEnabled && (session.status in setOf(
              HealthConnectExportStatus.WAITING_PERMISSION,
              HealthConnectExportStatus.PROVIDER_UNAVAILABLE,
              HealthConnectExportStatus.RETRYABLE_ERROR,
              HealthConnectExportStatus.FAILED) ||
          (session.status == HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS &&
              session.omittedPermissions.any(grantedPermissions::contains)))) {
        TextButton(onClick = { onRetry(session.sessionId) }) {
          Text(if (session.status == HealthConnectExportStatus.EXPORTED_WITH_OMISSIONS)
            "Komplettera export" else "Försök igen")
        }
      }
    }
  }
}

private fun healthConnectPermissionLabel(permission: String): String? = when {
  permission.endsWith("WRITE_EXERCISE_ROUTE") -> "GPS-rutt"
  permission.endsWith("WRITE_DISTANCE") -> "distans"
  permission.endsWith("WRITE_HEART_RATE") -> "puls"
  permission.endsWith("WRITE_SPEED") -> "hastighet"
  permission.endsWith("WRITE_STEPS") -> "kadens"
  permission.endsWith("WRITE_EXERCISE") -> "träningspass"
  else -> null
}

private fun String.toSwedishHealthConnectError(): String {
  if (!startsWith("android.permission.health.")) return this
  return healthConnectPermissionLabel(this)?.let { "Skrivbehörighet för $it saknas." }
      ?: "En Health Connect-behörighet saknas."
}

private fun org.runnerup.relven.health.HealthConnectAvailabilityStatus.toSwedishLabel(): String = when (this) {
  org.runnerup.relven.health.HealthConnectAvailabilityStatus.AVAILABLE -> "Health Connect är tillgängligt"
  org.runnerup.relven.health.HealthConnectAvailabilityStatus.PROVIDER_UNAVAILABLE ->
    "Health Connect är inte installerat eller tillgängligt"
  org.runnerup.relven.health.HealthConnectAvailabilityStatus.PROVIDER_UPDATE_REQUIRED ->
    "Uppdatera Health Connect för att fortsätta"
  org.runnerup.relven.health.HealthConnectAvailabilityStatus.UNSUPPORTED_ANDROID_VERSION ->
    "Kräver Android 8 eller senare"
  org.runnerup.relven.health.HealthConnectAvailabilityStatus.UNKNOWN_ERROR ->
    "Kunde inte kontrollera Health Connect"
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
  Surface(
      color = RelvenColors.Surface,
      shape = RoundedCornerShape(22.dp),
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))) {
    Column(content = content)
  }
}

@Composable
internal fun SettingsSwitch(
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
