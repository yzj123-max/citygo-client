package com.example.citygo.skeleton.data.remote

import com.example.citygo.skeleton.data.remote.auth.AuthHttpClient
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 全局统一 Retrofit 工厂：所有自有后端 Service（账号 / 用户资料 / 餐厅 / AI 攻略…）
 * 都通过这里创建，baseUrl 统一来自 [ServerConfig]，不再各自 new Retrofit.Builder()。
 *
 * 实例按「baseUrl + OkHttp 客户端」缓存：
 * - 同一地址重复取用不重复创建；
 * - 在输入框切换地址后，再次取用会自动得到指向新地址的新实例；
 * - 是否带鉴权拦截器由传入的 client 决定，互不干扰。
 */
object AppRetrofit {

    /** 缓存键：地址与客户端都相同时才复用同一个 Retrofit */
    private data class CacheKey(val baseUrl: String, val client: OkHttpClient)

    private val cache = ConcurrentHashMap<CacheKey, Retrofit>()

    /**
     * 免鉴权公共客户端：餐厅 / AI 攻略等接口（不需要 Bearer 头）使用。
     * 账号模块的 [AuthHttpClient.client] 已含 Bearer / 401 拦截器，这里不重复造。
     */
    @PublishedApi
    internal val plainClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // 联调期打印完整请求/响应，Logcat 过滤 tag=OkHttp 可核对地址、路径与 JSON
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS) // AI 生成链路查询稍慢，读超时放宽
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    /**
     * 通用创建入口。
     *
     * @param serviceClass Retrofit 接口 Class
     * @param client       OkHttp 客户端；默认复用账号模块客户端（自动带 Bearer + 401 处理）
     * @param baseUrl      服务地址；默认取全局 [ServerConfig.get]
     */
    fun <T> create(
        serviceClass: Class<T>,
        client: OkHttpClient = AuthHttpClient.client,
        baseUrl: String = ServerConfig.get(),
    ): T {
        val normalizedUrl = ServerConfig.normalize(baseUrl)
        val key = CacheKey(normalizedUrl, client)
        val retrofit = cache.getOrPut(key) {
            Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit.create(serviceClass)
    }

    /**
     * 创建需要 Bearer 鉴权的 Service（用户信息 / 资料 / 退出登录 / AI 攻略提交与轮询等）。
     * 可显式指定本次请求的地址（AI 弹窗中可临时改地址），默认取全局 [ServerConfig.get]。
     */
    inline fun <reified T> createAuthed(baseUrl: String = ServerConfig.get()): T =
        create(T::class.java, AuthHttpClient.client, baseUrl)

    /**
     * 创建需要 Bearer 鉴权、且读超时较长的 Service（AI 攻略：后端同步等待 Agent 生成
     * Markdown，可能耗时 100~180s）。鉴权 / 401 / BODY 日志拦截器与 [createAuthed] 完全一致，
     * 仅读超时放宽到 300s（与后端 RestTemplate 对齐）；可显式指定地址。
     */
    inline fun <reified T> createAuthedLongRead(baseUrl: String = ServerConfig.get()): T =
        create(T::class.java, AuthHttpClient.longReadClient, baseUrl)

    /** 创建免鉴权 Service（餐厅 / AI 攻略轮询等），可显式指定本次请求的地址 */
    inline fun <reified T> createPlain(baseUrl: String = ServerConfig.get()): T =
        create(T::class.java, plainClient, baseUrl)
}
