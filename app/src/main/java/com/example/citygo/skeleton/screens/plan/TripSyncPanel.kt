package com.example.citygo.skeleton.screens.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.message, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = viewModel::retryTripSync, enabled = !state.busy) { Text("同步") }
                TextButton(onClick = { showLibrary = true }) { Text("历史行程（${state.trips.size}）") }
            }
            state.conflicts.forEach { id ->
                val title = state.trips.firstOrNull { it.tripId == id }?.title ?: "行程"
                Text("「$title」有另一设备的更新。两种选择都会保留本地副本。", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { viewModel.resolveTripConflict(id, true) }, enabled = !state.busy) { Text("使用云端") }
                    TextButton(onClick = { viewModel.resolveTripConflict(id, false) }, enabled = !state.busy) { Text("本地另存为新行程") }
                }
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
