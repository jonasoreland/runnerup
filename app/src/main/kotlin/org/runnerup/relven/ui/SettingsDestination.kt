package org.runnerup.relven.ui

internal enum class SettingsGroup {
  Devices,
  Training,
  App,
  About
}

internal enum class SettingsPage(val group: SettingsGroup) {
  Watch(SettingsGroup.Devices),
  HealthConnect(SettingsGroup.Devices),
  Workout(SettingsGroup.Training),
  Voice(SettingsGroup.Training),
  GpsSensors(SettingsGroup.Training),
  Appearance(SettingsGroup.App),
  LanguageUnits(SettingsGroup.App),
  Notifications(SettingsGroup.App),
  DataExport(SettingsGroup.App),
  About(SettingsGroup.About)
}

internal sealed interface SettingsRoute {
  data object Overview : SettingsRoute
  data class Page(val page: SettingsPage) : SettingsRoute
}

internal fun settingsRouteFromSaved(saved: String?): SettingsRoute =
    SettingsPage.entries.firstOrNull { it.name == saved }?.let(SettingsRoute::Page)
        ?: SettingsRoute.Overview
