package com.memora.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.data.MemoryItem
import com.memora.app.data.ReminderItem
import com.memora.app.ui.MemoraUiState
import com.memora.app.ui.theme.DataMono
import com.memora.app.ui.theme.GlassPanel
import com.memora.app.ui.theme.LocalMemoraColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun RemindersScreen(
  state: MemoraUiState,
  onConfirm: (ReminderItem) -> Unit,
  onComplete: (ReminderItem) -> Unit,
  onSnooze: (ReminderItem) -> Unit,
  onDelete: (String) -> Unit,
  onCreateManual: (title: String, dateIso: String, time: String, venue: String, category: String) -> Unit,
  onOpenMemory: (MemoryItem) -> Unit,
) {
  val palette = LocalMemoraColors.current
  val byId = remember(state.memories) { state.memories.associateBy { it.id } }
  var showManualForm by remember { mutableStateOf(false) }
  var titleInput by remember { mutableStateOf("") }
  var dateInput by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
  var timeInput by remember { mutableStateOf("09:00") }
  var venueInput by remember { mutableStateOf("") }

  val suggested = state.reminders.filter { it.status == "suggested" }
  val upcoming = state.reminders.filter { it.status == "confirmed" || it.status == "snoozed" }
  val completed = state.reminders.filter { it.status == "completed" }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 104.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Visual Reminders", style = MaterialTheme.typography.displaySmall)
          Text(
            text = "Dates from flyers, exam timetables, and payment notices become local Android notifications.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { showManualForm = !showManualForm }) {
          Icon(Icons.Outlined.Add, contentDescription = "Add manual reminder")
        }
      }
    }

    if (showManualForm) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, palette.borderStrong),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = titleInput,
              onValueChange = { titleInput = it },
              label = { Text("Reminder title") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = dateInput,
                onValueChange = { dateInput = it },
                label = { Text("YYYY-MM-DD") },
                modifier = Modifier.weight(1f),
                singleLine = true,
              )
              OutlinedTextField(
                value = timeInput,
                onValueChange = { timeInput = it },
                label = { Text("HH:mm") },
                modifier = Modifier.weight(0.7f),
                singleLine = true,
              )
            }
            OutlinedTextField(
              value = venueInput,
              onValueChange = { venueInput = it },
              label = { Text("Venue (optional)") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              TextButton(onClick = { showManualForm = false }) { Text("Cancel") }
              Button(
                onClick = {
                  if (titleInput.isNotBlank()) {
                    onCreateManual(titleInput, dateInput, timeInput, venueInput, "event")
                    titleInput = ""
                    venueInput = ""
                    showManualForm = false
                  }
                },
                shape = RoundedCornerShape(10.dp),
              ) { Text("Schedule") }
            }
          }
        }
      }
    }

    if (suggested.isNotEmpty()) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = palette.spark, modifier = Modifier.size(18.dp))
          Text("Detected from Captures (${suggested.size})", style = MaterialTheme.typography.titleSmall)
        }
      }
      items(suggested, key = { it.id }) { rem ->
        ReminderItemCard(
          rem = rem,
          sourceMemory = rem.memoryId?.let { byId[it] },
          onConfirm = { onConfirm(rem) },
          onComplete = { onComplete(rem) },
          onSnooze = { onSnooze(rem) },
          onDelete = { onDelete(rem.id) },
          onOpenMemory = onOpenMemory,
        )
      }
    }

    item {
      Text("Upcoming Schedule (${upcoming.size})", style = MaterialTheme.typography.titleSmall)
    }

    if (upcoming.isEmpty()) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, palette.borderSubtle),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "No scheduled reminders yet. Capture a flyer, exam timetable, or receipt and tap Confirm to schedule an offline Android alert.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.mutedText,
            modifier = Modifier.padding(18.dp),
          )
        }
      }
    } else {
      items(upcoming, key = { it.id }) { rem ->
        ReminderItemCard(
          rem = rem,
          sourceMemory = rem.memoryId?.let { byId[it] },
          onConfirm = { onConfirm(rem) },
          onComplete = { onComplete(rem) },
          onSnooze = { onSnooze(rem) },
          onDelete = { onDelete(rem.id) },
          onOpenMemory = onOpenMemory,
        )
      }
    }

    if (completed.isNotEmpty()) {
      item {
        Text("Completed (${completed.size})", style = MaterialTheme.typography.titleSmall, color = palette.mutedText)
      }
      items(completed, key = { it.id }) { rem ->
        ReminderItemCard(
          rem = rem,
          sourceMemory = rem.memoryId?.let { byId[it] },
          onConfirm = { onConfirm(rem) },
          onComplete = { onComplete(rem) },
          onSnooze = { onSnooze(rem) },
          onDelete = { onDelete(rem.id) },
          onOpenMemory = onOpenMemory,
        )
      }
    }
  }
}

@Composable
private fun ReminderItemCard(
  rem: ReminderItem,
  sourceMemory: MemoryItem?,
  onConfirm: () -> Unit,
  onComplete: () -> Unit,
  onSnooze: () -> Unit,
  onDelete: () -> Unit,
  onOpenMemory: (MemoryItem) -> Unit,
) {
  val palette = LocalMemoraColors.current
  val parsedDate = remember(rem.dueDateIso) {
    runCatching { LocalDate.parse(rem.dueDateIso) }.getOrNull()
  }
  val monthLabel = parsedDate?.month?.getDisplayName(TextStyle.SHORT, Locale.US)?.uppercase() ?: "DATE"
  val dayLabel = parsedDate?.dayOfMonth?.toString()?.padStart(2, '0') ?: "--"
  val isSuggested = rem.status == "suggested"

  GlassPanel(
    shape = RoundedCornerShape(24.dp),
    rimHighlight = if (rem.conflictWithId != null) palette.spark else null,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
      ) {
        // Physical Calendar Date Tile
        Surface(
          color = if (isSuggested) palette.primarySoft else palette.surfaceSoft,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(
            1.dp,
            if (isSuggested) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else palette.borderSubtle,
          ),
          modifier = Modifier
            .width(54.dp)
            .height(62.dp),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(
              text = monthLabel,
              fontFamily = DataMono,
              fontSize = 10.sp,
              color = if (isSuggested) MaterialTheme.colorScheme.primary else palette.mutedText,
            )
            Text(
              text = dayLabel,
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
          ) {
            Text(
              text = rem.title,
              style = MaterialTheme.typography.titleMedium,
              textDecoration = if (rem.status == "completed") TextDecoration.LineThrough else TextDecoration.None,
              modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
              Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = palette.mutedText, modifier = Modifier.size(16.dp))
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = palette.mutedText, modifier = Modifier.size(13.dp))
            Text(
              text = buildString {
                append(rem.category.replaceFirstChar { it.uppercase() })
                if (!rem.dueTime.isNullOrBlank()) append(" · ${rem.dueTime}")
                if (!rem.venue.isNullOrBlank()) append(" · ${rem.venue}")
              },
              fontFamily = DataMono,
              fontSize = 11.5.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (rem.conflictWithId != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = palette.spark, modifier = Modifier.size(13.dp))
              Text("Time conflict detected", style = MaterialTheme.typography.labelSmall, color = palette.spark)
            }
          }

          if (!rem.notes.isNullOrBlank()) {
            Text(rem.notes, style = MaterialTheme.typography.bodySmall, color = palette.mutedText)
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (sourceMemory != null) {
          TextButton(onClick = { onOpenMemory(sourceMemory) }, contentPadding = PaddingValues(0.dp)) {
            Text("View Source", fontSize = 12.sp)
          }
        } else {
          Spacer(Modifier.size(1.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (isSuggested) {
            Button(onClick = onConfirm, shape = CircleShape) {
              Text("Confirm & Notify", fontSize = 12.sp)
            }
          } else if (rem.status != "completed") {
            OutlinedButton(
              onClick = onSnooze,
              shape = CircleShape,
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
              Text("+1d", fontSize = 12.sp)
            }
            Button(
              onClick = onComplete,
              shape = CircleShape,
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
              Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(Modifier.size(4.dp))
              Text("Done", fontSize = 12.sp)
            }
          }
        }
      }
    }
  }
}
