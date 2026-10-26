package com.example.citygo.skeleton.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.citygo.skeleton.data.model.Diary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* =====================================================================================
 * 旅行日记 新增 / 编辑弹窗（数据落 Room，由全局 AppViewModel 订阅）
 *
 * 字段：标题、正文（多行）。新增时日期自动记为当天（yyyy-MM-dd，仅用于列表展示）；
 * - [initial] 为 null 时是新增，否则按其 id 编辑；
 * - 保存前校验：标题与正文均非空（去空白），校验不通过时确认按钮不可点。
 * ===================================================================================== */

/**
 * @param initial 待编辑的已有日记；新增时传 null
 * @param onDismiss 关闭弹窗
 * @param onSave 保存回调（已通过校验的日记对象，交给全局 AppViewModel.saveDiary）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditDialog(
    initial: Diary?,
    onDismiss: () -> Unit,
    onSave: (Diary) -> Unit,
) {
    // 表单本地状态：以 initial 预填（编辑场景），新增时为空
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }

    val canSave = title.isNotBlank() && content.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        // 关闭策略：系统返回键、点击弹窗外遮罩都只关闭弹窗、不保存（关闭即丢弃本次输入）
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = { Text(if (initial == null) "写日记" else "编辑日记") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // —— 标题 ——
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    placeholder = { Text("给今天起个名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                // —— 正文 ——
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    placeholder = { Text("记录旅途见闻、心情与想留念的事…") },
                    minLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        Diary(
                            id = initial?.id ?: 0L,
                            title = title.trim(),
                            content = content.trim(),
                            // 新增记当天日期；编辑沿用原日期
                            dateText = initial?.dateText?.ifBlank { null }
                                ?: SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date()),
                            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
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
