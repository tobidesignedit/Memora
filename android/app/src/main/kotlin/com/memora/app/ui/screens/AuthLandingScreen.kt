package com.memora.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.memora.app.ui.theme.GalleryLightInk
import com.memora.app.ui.theme.LocalMemoraColors

@Composable
fun AuthLandingScreen(
  authBusy: Boolean,
  onSignIn: (String, String) -> Unit,
  onSignUp: (String, String) -> Unit,
  onGoogleSignIn: () -> Unit,
  onContinueOffline: () -> Unit,
) {
  val palette = LocalMemoraColors.current
  var isSignUp by remember { mutableStateOf(true) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .navigationBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      // Prominent MEMORA Constellation Logo
      Canvas(modifier = Modifier.size(68.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
          color = GalleryLightInk,
          cornerRadius = CornerRadius(w * 0.28f, h * 0.28f),
        )
        val p1 = Offset(w * 0.35f, h * 0.39f)
        val p2 = Offset(w * 0.67f, h * 0.35f)
        val p3 = Offset(w * 0.54f, h * 0.68f)
        val lineColor = Color.White.copy(alpha = 0.5f)
        val stroke = w * 0.042f
        drawLine(lineColor, p1, p2, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(lineColor, p1, p3, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(lineColor, p2, p3, strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(Color(0xFF6366F1), radius = w * 0.115f, center = p1)
        drawCircle(Color(0xFF10B981), radius = w * 0.075f, center = p2)
        drawCircle(Color.White, radius = w * 0.088f, center = p3)
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Memora",
          style = MaterialTheme.typography.displaySmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = "Your private, offline-first second brain.\nSign in or create an account to sync Phone & PC.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }

      // Crisp Login / Sign Up Card
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(palette.surfaceSoft, RoundedCornerShape(12.dp))
              .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            listOf(true to "Sign Up", false to "Log In").forEach { (mode, label) ->
              val active = isSignUp == mode
              Surface(
                color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                  .weight(1f)
                  .clickable { isSignUp = mode },
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelLarge,
                  color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(vertical = 10.dp),
                )
              }
            }
          }

          OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          )

          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (6+ characters)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          )

          Button(
            onClick = {
              if (isSignUp) onSignUp(email, password) else onSignIn(email, password)
            },
            enabled = !authBusy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp),
          ) {
            if (authBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White,
              )
              Spacer(Modifier.size(8.dp))
            }
            Text(
              text = if (isSignUp) "Create Account" else "Log In",
              style = MaterialTheme.typography.labelLarge,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = palette.borderSubtle)
            Text("or", style = MaterialTheme.typography.bodySmall, color = palette.mutedText)
            HorizontalDivider(modifier = Modifier.weight(1f), color = palette.borderSubtle)
          }

          OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = !authBusy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
          ) {
            Text(
              text = "Continue with Google",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      TextButton(onClick = onContinueOffline) {
        Text(
          text = "Continue without an account",
          style = MaterialTheme.typography.labelMedium,
          color = palette.mutedText,
        )
      }
    }
  }
}
