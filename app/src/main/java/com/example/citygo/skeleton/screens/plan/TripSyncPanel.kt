package com.example.citygo.skeleton.screens.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripSyncPanel(viewModel: AppViewModel) {
    val state by viewModel.tripSyncState.collectAsStateWithLifecycle()
    var showLibrary by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
            // 正常保存不占用整张卡片；仅在同步进行中或需要处理时展示状态。
            val needsAttention = !state.busy && state.message !in setOf("已与云端同步", "已保存到本机")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在同步", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (needsAttention) {
                    Text("待同步", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = viewModel::retryTripSync) { Text("重试") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showLibrary = true }) { Text("历史行程 · ${state.trips.size}") }
            }
            if (needsAttention) Text(state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp))
            state.conflicts.forEach { id ->
                val title = state.trips.firstOrNull { it.tripId == id }?.title ?: "行程"
                Text("「$title」有另一设备的更新。两种选择都会保留本地副本。", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { viewModel.resolveTripConflict(id, true) }, enabled = !state.busy) { Text("使用云端") }
                    TextButton(onClick = { viewModel.resolveTripConflict(id, false) }, enabled = !state.busy) { Text("本地另存为新行程") }
                }
            }
    }
    if (showLibrary) AlertDialog(
        onDismissRequest = { showLibrary = false },
        title = { Text("历史行程") },
        text = {
            if (state.trips.isEmpty()) Text("还没有保存的行程。登录后可从云端恢复。")
            else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(state.trips, key = { it.tripId }) { trip ->
                    TextButton(onClick = {
                        viewModel.selectSavedTrip(trip.tripId)
                        showLibrary = false
                    }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(trip.title + if (trip.tripId == state.activeTripId) " · 当前" else "")
                            Text(SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(trip.updatedAt)),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showLibrary = false }) { Text("关闭") } },
    )
}
