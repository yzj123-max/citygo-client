package com.example.citygo.skeleton.share

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.citygo.skeleton.data.ShareViewModel
import com.example.citygo.skeleton.data.model.TravelArticle
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll

/* =====================================================================================
 * 游记发布页
 *
 * - 系统 PhotoPicker 多选本地图片（最多 9 张，无需存储权限），九宫格预览、可删除；
 * - 标题输入框 + 多行正文输入框（旅游感受 / 旅行笔记）；
 * - 发布：校验「至少 1 张图 + 标题 + 正文」后写入本地 Mock 数据，
 *   返回社区首页并刷新瀑布流（新游记在第一位）。
 *
 * 【后端替换位置】图片当前直接保存 content:// Uri 字符串（仅本次安装会话可读）；
 * 接后端时应在此处先上传图片文件换取 https URL，再把 URL 列表传给 publishArticle，
 * ShareViewModel / ArticleRepository 接口不用变。
 * ===================================================================================== */

private const val MAX_IMAGE_COUNT = 9

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlePublishScreen(
    shareViewModel: ShareViewModel,
    onBack: () -> Unit,
    original: TravelArticle? = null,
) {
    var imageUris by remember { mutableStateOf(original?.imgUrls.orEmpty()) }
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var content by remember { mutableStateOf(original?.content.orEmpty()) }
    var visibility by remember { mutableStateOf(original?.visibility ?: "PRIVATE") }
    var audience by remember { mutableStateOf(original?.audienceUserIds.orEmpty().toSet()) }
    var showAudience by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var errorTip by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = publishing) { /* 等待提交完成，避免重复发布。 */ }

    // 用户重新选择图片 / 修改文案后，清除上一次的校验提示
    LaunchedEffect(imageUris, title, content) { errorTip = null }

    // 系统相册多选（PhotoPicker，无需申请任何权限）
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGE_COUNT),
    ) { uris ->
        if (uris.isNotEmpty()) {
            // 保留已选顺序，追加新选择，最多 9 张
            imageUris = (imageUris + uris.map { it.toString() }).distinct().take(MAX_IMAGE_COUNT)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "发布游记" else "编辑游记") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !publishing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // —— 图片选择区（九宫格） ——
            Text(
                text = "游记配图（${imageUris.size}/$MAX_IMAGE_COUNT）",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            ImagePickerGrid(
                imageUris = imageUris,
                onAdd = {
                    if (!publishing) pickMediaLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemove = { index ->
                    if (!publishing) imageUris = imageUris.toMutableList().apply { removeAt(index) }
                },
            )
            errorTip?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))

            // —— 标题 ——
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 100) title = it },
                enabled = !publishing,
                label = { Text("游记标题") },
                singleLine = true,
                placeholder = { Text("给游记起个吸引人的标题") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // —— 正文 ——
            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= 1000) content = it },
                enabled = !publishing,
                label = { Text("正文") },
                placeholder = { Text("记录你的旅游感受、旅行笔记、避坑经验…") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            Text("谁可以看", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("PUBLIC" to "公开", "PRIVATE" to "仅自己", "ALLOW_LIST" to "仅选中的人", "DENY_LIST" to "不给选中的人看", "FRIENDS" to "互关好友").forEach { (key, label) ->
                    FilterChip(selected = visibility == key, enabled = !publishing,
                        onClick = { visibility = key }, label = { Text(label) })
                }
            }
            if (visibility == "ALLOW_LIST" || visibility == "DENY_LIST") {
                TextButton(onClick = { showAudience = true }, enabled = !publishing) {
                    Text("选择用户（已选 ${audience.size} 人）")
                }
            }
            Text("${title.length}/100 字标题 · ${content.length}/1000 字正文", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))

            // —— 发布 ——
            Button(
                onClick = {
                    val tip = when {
                        imageUris.isEmpty() -> "请至少选择 1 张配图"
                        title.isBlank() -> "请填写游记标题"
                        (visibility == "ALLOW_LIST" || visibility == "DENY_LIST") && audience.isEmpty() -> "请先选择观看范围中的用户"
                        else -> null
                    }
                    if (tip != null) {
                        errorTip = tip
                        return@Button
                    }
                    errorTip = null
                    publishing = true
                    val accepted = shareViewModel.publishArticle(
                        title = title,
                        content = content,
                        imgUrls = imageUris,
                        visibility = visibility,
                        audience = if (visibility == "ALLOW_LIST" || visibility == "DENY_LIST") audience.toList() else emptyList(),
                        original = original,
                        onFailure = { publishing = false; errorTip = it },
                    ) {
                        // 入库成功：回到社区首页，首页已刷新到第一页
                        onBack()
                    }
                    if (!accepted) { publishing = false; errorTip = "请检查标题、正文长度和图片数量" }
                },
                enabled = !publishing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (publishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("发布中…")
                } else {
                    Text(if (original == null) "发布游记" else "保存修改")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
    if (showAudience) CommunityAudiencePicker(
        selected = audience,
        onDismiss = { showAudience = false },
        onConfirm = { audience = it; showAudience = false },
    )
}

/**
 * 已选图片九宫格：每行 3 张，末尾是「添加」占位格。
 */
@Composable
private fun ImagePickerGrid(
    imageUris: List<String>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    // 三列网格（图片 + 可能存在的添加按钮）
    val cellCount = imageUris.size + if (imageUris.size < MAX_IMAGE_COUNT) 1 else 0
    val rows = (cellCount + 2) / 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { rowIndex ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { columnIndex ->
                    val index = rowIndex * 3 + columnIndex
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                        when {
                            index < imageUris.size -> SelectedImageTile(
                                uri = imageUris[index],
                                onRemove = { onRemove(index) },
                            )
                            // 最后一个格子是添加按钮
                            index == imageUris.size -> AddImageTile(onAdd = onAdd)
                            else -> Spacer(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/** 单张已选图片 + 右上角删除按钮（content:// 本地图，Coil 直接渲染） */
@Composable
private fun SelectedImageTile(uri: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFECEFF1)),
    ) {
        CommunityImage(
            data = uri,
            fit = ImageFit.Fill,
            contentDescription = "已选图片",
        )
        // 删除角标
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xAA000000))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "删除图片",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 添加图片占位格 */
@Composable
private fun AddImageTile(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFC6CEC9), RoundedCornerShape(10.dp))
            .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.AddPhotoAlternate,
                contentDescription = "添加图片",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = "添加图片",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
