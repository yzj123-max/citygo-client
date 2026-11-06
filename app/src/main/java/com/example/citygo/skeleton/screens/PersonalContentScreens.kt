package com.example.citygo.skeleton.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.AppViewModel
import com.example.citygo.skeleton.data.model.Pet
import com.example.citygo.skeleton.data.model.Diary
import com.example.citygo.skeleton.profile.PetEditDialog
import com.example.citygo.skeleton.profile.DiaryEditDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.citygo.skeleton.data.WeatherViewModel
import com.example.citygo.skeleton.screens.weather.WeatherPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherOverviewScreen(viewModel: WeatherViewModel, onBack: () -> Unit, onDay: (Int) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("天气预报") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }) { padding -> WeatherPage(viewModel, onDay, Modifier.padding(padding)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetProfilesScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val pets by viewModel.petList.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Pet?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Pet?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("我的旅行搭子") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }, actions = { TextButton(onClick = { editing = null; showEditor = true }) { Text("添加") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (pets.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Outlined.Pets, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("添加第一位旅行搭子", style = MaterialTheme.typography.titleLarge)
                    Text("记录名字、品种与体重", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { editing = null; showEditor = true }) { Text("添加宠物档案") }
                }
            }
            items(pets, key = { it.id }) { pet ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(Icons.Outlined.Pets, null, Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pet.name, style = MaterialTheme.typography.titleLarge)
                                Text("${pet.type} · ${pet.weightKg} kg", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { deleting = pet }, modifier = Modifier.weight(1f)) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            Button(onClick = { editing = pet; showEditor = true }, modifier = Modifier.weight(1f)) { Text("编辑档案") }
                        }
                    }
                }
            }
        }
    }
    if (showEditor) PetEditDialog(initial = editing, onDismiss = { showEditor = false }, onSave = { pet ->
        if (editing == null) viewModel.addPet(pet) else viewModel.updatePet(pet)
        showEditor = false
    })
    deleting?.let { pet ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除宠物档案？") },
            text = { Text("确认删除「${pet.name}」的档案吗？") }, confirmButton = {
                TextButton(onClick = { viewModel.deletePet(pet.id); deleting = null }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelDiariesScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val diaries by viewModel.diaryList.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Diary?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Diary?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("旅行日记") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }, actions = { TextButton(onClick = { editing = null; showEditor = true }) { Text("写日记") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (diaries.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Outlined.EditNote, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("写下第一篇旅行日记", style = MaterialTheme.typography.titleLarge)
                    Text("记录沿途风景与心情", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { editing = null; showEditor = true }) { Text("开始记录") }
                }
            }
            items(diaries, key = { it.id }) { diary ->
                Card(modifier = Modifier.fillMaxWidth().clickable { editing = diary; showEditor = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(diary.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { deleting = diary }) { Icon(Icons.Outlined.DeleteOutline, "删除日记") }
                        }
                        if (diary.dateText.isNotBlank()) Text(diary.dateText,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(diary.content, maxLines = 3, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showEditor) DiaryEditDialog(initial = editing, onDismiss = { showEditor = false }, onSave = {
        viewModel.saveDiary(it); showEditor = false
    })
    deleting?.let { diary ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这篇日记？") },
            text = { Text("删除后无法恢复。") }, confirmButton = {
                TextButton(onClick = { viewModel.deleteDiary(diary.id); deleting = null }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}
