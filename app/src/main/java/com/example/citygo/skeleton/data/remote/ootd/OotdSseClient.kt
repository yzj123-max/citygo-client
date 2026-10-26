package com.example.citygo.skeleton.data.remote.ootd

// OOTD SSE 网络请求工具类
import android.util.Log
import com.example.citygo.skeleton.data.remote.ServerConfig
import com.example.citygo.skeleton.data.remote.auth.AuthHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/* =====================================================================================
 * OOTD 穿搭 AI 分析 —— 独立网络工具（上传 + SSE）
 *
 * 链路（与后端现状对齐）：
 *   压缩图片二进制 --POST multipart--> [上传接口] --> TOS 预签名图片 URL
 *        --> 携带 URL 建立 POST SSE 长连接 --> 火山 Ark 分析文本分片下发 / [DONE] 结束
 *
 * 为什么不用 EventSource：EventSource 只支持 GET，本接口是 POST JSON，
 * 因此用 OkHttp 手动读 response.body.source() 逐行解析 data: 帧。
 *
 * 后端地址：统一走全局 [ServerConfig]（默认 adb reverse http://127.0.0.1:8080/，
 * 可在 App 内地址输入框切换为局域网 IP）；不再硬编码 HOST_IP。
 * Logcat 过滤 tag：OotdSse。
 * ===================================================================================== */

/** 上传阶段失败（区别于网络异常），UI 可据此展示「上传失败，请重试」 */
class OotdUploadException(message: String) : IOException(message)

/** SSE 流程事件；页面按事件类型渲染流式文本 / 知识库引用标题 */
sealed interface OotdSseEvent {
    /** 图片上传成功，已拿到 TOS 预签名 URL */
    data class Uploaded(val imageUrl: String) : OotdSseEvent

    /** 一段分析正文，UI 追加进打字机 */
    data class TextDelta(val text: String) : OotdSseEvent

    /** AI 输出引用到的知识库规则标题（后端校验 basic_style_knowledge 标题匹配） */
    data class ReferenceTitle(val title: String) : OotdSseEvent
}

class OotdSseClient {

    // 复用全局共享 OkHttpClient（连接池 / 调度器线程池单例），
    // 避免每个 ViewModel 实例都新建 OkHttpClient 造成连接池与线程泄漏。
    private val client: OkHttpClient = sharedClient

    /** 当前进行中的请求；页面退出 / 重新分析时 cancel，防止泄漏 */
    @Volatile
    private var currentCall: okhttp3.Call? = null

    /** 当前后端地址（统一走 ServerConfig，默认 adb reverse 127.0.0.1:8080，可动态切换） */
    fun baseUrl(): String = ServerConfig.get()

    /** 主动关闭 SSE（页面 onDispose / ViewModel.onCleared 调用） */
    fun close() {
        runCatching { currentCall?.cancel() }
        currentCall = null
        Log.i(TAG, "sse client closed by caller")
    }

    /**
     * 完整链路：上传压缩图 → 拿 TOS URL → SSE 流式分析。
     *
     * @param imageBytes [com.example.citygo.skeleton.util.OotdImageUtils] 压缩输出的 JPEG 字节
     * @param fileName 上传文件名（multipart file 字段）
     * @param question 用户提问（默认「帮我分析这套穿搭」）
     *
     * 正常结束（收到 [DONE] / 服务端 complete 后关流）Flow 直接 complete；
     * 上传失败抛 [OotdUploadException]；连接失败 / 超时等抛 [IOException]。
     */
    fun analyze(
        imageBytes: ByteArray,
        fileName: String,
        question: String,
    ): Flow<OotdSseEvent> = callbackFlow {
        // daemon 线程：进程退出时不阻塞 JVM；call.execute() 是阻塞调用，
        // awaitClose 中通过 currentCall.cancel() 主动中断 OkHttp 等待。
        val worker = Thread {
            var normalEnd = false
            var receivedAnyData = false
            runCatching {
                // ---------- 步骤 1：上传图片二进制，拿 TOS 预签名 URL ----------
                Log.i(TAG, "upload start: $fileName size=${imageBytes.size}")
                val imageUrl = uploadImage(imageBytes, fileName)
                Log.i(TAG, "upload ok, presigned url = $imageUrl")
                trySend(OotdSseEvent.Uploaded(imageUrl))

                // ---------- 步骤 2：建立 SSE 长连接 ----------
                val payload = JSONObject().apply {
                    put(KEY_IMAGE_URL, imageUrl)
                    put(KEY_QUESTION, question)
                }.toString()
                val request = Request.Builder()
                    .url(baseUrl() + PATH_SSE)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val call = client.newCall(request).also { currentCall = it }
                Log.i(TAG, "sse connect: ${baseUrl() + PATH_SSE}")

                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("服务异常（HTTP ${resp.code}），请稍后重试")
                    }
                    val source = resp.body?.source()
                        ?: throw IOException("服务响应为空，请稍后重试")

                    // 逐行读取；服务端关闭流后 readUtf8Line() 返回 null = 正常结束
                    while (true) {
                        val line = source.readUtf8Line()
                        if (line == null) {
                            normalEnd = true
                            break
                        }
                        when (val parsed = decodeSseLine(line)) {
                            LineResult.Skip -> Unit
                            LineResult.Done -> {
                                normalEnd = true
                                break
                            }
                            is LineResult.EventData -> {
                                if (parsed.event is OotdSseEvent.TextDelta) receivedAnyData = true
                                if (trySend(parsed.event).isFailure) break
                            }
                        }
                    }
                }
            }.onSuccess {
                Log.i(TAG, "sse finished normally, keep rendered text")
                close()
            }.onFailure { e ->
                val call = currentCall
                when {
                    call?.isCanceled() == true -> {
                        Log.i(TAG, "sse canceled by page, treat as normal stop")
                        close()
                    }
                    normalEnd -> {
                        Log.i(TAG, "completed([DONE]/EOF), ignore closing error: ${e.message}")
                        close()
                    }
                    // 部分后端 complete() 后直接断开 TCP，正文已完整收到：按成功处理
                    receivedAnyData && e.isEndOfStreamAfterContent() -> {
                        Log.i(TAG, "server closed after content, treat as success: ${e.message}")
                        close()
                    }
                    else -> {
                        Log.w(TAG, "sse failed: ${e.javaClass.simpleName}: ${e.message}")
                        close(e)
                    }
                }
            }
        }.apply { isDaemon = true }
        worker.start()

        awaitClose {
            runCatching { currentCall?.cancel() }
            currentCall = null
            runCatching { worker.interrupt() }
        }
    }.flowOn(Dispatchers.IO)

    /** 上传 JPEG 字节（multipart/form-data，key=file），容错解析 TOS 预签名 URL */
    private fun uploadImage(imageBytes: ByteArray, fileName: String): String {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                imageBytes.toRequestBody(JPEG_MEDIA_TYPE),
            )
            .build()

        val request = Request.Builder()
            .url(baseUrl() + PATH_UPLOAD)
            .post(multipart)
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "upload http ${resp.code}: $raw")
                    throw OotdUploadException("图片上传失败（HTTP ${resp.code}），请重试")
                }
                val url = parsePresignedUrl(raw)
                if (url.isNullOrBlank()) {
                    Log.w(TAG, "upload ok but no presigned url in body: $raw")
                    throw OotdUploadException("上传成功但未获取到图片地址，请稍后重试")
                }
                return url
            }
        } catch (e: OotdUploadException) {
            throw e
        } catch (e: IOException) {
            // 连接层失败归类为网络问题（UI 文案引导检查局域网 IP / WiFi），不冒充上传成功
            Log.w(TAG, "upload network error: ${e.message}")
            throw IOException("图片上传失败：${e.message ?: "网络异常，请检查手机与电脑是否同一 WiFi"}", e)
        }
    }

    /**
     * 解析上传响应中的 TOS 预签名 URL，兼容多种常见包裹：
     * 根字段 presignedUrl/url/imageUrl/tosUrl → data.xxx → result.xxx → data 纯字符串。
     */
    private fun parsePresignedUrl(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        // 后端直接返回纯 URL（带引号也兼容）
        if (!text.startsWith("{")) {
            return text.trim('"').takeIf { it.startsWith("http", ignoreCase = true) }
        }
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        for (key in URL_FIELDS) {
            json.optString(key, "").takeIf { it.isNotBlank() }?.let { return it }
        }
        json.optJSONObject("data")?.let { data ->
            for (key in URL_FIELDS) {
                data.optString(key, "").takeIf { it.isNotBlank() }?.let { return it }
            }
            data.optString("data", "").takeIf { it.isNotBlank() }?.let { return it }
        }
        json.optJSONObject("result")?.let { result ->
            for (key in URL_FIELDS) {
                result.optString(key, "").takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    /** SSE 单行解析结果：跳过 / [DONE] / 有效事件 */
    private sealed class LineResult {
        object Skip : LineResult()
        object Done : LineResult()
        data class EventData(val event: OotdSseEvent) : LineResult()
    }

    /**
     * 解析一行 SSE：
     * - 仅处理 data: 帧，截取其后字符串；忽略空行 / 注释 / event:、id:、retry:；
     * - data:[DONE] → [LineResult.Done]；
     * - data 负载为 JSON：识别「知识库引用标题」帧，或取 content/delta/text 等文本字段；
     * - 非 JSON：整段作为正文片段（后端直接吐纯文本时）。
     */
    private fun decodeSseLine(line: String): LineResult {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return LineResult.Skip
        if (!trimmed.startsWith("data:", ignoreCase = true)) return LineResult.Skip

        val payload = trimmed.substring(5).let { if (it.startsWith(" ")) it.substring(1) else it }
        if (payload.isEmpty()) return LineResult.Skip
        if (payload == DONE_TOKEN) return LineResult.Done

        val json = runCatching { JSONObject(payload) }.getOrNull()
            ?: return LineResult.EventData(OotdSseEvent.TextDelta(payload))

        // ① 知识库引用标题帧：type/event 表明是引用，或仅含标题字段
        val kind = json.optString("type").ifBlank { json.optString("event") }.lowercase()
        val isReferenceFrame = kind in REFERENCE_KINDS
        if (isReferenceFrame) {
            for (key in TITLE_FIELDS) {
                json.optString(key, "").takeIf { it.isNotBlank() }?.let {
                    return LineResult.EventData(OotdSseEvent.ReferenceTitle(it))
                }
            }
        }

        // ② 正文帧：多字段兼容
        for (key in TEXT_FIELDS) {
            json.optString(key, "").takeIf { it.isNotBlank() }?.let {
                return LineResult.EventData(OotdSseEvent.TextDelta(it))
            }
        }
        json.optJSONObject("data")?.let { data ->
            for (key in TEXT_FIELDS) {
                data.optString(key, "").takeIf { it.isNotBlank() }?.let {
                    return LineResult.EventData(OotdSseEvent.TextDelta(it))
                }
            }
        }
        // JSON 但结构不可识别：不把原始 JSON 渲染到页面
        return LineResult.Skip
    }

    /** 正文已下发后服务端直接断连的识别（与 Stylist 模块同一甄别规则） */
    private fun Throwable.isEndOfStreamAfterContent(): Boolean {
        if (this !is IOException) return false
        val msg = message.orEmpty().lowercase()
        return msg.contains("unexpected end of stream") ||
            msg.contains("end of stream") ||
            msg.contains("connection is closed") ||
            msg.contains("stream was closed")
    }

    companion object {
        private const val TAG = "OotdSse"

        /**
         * 全局共享 OkHttpClient（连接池 / 调度器线程池单例）：
         * 所有 OotdSseClient 实例复用同一实例，避免反复创建导致连接池与线程泄漏。
         * 读超时 120s：按「两个分片之间」的最大间隔计，持续有分片下发不会超时。
         */
        private val sharedClient: OkHttpClient by lazy {
            AuthHttpClient.streamingClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        /**
         * 后端地址统一走全局 [ServerConfig]：
         * 默认 adb reverse（USB 端口转发）= http://127.0.0.1:8080/，
         * 也可通过 ServerConfig.updateBaseUrl 切换为局域网 IP；不再硬编码 HOST_IP。
         */
        /** 供 UI 展示当前后端地址（取 ServerConfig 实时值） */
        fun currentBaseUrl(): String = ServerConfig.get()

        /** 上传接口：multipart 二进制图片 → TOS 预签名 URL */
        private const val PATH_UPLOAD = "api/ootd/upload"

        /** SSE 接口：POST JSON，分片返回穿搭分析文本 */
        private const val PATH_SSE = "api/ootd/chat/sse"

        /** SSE 请求体 JSON 字段（与后端契约对齐，如不一致只改这里） */
        private const val KEY_IMAGE_URL = "imageUrl"
        private const val KEY_QUESTION = "prompt"

        private const val DONE_TOKEN = "[DONE]"
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()

        private val URL_FIELDS = listOf("presignedUrl", "imageUrl", "tosUrl", "url", "data")
        private val TEXT_FIELDS = listOf("content", "delta", "text", "message", "answer", "result")
        private val TITLE_FIELDS = listOf("title", "ruleTitle", "knowledgeTitle", "name")
        private val REFERENCE_KINDS = setOf(
            "reference", "knowledge", "cite", "citation", "ref", "rule", "rule_title",
        )
    }
}
