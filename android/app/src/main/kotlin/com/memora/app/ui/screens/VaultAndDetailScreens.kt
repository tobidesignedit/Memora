package com.memora.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.data.LocalVaultStore
import com.memora.app.data.MemoryItem
import com.memora.app.data.ReminderItem
import com.memora.app.ui.MemoraUiState
import com.memora.app.ui.theme.DataMono
import com.memora.app.ui.theme.DisplaySerif
import com.memora.app.ui.theme.GlassPanel
import com.memora.app.ui.theme.LocalMemoraColors
import kotlin.math.abs
import kotlin.math.sin
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val VaultFilterOptions = listOf(
  "all" to "All",
  "visual" to "Visual & Flyers",
  "voice" to "Voice",
  "receipt" to "Receipts",
  "link" to "Links",
  "local_only" to "Local Vault",
)

private data class PillVisuals(
  val bg: Color,
  val fg: Color,
  val label: String,
  val icon: ImageVector,
)

@Composable
fun InferencePill(
  source: String,
  privacyMode: String,
  syncStatus: String,
) {
  val palette = LocalMemoraColors.current
  val visuals = when {
    privacyMode == "only_on_device" ->
      PillVisuals(palette.onDeviceSoft, palette.onDevice, "Local Vault", Icons.Outlined.Lock)
    syncStatus == "pending" ->
      PillVisuals(palette.surfaceSoft, palette.mutedText, "Queued Offline", Icons.Outlined.CloudOff)
    source == "on_device" ->
      PillVisuals(palette.onDeviceSoft, palette.onDevice, "On-Device", Icons.Outlined.Memory)
    source == "in_cloud" ->
      PillVisuals(palette.primarySoft, MaterialTheme.colorScheme.primary, "Cloud Gemini", Icons.Outlined.Cloud)
    else ->
      PillVisuals(palette.surfaceSoft, palette.mutedText, "Local Index", Icons.Outlined.Memory)
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (privacyMode == "only_on_device" || syncStatus == "pending") {
      Icon(visuals.icon, contentDescription = null, tint = visuals.fg, modifier = Modifier.size(12.dp))
    } else {
      Box(
        modifier = Modifier
          .size(6.dp)
          .clip(CircleShape)
          .background(visuals.fg),
      )
    }
    Text(
      text = visuals.label,
      style = MaterialTheme.typography.labelSmall,
      color = if (privacyMode == "only_on_device") visuals.fg else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(
  state: MemoraUiState,
  localStore: LocalVaultStore,
  onFilterChange: (String) -> Unit,
  onQuickQueryChange: (String) -> Unit,
  onAskAiFromSearch: (String) -> Unit,
  onOpenMemory: (MemoryItem) -> Unit,
  onConfirmReminder: (ReminderItem) -> Unit,
  onOpenRemindersTab: () -> Unit,
  onOpenCapture: () -> Unit,
  onLoadDemo: () -> Unit,
  onToggleAutoIndex: (Boolean) -> Unit,
  onScanMediaNow: () -> Unit,
  onOpenAutoIndexSettings: () -> Unit,
) {
  val palette = LocalMemoraColors.current
  val hourNow = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
  val greeting = when {
    hourNow < 12 -> "Good morning."
    hourNow < 18 -> "Good afternoon."
    else -> "Good evening."
  }

  val visibleMemories = remember(state.memories, state.activeFilter, state.quickQuery) {
    val needle = state.quickQuery.trim().lowercase()
    state.memories.filter { item ->
      if (item.archived) return@filter false
      val matchesChip = when (state.activeFilter) {
        "visual" -> item.kind in setOf("screenshot", "photo", "flyer", "timetable", "ticket")
        "voice" -> item.kind == "voice"
        "receipt" -> item.kind == "receipt"
        "link" -> item.kind == "link"
        "local_only" -> item.privacyMode == "only_on_device"
        else -> true
      }
      if (!matchesChip) return@filter false
      if (needle.isEmpty()) return@filter true
      item.title.lowercase().contains(needle) ||
        item.summary.lowercase().contains(needle) ||
        item.extractedText.lowercase().contains(needle) ||
        item.tags.any { it.lowercase().contains(needle) }
    }
  }

  val reminderLookup = remember(state.reminders) {
    state.reminders
      .filter { it.memoryId != null && it.status != "completed" }
      .associateBy { it.memoryId!! }
  }
  val awaitingConfirmation = state.reminders.count { it.status == "suggested" }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 92.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text(
        text = "$greeting Everything worth keeping.",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    item {
      GlassPanel(
        shape = CircleShape,
        rimHighlight = MaterialTheme.colorScheme.primary.copy(alpha = if (palette.isDark) 0.55f else 0.35f),
        modifier = Modifier
          .fillMaxWidth()
          .height(46.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (state.quickQuery.isEmpty()) {
              Text(
                text = "Search photos, docs, receipts, voice…",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.mutedText,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = state.quickQuery,
              onValueChange = onQuickQueryChange,
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
              ),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              modifier = Modifier.fillMaxWidth(),
            )
          }
          if (state.quickQuery.isNotBlank()) {
            TextButton(
              onClick = { onAskAiFromSearch(state.quickQuery) },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
              modifier = Modifier.height(34.dp),
            ) {
              Text("Ask AI", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
          } else {
            IconButton(
              onClick = onOpenCapture,
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            ) {
              Icon(
                imageVector = Icons.Outlined.GraphicEq,
                contentDescription = "Record voice thought",
                tint = Color.White,
                modifier = Modifier.size(15.dp),
              )
            }
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        VaultFilterOptions.forEach { (filterId, label) ->
          val isSelected = state.activeFilter == filterId
          FilterChip(
            selected = isSelected,
            onClick = { onFilterChange(filterId) },
            label = {
              Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
              )
            },
            shape = CircleShape,
            border = FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = isSelected,
              borderColor = palette.borderSubtle,
              selectedBorderColor = Color.Transparent,
            ),
            colors = FilterChipDefaults.filterChipColors(
              containerColor = MaterialTheme.colorScheme.surface,
              labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
              selectedContainerColor = MaterialTheme.colorScheme.onBackground,
              selectedLabelColor = MaterialTheme.colorScheme.background,
            ),
          )
        }
      }
    }

    // Permission-gated automatic indexing prompt (hidden once enabled so it never nags repeatedly)
    if (!state.autoIndexEnabled) {
      item {
        GlassPanel(
          shape = RoundedCornerShape(16.dp),
          onClick = { onOpenAutoIndexSettings() },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Column {
                Text(
                  text = "Auto-Index Everything on Your Phone",
                  style = MaterialTheme.typography.labelLarge,
                )
                Text(
                  text = "Photos, documents, audio & video read privately with your permission",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            TextButton(onClick = { onToggleAutoIndex(true) }) {
              Text("Enable", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    if (awaitingConfirmation > 0) {
      item {
        GlassPanel(
          shape = RoundedCornerShape(20.dp),
          tintTop = palette.sparkSoft,
          tintBottom = palette.sparkSoft.copy(alpha = 0.06f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = palette.spark,
                modifier = Modifier.size(19.dp),
              )
              Text(
                text = "$awaitingConfirmation reminder${if (awaitingConfirmation == 1) "" else "s"} detected from captures",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            TextButton(onClick = onOpenRemindersTab) {
              Text("Review", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    if (visibleMemories.isEmpty()) {
      item {
        GlassPanel(
          shape = RoundedCornerShape(26.dp),
          elevated = true,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              Icons.Outlined.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(32.dp),
            )
            Text(
              text = if (state.memories.isEmpty()) "Your private glass vault is ready" else "No matching memories",
              style = MaterialTheme.typography.headlineSmall,
            )
            Text(
              text = if (state.memories.isEmpty()) {
                "Snap a flyer or receipt, record a voice thought, or share any screenshot from another app into Memora."
              } else {
                "Clear the filter or switch to Ask Memora for natural-language search."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(onClick = onOpenCapture, shape = CircleShape) {
                Text("Capture Now")
              }
            }
          }
        }
      }
    } else {
      items(visibleMemories, key = { it.id }) { mem ->
        MemoryGalleryCard(
          memory = mem,
          linkedReminder = reminderLookup[mem.id],
          localStore = localStore,
          onClick = { onOpenMemory(mem) },
          onConfirmReminder = onConfirmReminder,
        )
      }
    }
  }
}

@Composable
private fun VoiceWaveformCanvas(seed: String, activeColor: Color, mutedColor: Color) {
  val bars = remember(seed) {
    var h = 2166136261L.toInt()
    seed.forEach { ch ->
      h = (h xor ch.code) * 16777619
    }
    List(24) { i ->
      h = h xor (h shl 13)
      h = h xor (h ushr 17)
      h = h xor (h shl 5)
      val norm = (abs(h) % 100) / 100f
      val env = sin((i / 23f) * Math.PI).toFloat()
      (0.22f + (norm * 0.58f + env * 0.42f) * 0.72f).coerceIn(0.2f, 0.96f)
    }
  }
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(34.dp),
  ) {
    val gap = 4.dp.toPx()
    val count = bars.size
    val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)
    bars.forEachIndexed { idx, frac ->
      val barH = size.height * frac
      val top = (size.height - barH) / 2f
      drawRoundRect(
        color = if (idx < 9) activeColor else mutedColor,
        topLeft = Offset(idx * (barW + gap), top),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryGalleryCard(
  memory: MemoryItem,
  linkedReminder: ReminderItem?,
  localStore: LocalVaultStore,
  onClick: () -> Unit,
  onConfirmReminder: (ReminderItem) -> Unit,
) {
  val palette = LocalMemoraColors.current
  val bitmap: Bitmap? = remember(memory.mediaLocalKey, memory.mediaThumbnail) {
    localStore.loadLocalBitmap(memory.mediaLocalKey) ?: localStore.loadLocalBitmap(memory.mediaThumbnail)
  }
  val formattedDate = remember(memory.createdAt) {
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(memory.createdAt))
  }
  val isVoiceCard = memory.kind == "voice" && bitmap == null
  val isNoteOrReceipt = (memory.kind == "note" || memory.kind == "receipt") && bitmap == null
  val cardTint = when {
    isVoiceCard -> palette.voiceSurface
    isNoteOrReceipt -> palette.notePaper
    else -> null
  }

  GlassPanel(
    shape = RoundedCornerShape(16.dp),
    tintTop = cardTint,
    tintBottom = cardTint,
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column {
      if (bitmap != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = memory.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .height(210.dp),
          )
          if (memory.privacyMode == "only_on_device") {
            Surface(
              color = Color(0xBA161513),
              shape = CircleShape,
              modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFF47C48B), modifier = Modifier.size(11.dp))
                Text("Private", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF5F4F0))
              }
            }
          }
        }
      }

      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
      ) {
        if (isVoiceCard) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(
              modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
              contentAlignment = Alignment.Center,
            ) {
              Icon(Icons.Outlined.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Text("Voice Memo · $formattedDate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          VoiceWaveformCanvas(
            seed = memory.id,
            activeColor = MaterialTheme.colorScheme.primary,
            mutedColor = palette.mutedText.copy(alpha = 0.35f),
          )
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "${memory.category} · $formattedDate",
              style = MaterialTheme.typography.bodySmall,
              color = palette.mutedText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              if (memory.pinned) {
                Icon(
                  Icons.Outlined.PushPin,
                  contentDescription = "Pinned",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(14.dp),
                )
              }
              if (memory.favorite) {
                Icon(
                  Icons.Outlined.Star,
                  contentDescription = "Favorite",
                  tint = palette.spark,
                  modifier = Modifier.size(15.dp),
                )
              }
            }
          }
        }

        if (memory.kind == "receipt" && memory.entities.amounts.isNotEmpty() && bitmap == null) {
          Text(
            text = memory.entities.amounts.first(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        Text(
          text = memory.title,
          style = if (isVoiceCard || isNoteOrReceipt || memory.kind == "link") {
            MaterialTheme.typography.headlineSmall
          } else {
            MaterialTheme.typography.titleMedium
          },
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Text(
          text = if (isVoiceCard) "\"${memory.summary}\"" else memory.summary,
          style = if (isVoiceCard) {
            MaterialTheme.typography.bodyMedium.copy(fontFamily = DisplaySerif, fontStyle = FontStyle.Italic)
          } else {
            MaterialTheme.typography.bodyMedium
          },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )

        if (memory.entities.amounts.isNotEmpty() || memory.tags.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            memory.entities.amounts.take(2).forEach { amount ->
              Surface(color = palette.surfaceSoft, shape = RoundedCornerShape(6.dp)) {
                Text(
                  text = amount,
                  fontFamily = DataMono,
                  fontSize = 11.sp,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
              }
            }
            memory.tags.take(3).forEach { tag ->
              Surface(color = palette.surfaceSoft, shape = CircleShape) {
                Text(
                  text = "#$tag",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.mutedText,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
          }
        }

        if (linkedReminder != null) {
          Surface(
            color = palette.sparkSoft,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = linkedReminder.title,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${linkedReminder.dueDateIso}${if (!linkedReminder.dueTime.isNullOrBlank()) " · ${linkedReminder.dueTime}" else ""}",
                  fontFamily = DataMono,
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (linkedReminder.status == "suggested") {
                Button(
                  onClick = { onConfirmReminder(linkedReminder) },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.height(32.dp),
                ) {
                  Text("Set", fontSize = 12.sp)
                }
              } else {
                Text(
                  text = "Active",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.onDevice,
                )
              }
            }
          }
        }

        HorizontalDivider(color = palette.borderSubtle)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          InferencePill(memory.inferenceSource, memory.privacyMode, memory.syncStatus)
          Text(
            text = "${memory.inferenceLatencyMs}ms",
            fontFamily = DataMono,
            fontSize = 11.sp,
            color = palette.mutedText,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryDetailSheet(
  memory: MemoryItem,
  reminders: List<ReminderItem>,
  localStore: LocalVaultStore,
  onDismiss: () -> Unit,
  onTogglePin: () -> Unit,
  onToggleFavorite: () -> Unit,
  onSpeak: (String) -> Unit,
  onSaveNote: (String) -> Unit,
  onDelete: () -> Unit,
  onConfirmReminder: (ReminderItem) -> Unit,
) {
  val palette = LocalMemoraColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var noteDraft by remember(memory.id) { mutableStateOf(memory.personalNote.orEmpty()) }
  val bitmap = remember(memory.mediaLocalKey, memory.mediaThumbnail) {
    localStore.loadLocalBitmap(memory.mediaLocalKey) ?: localStore.loadLocalBitmap(memory.mediaThumbnail)
  }
  val relatedReminders = remember(reminders, memory.id) {
    reminders.filter { it.memoryId == memory.id }
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        InferencePill(memory.inferenceSource, memory.privacyMode, memory.syncStatus)
        Row {
          IconButton(onClick = { onSpeak("${memory.title}. ${memory.summary}") }) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "Listen")
          }
          IconButton(onClick = onTogglePin) {
            Icon(
              Icons.Outlined.PushPin,
              contentDescription = "Pin",
              tint = if (memory.pinned) MaterialTheme.colorScheme.primary else palette.mutedText,
            )
          }
          IconButton(onClick = onToggleFavorite) {
            Icon(
              if (memory.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
              contentDescription = "Favorite",
              tint = if (memory.favorite) palette.spark else palette.mutedText,
            )
          }
          IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
          }
        }
      }

      Text(text = memory.title, style = MaterialTheme.typography.headlineMedium)
      Text(
        text = "${memory.category} · ${memory.inferenceModel ?: "hybrid"} · ${memory.inferenceLatencyMs}ms",
        fontFamily = DataMono,
        fontSize = 11.sp,
        color = palette.mutedText,
      )

      if (bitmap != null) {
        Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = memory.title,
          contentScale = ContentScale.FillWidth,
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        )
      }

      Text(text = memory.summary, style = MaterialTheme.typography.bodyLarge)

      if (relatedReminders.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Visual Intelligence Reminders", style = MaterialTheme.typography.titleSmall)
          relatedReminders.forEach { rem ->
            Surface(
              color = palette.sparkSoft,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, palette.borderSubtle),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(rem.title, style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = "${rem.dueDateIso}${if (!rem.dueTime.isNullOrBlank()) " · ${rem.dueTime}" else ""}",
                    fontFamily = DataMono,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (rem.status == "suggested") {
                  Button(onClick = { onConfirmReminder(rem) }, shape = RoundedCornerShape(10.dp)) {
                    Text("Confirm")
                  }
                } else {
                  Text(rem.status.uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.onDevice)
                }
              }
            }
          }
        }
      }

      if (memory.actionItems.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Action Items", style = MaterialTheme.typography.titleSmall)
          memory.actionItems.forEach { action ->
            Text("• $action", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      val entitiesList = memory.entities.dates + memory.entities.amounts +
        memory.entities.organizations + memory.entities.people + memory.entities.locations
      if (entitiesList.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Extracted Entities", style = MaterialTheme.typography.titleSmall)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entitiesList.forEach { value ->
              Surface(color = palette.surfaceSoft, shape = RoundedCornerShape(8.dp)) {
                Text(
                  text = value,
                  fontFamily = DataMono,
                  fontSize = 12.sp,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          }
        }
      }

      if (memory.extractedText.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Extracted Text / OCR", style = MaterialTheme.typography.titleSmall)
          Surface(color = palette.surfaceSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
              text = memory.extractedText,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
      }

      OutlinedTextField(
        value = noteDraft,
        onValueChange = { noteDraft = it },
        label = { Text("Personal Annotation") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        shape = RoundedCornerShape(12.dp),
      )

      Button(
        onClick = { onSaveNote(noteDraft) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
      ) {
        Text("Save Annotation")
      }
    }
  }
}
