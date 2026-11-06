package com.example.citygo.skeleton.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.citygo.skeleton.data.model.Diary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditDialog(initial: Diary?, onDismiss: () -> Unit, onSave: (Diary) -> Unit) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var body by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    val save = {
        val entry = initial ?: Diary(title = "", dateText = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date()))
        onSave(entry.copy(title = title.trim(), content = body.trim()))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
            TopAppBar(title = { Text(if (initial == null) "写旅行日记" else "编辑旅行日记") }, navigationIcon = {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            })
        }, bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = save, enabled = title.isNotBlank() && body.isNotBlank(), modifier = Modifier.weight(1f)) { Text("保存") }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("给这段旅程，留下一点记忆", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, placeholder = { Text("给今天起个名字") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(body, { body = it }, label = { Text("正文") }, placeholder = { Text("记录旅途见闻、心情与想留念的事…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp), minLines = 12)
            }
        }
    }
}
