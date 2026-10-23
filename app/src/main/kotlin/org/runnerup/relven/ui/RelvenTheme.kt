package org.runnerup.relven.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import org.runnerup.R

/** Native color and type tokens from the current Kinetic Obsidian Stitch design system. */
object RelvenColors {
  val Background = Color(0xFF0D141E)
  val Surface = Color(0xFF151C26)
  val SurfaceRaised = Color(0xFF19202A)
  val SurfaceHigh = Color(0xFF232A35)
  val Blue = Color(0xFF2979FF)
  val Orange = Color(0xFFFF6A00)
  val Text = Color(0xFFDCE3F1)
  val MutedText = Color(0xFF94A3B8)
}

internal val RelvenMetricFont =
    FontFamily(
        Font(R.font.space_grotesk, weight = androidx.compose.ui.text.font.FontWeight.Normal),
        Font(R.font.space_grotesk, weight = androidx.compose.ui.text.font.FontWeight.Bold))
internal val RelvenBodyFont = FontFamily(Font(R.font.inter))
private val baseType = Typography()
private val relvenType =
    Typography(
        displayLarge = baseType.displayLarge.copy(fontFamily = RelvenMetricFont),
        headlineLarge = baseType.headlineLarge.copy(fontFamily = RelvenMetricFont),
        headlineMedium = baseType.headlineMedium.copy(fontFamily = RelvenMetricFont),
        titleLarge = baseType.titleLarge.copy(fontFamily = RelvenMetricFont),
        titleMedium = baseType.titleMedium.copy(fontFamily = RelvenMetricFont),
        bodyLarge = baseType.bodyLarge.copy(fontFamily = RelvenBodyFont),
        bodyMedium = baseType.bodyMedium.copy(fontFamily = RelvenBodyFont),
        bodySmall = baseType.bodySmall.copy(fontFamily = RelvenBodyFont),
        labelLarge = baseType.labelLarge.copy(fontFamily = RelvenMetricFont),
        labelMedium = baseType.labelMedium.copy(fontFamily = RelvenMetricFont),
        labelSmall = baseType.labelSmall.copy(fontFamily = RelvenMetricFont))

private val relvenScheme =
    darkColorScheme(
        primary = RelvenColors.Orange,
        onPrimary = Color.White,
        background = RelvenColors.Background,
        onBackground = RelvenColors.Text,
        surface = RelvenColors.Surface,
        onSurface = RelvenColors.Text,
        surfaceVariant = RelvenColors.SurfaceRaised,
        onSurfaceVariant = RelvenColors.MutedText)

@Composable
fun RelvenTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = relvenScheme, typography = relvenType, content = content)
}
