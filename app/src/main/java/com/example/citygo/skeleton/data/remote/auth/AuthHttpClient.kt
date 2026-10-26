package com.example.citygo.skeleton.data.remote.auth

import android.content.Context
import android.util.Log
import com.example.citygo.skeleton.data.datastore.AccountSession
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.EventListener
import com.example.citygo.skeleton.data.datastore.SessionSnapshot
import java.io.IOException
import java.util.Collections
import java.util.WeakHashMap
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * 账号模块 OkHttp 客户端（进程内单例，本模块不使用 Retrofit，只用原生 OkHttp）。
 *
 * 职责：
 * 1. 构建统一 [OkHttpClient]：连接/读/写超时（见 [AuthApiConfig]）；
 * 2. 认证拦截器：本地存在 JWT 时，为每个请求自动追加 "Authorization: Bearer <token>"；
 * 3. 日志拦截器：打印请求行 / 响应码 / body，方便联调。
 *
 * [init] 在 Application.onCreate 调用一次完成客户端预热；当前登录账号在
 * AccountSession（StateFlow 单例）中维护，拦截器只从该唯一来源同步读取 token。
 */
object AuthHttpClient {

    private const val TAG = "AuthHttpClient"

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    /**
     * AI 攻略专用长读超时客户端（同步等待 Agent 生成 Markdown，可能耗时数十秒）。
     * 鉴权 / 401 / 完整日志拦截器与默认客户端完全一致，仅读超时放宽，
     * 不影响注册登录等既有请求的 10s 超时配置。
     */
    @Volatile
    private var longReadHttpClient: OkHttpClient? = null
    @Volatile
    private var streamingHttpClient: OkHttpClient? = null

    val streamingClient: OkHttpClient
        get() = streamingHttpClient ?: synchronized(this) {
            streamingHttpClient ?: buildClient(
                readTimeoutSeconds = 300,
                writeTimeoutSeconds = 120,
                logLevel = HttpLoggingInterceptor.Level.HEADERS,
            ).also { streamingHttpClient = it }
        }

    private val requestSessions = Collections.synchronizedMap(WeakHashMap<Call, SessionSnapshot>())

    /** 不创建新客户端；身份切换时立即中断旧账号正在运行和排队的请求。 */
    fun cancelAccountCalls() {
        okHttpClient?.dispatcher?.cancelAll()
        longReadHttpClient?.dispatcher?.cancelAll()
        streamingHttpClient?.dispatcher?.cancelAll()
    }

    /** 对外暴露的唯一 OkHttp 客户端（懒加载 + 双检锁） */
    val client: OkHttpClient
        get() = okHttpClient ?: synchronized(this) {
            okHttpClient ?: buildClient().also { okHttpClient = it }
        }

    /** AI 攻略长读客户端（connect/write 10s，read 300s，与后端 RestTemplate 300s 对齐） */
    val longReadClient: OkHttpClient
        get() = longReadHttpClient ?: synchronized(this) {
            longReadHttpClient ?: buildClient(readTimeoutSeconds = AI_GUIDE_READ_TIMEOUT_SECONDS)
                .also { longReadHttpClient = it }
        }

    /** Application.onCreate 调用：预热客户端（token 统一由 AccountSession 提供） */
    fun init(context: Context) {
        client
    }

    private fun buildClient(
        readTimeoutSeconds: Long = AuthApiConfig.READ_TIMEOUT_SECONDS,
        writeTimeoutSeconds: Long = AuthApiConfig.WRITE_TIMEOUT_SECONDS,
        logLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY,
    ): OkHttpClient {
        // ---- 认证拦截器（全 App 鉴权请求统一入口，Retrofit / 手写 OkHttp 共用此 client）----
        // 规则（严格对齐后端契约）：
        // 1) 注册 /userRegister、登录 /userLogin、发验证码 /sendCode、验证码登录 /verifyCodeLogin
        //    属于公开接口：即使本地残留旧 JWT，也【绝不】附加 Authorization 头；
        // 2) 其余接口（资料库、/travel/generateTask 提交、任务轮询、用户信息等）：
        //    只要 SharedPreferences 中存在登录时保存的 JWT，自动添加
        //    请求头 Authorization: Bearer <JWT_TOKEN>；
        // 3) token 持久化在 SharedPreferences（AuthTokenStore），AccountSession 是同步内存镜像，
        //    切换账号 / 重新登录后立即取到新值，无需重启 App。
        //
        // 同时承担「token 失效统一处置」：
        // - 响应 HTTP 401，或响应体为统一结构 {code:401,...}（后端常以 HTTP 200 包裹业务 401），
        //   发射 AccountSession.authInvalidation 全局事件 → UI 层吐司并清栈跳登录页；
        // - 带内部标记头 HEADER_SKIP_AUTH_EVENT 的请求（如 /logout）不触发该事件，
        //   标记头仅本地使用，发出前必须移除。
        val authInterceptor = okhttp3.Interceptor { chain ->
            val original = chain.request()
            val session = checkNotNull(requestSessions[chain.call()])
            if (!AccountSession.isCurrent(session)) throw IOException("账号已切换，请重新操作")
            val skipAuthEvent = original.header(AuthApiConfig.HEADER_SKIP_AUTH_EVENT) != null
            // 公开接口（注册 / 登录等）不允许带 JWT
            val isPublic = AuthApiConfig.isPublicPath(original.url.encodedPath)

            val builder = original.newBuilder()
                // 内部标记头不下发给后端
                .removeHeader(AuthApiConfig.HEADER_SKIP_AUTH_EVENT)

            if (!isPublic) {
                // 内存会话优先（实时），SharedPreferences 兜底（冷启动等场景），二者始终同步
                val token = session.token
                if (!token.isNullOrBlank()) {
                    builder.header(
                        AuthApiConfig.HEADER_AUTH,
                        AuthApiConfig.BEARER_PREFIX + token,
                    )
                }
            } else {
                // 双保险：移除任何上游可能误加的 Authorization 头
                builder.removeHeader(AuthApiConfig.HEADER_AUTH)
            }

            val response = chain.proceed(builder.build())

            if (!AccountSession.isCurrent(session)) {
                response.close()
                throw IOException("账号已切换，已丢弃旧请求结果")
            }

            if (!skipAuthEvent) {
                val invalid = when {
                    response.code == 401 -> true
                    // peekBody 只预读最多 2KB 到内存，不消耗真实响应流，不影响后续解析
                    response.code == 200 && response.body?.contentType()?.subtype != "event-stream" ->
                        runCatching {
                            // 后端统一体可能以 HTTP 200 返回业务 code=401
                            Regex("\"code\"\\s*:\\s*401").containsMatchIn(
                                response.peekBody(2048).string(),
                            )
                        }.getOrDefault(false)
                    else -> false
                }
                if (invalid) {
                    Log.w(TAG, "unauthorized detected: http=${response.code}, path=${original.url.encodedPath}")
                    AccountSession.notifyTokenInvalid(
                        "http=${response.code}, path=${original.url.encodedPath}",
                        session,
                    )
                }
            }
            response
        }

        // ---- HTTP 日志拦截器（联调打印完整请求/响应） ----
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            redactHeader("Authorization")
            level = logLevel
        }

        return OkHttpClient.Builder()
            .eventListenerFactory { call ->
                // newCall 时捕获身份，而非排队结束后才读取当前账号。
                requestSessions[call] = AccountSession.snapshot()
                object : EventListener() {}
            }
            .connectTimeout(AuthApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * AI 攻略专用读超时（秒）：与后端 RestTemplate 300s 读取超时对齐，
     * 覆盖「提交请求同步等待生成」+「轮询单次请求」两种场景，避免前端 OkHttp
     * 先于后端超时把仍在执行的任务误判为失败。
     */
    private const val AI_GUIDE_READ_TIMEOUT_SECONDS = 300L
}
