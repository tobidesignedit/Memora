package com.memora.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.data.MemoryItem
import com.memora.app.ui.MemoraUiState
import com.memora.app.ui.theme.DataMono
import com.memora.app.ui.theme.GlassPanel
import com.memora.app.ui.theme.LocalMemoraColors

private val DefaultSearchPrompts = listOf(
  "Find any upcoming payment deadlines or bills",
  "When and where is my next exam?",
  "Summarize my voice notes and action items",
  "What events did I save flyers for?",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchAssistantScreen(
  state: MemoraUiState,
  voiceSearchText: String,
  onLaunchVoiceSearch: () -> Unit,
  onSubmitQuery: (String) -> Unit,
  onOpenMemory: (MemoryItem) -> Unit,
  onSpeak: (String) -> Unit,
) {
  val palette = LocalMemoraColors.current
  var inputText by remember(voiceSearchText) {
    mutableStateOf(voiceSearchText.ifBlank { state.quickQuery })
  }
  val byId = remember(state.memories) { state.memories.associateBy { it.id } }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 104.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ask Memora", style = MaterialTheme.typography.displaySmall)
        Text(
          text = "Query screenshots, receipts, voice notes, and links naturally — answers link back to sources.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          modifier = Modifier.weight(1f),
          placeholder = { Text("Ask anything in your vault…") },
          trailingIcon = {
            IconButton(onClick = onLaunchVoiceSearch) {
              Icon(Icons.Outlined.Mic, contentDescription = "Voice search")
            }
          },
          singleLine = true,
          shape = CircleShape,
        )
        Button(
          onClick = { onSubmitQuery(inputText) },
          enabled = inputText.isNotBlank() && !state.searchBusy,
          shape = CircleShape,
          modifier = Modifier.height(52.dp),
        ) {
          if (state.searchBusy) {
            CircularProgressIndicator(
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(18.dp),
            )
          } else {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Ask")
          }
        }
      }
    }

    if (state.searchHistory.isEmpty()) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Suggested questions", style = MaterialTheme.typography.labelMedium, color = palette.mutedText)
          DefaultSearchPrompts.forEach { sample ->
            GlassPanel(
              shape = RoundedCornerShape(20.dp),
              onClick = {
                inputText = sample
                onSubmitQuery(sample)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(sample, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
          }
        }
      }
    } else {
      items(state.searchHistory) { result ->
        GlassPanel(
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(result.query, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
              IconButton(onClick = { onSpeak(result.answer) }) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "Listen")
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              InferencePill(result.source, "prefer_on_device", "synced")
              Text(
                text = "${result.modelUsed} · ${result.latencyMs}ms",
                fontFamily = DataMono,
                fontSize = 11.sp,
                color = palette.mutedText,
              )
            }

            Text(
              text = result.answer,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (result.citations.isNotEmpty()) {
              HorizontalDivider(color = palette.borderSubtle)
              Text(
                text = "Cited Memories (${result.citations.size})",
                style = MaterialTheme.typography.labelMedium,
                color = palette.mutedText,
              )
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                result.citations.forEach { cit ->
                  val mem = byId[cit.memoryId]
                  Surface(
                    color = palette.surfaceSoft,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.clickable { if (mem != null) onOpenMemory(mem) },
                  ) {
                    Text(
                      text = cit.title.ifBlank { mem?.title ?: "Source" },
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                  }
                }
              }
            }

            if (result.suggestedFollowUps.isNotEmpty()) {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                result.suggestedFollowUps.forEach { fup ->
                  Surface(
                    color = palette.surfaceSoft,
                    shape = CircleShape,
                    modifier = Modifier.clickable {
                      inputText = fup
                      onSubmitQuery(fup)
                    },
                  ) {
                    Text(fup, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
