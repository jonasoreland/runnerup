package org.runnerup.relven.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import org.runnerup.R

/** Native tokens measured from the verified Stitch Daily Activity Dashboard export. */
object RelvenColors {
  val Background = Color(0xFF0A0F18)
  val Surface = Color(0xFF172131)
  val SurfaceRaised = Color(0xFF26344A)
  val Orange = Color(0xFFFF5A1F)
  val Text = Color.White
  val MutedText = Color(0xFF8E9BAE)
}

private val jakarta = FontFamily(Font(R.font.plus_jakarta_sans))
private val baseType = Typography()
private val relvenType =
    Typography(
        headlineLarge = baseType.headlineLarge.copy(fontFamily = jakarta),
        headlineMedium = baseType.headlineMedium.copy(fontFamily = jakarta),
        titleLarge = baseType.titleLarge.copy(fontFamily = jakarta),
        titleMedium = baseType.titleMedium.copy(fontFamily = jakarta),
        bodyLarge = baseType.bodyLarge.copy(fontFamily = jakarta),
        bodyMedium = baseType.bodyMedium.copy(fontFamily = jakarta),
        labelLarge = baseType.labelLarge.copy(fontFamily = jakarta),
        labelMedium = baseType.labelMedium.copy(fontFamily = jakarta))

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
