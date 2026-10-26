package com.example.citygo.skeleton.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.citygo.skeleton.data.model.chat.ChatMessage
import com.example.citygo.skeleton.data.model.chat.ProposalStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* =====================================================================================
 * AI 旅行助手（豆包对话 Agent）弹窗
 *
 * 纯新增文件：计划页通过 FAB 唤起，弹窗内完成多轮对话；
 * 聊天列表可上下滚动查看历史，等待回复时显示「正在思考…」loading，
 * 模型若回传新行程，由 ViewModel 直接更新计划页 DAY 列表（本文件不碰任何行程 UI）。
 * 关闭弹窗不会清空聊天记录（状态保存在 Activity 级 AppViewModel 内存中）。
 * ===================================================================================== */

/** 列表项：真实消息 or 底部「正在思考」占位。 */
private sealed interface ChatUiItem {
    data class Message(val message: ChatMessage) : ChatUiItem
    data object Typing : ChatUiItem
}

@Composable
fun PlanChatAssistantDialog(
    visible: Boolean,
    messages: List<ChatMessage>,
    loading: Boolean,
    syncingMessageId: Long?,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    onApplyProposal: (Long) -> Unit,
    onDeclineProposal: (Long) -> Unit,
) {
    if (!visible) return

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val uiItems = remember(messages, loading) {
        buildList {
            messages.forEach { add(ChatUiItem.Message(it)) }
            if (loading) add(ChatUiItem.Typing)
        }
    }

    // 新消息到达 / 开始等待回复时自动滚到最底部；用户仍可手动上滑翻看历史
    LaunchedEffect(uiItems.size) {
        if (uiItems.isNotEmpty()) {
            listState.animateScrollToItem(uiItems.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        // 占满宽度，圆角卡片由内部 Surface 控制
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 10.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                ChatHeader(onClose = onDismiss)

                // —— 消息列表（可滚动查看全部历史） ——
                Box(modifier = Modifier.weight(1f)) {
                    if (messages.isEmpty() && !loading) {
                        ChatEmptyHint()
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 14.dp,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiItems, key = { item ->
                            when (item) {
                                is ChatUiItem.Message -> "msg_${item.message.id}"
                                is ChatUiItem.Typing -> "typing"
                            }
                        }) { item ->
                            when (item) {
                                is ChatUiItem.Message -> ChatMessageItem(
                                    message = item.message,
                                    syncing = syncingMessageId == item.message.id,
                                    onApplyProposal = onApplyProposal,
                                    onDeclineProposal = onDeclineProposal,
                                )
                                is ChatUiItem.Typing -> TypingBubble()
                            }
                        }
                    }
                }

                // —— 底部输入框 + 发送按钮 ——
                ChatInputBar(
                    value = input,
                    loading = loading,
                    onValueChange = { input = it },
                    onSend = {
                        val text = input.trim()
                        if (text.isNotEmpty()) {
                            onSend(text)
                            input = ""
                        }
                    },
                )
            }
        }
    }
}

/* -------------------------------------- 头部 -------------------------------------- */

@Composable
private fun ChatHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AI 旅行助手",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "基于当前行程，为你调整与推荐",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭对话")
        }
    }
}

/* ------------------------------------- 空态引导 ------------------------------------- */

@Composable
private fun ChatEmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "和 AI 旅行助手聊聊当前行程",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "例如：\n· 把某个点换成宠物友好的地方\n· 推荐附近小众探店美食\n· 删掉第 2 个点，行程重新排一下",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/* ------------------------------------- 消息气泡 ------------------------------------- */

@Composable
private fun ChatBubble(message: ChatMessage) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isUser = message.isUser
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    ),
                )
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                text = message.content,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = timeFormatter.format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/* --------------------------------- 行程提议操作区 --------------------------------- */

/**
 * 一条聊天消息的完整渲染：气泡 + （仅助手消息）行程提议操作区。
 * 操作区状态见 [ProposalStatus]：待确认显示双按钮，其余状态显示对应状态提示。
 */
@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    syncing: Boolean,
    onApplyProposal: (Long) -> Unit,
    onDeclineProposal: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ChatBubble(message)
        if (!message.isUser && message.proposalStatus != ProposalStatus.NONE) {
            ProposalActionArea(
                message = message,
                syncing = syncing,
                onApply = { onApplyProposal(message.id) },
                onDecline = { onDeclineProposal(message.id) },
            )
        }
    }
}

@Composable
private fun ProposalActionArea(
    message: ChatMessage,
    syncing: Boolean,
    onApply: () -> Unit,
    onDecline: () -> Unit,
) {
    val status = message.proposalStatus
    Column(
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp, end = 24.dp)
            .widthIn(max = 320.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        when (status) {
            ProposalStatus.PENDING -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = "AI 提议了一份新行程，是否同步到出行计划？",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onApply,
                                enabled = !syncing,
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            ) {
                                if (syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (syncing) "正在同步…" else "同步修改行程",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onDecline,
                                enabled = !syncing,
                            ) {
                                Text("暂不", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            ProposalStatus.APPLIED -> ProposalStatusChip(
                icon = Icons.Filled.CheckCircle,
                text = "已同步到出行计划",
                color = MaterialTheme.colorScheme.primary,
            )

            ProposalStatus.DECLINED -> ProposalStatusChip(
                icon = Icons.Filled.Close,
                text = "已暂不同步",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ProposalStatus.SUPERSEDED -> ProposalStatusChip(
                icon = Icons.Filled.AutoAwesome,
                text = "该方案已被新方案替代",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )

            ProposalStatus.NONE -> Unit
        }
    }
}

/** 提议终态的小号状态提示（浅色描边胶囊 + 图标 + 文案，不可点）。 */
@Composable
private fun ProposalStatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在思考…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* -------------------------------------- 输入栏 -------------------------------------- */

@Composable
private fun ChatInputBar(
    value: String,
    loading: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = value.isNotBlank() && !loading
    Surface(tonalElevation = 2.dp, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("想怎么调整行程？也可以问景点信息…") },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            // 发送中 / 空文本时禁用
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
