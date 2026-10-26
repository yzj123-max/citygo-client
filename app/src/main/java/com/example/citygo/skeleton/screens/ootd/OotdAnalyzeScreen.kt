package com.example.citygo.skeleton.screens.ootd

// OOTD AI 穿搭分析页面
import android.Manifest
import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.citygo.skeleton.data.remote.ootd.OotdSseClient
import com.example.citygo.skeleton.data.remote.ootd.OotdSseEvent
import com.example.citygo.skeleton.data.remote.ootd.OotdUploadException
import com.example.citygo.skeleton.util.OotdImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/* ============================== 页面状态模型 ============================== */

/** 评估流程阶段 */
enum class OotdPhase { IDLE, UPLOADING, STREAMING, DONE, FAILED }

/** 失败归类：上传失败 / 网络异常（含超时与连不上），驱动不同的红色状态卡 */
enum class OotdErrorKind { UPLOAD, NETWORK, TIMEOUT, SERVER }

data class OotdUiState(
    val imageUri: Uri? = null,
    val question: String = "",
    val phase: OotdPhase = OotdPhase.IDLE,
    /** 打字机已展示的文本（收到全文后逐字吐出） */
    val displayText: String = "",
    /** 后端校验通过的知识库引用规则标题（去重） */
    val referenceTitles: List<String> = emptyList(),
    val errorKind: OotdErrorKind? = null,
    val errorMessage: String? = null,
) {
    val busy: Boolean get() = phase == OotdPhase.UPLOADING || phase == OotdPhase.STREAMING
}

private const val DEFAULT_QUESTION = "帮我分析这套穿搭"

/** 打字机节奏：每拍间隔与吐出字符数 */
private const val TYPE_INTERVAL_MS = 18L
private const val TYPE_CHARS_PER_TICK = 2

/**
 * OOTD 副页面 ViewModel：
 * 选图压缩（[OotdImageUtils]）→ 上传 + SSE（[OotdSseClient]）→ 打字机逐字渲染。
 * 页面退出时 [close] / onCleared 主动取消请求并关闭 SSE，防止内存泄漏。
 */
class OotdAnalyzeViewModel(app: Application) : AndroidViewModel(app) {

    private val client = OotdSseClient()

    private val _uiState = MutableStateFlow(OotdUiState())
    val uiState: StateFlow<OotdUiState> = _uiState.asStateFlow()

    private var analyzeJob: Job? = null

    /** 选好图片（拍照 / 相册共用入口），同时清空上一轮结果 */
    fun onImageSelected(uri: Uri) {
        analyzeJob?.cancel()
        Log.i(TAG_VM, "image selected: $uri")
        _uiState.update {
            it.copy(
                imageUri = uri,
                phase = OotdPhase.IDLE,
                displayText = "",
                referenceTitles = emptyList(),
                errorKind = null,
                errorMessage = null,
            )
        }
    }

    fun updateQuestion(text: String) {
        _uiState.update { it.copy(question = text) }
    }

    /** 开始 / 重新分析 */
    fun analyze() {
        val uri = _uiState.value.imageUri
        if (uri == null || _uiState.value.busy) return

        analyzeJob?.cancel()
        val question = _uiState.value.question.ifBlank { DEFAULT_QUESTION }
        // 分片文本先进入无界通道，打字机协程按固定节奏取出逐字上屏
        val deltaChannel = Channel<String>(Channel.UNLIMITED)

        analyzeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = OotdPhase.UPLOADING,
                    displayText = "",
                    referenceTitles = emptyList(),
                    errorKind = null,
                    errorMessage = null,
                )
            }

            // ① 本地压缩图片为 JPEG 字节
            val bytes = try {
                withContext(Dispatchers.IO) {
                    OotdImageUtils.compressToBytes(getApplication(), uri)
                }
            } catch (e: CancellationException) {
                // 页面退出 / 重新分析触发取消：关闭通道避免资源泄漏
                deltaChannel.close()
                throw e
            } catch (e: Throwable) {
                // 压缩失败：关闭通道后展示错误，避免通道对象滞留
                deltaChannel.close()
                enterFailure(OotdErrorKind.UPLOAD, "图片处理失败：${e.message ?: "请更换图片重试"}")
                return@launch
            }

            // ② 打字机消费者：固定在主线程逐字更新 displayText
            val typer = launch {
                for (piece in deltaChannel) {
                    var index = 0
                    while (index < piece.length) {
                        val end = minOf(index + TYPE_CHARS_PER_TICK, piece.length)
                        val part = piece.substring(index, end)
                        _uiState.update { state -> state.copy(displayText = state.displayText + part) }
                        index = end
                        delay(TYPE_INTERVAL_MS)
                    }
                }
            }

            // ③ 上传 → SSE 收集（IO 线程），事件切到状态 / 打字机
            try {
                withContext(Dispatchers.IO) {
                    client.analyze(
                        imageBytes = bytes,
                        fileName = "ootd_${System.currentTimeMillis()}.jpg",
                        question = question,
                    ).collect { event ->
                        when (event) {
                            is OotdSseEvent.Uploaded ->
                                _uiState.update { it.copy(phase = OotdPhase.STREAMING) }

                            is OotdSseEvent.TextDelta -> {
                                _uiState.update { it.copy(phase = OotdPhase.STREAMING) }
                                deltaChannel.send(event.text)
                            }

                            is OotdSseEvent.ReferenceTitle ->
                                _uiState.update { state ->
                                    if (state.referenceTitles.any { t -> t == event.title }) {
                                        state
                                    } else {
                                        state.copy(referenceTitles = state.referenceTitles + event.title)
                                    }
                                }
                        }
                    }
                }
                // 服务端正常结束：等打字机把已接收文本全部吐完再置 DONE（保留全文展示）
                deltaChannel.close()
                typer.join()
                _uiState.update { it.copy(phase = OotdPhase.DONE) }
                Log.i(TAG_VM, "analyze done, text length=${_uiState.value.displayText.length}")
            } catch (e: CancellationException) {
                deltaChannel.close()
                throw e
            } catch (e: Throwable) {
                // 出错也等已接收文本吐完，打字文字保留在页面上，仅额外展示错误状态
                deltaChannel.close()
                typer.join()
                val (kind, message) = mapError(e)
                enterFailure(kind, message)
            }
        }
    }

    /** 清空结果（保留图片与问题，可再次分析） */
    fun resetResult() {
        analyzeJob?.cancel()
        client.close()
        _uiState.update {
            it.copy(
                phase = OotdPhase.IDLE,
                displayText = "",
                referenceTitles = emptyList(),
                errorKind = null,
                errorMessage = null,
            )
        }
    }

    /** 页面退出：取消协程 + 主动关闭 SSE，防止连接与线程泄漏 */
    fun close() {
        analyzeJob?.cancel()
        client.close()
    }

    private fun enterFailure(kind: OotdErrorKind, message: String) {
        Log.w(TAG_VM, "analyze failed[$kind]: $message")
        _uiState.update { it.copy(phase = OotdPhase.FAILED, errorKind = kind, errorMessage = message) }
    }

    /** 异常 → （错误分类，友好文案） */
    private fun mapError(e: Throwable): Pair<OotdErrorKind, String> {
        val lanUrl = client.baseUrl()
        return when (e) {
            is OotdUploadException ->
                OotdErrorKind.UPLOAD to (e.message ?: "图片上传失败，请重试")
            is SocketTimeoutException ->
                OotdErrorKind.TIMEOUT to
                    "服务响应超时（120 秒无数据返回），请稍后重试"
            is UnknownHostException, is ConnectException ->
                OotdErrorKind.NETWORK to
                    "暂时无法连接穿搭服务，请稍后重试"
            is IOException ->
                OotdErrorKind.NETWORK to (e.message?.takeIf { it.isNotBlank() } ?: "网络异常，请稍后重试")
            else ->
                OotdErrorKind.SERVER to (e.message?.takeIf { it.isNotBlank() } ?: "服务异常，请稍后重试")
        }
    }

    override fun onCleared() {
        client.close()
        super.onCleared()
    }

    companion object {
        private const val TAG_VM = "OotdPage"
    }
}

/* ============================== 副页面 UI ============================== */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OotdAnalyzeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OotdAnalyzeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 相机目标 Uri（TakePicture 写入用），拍照成功后交给 VM
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            cameraOutputUri?.let(viewModel::onImageSelected)
        } else {
            Log.i("OotdPage", "camera capture canceled")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = OotdImageUtils.createCameraOutputUri(context)
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "相机权限被拒绝，无法拍照", Toast.LENGTH_LONG).show()
        }
    }

    // Android13+ 用系统 Photo Picker（免存储权限）；低版本走 GetContent + 运行时权限
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::onImageSelected) }

    val legacyGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(viewModel::onImageSelected) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            legacyGalleryLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "存储权限被拒绝，无法打开相册", Toast.LENGTH_LONG).show()
        }
    }

    fun launchCamera() {
        if (state.busy) return
        if (OotdImageUtils.hasCameraPermission(context)) {
            val uri = OotdImageUtils.createCameraOutputUri(context)
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGallery() {
        if (state.busy) return
        val requiredPermission = OotdImageUtils.requiredGalleryPermission()
        when {
            requiredPermission == null ->
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            OotdImageUtils.hasGalleryPermission(context) ->
                legacyGalleryLauncher.launch("image/*")
            else -> storagePermissionLauncher.launch(requiredPermission)
        }
    }

    // 页面退出（返回分享主页 / 路由销毁）：自动关闭 SSE，防止后台连接与内存泄漏
    DisposableEffect(Unit) {
        onDispose { viewModel.close() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("OOTD 穿搭 AI 分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回分享页",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ① 原图预览 / 选图
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("① 选择穿搭照片", fontWeight = FontWeight.SemiBold)
                    val imageUri = state.imageUri
                    if (imageUri == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("拍照")
                            }
                            OutlinedButton(onClick = { launchGallery() }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("相册")
                            }
                        }
                        Text(
                            "图片将在本机压缩后上传（最长边 1280px / JPEG）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    } else {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "穿搭原图预览",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !state.busy,
                                onClick = { launchCamera() },
                                modifier = Modifier.weight(1f),
                            ) { Text("重新拍照") }
                            OutlinedButton(
                                enabled = !state.busy,
                                onClick = { launchGallery() },
                                modifier = Modifier.weight(1f),
                            ) { Text("相册重选") }
                        }
                    }
                }
            }

            // ② 提问
            OutlinedTextField(
                value = state.question,
                onValueChange = viewModel::updateQuestion,
                enabled = !state.busy,
                label = { Text("想让 AI 分析什么？") },
                placeholder = { Text(DEFAULT_QUESTION) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            // ③ 主操作按钮
            Button(
                onClick = {
                    if (state.phase == OotdPhase.FAILED ||
                        state.phase == OotdPhase.DONE
                    ) viewModel.resetResult()
                    viewModel.analyze()
                },
                enabled = state.imageUri != null && !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                when (state.phase) {
                    OotdPhase.UPLOADING, OotdPhase.STREAMING ->
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    OotdPhase.DONE, OotdPhase.FAILED ->
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    else -> Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when (state.phase) {
                        OotdPhase.UPLOADING -> "图片上传中…"
                        OotdPhase.STREAMING -> "AI 正在分析…"
                        OotdPhase.DONE -> "重新分析"
                        OotdPhase.FAILED -> "重试分析"
                        OotdPhase.IDLE -> "开始 AI 分析"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // ④ 上传中状态
            if (state.phase == OotdPhase.UPLOADING) {
                StatusCard {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("正在压缩并上传图片，请稍候…")
                }
            }

            // ⑤ 失败状态：上传失败 / 网络异常 / 超时，页面内提示不崩溃
            if (state.phase == OotdPhase.FAILED) {
                FailureCard(state.errorKind, state.errorMessage)
            }

            // ⑥ 流式结果区（失败时若已有文本，也保留展示）
            if (state.displayText.isNotEmpty() || state.phase == OotdPhase.STREAMING) {
                ResultCard(
                    displayText = state.displayText,
                    streaming = state.phase == OotdPhase.STREAMING,
                    done = state.phase == OotdPhase.DONE,
                )
            }

            // ⑦ 知识库引用标题（后端校验 AI 输出引用到的规则标题）
            if (state.referenceTitles.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "知识库引用（${state.referenceTitles.size} 条）",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.referenceTitles.forEach { title ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                                        )
                                    },
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
private fun StatusCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/** 失败卡：上传失败（琥珀色）与网络 / 超时（红色）两种视觉，文案直接展示后端 / 网络原因 */
@Composable
private fun FailureCard(kind: OotdErrorKind?, message: String?) {
    val isUpload = kind == OotdErrorKind.UPLOAD
    val container = if (isUpload) Color(0xFFFFF4E5) else Color(0xFFFFEDEA)
    val accent = if (isUpload) Color(0xFFB26A00) else Color(0xFFBA1A1A)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isUpload) Icons.Filled.UploadFile else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isUpload) "上传失败" else "网络异常",
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                if (kind == OotdErrorKind.TIMEOUT) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                message ?: "请求失败，请稍后重试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "点击下方「重试分析」可再次发起请求",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
        }
    }
}

/** 流式结果卡：逐字文本 + 流式期间闪烁竖条光标 */
@Composable
private fun ResultCard(displayText: String, streaming: Boolean, done: Boolean) {
    val cursorTransition = rememberInfiniteTransition(label = "ootd-cursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "ootd-cursor-alpha",
    )

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text("AI 穿搭分析", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                when {
                    done -> Text(
                        "分析完成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    streaming -> Text(
                        "逐字输出中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            if (displayText.isEmpty() && streaming) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI 正在思考搭配建议…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                // 光标以「▍」内联在正文末尾，透明度随动画闪烁，形成打字机效果
                val annotated = buildAnnotatedString {
                    append(displayText)
                    if (streaming) {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha),
                            ),
                        ) { append("▍") }
                    }
                }
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.7,
                )
            }
        }
    }
}
