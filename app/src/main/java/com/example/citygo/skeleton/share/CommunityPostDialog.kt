package com.example.citygo.skeleton.share

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.example.citygo.skeleton.data.model.Post

/* =====================================================================================
 * 社区游记发布弹窗
 *
 * 输入标题、正文，校验通过后回调 onSave（由全局 AppViewModel.addCommunityPost 落 Room）。
 * id 传 0 由 ViewModel 分配；作者 / 时间为本地模拟值。
 * ===================================================================================== */

/**
 * @param onDismiss 关闭弹窗
 * @param onSave 保存回调（已通过非空校验的帖子对象）
 */
@Composable
fun CommunityPostDialog(
    onDismiss: () -> Unit,
    onSave: (Post) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    val canSave = title.isNotBlank() && content.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        // 关闭策略：系统返回键、点击弹窗外遮罩都只关闭弹窗、不发布（关闭即丢弃本次输入）
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = { Text("发布游记") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // —— 标题 ——
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    placeholder = { Text("给游记起个吸引人的标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                // —— 正文 ——
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("正文") },
                    placeholder = { Text("分享路线、花费和沿途的惊喜吧…") },
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
                        Post(
                            id = 0L,
                            title = title.trim(),
                            content = content.trim(),
                            authorName = "我",
                            timeText = "刚刚",
                        ),
                    )
                },
            ) {
                Text("发布")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
