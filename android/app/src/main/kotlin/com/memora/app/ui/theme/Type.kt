package com.memora.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DisplaySerif = FontFamily.Serif
val UiSans = FontFamily.SansSerif
val DataMono = FontFamily.Monospace

val MemoraTypography = Typography(
  displaySmall = TextStyle(
    fontFamily = DisplaySerif,
    fontWeight = FontWeight.Medium,
    fontSize = 32.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.75).sp,
  ),
  headlineMedium = TextStyle(
    fontFamily = DisplaySerif,
    fontWeight = FontWeight.Medium,
    fontSize = 25.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.4).sp,
  ),
  headlineSmall = TextStyle(
    fontFamily = DisplaySerif,
    fontWeight = FontWeight.Medium,
    fontSize = 21.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.3).sp,
  ),
  titleMedium = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.5.sp,
    lineHeight = 21.sp,
    letterSpacing = (-0.15).sp,
  ),
  titleSmall = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.5.sp,
    lineHeight = 19.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 23.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.Normal,
    fontSize = 13.5.sp,
    lineHeight = 20.sp,
  ),
  bodySmall = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 17.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.5.sp,
    lineHeight = 18.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.2.sp,
  ),
)
