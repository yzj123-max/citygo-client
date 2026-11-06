package com.example.citygo.skeleton.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.citygo.skeleton.data.model.Pet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetEditDialog(initial: Pet?, onDismiss: () -> Unit, onSave: (Pet) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var breed by remember(initial?.id) { mutableStateOf(initial?.type.orEmpty()) }
    var weight by remember(initial?.id) { mutableStateOf(initial?.weightKg?.toString().orEmpty()) }
    val parsed = weight.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (initial == null) "新增宠物档案" else "编辑宠物档案", style = MaterialTheme.typography.titleLarge)
            Text("记录旅行搭子的基本信息", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text("宠物名字") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(breed, { breed = it }, label = { Text("品种") }, placeholder = { Text("如 柯基 / 金毛 / 布偶") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(weight, { weight = it.filter { char -> char.isDigit() || char == '.' }.take(12) },
                label = { Text("体重（kg）") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = weight.isNotBlank() && parsed == null,
                supportingText = { if (weight.isNotBlank() && parsed == null) Text("请输入大于 0 的数字") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { onSave(Pet(id = initial?.id ?: 0L, name = name.trim(), type = breed.trim(),
                    weightKg = parsed ?: 0.0, note = initial?.note.orEmpty())) },
                    enabled = name.isNotBlank() && breed.isNotBlank() && parsed != null, modifier = Modifier.weight(1f)) { Text("保存") }
            }
        }
    }
}
