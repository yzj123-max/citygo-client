package com.example.citygo.skeleton.data.remote.ootd

// OOTD SSE 网络请求工具类
import android.util.Base64
import com.example.citygo.skeleton.data.remote.ServerConfig
import com.example.citygo.skeleton.data.remote.auth.AuthHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface OotdSseEvent {
    data class TextDelta(val text: String) : OotdSseEvent
    data class ReferenceTitle(val title: String) : OotdSseEvent
}

class OotdServiceException(message: String) : IOException(message)

class OotdSseClient {
    private fun baseUrl(): String = ServerConfig.get()

    fun analyze(imageBytes: ByteArray, question: String): Flow<OotdSseEvent> = callbackFlow<OotdSseEvent> {
        val payload = JSONObject().apply {
            put("base64", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
            put("prompt", question)
        }.toString()
        val request = Request.Builder()
            .url(baseUrl() + "api/ai/ootd-stream")
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val call = sharedClient.newCall(request)
        val worker = Thread {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw OotdServiceException("穿搭服务请求失败（HTTP ${response.code}），请稍后重试")
                    }
                    val body = response.body ?: throw OotdServiceException("穿搭服务未返回分析结果，请稍后重试")
                    if (body.contentType()?.let { it.type == "text" && it.subtype == "event-stream" } != true) {
                        throw OotdServiceException("穿搭服务返回格式异常，请稍后重试")
                    }
                    val source = body.source()
                    val data = mutableListOf<String>()
                    var receivedText = false
                    while (true) {
                        val line = source.readUtf8Line()
                        if (line?.startsWith("data:") == true) {
                            data.add(line.substring(5).removePrefix(" "))
                        } else if (line.isNullOrEmpty()) {
                            val parsed = decodeSseData(data.joinToString("\n"))
                            data.clear()
                            if (parsed == LineResult.Done) break
                            if (parsed is LineResult.EventData) {
                                if (parsed.event is OotdSseEvent.TextDelta && parsed.event.text.isNotBlank()) {
                                    receivedText = true
                                }
                                if (trySendBlocking(parsed.event).isFailure) return@Thread
                            }
                            if (line == null) break
                        }
                    }
                    if (!receivedText) {
                        throw OotdServiceException("穿搭服务未返回分析结果，请稍后重试")
                    }
                }
                close()
            } catch (error: Exception) {
                close(error)
            }
        }.apply { isDaemon = true }
        worker.start()
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    /** SSE 事件解析结果：跳过 / [DONE] / 有效事件 */
    private sealed class LineResult {
        object Skip : LineResult()
        object Done : LineResult()
        data class EventData(val event: OotdSseEvent) : LineResult()
    }

    private fun decodeSseData(payload: String): LineResult {
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
            json.optString(key, "").takeIf { it.isNotEmpty() }?.let {
                return LineResult.EventData(OotdSseEvent.TextDelta(it))
            }
        }
        json.optJSONObject("data")?.let { data ->
            for (key in TEXT_FIELDS) {
                data.optString(key, "").takeIf { it.isNotEmpty() }?.let {
                    return LineResult.EventData(OotdSseEvent.TextDelta(it))
                }
            }
        }
        // JSON 但结构不可识别：不把原始 JSON 渲染到页面
        return LineResult.Skip
    }

    companion object {
        private val sharedClient: OkHttpClient by lazy {
            AuthHttpClient.streamingClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        private const val DONE_TOKEN = "[DONE]"
        private val TEXT_FIELDS = listOf("content", "delta", "text", "message", "answer", "result")
        private val TITLE_FIELDS = listOf("title", "ruleTitle", "knowledgeTitle", "name")
        private val REFERENCE_KINDS = setOf(
            "reference", "knowledge", "cite", "citation", "ref", "rule", "rule_title",
        )
    }
}
