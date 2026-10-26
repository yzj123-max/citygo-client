package com.example.citygo.skeleton.data.remote.auth

import com.example.citygo.skeleton.data.remote.AppRetrofit
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/* =====================================================================================
 * 用户资料接口（Retrofit 预留，后端开发中）
 *
 * 「完善资料」页提交时调用 POST /updateUserInfo：
 * - 请求体 JSON：{nickname, avatarUrl, gender, birthday}；
 * - 鉴权头 Authorization: Bearer <token> 由 AuthHttpClient 的拦截器自动从
 *   AccountSession(StateFlow) 读取附加，这里不手动传 token；
 * - 后端就绪前该请求会失败，页面保留并已写好完整调用 / 错误处理，后端上线即可联调。
 * ===================================================================================== */

/**
 * 更新用户资料请求体（字段全部允许后端做部分更新，可空字段为 null 时 Gson 默认仍会序列化；
 * 如后端要求忽略 null，可后续给 Retrofit 配置 serializeNulls=false，页面无需改动）。
 */
data class UpdateUserInfoRequest(
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    /** 性别：1=男，2=女；未选为 null */
    @SerializedName("gender") val gender: Int?,
    /** 生日 yyyy-MM-dd；未选为 null */
    @SerializedName("birthday") val birthday: String?,
)

/** 用户资料 Retrofit 接口 */
interface ProfileApi {

    /** 完善 / 更新资料；返回统一 {code,msg,data} */
    @POST(AuthApiConfig.PATH_UPDATE_USER_INFO)
    suspend fun updateUserInfo(@Body body: UpdateUserInfoRequest): ApiResponse<Any>
}

/**
 * Retrofit 实例入口：统一走 [AppRetrofit]，baseUrl 取全局动态地址
 * [com.example.citygo.skeleton.data.remote.ServerConfig]，复用账号模块 OkHttp 客户端。
 * 用 getter 而非 lazy：输入框切换地址后下次取用自动获得指向新地址的实例。
 */
object ProfileRetrofitProvider {

    val profileApi: ProfileApi
        get() = AppRetrofit.createAuthed()
}
