package com.example.citygo.skeleton.screens.stylist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.citygo.skeleton.data.AppViewModel
import com.example.citygo.skeleton.data.model.TripPlan

/**
 * AI 穿搭评估页（仅新增页面）：
 * 多张穿搭照片与行程 → /api/stylist/chat/multiImgChatSse 一次提交并综合评估，
 * 评估文字收到一段渲染一段；网络 / 接口异常仅页面友好提示，不崩溃。
 *
 * 行程数据直接读取全局 [AppViewModel.tripPlan]（与地图 / 计划页同源，不在此页修改）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylistScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StylistViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tripPlan by appViewModel.tripPlan.collectAsStateWithLifecycle()

    // 系统相册多图选择器（Photo Picker，沙盒临时 Uri 授权，无需存储权限）
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK_COUNT),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    // 评估文本区域随流式内容自动滚动到最新一行
    val answerScroll = rememberScrollState()
    LaunchedEffect(state.answerText.length, state.phase) {
        if (state.answerText.isNotEmpty()) {
            answerScroll.scrollTo(answerScroll.maxValue)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI 穿搭评估") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(answerScroll)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ① 当前行程景点（评估上下文，只读）
            TripContextCard(tripPlan)

            // ② 穿搭照片选择
            PhotoPickerCard(
                images = state.images,
                enabled = !state.busy,
                onAdd = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemove = viewModel::removeImage,
            )

            // ③ 提问（预填后端样例问题，可改）
            OutlinedTextField(
                value = state.userQuery,
                onValueChange = viewModel::updateQuery,
                enabled = !state.busy,
                label = { Text("想让 AI 评估什么？") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            // ④ 评估状态 / 流式结果
            if (state.phase != StylistPhase.IDLE || state.answerText.isNotEmpty()) {
                ResultCard(state)
            }

            // ⑤ 主操作按钮
            val primaryText = when (state.phase) {
                StylistPhase.UPLOADING -> "正在提交 ${state.totalCount} 张照片并等待评估…"
                StylistPhase.STREAMING -> "AI 正在综合匹配穿搭与景点…"
                StylistPhase.DONE -> "重新评估"
                StylistPhase.FAILED -> "重试评估"
                StylistPhase.IDLE -> "上传照片并开始评估"
            }
            Button(
                onClick = {
                    if (state.phase == StylistPhase.FAILED) viewModel.resetResult()
                    viewModel.evaluate(tripPlan)
                },
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                when (state.phase) {
                    StylistPhase.UPLOADING, StylistPhase.STREAMING ->
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )

                    StylistPhase.FAILED -> Icon(Icons.Filled.Refresh, contentDescription = null)
                    StylistPhase.DONE -> Icon(Icons.Filled.Refresh, contentDescription = null)
                    StylistPhase.IDLE -> Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(primaryText, fontWeight = FontWeight.SemiBold)
            }


        }
    }
}

/** 行程上下文卡片：展示将随请求发送的按天景点列表 */
@Composable
private fun TripContextCard(trip: TripPlan) {
    var expanded by remember(trip) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "评估行程：${trip.city.ifBlank { "未选择城市" }} · " +
                        "共 ${trip.dailyPlans.size} 天 ${trip.allPlaces.size} 个点位",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (trip.allPlaces.isEmpty()) {
                Text(
                    text = "暂无行程景点，请先在「行程」页生成行程攻略，否则无法开始评估。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起行程" else "展开当前行程")
                }
                if (expanded) trip.dailyPlans.forEach { day ->
                    day.places.forEach { place ->
                        Text(
                            text = "第${day.day}天 · ${place.name}" +
                                "（${place.placeType.label}" +
                                place.photoScene?.takeIf { it.isNotBlank() }?.let { "·$it" }.orEmpty() +
                                "）",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 穿搭照片选择卡片：3 列缩略图 + 末尾添加按钮 */
@Composable
private fun PhotoPickerCard(
    images: List<StylistImage>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "穿搭照片（${images.size}/$MAX_PICK_COUNT，可多选）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))

            // 末尾一格固定为「添加」按钮；按 3 列分行，缺位用等宽 Spacer 补齐
            val cellCount = images.size + 1
            val rowCount = (cellCount + 2) / 3
            for (row in 0 until rowCount) {
                if (row > 0) Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (col in 0 until 3) {
                        val index = row * 3 + col
                        Box(modifier = Modifier.weight(1f)) {
                            when {
                                index < images.size ->
                                    PhotoThumb(image = images[index], enabled = enabled, onRemove = onRemove)

                                index == images.size ->
                                    AddPhotoTile(
                                        enabled = enabled && images.size < MAX_PICK_COUNT,
                                        onAdd = onAdd,
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumb(image: StylistImage, enabled: Boolean, onRemove: (Long) -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = "穿搭照片",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xAA000000))
                    .clickable { onRemove(image.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "移除照片",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun AddPhotoTile(enabled: Boolean, onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled, onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.AddAPhoto,
                contentDescription = "添加穿搭照片",
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "添加照片",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}

/** 评估状态 + 流式文本结果卡片 */
@Composable
private fun ResultCard(state: StylistUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            when (state.phase) {
                StylistPhase.UPLOADING -> StatusRow(
                    text = "正在提交 ${state.totalCount} 张照片并等待 AI 返回…",
                    loading = true,
                )

                StylistPhase.STREAMING -> StatusRow(
                    text = "AI 正在逐段评估，收到内容即时展示…",
                    loading = true,
                )

                StylistPhase.DONE -> StatusRow(
                    text = "评估完成（连接已正常关闭）",
                    loading = false,
                )

                StylistPhase.FAILED -> StatusRow(
                    text = state.errorMessage ?: "评估失败，请稍后重试",
                    loading = false,
                    isError = true,
                )

                StylistPhase.IDLE -> Unit
            }

            if (state.answerText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.answerText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(text: String, loading: Boolean, isError: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            isError -> Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            else -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF1FA86B),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val MAX_PICK_COUNT = 9
