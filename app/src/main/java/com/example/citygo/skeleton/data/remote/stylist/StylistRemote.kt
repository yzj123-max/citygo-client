package com.example.citygo.skeleton.data.remote.stylist

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.citygo.skeleton.util.OotdImageUtils
import com.example.citygo.skeleton.data.remote.ServerConfig
import com.example.citygo.skeleton.data.remote.auth.AuthHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 图片文件直接提交，后端负责上传 TOS 并生成签名 URL。 */
class StylistRemote(private val appContext: Context) {

    /**
     * 发起 SSE 流式穿搭评估，Flow 每发射一个 String 就是本次新增的一段文字，
     * UI 直接 append 即可；服务端关闭连接（Connection closed）视为正常结束。
     *
     * 注意：Flow 取消（用户离开页面 / 发起新一轮）时会同步 Call.cancel() 中断读取。
     */
    fun streamChat(
        imageUris: List<Uri>,
        message: String,
        travelSchedule: String,
    ): Flow<String> = callbackFlow {
            require(imageUris.size in 1..9) { "请选择 1 到 9 张穿搭照片" }
            val url = ServerConfig.get() + PATH_CHAT_SSE
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("msg", message)
                .addFormDataPart("travelSchedule", travelSchedule)
            var totalBytes = 0L
            imageUris.forEachIndexed { index, uri ->
                coroutineContext.ensureActive()
                // 复用已有的旋正和压缩工具，避免多张原图占用过多内存。
                val bytes = OotdImageUtils.compressToBytes(appContext, uri)
                if (bytes.isEmpty() || bytes.size > 10 * 1024 * 1024) {
                    throw IOException("照片 ${index + 1} 处理失败或超过 10MB")
                }
                totalBytes += bytes.size
                if (totalBytes > 40L * 1024 * 1024) {
                    throw IOException("照片合计不能超过 40MB")
                }
                multipartBuilder.addFormDataPart(
                    "images", "outfit_${index + 1}.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaType()),
                )
            }
            val multipart = multipartBuilder.build()
            Log.i(TAG, "POST $url, images=${imageUris.size}, imageBytes=$totalBytes")

            // 本模块独立客户端：仅 HEADERS 级日志（不缓冲 body）。
            // 后端先上传 TOS 再等待模型首帧，读取超时与其 300s emitter 对齐。
            // SSE 持续有分块下发时不会超时。
            val client = AuthHttpClient.streamingClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            // MultipartBody 自动生成 Content-Type 和 boundary。
            val httpRequest = Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .post(multipart)
                .build()

            val call = client.newCall(httpRequest)
            val worker = Thread {
                var normalEnd = false
                var receivedAnyData = false
                runCatching {
                    call.execute().use { resp ->
                        // ── StylistDebug：收到响应，打印 response.code ──
                        Log.i("StylistDebug", "SSE评估接口 response.code=${resp.code}, message=${resp.message}, isSuccessful=${resp.isSuccessful}")
                        if (!resp.isSuccessful) {
                            val errBody = resp.body?.string() ?: ""
                            Log.e(TAG, "sse non-2xx responseBody=$errBody")
                            throw IOException("评估接口返回 HTTP ${resp.code}（${resp.request.url.encodedPath}），请核对后端接口及服务配置")
                        }
                        val source = resp.body?.source()
                            ?: throw IOException("服务响应为空，请稍后重试")
                        // 逐行读取 SSE；服务端正常关闭连接后 readUtf8Line() 返回 null，结束收集
                        while (true) {
                            val line = source.readUtf8Line()
                            if (line == null) {
                                // 响应体正常读到结尾（chunked 正常收尾）= 生成成功
                                normalEnd = true
                                break
                            }
                            val delta = decodeSseLine(line) ?: continue
                            if (delta == DONE_TOKEN) {
                                // 后端 complete() 前下发的 [DONE] = 生成成功
                                normalEnd = true
                                break
                            }
                            if (delta.isEmpty()) continue
                            receivedAnyData = true
                            // callbackFlow 自带缓冲通道；channel 已关闭（取消/异常）时停止读取
                            val sendResult = trySendBlocking(delta)
                            if (sendResult.isFailure) break
                        }
                    }
                }.onSuccess {
                    // 正常读完：成功收尾，打字机文字保留，不弹任何错误
                    close()
                }.onFailure { e ->
                    when {
                        // 用户离开页面 / 发起新一轮导致 Call.cancel()：主动停止，非异常
                        call.isCanceled() -> {
                            Log.i(TAG, "sse call canceled, treat as normal stop")
                            close()
                        }
                        // 已确认正常结束（[DONE] / 流尾 null）后，收尾读取时再抛错：按成功
                        normalEnd -> {
                            Log.i(TAG, "stream completed, ignore closing error: ${e.message}")
                            close()
                        }
                        // 部分后端 complete() 后直接断开 TCP（无 chunked 结束标记），
                        // OkHttp 会抛 "unexpected end of stream"；正文已完整收到，按成功处理
                        receivedAnyData && e.isEndOfStreamAfterContent() -> {
                            Log.i(TAG, "server closed after content, treat as success: ${e.message}")
                            close()
                        }
                        // 其余情况（连接失败 / HTTP 非 2xx / 300s 读超时 / 流中途断开）才是真异常
                        else -> {
                            Log.e(TAG, "sse stream failed: ${e.message}", e)
                            close(e)
                        }
                    }
                }
            }
            worker.start()

            // 取消收集（页面销毁 / 新一轮评估）时中断底层请求，线程随即退出
            awaitClose {
                runCatching { call.cancel() }
                runCatching { worker.interrupt() }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * 解析一行 SSE 文本为「本段新增文字」（严格按后端当前下发规则）：
     * - 逐行读取，只有以 "data:"（或 "data: "）开头的行才是正文帧；
     * - 截取 "data:" 后面的字符串（SSE 规范允许省略一个前导空格）直接返回，
     *   UI 拿到后 append 即可形成逐字打字效果；
     * - 负载为 [DONE] 时返回特殊令牌 [DONE_TOKEN]，上层据此判定生成成功；
     * - 空行 / 注释行(:) / event: / id: / retry: 等元信息行一律忽略。
     */
    private fun decodeSseLine(line: String): String? {
        val trimmed = line
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith(":")) return null
        if (!trimmed.startsWith("data:", ignoreCase = true)) return null

        // 截取 "data:" 之后的字符串；若有一个前导空格（"data: xxx"）则去掉
        val payload = trimmed.substring(5).let { rest ->
            if (rest.startsWith(" ")) rest.substring(1) else rest
        }
        if (payload.isEmpty()) return null
        if (payload == DONE_TOKEN) return DONE_TOKEN
        return payload
    }

    /**
     * 判断异常是否属于「正文已经下发，服务端 complete() 后直接断开连接」：
     * 部分 SSE 服务端不写 chunked 结束标记，OkHttp 会抛
     * "unexpected end of stream ..." / "Connection is closed"。
     * 这不是生成失败，调用方需在已收到正文的前提下按成功处理。
     */
    private fun Throwable.isEndOfStreamAfterContent(): Boolean {
        if (this !is IOException) return false
        val msg = message.orEmpty().lowercase()
        return msg.contains("unexpected end of stream") ||
            msg.contains("end of stream") ||
            msg.contains("connection is closed") ||
            msg.contains("stream was closed")
    }

    companion object {
        private const val TAG = "StylistRemote"
        private const val PATH_CHAT_SSE = "api/stylist/chat/multiImgChatSse"
        private const val DONE_TOKEN = "[DONE]"

    }
}
