package com.memora.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.BuildConfig
import com.memora.app.ui.IncomingSharePayload
import com.memora.app.ui.MemoraUiState
import com.memora.app.ui.theme.DataMono
import com.memora.app.ui.theme.LocalMemoraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureBottomSheet(
  defaultMode: String,
  busy: Boolean,
  incomingShare: IncomingSharePayload?,
  pickedBitmap: Bitmap?,
  voiceDraft: String,
  onLaunchCamera: () -> Unit,
  onLaunchGallery: () -> Unit,
  onLaunchVoiceInput: () -> Unit,
  onClearMedia: () -> Unit,
  onDismiss: () -> Unit,
  onSubmit: (rawText: String, url: String?, bitmap: Bitmap?, kindHint: String, mode: String) -> Unit,
) {
  val palette = LocalMemoraColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var noteText by remember { mutableStateOf(incomingShare?.text.orEmpty()) }
  var linkText by remember { mutableStateOf(incomingShare?.url.orEmpty()) }
  var routingMode by remember { mutableStateOf(defaultMode) }

  LaunchedEffect(voiceDraft) {
    if (voiceDraft.isNotBlank()) {
      noteText = if (noteText.isBlank()) voiceDraft else "$noteText\n$voiceDraft"
    }
  }

  val activeBitmap = pickedBitmap ?: incomingShare?.bitmap
  val kindHint = when {
    activeBitmap != null -> "screenshot"
    voiceDraft.isNotBlank() -> "voice"
    linkText.isNotBlank() -> "link"
    else -> incomingShare?.kindHint ?: "note"
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(text = "Capture Memory", style = MaterialTheme.typography.headlineMedium)

      // One-handed quick source buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onLaunchCamera,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.size(6.dp))
          Text("Camera", fontSize = 12.sp)
        }
        OutlinedButton(
          onClick = onLaunchGallery,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.size(6.dp))
          Text("Photo", fontSize = 12.sp)
        }
        OutlinedButton(
          onClick = onLaunchVoiceInput,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.size(6.dp))
          Text("Voice", fontSize = 12.sp)
        }
      }

      if (activeBitmap != null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Image(
            bitmap = activeBitmap.asImageBitmap(),
            contentDescription = "Selected image preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .clip(RoundedCornerShape(14.dp)),
          )
          TextButton(onClick = onClearMedia) {
            Text("Remove image", color = MaterialTheme.colorScheme.error)
          }
        }
      }

      OutlinedTextField(
        value = noteText,
        onValueChange = { noteText = it },
        label = { Text("Note, voice transcript, or image context") },
        placeholder = { Text("e.g. Pay electric bill $42 by Friday, or leave blank for image OCRâ€¦") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        shape = RoundedCornerShape(12.dp),
      )

      OutlinedTextField(
        value = linkText,
        onValueChange = { linkText = it },
        label = { Text("Optional URL / Article Link") },
        placeholder = { Text("https://â€¦") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
      )

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Hybrid Routing & Privacy", style = MaterialTheme.typography.titleSmall)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(
            Triple("prefer_on_device", "Smart Hybrid", Icons.Outlined.Memory),
            Triple("only_on_device", "Local Vault", Icons.Outlined.Lock),
            Triple("prefer_in_cloud", "Cloud Deep", Icons.Outlined.Cloud),
          ).forEach { (id, title, icon) ->
            val selected = routingMode == id
            Surface(
              color = if (selected) palette.primarySoft else palette.surfaceSoft,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else palette.borderSubtle,
              ),
              modifier = Modifier
                .weight(1f)
                .clickable { routingMode = id },
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  icon,
                  contentDescription = null,
                  tint = if (selected) MaterialTheme.colorScheme.primary else palette.mutedText,
                  modifier = Modifier.size(16.dp),
                )
                Text(title, style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
      }

      Button(
        onClick = {
          onSubmit(
            noteText.trim(),
            linkText.trim().ifBlank { null },
            activeBitmap,
            kindHint,
            routingMode,
          )
        },
        enabled = !busy && (noteText.isNotBlank() || linkText.isNotBlank() || activeBitmap != null),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp),
        shape = RoundedCornerShape(14.dp),
      ) {
        if (busy) {
          CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.size(8.dp))
          Text("Understandingâ€¦")
        } else {
          Text("Save & Understand")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HybridTelemetrySheet(
  state: MemoraUiState,
  onDismiss: () -> Unit,
  onModeChange: (String) -> Unit,
  onToggleAutoIndex: (Boolean) -> Unit,
  onScopeChange: (String) -> Unit,
  onConnectFolder: () -> Unit,
  onDisconnectFolders: () -> Unit,
  onAutoIndexLocalOnlyChange: (Boolean) -> Unit,
  onScanMediaNow: () -> Unit,
  onRunSideBySideBenchmark: () -> Unit = {},
  onSignInEmail: (String, String) -> Unit = { _, _ -> },
  onSignUpEmail: (String, String, String, String) -> Unit = { _, _, _, _ -> },
  onGoogleSignIn: () -> Unit = {},
  onSignOut: () -> Unit = {},
  onCheckForUpdate: () -> Unit = {},
  onDownloadUpdate: () -> Unit = {},
  onToggleTheme: () -> Unit = {},
  onSyncNow: () -> Unit,
  onLoadDemo: () -> Unit,
) {
  val palette = LocalMemoraColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var isSignUpMode by remember { mutableStateOf(false) }
  var nameDraft by remember { mutableStateOf("") }
  var usernameDraft by remember { mutableStateOf("") }
  var emailDraft by remember { mutableStateOf("") }
  var passwordDraft by remember { mutableStateOf("") }
  val onDeviceRuns = state.benchmarks.filter { it.source == "on_device" }
  val cloudRuns = state.benchmarks.filter { it.source == "in_cloud" }
  val avgLocal = if (onDeviceRuns.isNotEmpty()) onDeviceRuns.map { it.latencyMs }.average().toLong() else null
  val avgCloud = if (cloudRuns.isNotEmpty()) cloudRuns.map { it.latencyMs }.average().toLong() else null
  val pendingCount = state.memories.count { it.syncStatus == "pending" }
  val localOnlyCount = state.memories.count { it.privacyMode == "only_on_device" }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text("Account, Updates & Hybrid AI", style = MaterialTheme.typography.headlineMedium)

      // 1. Account Login / Sign Up (Link Phone & PC Automatically)
      Surface(
        color = palette.surfaceSoft,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
              Text("Link Phone & PC Vault", style = MaterialTheme.typography.titleSmall)
            }
            if (state.accountEmail != null) {
              Text(
                text = "Synced",
                style = MaterialTheme.typography.labelMedium,
                color = palette.onDevice,
              )
            }
          }

          if (state.accountEmail != null) {
            val identityLine = listOfNotNull(
              state.accountName?.takeIf { it.isNotBlank() },
              state.accountUsername?.takeIf { it.isNotBlank() }?.let { "@$it" },
            ).joinToString(" · ")
            if (identityLine.isNotEmpty()) {
              Text(
                text = identityLine,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text = "Signed in as ${state.accountEmail}. Sign in with this same email on MEMORA Web to sync automatically.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = onSyncNow,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
              ) {
                Text("Sync Now")
              }
              OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
              ) {
                Text("Sign Out")
              }
            }
          } else {
            Text(
              text = "Sign in or create an account to automatically sync memories & reminders between your phone and PC.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(false to "Sign In", true to "Sign Up").forEach { (mode, label) ->
                val active = isSignUpMode == mode
                Surface(
                  color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .clickable { isSignUpMode = mode },
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                  )
                }
              }
            }
            if (isSignUpMode) {
              OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                value = usernameDraft,
                onValueChange = { usernameDraft = it },
                label = { Text("Username") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              )
            }
            OutlinedTextField(
              value = emailDraft,
              onValueChange = { emailDraft = it },
              label = { Text("Email address") },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = passwordDraft,
              onValueChange = { passwordDraft = it },
              label = { Text("Password (6+ characters)") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            )
            Button(
              onClick = {
                if (isSignUpMode) onSignUpEmail(nameDraft, usernameDraft, emailDraft, passwordDraft)
                else onSignInEmail(emailDraft, passwordDraft)
              },
              enabled = !state.authBusy,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              if (state.authBusy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.size(8.dp))
              }
              Text(if (isSignUpMode) "Create Synced Account" else "Sign In & Sync")
            }
            OutlinedButton(
              onClick = onGoogleSignIn,
              enabled = !state.authBusy,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              GoogleGLogo(modifier = Modifier.size(16.dp))
              Spacer(Modifier.size(8.dp))
              Text("Continue with Google")
            }
          }
        }
      }

      // 1b. Appearance (Light / Dark Mode inside Settings)
      Surface(
        color = palette.surfaceSoft,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleTheme() }
            .padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = if (state.darkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Column {
              Text("Appearance", style = MaterialTheme.typography.titleSmall)
              Text(
                text = if (state.darkMode) "Dark mode enabled" else "Light mode enabled",
                style = MaterialTheme.typography.bodySmall,
                color = palette.mutedText,
              )
            }
          }
          Switch(
            checked = state.darkMode,
            onCheckedChange = { onToggleTheme() },
          )
        }
      }

      // 2. Automatic Over-the-Air (OTA) App Updates
      Surface(
        color = palette.surfaceSoft,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (state.updateAvailable) MaterialTheme.colorScheme.primary else palette.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
              Text("App Updates", style = MaterialTheme.typography.titleSmall)
            }
            Text(
              text = "Installed v${state.appVersionName}",
              fontFamily = DataMono,
              fontSize = 11.sp,
              color = palette.mutedText,
            )
          }
          Text(
            text = if (state.updateAvailable) {
              "A newer version (v${state.remoteConfig.latestAppVersionName}) is available."
            } else {
              "Up to date — you are running the latest version."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.updateAvailable) MaterialTheme.colorScheme.primary else palette.onDevice,
          )
          if (state.updateDownloading) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Downloading v${state.remoteConfig.latestAppVersionName}...", style = MaterialTheme.typography.bodySmall)
                Text("${state.updateDownloadProgress}%", fontFamily = DataMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
              LinearProgressIndicator(
                progress = { state.updateDownloadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = onCheckForUpdate,
              enabled = !state.updateCheckBusy && !state.updateDownloading,
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(10.dp),
            ) {
              Text(if (state.updateCheckBusy) "Checking..." else "Check for Update")
            }
            if (state.updateAvailable && state.remoteConfig.apkDownloadUrl.isNotBlank()) {
              Button(
                onClick = onDownloadUpdate,
                enabled = !state.updateDownloading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
              ) {
                Text(if (state.updateDownloading) "${state.updateDownloadProgress}%" else "Download v${state.remoteConfig.latestAppVersionName}")
              }
            }
          }
        }
      }

      // Default Inference Policy
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Default Inference Policy", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
            "prefer_on_device" to "Prefer Local",
            "only_on_device" to "Local Only",
            "prefer_in_cloud" to "Prefer Cloud",
          ).forEach { (id, label) ->
            val active = state.defaultPrivacyMode == id
            Surface(
              color = if (active) MaterialTheme.colorScheme.primary else palette.surfaceSoft,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .clickable { onModeChange(id) },
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
              )
            }
          }
        }
      }

      // Permission-gated automatic indexing of everything on the phone
      Surface(
        color = palette.surfaceSoft,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Auto-Index Phone Files", style = MaterialTheme.typography.titleSmall)
              Text(
                text = if (state.hasMediaPermission) {
                  "Permission granted"
                } else {
                  "Grant photo, video & audio access to index automatically"
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.mutedText,
              )
            }
            Switch(
              checked = state.autoIndexEnabled,
              onCheckedChange = onToggleAutoIndex,
            )
          }

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
              "everything" to "Everything",
              "all_photos" to "All Images",
              "screenshots" to "Screenshots",
            ).forEach { (scopeId, label) ->
              val selected = state.autoIndexScope == scopeId
              Surface(
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .weight(1f)
                  .clickable { onScopeChange(scopeId) },
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Keep auto-indexed files strictly Local Vault",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            Switch(
              checked = state.autoIndexLocalOnly,
              onCheckedChange = onAutoIndexLocalOnlyChange,
            )
          }

          // Documents (PDF, Word, text) come from folders you connect once with the system folder picker
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(
              onClick = onConnectFolder,
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(10.dp),
            ) {
              Text(
                if (state.connectedFolderCount == 0) "Connect Documents Folder"
                else "Add Another Folder (${state.connectedFolderCount})",
              )
            }
            if (state.connectedFolderCount > 0) {
              TextButton(onClick = onDisconnectFolders) { Text("Disconnect") }
            }
          }
          Text(
            text = "Pick Downloads, Documents, WhatsApp or any folder once. PDFs, Word and text files inside are read on-device.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.mutedText,
          )

          if (state.autoIndexEnabled) {
            OutlinedButton(
              onClick = onScanMediaNow,
              enabled = !state.autoIndexBusy,
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp),
            ) {
              Text(if (state.autoIndexBusy) "Scanning your phoneâ€¦" else "Scan for New Files Now")
            }
          }
        }
      }
    }
  }
}
