package com.example.citygo.skeleton.data.remote.travel

import com.example.citygo.skeleton.data.remote.AppRetrofit
import com.example.citygo.skeleton.data.remote.ServerConfig

/**
 * AI 攻略模块网络入口（薄封装）。
 *
 * 全局只有一套 Retrofit 创建逻辑（[AppRetrofit]）和一个后端地址来源（[ServerConfig]）：
 * - 使用带鉴权拦截器的【长读超时】客户端：登录拿到 JWT 后请求自动附带
 *   "Authorization: Bearer <token>"；后端同步等待 Agent 生成 Markdown 期间不会被短读超时断开；
 * - OkHttp BODY 日志会打印完整请求体 / 响应体，真机联调时 Logcat 过滤 AuthHttpClient 即可排查。
 */
object TravelRetrofit {

    /** 兜底地址，与全局默认地址同源 */
    const val DEFAULT_BASE_URL = ServerConfig.DEFAULT_BASE_URL

    /**
     * 按运行时传入的 baseUrl 创建（或复用缓存的）[TravelApi]。
     * 地址需以 http(s):// 开头，内部自动补齐末尾 "/"；非法地址抛 IllegalArgumentException。
     */
    fun create(baseUrl: String): TravelApi = AppRetrofit.createAuthedLongRead(baseUrl)
}
