package com.example.citygo.skeleton.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.citygo.skeleton.data.model.Pet

/* =====================================================================================
 * 宠物档案 新增 / 编辑弹窗（数据落 Room，由全局 AppViewModel 订阅）
 *
 * 字段：宠物名字、品种（自由文本，如 柯基 / 金毛 / 布偶）、体重（kg，数字键盘）。
 * - [initial] 为 null 时是新增，否则按其 id 编辑；
 * - 保存前校验：名字非空、品种非空、体重大于 0，校验不通过时确认按钮不可点。
 * ===================================================================================== */

/**
 * @param initial 待编辑的已有档案；新增时传 null
 * @param onDismiss 关闭弹窗
 * @param onSave 保存回调（已通过校验的档案对象，交给全局 AppViewModel.addPet / updatePet）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetEditDialog(
    initial: Pet?,
    onDismiss: () -> Unit,
    onSave: (Pet) -> Unit,
) {
    // 表单本地状态：以 initial 预填（编辑场景），新增时为空
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var breed by remember { mutableStateOf(initial?.type ?: "") }
    var weightText by remember { mutableStateOf(initial?.weightKg?.toPrettyWeight() ?: "") }

    // 体重解析：只接受正数
    val weightValue: Double? = weightText.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
    val canSave = name.isNotBlank() && breed.isNotBlank() && weightValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        // 关闭策略：系统返回键、点击弹窗外遮罩都只关闭弹窗、不保存（关闭即丢弃本次输入）
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = { Text(if (initial == null) "新增宠物档案" else "编辑宠物档案") },
        text = {
            Column {
                // —— 名字 ——
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("宠物名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                // —— 品种 ——
                OutlinedTextField(
                    value = breed,
                    onValueChange = { breedInput -> breed = breedInput },
                    label = { Text("品种") },
                    placeholder = { Text("如 柯基 / 金毛 / 布偶") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                // —— 体重 ——
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { input ->
                        // 仅保留数字与小数点，防止非法字符
                        weightText = input.filter { it.isDigit() || it == '.' }
                            .let { filtered ->
                                // 最多一个小数点
                                val firstDot = filtered.indexOf('.')
                                if (firstDot >= 0) {
                                    filtered.substring(0, firstDot + 1) +
                                        filtered.substring(firstDot + 1).replace(".", "")
                                } else {
                                    filtered
                                }
                            }
                    },
                    label = { Text("体重（kg）") },
                    placeholder = { Text("如 5.8") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        if (weightText.isNotBlank() && weightValue == null) {
                            Text("请输入大于 0 的数字")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        Pet(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            type = breed.trim(),
                            weightKg = weightValue ?: 0.0,
                            note = initial?.note.orEmpty(),
                        ),
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 体重展示格式化：整数不带小数点，非整数保留 1 位小数（编辑回填用）。 */
private fun Double.toPrettyWeight(): String =
    if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        (Math.round(this * 10.0).toDouble() / 10.0).toString()
    }
