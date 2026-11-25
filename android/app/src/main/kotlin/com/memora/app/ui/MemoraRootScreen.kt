package com.memora.app.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memora.app.ui.screens.AuthLandingScreen
import com.memora.app.ui.screens.CaptureBottomSheet
import com.memora.app.ui.screens.HybridTelemetrySheet
import com.memora.app.ui.screens.MemoryDetailSheet
import com.memora.app.ui.screens.RemindersScreen
import com.memora.app.ui.screens.SearchAssistantScreen
import com.memora.app.ui.screens.ThreadsScreen
import com.memora.app.ui.screens.VaultScreen
import com.memora.app.ui.theme.GalleryDarkSurface
import com.memora.app.ui.theme.GalleryLightInk
import com.memora.app.ui.theme.GlassPill
import com.memora.app.ui.theme.LocalMemoraColors
import com.memora.app.ui.theme.MemoraAtmosphericBackdrop

@Composable
private fun MemoraGlassMark(
  primaryDot: Color,
  emeraldDot: Color,
) {
  Canvas(modifier = Modifier.size(30.dp)) {
    val w = size.width
    val h = size.height
    drawRoundRect(
      color = GalleryLightInk,
      cornerRadius = CornerRadius(w * 0.26f, h * 0.26f),
    )
    val p1 = Offset(w * 0.35f, h * 0.39f)
    val p2 = Offset(w * 0.67f, h * 0.35f)
    val p3 = Offset(w * 0.54f, h * 0.68f)
    val lineColor = Color.White.copy(alpha = 0.45f)
    val stroke = w * 0.045f
    drawLine(lineColor, p1, p2, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(lineColor, p1, p3, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(lineColor, p2, p3, strokeWidth = stroke, cap = StrokeCap.Round)
    drawCircle(primaryDot, radius = w * 0.115f, center = p1)
    drawCircle(emeraldDot, radius = w * 0.075f, center = p2)
    drawCircle(Color.White, radius = w * 0.088f, center = p3)
  }
}

@Composable
private fun DockGlassTab(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  badgeCount: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMemoraColors.current
  val fg = if (selected) MaterialTheme.colorScheme.primary else palette.mutedText
  val activeBg = palette.surfaceSoft
  Box(
    modifier = modifier
      .clip(CircleShape)
      .background(if (selected) activeBg else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Box {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(19.dp))
        if (badgeCount > 0) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .size(7.dp)
              .clip(CircleShape)
              .background(palette.spark),
          )
        }
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = fg,
      )
    }
  }
}

@Composable
private fun GlowingRotatingOrb(
  primary: Color,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "ask_memora_orb")
  val rotation by transition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "orb_rotation",
  )
  val pulse by transition.animateFloat(
    initialValue = 0.86f,
    targetValue = 1.12f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "orb_pulse",
  )

  Canvas(modifier = modifier.size(18.dp)) {
    val r = size.minDimension / 2f
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(primary.copy(alpha = 0.45f), accent.copy(alpha = 0.18f), Color.Transparent),
        center = center,
        radius = r * 1.25f * pulse,
      ),
      radius = r * 1.25f * pulse,
      center = center,
    )
    rotate(degrees = rotation, pivot = center) {
      drawCircle(
        brush = Brush.sweepGradient(
          colors = listOf(primary, accent, Color(0xFFA855F7), primary),
          center = center,
        ),
        radius = r * 0.68f,
        center = center,
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
      )
    }
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color.White, primary),
        center = center,
        radius = r * 0.42f,
      ),
      radius = r * 0.36f * (0.95f + (pulse - 1f) * 0.3f),
      center = center,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoraRootScreen(
  viewModel: MemoraViewModel,
  pickedBitmap: Bitmap?,
  voiceCaptureDraft: String,
  voiceSearchDraft: String,
  onLaunchCamera: () -> Unit,
  onLaunchGallery: () -> Unit,
  onLaunchVoiceCapture: () -> Unit,
  onLaunchVoiceSearch: () -> Unit,
  onRequestAutoIndexToggle: (Boolean) -> Unit,
  onConnectFolder: () -> Unit,
  onClearPickedMedia: () -> Unit,
) {
  val state by viewModel.ui.collectAsStateWithLifecycle()
  val palette = LocalMemoraColors.current
  val context = LocalContext.current
  val snackbarHost = remember { SnackbarHostState() }

  LaunchedEffect(state.toastMessage) {
    val msg = state.toastMessage
    if (!msg.isNullOrBlank()) {
      snackbarHost.showSnackbar(msg)
      viewModel.clearToast()
    }
  }

  val pendingReminders = state.reminders.count { it.status == "suggested" || it.status == "confirmed" }
  val hasLocalRun = state.benchmarks.any { it.source == "on_device" }

  if (state.accountEmail == null && !state.dismissedAuthLanding) {
    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(snackbarHost) },
    ) { _ ->
      AuthLandingScreen(
        authBusy = state.authBusy,
        onSignIn = viewModel::signInWithEmail,
        onSignUp = viewModel::signUpWithEmail,
        onGoogleSignIn = { (context as? Activity)?.let { viewModel.signInWithGoogle(it) } },
        onContinueOffline = viewModel::dismissAuthLanding,
      )
    }
    return
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // Full-screen living atmospheric light field behind every translucent pane
    MemoraAtmosphericBackdrop()

    Scaffold(
      containerColor = Color.Transparent,
      contentColor = MaterialTheme.colorScheme.onBackground,
      snackbarHost = { SnackbarHost(snackbarHost) },
      topBar = {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
          ),
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              MemoraGlassMark(
                primaryDot = MaterialTheme.colorScheme.primary,
                emeraldDot = palette.onDevice,
              )
              Text(
                "Memora",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
              )
            }
          },
          actions = {
            GlassPill(
              elevated = true,
              onClick = { viewModel.openTelemetry(true) },
              modifier = Modifier
                .padding(end = 6.dp)
                .size(36.dp),
            ) {
              Box(modifier = Modifier.align(Alignment.Center)) {
                Icon(
                  imageVector = Icons.Outlined.Settings,
                  contentDescription = "Settings",
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(18.dp),
                )
                if (state.updateAvailable) {
                  Box(
                    modifier = Modifier
                      .align(Alignment.TopEnd)
                      .size(7.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary),
                  )
                }
              }
            }
            GlassPill(
              elevated = true,
              onClick = { viewModel.selectTab(MemoraTab.SEARCH) },
              modifier = Modifier.padding(end = 6.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
              ) {
                GlowingRotatingOrb(
                  primary = MaterialTheme.colorScheme.primary,
                  accent = palette.onDevice,
                )
                Text(
                  text = "Ask Memora",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }

            GlassPill(
              elevated = true,
              onClick = { viewModel.toggleTheme() },
              modifier = Modifier
                .padding(end = 10.dp)
                .size(36.dp),
            ) {
              Icon(
                imageVector = if (state.darkMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = "Toggle light and dark theme",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                  .size(18.dp)
                  .align(Alignment.Center),
              )
            }
          },
        )
      },
    ) { innerPadding ->
      Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (state.updateAvailable || state.updateDownloading) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp)
              .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.primary)
              .clickable(enabled = !state.updateDownloading) { viewModel.downloadAndInstallUpdate() }
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.SystemUpdateAlt,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = if (state.updateDownloading) {
                    "Downloading MEMORA v${state.remoteConfig.latestAppVersionName}…"
                  } else {
                    "MEMORA v${state.remoteConfig.latestAppVersionName} Ready"
                  },
                  style = MaterialTheme.typography.labelMedium,
                  color = Color.White,
                )
              }
              Text(
                text = if (state.updateDownloading) "${state.updateDownloadProgress}%" else "Download Update",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
              )
            }
            if (state.updateDownloading) {
              LinearProgressIndicator(
                progress = { state.updateDownloadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.28f),
              )
            }
          }
        }

        Box(modifier = Modifier.fillMaxSize()) {
          when (state.activeTab) {
          MemoraTab.VAULT -> VaultScreen(
            state = state,
            localStore = viewModel.repo.localStore,
            onFilterChange = viewModel::setFilter,
            onQuickQueryChange = viewModel::setQuickQuery,
            onAskAiFromSearch = { q -> viewModel.runSearch(q) },
            onOpenMemory = viewModel::openMemory,
            onConfirmReminder = viewModel::confirmReminder,
            onOpenRemindersTab = { viewModel.selectTab(MemoraTab.REMINDERS) },
            onOpenCapture = { viewModel.openCapture(true) },
            onLoadDemo = viewModel::loadDemoMemories,
            onToggleAutoIndex = onRequestAutoIndexToggle,
            onScanMediaNow = { viewModel.scanMediaNow(silent = false) },
            onOpenAutoIndexSettings = { viewModel.openTelemetry(true) },
          )

          MemoraTab.SEARCH -> SearchAssistantScreen(
            state = state,
            voiceSearchText = voiceSearchDraft,
            onLaunchVoiceSearch = onLaunchVoiceSearch,
            onSubmitQuery = viewModel::runSearch,
            onOpenMemory = viewModel::openMemory,
            onSpeak = viewModel::speakText,
          )

          MemoraTab.THREADS -> ThreadsScreen(
            state = state,
            onSynthesize = viewModel::synthesizeThread,
            onOpenMemory = viewModel::openMemory,
          )

          MemoraTab.REMINDERS -> RemindersScreen(
            state = state,
            onConfirm = viewModel::confirmReminder,
            onComplete = viewModel::completeReminder,
            onSnooze = viewModel::snoozeReminder,
            onDelete = viewModel::deleteReminder,
            onCreateManual = viewModel::createManualReminder,
            onOpenMemory = viewModel::openMemory,
          )
        }

        // Crisp Solid Floating Pill Bottom Dock (Zero Bleed-Through)
        GlassPill(
          elevated = true,
          tintTop = if (palette.isDark) GalleryDarkSurface else Color.White,
          tintBottom = if (palette.isDark) GalleryDarkSurface else Color.White,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            DockGlassTab(
              label = "Vault",
              icon = Icons.Outlined.Layers,
              selected = state.activeTab == MemoraTab.VAULT,
              badgeCount = 0,
              onClick = { viewModel.selectTab(MemoraTab.VAULT) },
              modifier = Modifier.weight(1f),
            )
            DockGlassTab(
              label = "Ask",
              icon = Icons.Outlined.Search,
              selected = state.activeTab == MemoraTab.SEARCH,
              badgeCount = 0,
              onClick = { viewModel.selectTab(MemoraTab.SEARCH) },
              modifier = Modifier.weight(1f),
            )
            DockGlassTab(
              label = "Threads",
              icon = Icons.Outlined.AccountTree,
              selected = state.activeTab == MemoraTab.THREADS,
              badgeCount = 0,
              onClick = { viewModel.selectTab(MemoraTab.THREADS) },
              modifier = Modifier.weight(1f),
            )
            DockGlassTab(
              label = "Remind",
              icon = Icons.Outlined.Notifications,
              selected = state.activeTab == MemoraTab.REMINDERS,
              badgeCount = pendingReminders,
              onClick = { viewModel.selectTab(MemoraTab.REMINDERS) },
              modifier = Modifier.weight(1f),
            )
            // Primary Indigo Capture Button (Genesis Indigo CTA)
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { viewModel.openCapture(true) },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Outlined.Add,
                contentDescription = "Capture memory",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
              )
            }
          }
        }
      }
      }
    }
  }

  if (state.captureSheetOpen) {
    CaptureBottomSheet(
      defaultMode = state.defaultPrivacyMode,
      busy = state.captureBusy,
      incomingShare = state.incomingShare,
      pickedBitmap = pickedBitmap,
      voiceDraft = voiceCaptureDraft,
      onLaunchCamera = onLaunchCamera,
      onLaunchGallery = onLaunchGallery,
      onLaunchVoiceInput = onLaunchVoiceCapture,
      onClearMedia = onClearPickedMedia,
      onDismiss = {
        onClearPickedMedia()
        viewModel.openCapture(false)
      },
      onSubmit = { rawText, url, bitmap, kindHint, mode ->
        viewModel.submitCapture(rawText, url, bitmap, kindHint, mode)
        onClearPickedMedia()
      },
    )
  }

  state.selectedMemory?.let { mem ->
    MemoryDetailSheet(
      memory = mem,
      reminders = state.reminders,
      localStore = viewModel.repo.localStore,
      onDismiss = { viewModel.openMemory(null) },
      onTogglePin = { viewModel.togglePin(mem) },
      onToggleFavorite = { viewModel.toggleFavorite(mem) },
      onSpeak = viewModel::speakText,
      onSaveNote = { note -> viewModel.savePersonalNote(mem, note) },
      onDelete = { viewModel.deleteMemory(mem.id) },
      onConfirmReminder = viewModel::confirmReminder,
    )
  }

  if (state.telemetrySheetOpen) {
    HybridTelemetrySheet(
      state = state,
      onDismiss = { viewModel.openTelemetry(false) },
      onModeChange = viewModel::setDefaultPrivacyMode,
      onToggleAutoIndex = onRequestAutoIndexToggle,
      onScopeChange = viewModel::setAutoIndexScope,
      onConnectFolder = onConnectFolder,
      onDisconnectFolders = viewModel::disconnectAllFolders,
      onAutoIndexLocalOnlyChange = viewModel::setAutoIndexLocalOnly,
      onScanMediaNow = { viewModel.scanMediaNow(silent = false) },
      onRunSideBySideBenchmark = viewModel::runSideBySideBenchmark,
      onSignInEmail = viewModel::signInWithEmail,
      onSignUpEmail = viewModel::signUpWithEmail,
      onGoogleSignIn = { (context as? Activity)?.let { viewModel.signInWithGoogle(it) } },
      onSignOut = viewModel::signOutAccount,
      onCheckForUpdate = viewModel::checkForAppUpdate,
      onDownloadUpdate = viewModel::downloadAndInstallUpdate,
      onSyncNow = viewModel::syncNow,
      onLoadDemo = {
        viewModel.loadDemoMemories()
        viewModel.openTelemetry(false)
      },
    )
  }
}
