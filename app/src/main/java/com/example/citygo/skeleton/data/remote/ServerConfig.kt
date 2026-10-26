package com.example.citygo.skeleton.data.remote

import android.content.Context
import android.util.Log

/**
 * 全局后端服务地址配置（唯一权威来源，Single Source of Truth）。
 *
 * =====================================================================================
 * ⚠️ 当前为【adb reverse USB 端口转发联调模式】：默认 baseUrl = http://127.0.0.1:8080/
 *
 * - 真机用 USB 连电脑，电脑执行过：adb reverse tcp:8080 tcp:8080；
 *   真机对 127.0.0.1:8080 的请求会被 USB 转发到电脑本机的 8080 端口，不依赖局域网 IP；
 * - App（Retrofit / OkHttp）统一通过 [get] 取地址；
 * - 注册 / 登录 / 资料库 / travel/generateTask 攻略生成全部打到该地址。
 *
 * 🔧 调试切换能力（不要写死）：
 *    地址【可】通过 [updateBaseUrl] 运行时切换，[normalize] 会校验 http(s) 开头并补 "/"。
 *    例如切回局域网模式：
 *        ServerConfig.updateBaseUrl("http://172.20.10.2:8080/")
 *
 * 📌 后端配置不受本文件影响：
 *    后端 -Djava.net.preferIPv6Addresses=true 及内部调用 IPv6 Agent 的逻辑全部保留，
 *    该 JVM 参数只影响后端【出站】访问 Agent，不影响 App 到后端的【入站】请求。
 * =====================================================================================
 *
 * 注意：第三方服务（高德天气/POI、火山方舟、通义千问）地址与本配置无关，保持各自独立。
 */
object ServerConfig {

    /**
     * adb reverse（USB 端口转发）联调默认地址（末尾必须保留 "/"，Retrofit baseUrl 要求）。
     * 前提：电脑执行过 adb reverse tcp:8080 tcp:8080，后端监听本机 8080 端口即可。
     */
    const val DEBUG_BASE_URL = "http://127.0.0.1:8080/"

    /** adb reverse（USB 端口转发）模式地址（与当前默认地址同源，保留常量供显式切换使用） */
    const val ADB_REVERSE_BASE_URL = "http://127.0.0.1:8080/"

    /** 历史默认地址别名：保持现有调用点（DEFAULT_BASE_URL）不改动，值与当前默认地址一致 */
    const val DEFAULT_BASE_URL = DEBUG_BASE_URL

    /** 当前生效地址：默认 adb reverse USB 转发地址，可被 [updateBaseUrl] 切换（如切回局域网 IP） */
    @Volatile
    private var currentBaseUrl: String = DEBUG_BASE_URL

    private lateinit var appContext: Context

    /** Application.onCreate 中调用一次（保持原有初始化时序）。 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 当前后端地址；Retrofit 工厂与账号模块 OkHttp 每次构造都调用这里。 */
    fun get(): String {
        // ── 诊断日志：确认运行时最终生效的 baseUrl 未被切到局域网 IP ──
        Log.d("ServerConfig", "get() currentBaseUrl=$currentBaseUrl")
        return currentBaseUrl
    }

    /**
     * 切换后端基础地址（调试用）：
     * - 必须 http:// 或 https:// 开头，末尾缺 "/" 自动补齐，非法抛 IllegalArgumentException；
     * - 传入 [ADB_REVERSE_BASE_URL] 即切回 adb reverse 模式；
     * - AppRetrofit 按地址缓存实例，切换后再取 Service 会自动得到指向新地址的新实例。
     *
     * 【场景C临时禁用】动态切换已被禁用，调用仅打日志不实际改地址，
     * 防止运行时被误切到局域网 IP 导致 404。如需恢复，移除下方 early return 即可。
     */
    fun updateBaseUrl(input: String): String {
        // 场景C修复：禁用动态切换，锁定开发环境地址
        Log.w("ServerConfig", "updateBaseUrl BLOCKED (requested=$input), keeping current=$currentBaseUrl")
        return currentBaseUrl
//        val old = currentBaseUrl
//        currentBaseUrl = normalize(input)
//        // ── 诊断日志：地址被切换时打印新旧值，排查是否误切到局域网 IP ──
//        Log.w("ServerConfig", "updateBaseUrl: $old -> $currentBaseUrl (input=$input)")
//        return currentBaseUrl
    }

    /**
     * 规整用户输入：去空白、必须 http(s) 开头、自动补末尾 "/"。
     */
    fun normalize(input: String): String {
        val trimmed = input.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "地址需以 http:// 或 https:// 开头"
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
