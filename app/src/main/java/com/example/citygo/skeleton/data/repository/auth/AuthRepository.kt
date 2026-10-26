package com.example.citygo.skeleton.data.repository.auth

import android.util.Log
import com.example.citygo.skeleton.data.remote.auth.ApiResponse
import com.example.citygo.skeleton.data.remote.auth.AuthApi
import com.example.citygo.skeleton.data.remote.auth.ServerApiException
import com.example.citygo.skeleton.data.remote.auth.UserInfo
import com.google.gson.JsonElement
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 登录结果业务载体（兼容后端两种返回形态）：
 *
 * @property token JWT
 * @property isNewUser 是否新注册用户：true 需跳「完善资料」页；false 老用户直接进首页。
 *           现阶段后端 data 只返回 token 字符串，该字段缺省按 false（老用户）处理；
 *           后端上线 {"token","isNewUser"} 后自动生效，无需改页面。
 */
data class LoginPayload(
    val token: String,
    val isNewUser: Boolean = false,
)

/**
 * 仓库层统一结果：把请求的三种结局显式区分，UI 只面对业务语义，不直接碰 OkHttp 异常。
 * - [Success]：HTTP 成功且后端 code=200，data 为业务数据，message 为后端 msg；
 * - [BusinessError]：HTTP 成功但后端 code!=200（如注册 400、验证码错误、token 失效 401），
 *   或 HTTP 4xx/5xx 但后端返回了 {code,msg}（[ServerApiException]）——两种都直接展示后端 msg；
 * - [NetworkError]：请求未到达/未通过业务层（断网 / 超时 / 连不上 / 响应非法 HTML），
 *   [userMessage] 为可直接展示的中文原因；[detail] 仅用于日志排查。
 */
sealed class AuthResult<out T> {
    /**
     * 业务成功。
     * @param data 业务数据（token 字符串 / UserInfo）
     * @param message 后端返回的 msg（可直接吐司）
     */
    data class Success<T>(val data: T?, val message: String? = null) : AuthResult<T>()

    /** 后端业务失败，[code] 为后端 JSON 中的 code（400 / 401 等） */
    data class BusinessError(val code: Int, val message: String) : AuthResult<Nothing>()

    /**
     * 网络层异常。
     * @param detail 技术细节，只写日志，不直接展示；
     * @param userMessage 可直接吐司的中文原因；为空时 UI 用本地兜底文案。
     */
    data class NetworkError(
        val detail: String,
        val userMessage: String? = null,
    ) : AuthResult<Nothing>()
}

/**
 * 账号模块仓库：封装注册 / 密码登录 / 发送验证码 / 验证码登录 / 用户信息五个请求，
 * 统一捕获网络异常并管理 token 持久化。网络实现为原生 OkHttp（见 [AuthApi]）。
 */
class AuthRepository(
    private val api: AuthApi = AuthApi(),
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    /** 注册：成功返回 Success，业务失败返回后端 msg（为空时由 ViewModel 用资源兜底） */
    suspend fun register(phone: String, password: String): AuthResult<Unit> = safeCall {
        val resp = api.register(phone, password)
        if (resp.isSuccess) {
            AuthResult.Success(Unit, resp.msg)
        } else {
            AuthResult.BusinessError(resp.code ?: -1, resp.msg.orEmpty())
        }
    }

    /**
     * 密码登录：解析 token 与 isNewUser。
     * token 的全局写入由 ViewModel 统一完成（AccountSession + DataStore），仓库层不再碰存储。
     */
    suspend fun login(phone: String, password: String): AuthResult<LoginPayload> = safeCall {
        val resp = api.login(phone, password)
        parseLogin(resp)
    }

    /**
     * 兼容解析登录 data：
     * - JsonPrimitive（字符串）→ token=字符串，isNewUser=false（当前后端）；
     * - JsonObject → token / isNewUser 按字段读取，缺 isNewUser 时按 false；
     * - 其它形态 / token 空 → 业务失败。
     */
    private fun parseLogin(resp: ApiResponse<JsonElement>): AuthResult<LoginPayload> {
        val data = resp.data
        if (!resp.isSuccess || data == null || data.isJsonNull) {
            return AuthResult.BusinessError(resp.code ?: -1, resp.msg.orEmpty())
        }
        val token: String?
        var isNewUser = false
        when {
            data.isJsonPrimitive -> token = data.asJsonPrimitive.asString
            data.isJsonObject -> {
                val obj = data.asJsonObject
                token = obj.get("token")?.takeIf { !it.isJsonNull }?.asString
                isNewUser = obj.get("isNewUser")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
            }
            else -> token = null
        }
        return if (token.isNullOrBlank()) {
            Log.w(TAG, "login response missing token: data=$data")
            AuthResult.BusinessError(resp.code ?: -1, resp.msg.orEmpty())
        } else {
            Log.i(TAG, "login parsed: tokenLen=${token.length}, isNewUser=$isNewUser")
            AuthResult.Success(LoginPayload(token = token, isNewUser = isNewUser), resp.msg)
        }
    }

    /** 获取用户信息：业务 code=401 时上层清 token、强制回登录页 */
    suspend fun getUserInfo(): AuthResult<UserInfo> = safeCall {
        val resp = api.getUserInfo()
        when {
            resp.isSuccess && resp.data != null -> AuthResult.Success(resp.data, resp.msg)
            resp.code == ApiResponse.CODE_UNAUTHORIZED ->
                AuthResult.BusinessError(ApiResponse.CODE_UNAUTHORIZED, resp.msg.orEmpty())
            else -> AuthResult.BusinessError(resp.code ?: -1, resp.msg.orEmpty())
        }
    }

    /**
     * 退出登录：仅请求后端 POST /logout 让 JWT 失效。
     * 本地账号记录删除 / AccountSession 清理由上层 ViewModel 按产品顺序统一处理。
     */
    suspend fun logout(): AuthResult<Unit> = safeCall {
        val resp = api.logout()
        if (resp.isSuccess) {
            Log.i(TAG, "remote logout success")
            AuthResult.Success(Unit, resp.msg)
        } else {
            Log.w(TAG, "remote logout business error: code=${resp.code}")
            AuthResult.BusinessError(resp.code ?: -1, resp.msg.orEmpty())
        }
    }

    /**
     * 发送模拟短信验证码。
     * 只有 Success 时前端才启动 60s 倒计时；业务失败 / 网络异常都保持按钮可点。
     */
    suspend fun sendCode(phone: String): AuthResult<Unit> = safeCall {
        val resp = api.sendCode(phone)
        if (resp.isSuccess) {
            AuthResult.Success(Unit, resp.msg)
        } else {
            AuthResult.BusinessError(resp.code ?: -1, resp.msg.orEmpty())
        }
    }

    /** 验证码登录：成功后的解析与密码登录共用（不改动验证码发送 / 校验本身） */
    suspend fun codeLogin(phone: String, code: String): AuthResult<LoginPayload> = safeCall {
        val resp = api.codeLogin(phone, code)
        parseLogin(resp)
    }

    /**
     * 统一异常捕获（OkHttp 同步调用在 IO 线程抛出的均为 IOException 子类）：
     * - [ServerApiException]：HTTP 4xx/5xx 但后端给了 {code,msg} → 按业务失败处理，
     *   UI 直接吐司后端 msg（如"手机号已注册"），不使用笼统的「网络异常」；
     * - UnknownHostException：DNS 解析失败 / 未连网；
     * - ConnectException：连不上（后端未启动、端口不对、防火墙拦截）；
     * - SocketTimeoutException：连接 / 读取超时；
     * - 其它 IOException：HTTP 错误、响应非 JSON、解析失败等（本类抛出的中文原因可直接展示）。
     */
    private suspend fun <T> safeCall(block: suspend () -> AuthResult<T>): AuthResult<T> {
        return try {
            block()
        } catch (e: ServerApiException) {
            // 必须在 IOException 之前：后端在非 2xx 状态码下返回的 msg 要原样展示给用户
            Log.e(TAG, "server error over http: code=${e.code}, msg=${e.serverMessage}")
            AuthResult.BusinessError(e.code, e.serverMessage)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "network: unknown host", e)
            AuthResult.NetworkError(
                detail = "unknown host: ${e.message}",
                userMessage = "无法连接服务器：服务器地址无法解析，请检查网络",
            )
        } catch (e: ConnectException) {
            Log.e(TAG, "network: connect failed", e)
            AuthResult.NetworkError(
                detail = "connect failed: ${e.message}",
                userMessage = "无法连接服务器，请确认后端已启动、手机与电脑在同一局域网且防火墙已放行 8080",
            )
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "network: timeout", e)
            AuthResult.NetworkError(
                detail = "timeout: ${e.message}",
                userMessage = "请求超时，请检查网络后重试",
            )
        } catch (e: IOException) {
            Log.e(TAG, "network io/http/parse error", e)
            val raw = e.message
            // 本类主动抛出的中文原因（如"响应不是合法 JSON"）可直接展示；
            // OkHttp 的英文技术信息不直接上屏，交给 UI 用本地兜底文案。
            val friendly = raw?.takeIf { it.any { ch -> ch.code in 0x4E00..0x9FFF } }
            AuthResult.NetworkError(raw ?: "io error", friendly)
        } catch (e: Exception) {
            Log.e(TAG, "unexpected error", e)
            AuthResult.NetworkError(e.message ?: "unknown error")
        }
    }
}
