package com.memora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MemoraExtendedColors(
  val isDark: Boolean,
  val surfaceSoft: Color,
  val surfaceRaised: Color,
  val notePaper: Color,
  val voiceSurface: Color,
  val glassTop: Color,
  val glassBottom: Color,
  val glassElevatedTop: Color,
  val glassElevatedBottom: Color,
  val rimTopLeft: Color,
  val rimBottomRight: Color,
  val borderSubtle: Color,
  val borderStrong: Color,
  val primarySoft: Color,
  val spark: Color,
  val sparkSoft: Color,
  val onDevice: Color,
  val onDeviceSoft: Color,
  val mutedText: Color,
)

private val LightExtended = MemoraExtendedColors(
  isDark = false,
  surfaceSoft = GalleryLightSoft,
  surfaceRaised = GalleryLightSurface,
  notePaper = GalleryLightNote,
  voiceSurface = GalleryLightVoice,
  glassTop = GalleryLightSurface,
  glassBottom = GalleryLightSurface,
  glassElevatedTop = GalleryLightSurface,
  glassElevatedBottom = GalleryLightSurface,
  rimTopLeft = GalleryLightBorder,
  rimBottomRight = GalleryLightBorder,
  borderSubtle = GalleryLightBorder,
  borderStrong = GalleryLightBorderStrong,
  primarySoft = IndigoSoftLight,
  spark = AmberLight,
  sparkSoft = AmberSoftLight,
  onDevice = EmeraldLight,
  onDeviceSoft = EmeraldSoftLight,
  mutedText = GalleryLightMuted,
)

private val DarkExtended = MemoraExtendedColors(
  isDark = true,
  surfaceSoft = GalleryDarkSoft,
  surfaceRaised = GalleryDarkSurface,
  notePaper = GalleryDarkNote,
  voiceSurface = GalleryDarkVoice,
  glassTop = GalleryDarkSurface,
  glassBottom = GalleryDarkSurface,
  glassElevatedTop = GalleryDarkSurface,
  glassElevatedBottom = GalleryDarkSurface,
  rimTopLeft = GalleryDarkBorder,
  rimBottomRight = GalleryDarkBorder,
  borderSubtle = GalleryDarkBorder,
  borderStrong = GalleryDarkBorderStrong,
  primarySoft = IndigoSoftDark,
  spark = AmberDark,
  sparkSoft = AmberSoftDark,
  onDevice = EmeraldDark,
  onDeviceSoft = EmeraldSoftDark,
  mutedText = GalleryDarkMuted,
)

val LocalMemoraColors = staticCompositionLocalOf { LightExtended }

private val LightColorScheme = lightColorScheme(
  primary = IndigoLight,
  onPrimary = Color.White,
  primaryContainer = IndigoSoftLight,
  onPrimaryContainer = IndigoLight,
  secondary = GalleryLightSecondary,
  onSecondary = Color.White,
  tertiary = AmberLight,
  background = GalleryLightCanvas,
  onBackground = GalleryLightInk,
  surface = GalleryLightSurface,
  onSurface = GalleryLightInk,
  surfaceVariant = GalleryLightSoft,
  onSurfaceVariant = GalleryLightSecondary,
  outline = GalleryLightBorder,
  outlineVariant = GalleryLightBorderStrong,
  error = DangerLight,
  onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
  primary = IndigoDark,
  onPrimary = Color.White,
  primaryContainer = IndigoSoftDark,
  onPrimaryContainer = IndigoDark,
  secondary = GalleryDarkSecondary,
  onSecondary = GalleryDarkCanvas,
  tertiary = AmberDark,
  background = GalleryDarkCanvas,
  onBackground = GalleryDarkInk,
  surface = GalleryDarkSurface,
  onSurface = GalleryDarkInk,
  surfaceVariant = GalleryDarkSoft,
  onSurfaceVariant = GalleryDarkSecondary,
  outline = GalleryDarkBorder,
  outlineVariant = GalleryDarkBorderStrong,
  error = DangerDark,
  onError = Color.White,
)

@Composable
fun MEMORATheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colors = if (darkTheme) DarkColorScheme else LightColorScheme
  val extended = if (darkTheme) DarkExtended else LightExtended

  CompositionLocalProvider(LocalMemoraColors provides extended) {
    MaterialTheme(
      colorScheme = colors,
      typography = MemoraTypography,
      content = content,
    )
  }
}
