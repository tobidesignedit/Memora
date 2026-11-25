package com.memora.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clean solid gallery canvas backdrop (Crisp Light #FAFAFA / Solid Obsidian Dark #0B0C0F).
 * Zero frosted fog or decorative gradient blobs.
 */
@Composable
fun MemoraAtmosphericBackdrop(
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  )
}

/**
 * Crisp solid gallery-frame card / panel (DESIGNmd Genesis + Block Canvas).
 * Flat 1dp hairline border at rest, subtle elevation only when elevated = true.
 */
@Composable
fun GlassPanel(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(16.dp),
  elevated: Boolean = false,
  tintTop: Color? = null,
  tintBottom: Color? = null,
  rimHighlight: Color? = null,
  borderWidth: Dp = 1.dp,
  onClick: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val palette = LocalMemoraColors.current
  val surfaceColor = tintTop ?: tintBottom ?: if (elevated) palette.surfaceRaised else MaterialTheme.colorScheme.surface
  val borderColor = rimHighlight ?: if (elevated) palette.borderStrong else palette.borderSubtle

  val baseModifier = modifier
    .let { mod ->
      if (elevated) {
        mod.shadow(
          elevation = if (palette.isDark) 10.dp else 6.dp,
          shape = shape,
          ambientColor = Color.Black.copy(alpha = if (palette.isDark) 0.45f else 0.12f),
          spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.45f else 0.12f),
        )
      } else {
        mod
      }
    }
    .clip(shape)
    .background(surfaceColor)
    .border(width = borderWidth, color = borderColor, shape = shape)
    .let { mod -> if (onClick != null) mod.clickable(onClick = onClick) else mod }

  Box(modifier = baseModifier) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
      content()
    }
  }
}

@Composable
fun GlassPill(
  modifier: Modifier = Modifier,
  elevated: Boolean = false,
  tintTop: Color? = null,
  tintBottom: Color? = null,
  onClick: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  GlassPanel(
    modifier = modifier,
    shape = CircleShape,
    elevated = elevated,
    tintTop = tintTop,
    tintBottom = tintBottom,
    onClick = onClick,
    content = content,
  )
}
