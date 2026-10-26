package com.example.citygo.skeleton.data.remote.auth

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.lang.reflect.Type

/**
 * 账号模块 API 层：用原生 OkHttp 发起请求 + Gson 解析统一返回体。
 *
 * 不使用 Retrofit，全部手工构建 [Request]：
 * - register / login：POST + 请求头 Content-Type: application/json，
 *   参数走 **JSON 请求体**（如 {"phone":"...","password":"..."}），不是表单、不是 Query；
 * - **codeLogin（/verifyCodeLogin）：POST + application/x-www-form-urlencoded 表单提交**
 *   （等价 Retrofit 的 @FormUrlEncoded + @Field），phone/code 为表单字段——
 *   后端用 @RequestParam 接收，只认 Query 或表单，发 JSON Body 会收不到手机号；
 * - sendCode：POST，phone 走 Query（URL 拼接），请求体为空；
 * - getUserInfo：GET，Authorization 头由 [AuthHttpClient] 拦截器统一附加。
 *
 * 所有方法都是 suspend，内部切到 IO 线程执行阻塞的 [OkHttpClient.newCall]；
 * 网络层 / HTTP / 解析异常统一抛 [IOException]，由上层 Repository 捕获归类。
 */
class AuthApi(
    private val client: OkHttpClient = AuthHttpClient.client,
    private val gson: Gson = Gson(),
) {
    companion object {
        private const val TAG = "AuthApi"
    }

    // 各返回体的泛型 Type（Gson 反序列化统一返回体时使用）
    private val typeAny: Type = object : TypeToken<ApiResponse<Any>>() {}.type
    private val typeUserInfo: Type = object : TypeToken<ApiResponse<UserInfo>>() {}.type

    /**
     * 登录返回的 data 是动态 JSON：
     * - 现阶段后端：data 直接是 JWT token 字符串；
     * - 新契约（后端开发中）：data 为对象 {"token":"...","isNewUser":true}。
     * 因此用 [com.google.gson.JsonElement] 承接，由 Repository 兼容两种形态。
     */
    private val typeLoginData: Type =
        object : TypeToken<ApiResponse<com.google.gson.JsonElement>>() {}.type

    /**
     * 1. 注册：POST /userRegister
     * 请求头 Content-Type: application/json；Body 为 JSON 对象 {"phone":"...","password":"..."}。
     * 成功返回 {code:200,msg:"操作成功",data:"注册成功"}。
     */
    suspend fun register(phone: String, password: String): ApiResponse<Any> {
        val request = buildJsonPostRequest(
            path = AuthApiConfig.PATH_REGISTER,
            payload = mapOf(
                AuthApiConfig.FIELD_PHONE to phone,
                AuthApiConfig.FIELD_PASSWORD to password,
            ),
        )
        return execute(request, typeAny)
    }

    /**
     * 2. 密码登录：POST /userLogin
     * 请求头 Content-Type: application/json；Body 为 JSON {"phone":"...","password":"..."}；
     * 成功返回 {code:200,msg,data:{"token":"xxx"}}（也兼容 data 直接为 token 字符串的旧形态）。
     * 公开接口：拦截器不附加 Authorization 头；成功后由上层把 JWT 存入 SharedPreferences。
     */
    suspend fun login(phone: String, password: String): ApiResponse<com.google.gson.JsonElement> {
        val request = buildJsonPostRequest(
            path = AuthApiConfig.PATH_LOGIN,
            payload = mapOf(
                AuthApiConfig.FIELD_PHONE to phone,
                AuthApiConfig.FIELD_PASSWORD to password,
            ),
        )
        return execute(request, typeLoginData)
    }

    /** 3. 发送模拟验证码：POST /sendCode?phone=（phone 走 Query，无 Body） */
    suspend fun sendCode(phone: String): ApiResponse<Any> {
        val request = buildQueryPostRequest(
            path = AuthApiConfig.PATH_SEND_CODE,
            params = listOf(AuthApiConfig.FIELD_PHONE to phone),
        )
        return execute(request, typeAny)
    }

    /**
     * 4. 验证码登录：POST /verifyCodeLogin
     *
     * **必须是表单提交**：Content-Type: application/x-www-form-urlencoded，
     * 请求体形如 phone=13800000000&code=1234（等价 Retrofit 写法
     * `@FormUrlEncoded @POST("verifyCodeLogin") fun codeLogin(@Field("phone") ..., @Field("code") ...)`）。
     * 因为后端该接口参数是 Spring @RequestParam，只从 Query / 表单解析；
     * 若发 JSON Body（application/json），后端绑定不到 phone，会直接请求失败。
     *
     * 成功 data 为 token 字符串或 {token,isNewUser} 对象（不修改验证码发送 / 校验逻辑）。
     */
    suspend fun codeLogin(phone: String, code: String): ApiResponse<com.google.gson.JsonElement> {
        val request = buildFormPostRequest(
            path = AuthApiConfig.PATH_CODE_LOGIN,
            fields = listOf(
                AuthApiConfig.FIELD_PHONE to phone,
                AuthApiConfig.FIELD_CODE to code,
            ),
        )
        return execute(request, typeLoginData)
    }

    /** 5. 获取用户信息：GET /getUserInfo，Authorization 头由拦截器自动携带 */
    suspend fun getUserInfo(): ApiResponse<UserInfo> {
        val url = baseUrlBuilder()
            .addPathSegment(AuthApiConfig.PATH_GET_USER_INFO)
            .build()
        val request = Request.Builder().url(url).get().build()
        return execute(request, typeUserInfo)
    }

    /**
     * 6. 退出登录：POST /logout
     * 无 Query、无请求 Body；Authorization: Bearer <token> 头由拦截器自动附加，
     * 后端据此让当前 JWT 失效。返回 {code, msg}（无业务 data）。
     */
    suspend fun logout(): ApiResponse<Any> {
        val url = baseUrlBuilder()
            .addPathSegment(AuthApiConfig.PATH_LOGOUT)
            .build()
        // OkHttp 的 post 不接受 null body，这里给零字节空 body
        val emptyBody = ByteArray(0).toRequestBody(contentType = null)
        val request = Request.Builder()
            .url(url)
            .post(emptyBody)
            // 退出接口的 401（旧 token 已失效）不触发全局「踢登录」，拦截器会在发出前移除该头
            .header(AuthApiConfig.HEADER_SKIP_AUTH_EVENT, "1")
            .build()
        return execute(request, typeAny)
    }

    // ================================ 内部工具 ================================

    /** 以 BASE_URL 为基础创建 HttpUrl.Builder（BASE_URL 以 / 结尾，可继续 addPathSegment） */
    private fun baseUrlBuilder(): okhttp3.HttpUrl.Builder =
        AuthApiConfig.BASE_URL.toHttpUrl().newBuilder()

    /**
     * 构建「参数走 Query 的 POST」请求：参数拼在 URL 上，请求体为空。
     * OkHttp 不允许 post(null)，这里使用零字节空 body。
     * 用 addPathSegments（复数）：路径若含 "/" 不会被编码成 %2F，单层路径行为不变。
     */
    private fun buildQueryPostRequest(
        path: String,
        params: List<Pair<String, String>>,
    ): Request {
        val urlBuilder = baseUrlBuilder().addPathSegments(path)
        params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        val emptyBody = ByteArray(0).toRequestBody(contentType = null)
        return Request.Builder().url(urlBuilder.build()).post(emptyBody).build()
    }

    /**
     * 构建「表单提交的 POST」（/verifyCodeLogin 使用，等价 Retrofit @FormUrlEncoded + @Field）：
     * - 使用 OkHttp [FormBody]，自动设置请求头
     *   Content-Type: application/x-www-form-urlencoded 并对字段做 URL 编码；
     * - 请求体形如 phone=138xxxx&code=1234，Spring 的 @RequestParam 可直接绑定；
     * - 不能用 JSON Body：@RequestParam 不会解析 application/json，会拿不到参数。
     */
    private fun buildFormPostRequest(
        path: String,
        fields: List<Pair<String, String>>,
    ): Request {
        val builder = FormBody.Builder()
        fields.forEach { (key, value) -> builder.add(key, value) }
        // addPathSegments（复数）：路径若含 "/" 不会被编码成 %2F，单层路径行为不变
        val url = baseUrlBuilder().addPathSegments(path).build()
        return Request.Builder().url(url).post(builder.build()).build()
    }

    /**
     * 构建「JSON Body 的 POST」请求（register / login 使用）：
     * - URL 不含业务参数；
     * - [payload] 经 Gson 序列化为 JSON 对象作为请求体；
     * - toRequestBody 携带 application/json MediaType，OkHttp 会自动写出
     *   "Content-Type: application/json; charset=utf-8" 请求头（不使用表单 / form-urlencoded）。
     */
    private fun buildJsonPostRequest(
        path: String,
        payload: Map<String, String>,
    ): Request {
        // addPathSegments（复数）拼接相对路径（userRegister/userLogin 均为单层、无前导 "/"）
        val url = baseUrlBuilder().addPathSegments(path).build()
        val json = gson.toJson(payload)
        val body = json.toRequestBody(AuthApiConfig.CONTENT_TYPE_JSON.toMediaType())
        return Request.Builder().url(url).post(body).build()
    }

    /**
     * 统一执行 + 解析，并做响应守卫：
     * - 网络不可达 / 超时：OkHttp 直接抛 IOException；
     * - HTTP 非 2xx：
     *   · 401/403 且返回体是 JSON（后端 {code:401,msg}）：照常解析，交业务层按 code=401 处理；
     *   · 其它状态码且返回体是 JSON：尽力解析统一体 {code,msg}，只要 msg 非空就抛
     *     [ServerApiException]（携带后端 code + msg），上层吐司**直接展示后端 msg**，
     *     不再笼统提示「网络异常 / HTTP 400」；
     *   · 其余（HTML 网关错误页等）：抛 IOException("HTTP code")；
     * - 响应体为空 / 不是 JSON（常见于网关 HTML 错误页）：抛 IOException，避免 Gson 误解析。
     */
    private suspend fun <T> execute(request: Request, dataType: Type): ApiResponse<T> =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string()
                val trimmed = text?.trimStart().orEmpty()
                val looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("[")
                val isAuthHttpError = response.code == 401 || response.code == 403

                if (!response.isSuccessful && !(isAuthHttpError && looksLikeJson)) {
                    Log.e(TAG, "http error: code=${response.code}, body=${text.orEmpty().take(200)}")
                    // 后端常在 4xx/5xx 时仍返回 {code,msg}：把 msg 透传给 UI 展示
                    if (looksLikeJson) {
                        val errorBody = runCatching {
                            gson.fromJson<ApiResponse<com.google.gson.JsonElement>>(text, typeAny)
                        }.getOrNull()
                        val serverMsg = errorBody?.msg?.takeIf { it.isNotBlank() }
                        if (serverMsg != null) {
                            throw ServerApiException(
                                code = errorBody.code ?: response.code,
                                serverMessage = serverMsg,
                            )
                        }
                    }
                    throw IOException("HTTP ${response.code}")
                }
                if (text.isNullOrBlank() || !looksLikeJson) {
                    Log.e(TAG, "non-json or empty response: code=${response.code}, body=${text.orEmpty().take(200)}")
                    throw IOException("响应不是合法 JSON")
                }

                val parsed: ApiResponse<T>? = runCatching {
                    gson.fromJson<ApiResponse<T>>(text, dataType)
                }.onFailure {
                    Log.e(TAG, "json parse failed: ${it.message}")
                }.getOrNull()

                parsed ?: throw IOException("响应解析失败")
            }
        }
}

/**
 * HTTP 非 2xx 但后端返回了统一体 {code,msg} 时抛出：
 * 携带后端业务 code 与可直接展示给用户的 msg（如"手机号已注册""密码错误"）。
 * 继承 [IOException] 以便在网络统一兜底处被最先识别（顺序必须在 IOException 之前 catch）。
 */
class ServerApiException(
    val code: Int,
    val serverMessage: String,
) : IOException(serverMessage)
