package com.example.citygo.skeleton.data.remote.auth

import android.content.Context

/* =====================================================================================
 * JWT 令牌本地持久化（经典 SharedPreferences）
 *
 * 严格按后端联调契约：
 * - 登录（POST /userLogin）成功拿到 JWT（data.token）后，立刻写入 SharedPreferences；
 * - OkHttp 鉴权拦截器（见 AuthHttpClient）对每个需要鉴权的请求，
 *   只要 SP 中存在 token，就自动附加请求头：Authorization: Bearer <JWT>；
 * - 注册 / 登录 / 发验证码等公开接口不附加该头；
 * - 退出登录 / 401 失效时清除 token。
 *
 * 文件：citygo_auth_prefs.xml；键：jwt_token（明文 HTTP 局域网联调环境，与项目现状一致）。
 * AccountSession 是同一 token 的进程内唯一内存来源，本对象是它的 SP 持久化镜像，
 * 两者在 hydrate / update / clear 时同步，保证拦截器取到的值与登录态一致。
 * ===================================================================================== */
object AuthTokenStore {

    private const val PREFS_NAME = "citygo_auth_prefs"
    private const val KEY_JWT_TOKEN = "jwt_token"

    private lateinit var prefs: android.content.SharedPreferences

    /** Application.onCreate（与 ServerConfig 同期）调用一次 */
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 持久化登录成功返回的 JWT */
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_JWT_TOKEN, token.ifBlank { null }).apply()
    }

    /** 读取已保存 JWT；未登录 / 已退出时为 null（拦截器据此决定是否带 Bearer） */
    fun getToken(): String? =
        prefs.getString(KEY_JWT_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** 退出登录 / token 失效：清除本地 JWT */
    fun clear() {
        prefs.edit().remove(KEY_JWT_TOKEN).apply()
    }
}
