package com.memora.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.data.MemoryItem
import com.memora.app.data.MemoryThread
import com.memora.app.ui.MemoraUiState
import com.memora.app.ui.theme.GlassPanel
import com.memora.app.ui.theme.LocalMemoraColors

@Composable
fun ThreadsScreen(
  state: MemoraUiState,
  onSynthesize: (MemoryThread) -> Unit,
  onOpenMemory: (MemoryItem) -> Unit,
) {
  val palette = LocalMemoraColors.current
  val byId = remember(state.memories) { state.memories.associateBy { it.id } }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 104.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Memory Threads", style = MaterialTheme.typography.displaySmall)
        Text(
          text = "Related screenshots, voice memos, flyers, and links connect automatically into topic timelines.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (state.threads.isEmpty()) {
      item {
        GlassPanel(
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = palette.mutedText, modifier = Modifier.size(28.dp))
            Text("No threads connected yet", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Capture two or more memories on the same subject or project to see them cluster automatically.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(state.threads, key = { it.id }) { thread ->
        val itemsList = thread.memoryIds.mapNotNull { byId[it] }
        val isRunning = state.synthesizingThreadId == thread.id
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
              Column(modifier = Modifier.weight(1f)) {
                Text(thread.title, style = MaterialTheme.typography.headlineSmall)
                Text("${itemsList.size} connected memories", style = MaterialTheme.typography.bodySmall, color = palette.mutedText)
              }
              OutlinedButton(
                onClick = { onSynthesize(thread) },
                enabled = !isRunning,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              ) {
                if (isRunning) {
                  CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                } else {
                  Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.size(6.dp))
                Text("Synthesize", fontSize = 12.sp)
              }
            }

            Text(thread.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (thread.nextActions.isNotEmpty()) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Next Actions", style = MaterialTheme.typography.labelMedium, color = palette.mutedText)
                thread.nextActions.take(3).forEach { act ->
                  Text("• $act", style = MaterialTheme.typography.bodySmall)
                }
              }
            }

            HorizontalDivider(color = palette.borderSubtle)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              itemsList.forEach { mem ->
                Surface(
                  color = palette.surfaceSoft,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMemory(mem) },
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(mem.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text(mem.kind, style = MaterialTheme.typography.bodySmall, color = palette.mutedText)
                    }
                    Text("Open", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
