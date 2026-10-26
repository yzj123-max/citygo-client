package com.example.citygo.skeleton.data.remote.auth

import com.example.citygo.skeleton.data.remote.ServerConfig

/**
 * 账号模块网络配置常量（统一收口，禁止在业务代码里散落写死 URL / 路径 / 头字段）。
 */
object AuthApiConfig {

    /**
     * 后端基础地址 —— 全局唯一动态地址，来自 [ServerConfig]。
     *
     * 地址在「AI 旅游攻略」页的后端地址输入框运行时填写并持久化，
     * 登录 / 注册 / 验证码 / 用户信息 / 退出登录等请求每次发起时都读取最新值，
     * 切换热点 / WiFi / ADB 反向代理后无需重新编译（默认值见 ServerConfig.DEFAULT_BASE_URL）。
     * 明文 HTTP 已在 res/xml/network_security_config.xml 全局放行。
     *
     * 注意：必须保持为计算属性（get），不能改成 const/lazy，否则地址更新后不会生效。
     */
    val BASE_URL: String
        get() = ServerConfig.get()

    // ---- 接口路径（相对 BASE_URL；严格对齐后端当前契约；均无前导 "/"） ----
    /** 注册：POST /userRegister（公开接口，不带 JWT；@Body JSON：{"phone","password"}） */
    const val PATH_REGISTER = "userRegister"
    /** 密码登录：POST /userLogin（公开接口，不带 JWT；@Body JSON，成功 data={"token":"xxx"}） */
    const val PATH_LOGIN = "userLogin"
    /** 发送验证码：POST /sendCode（保留，当前注册/登录页不使用） */
    const val PATH_SEND_CODE = "sendCode"
    /** 免密验证码登录：POST /verifyCodeLogin（保留，当前注册/登录页不使用） */
    const val PATH_CODE_LOGIN = "verifyCodeLogin"
    /** 获取当前登录用户信息（后端契约：GET /info） */
    const val PATH_GET_USER_INFO = "info"
    const val PATH_LOGOUT = "logout"
    /** 完善 / 更新用户资料（预留接口，后端就绪后直接联调） */
    const val PATH_UPDATE_USER_INFO = "updateUserInfo"

    /**
     * 公开（免鉴权）接口路径后缀：注册 / 登录 / 发验证码 / 验证码登录。
     * 这些请求即使本地残留旧 JWT 也绝不允许带 Authorization 头（后端安全配置放行 / 拦截要求）。
     * 用 encodedPath 后缀匹配，兼容任意动态 baseUrl（IP / 域名 / 端口 / 反代前缀）。
     */
    private val PUBLIC_PATH_SUFFIXES = listOf(
        "/$PATH_REGISTER",
        "/$PATH_LOGIN",
        "/$PATH_SEND_CODE",
        "/$PATH_CODE_LOGIN",
    )

    /** 判断该请求路径是否为公开接口（注册 / 登录等），公开接口不附加 Bearer 头 */
    fun isPublicPath(encodedPath: String): Boolean =
        PUBLIC_PATH_SUFFIXES.any { encodedPath.endsWith(it) }

    // ---- Query / Body / Header 字段名（与后端契约严格一致） ----
    const val FIELD_PHONE = "phone"
    const val FIELD_PASSWORD = "password"
    const val FIELD_CODE = "code"
    const val HEADER_AUTH = "Authorization"
    /**
     * 内部标记头：请求带此头表示「跳过 401 全局踢登录事件」。
     * 用于 /logout（退出 / 切换账号时旧 token 调退出，401 不应把用户踢去登录页）。
     * 拦截器会在发出前把该头移除，后端永远收不到。
     */
    const val HEADER_SKIP_AUTH_EVENT = "X-Auth-Event-Skip"
    /** JWT 前缀，Bearer 与 token 之间必须有一个空格 */
    const val BEARER_PREFIX = "Bearer "

    /** JSON 请求的 Content-Type（register / login 使用 JSON Body；verifyCodeLogin 用表单不用它） */
    const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
    /** 表单请求的 Content-Type（/verifyCodeLogin，后端 @RequestParam 只认 Query/表单） */
    const val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded"

    // ---- 超时时间（秒） ----
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 10L
    const val WRITE_TIMEOUT_SECONDS = 10L
}
