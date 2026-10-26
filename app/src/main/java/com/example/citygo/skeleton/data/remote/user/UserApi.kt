package com.example.citygo.skeleton.data.remote.user

import com.example.citygo.skeleton.data.remote.AppRetrofit
import com.example.citygo.skeleton.data.remote.auth.ApiResponse
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

/* =====================================================================================
 * 用户资料模块网络层（本轮全新增，不改动账号模块既有 AuthApi / ProfileApi）
 *
 * 接口约定（BASE_URL 沿用 [AuthApiConfig.BASE_URL]，Bearer 头 / 401 处置由
 * [AuthHttpClient] 拦截器统一完成，本层不重复处理 token）：
 * - GET  user/info         获取当前登录用户完整资料；
 * - PUT  user/updateInfo   更新基础文本资料（昵称/简介/性别/生日/地区/职业/学校）；
 * - POST user/uploadAvatar 头像单独上传（multipart/form-data），data 返回新头像 URL。
 *
 * 统一响应体复用账号模块 ApiResponse<T>{code,msg,data}。
 * ===================================================================================== */

/**
 * 后端 /user/info 返回的用户资料。
 * 所有字段可空 / 可能为空串：UI 层统一做友好占位，禁止直接展示 "null"。
 */
data class UserInfoDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("bgImageUrl") val bgImageUrl: String? = null,
    @SerializedName("desc") val desc: String? = null,
    /** 性别：1=男，2=女；null/0 表示未设置 */
    @SerializedName("gender") val gender: Int? = null,
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("job") val job: String? = null,
    @SerializedName("school") val school: String? = null,
)

/**
 * PUT /user/updateInfo 请求体（一次性提交文本资料；头像不在此处，走独立上传接口）。
 * 传 null 的字段后端按「不修改」处理；编辑页未填项统一提交 null。
 */
data class UpdateUserInfoRequest(
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("desc") val desc: String? = null,
    @SerializedName("gender") val gender: Int? = null,
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("job") val job: String? = null,
    @SerializedName("school") val school: String? = null,
)

/** 用户资料 Retrofit 接口 */
interface UserApi {

    /** 获取当前登录用户资料 */
    @GET(USER_INFO_PATH)
    suspend fun getUserInfo(): ApiResponse<UserInfoDto>

    /** 更新基础文本资料 */
    @PUT(UPDATE_INFO_PATH)
    suspend fun updateInfo(@Body body: UpdateUserInfoRequest): ApiResponse<JsonElement>

    /**
     * 上传头像（multipart 表单字段名 file；如后端字段不同只需改此常量）。
     * data 可能直接是 URL 字符串，也可能是 {"avatarUrl":"..."} 对象，由仓库层兼容解析。
     */
    @Multipart
    @POST(UPLOAD_AVATAR_PATH)
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): ApiResponse<JsonElement>

    companion object {
        // 路径严格对齐后端当前契约（BASE_URL 沿用 AuthApiConfig.BASE_URL）
        const val USER_INFO_PATH = "info"
        const val UPDATE_INFO_PATH = "updateInfo"
        const val UPLOAD_AVATAR_PATH = "uploadAvatar"

        /** multipart 表单文件字段名（与后端约定一致） */
        const val AVATAR_PART_NAME = "file"
    }
}

/**
 * Retrofit 实例入口：统一走 [AppRetrofit]，baseUrl 取全局动态地址 [com.example.citygo.skeleton.data.remote.ServerConfig]，
 * 复用账号模块 OkHttp 客户端（Bearer/401/超时/日志一致）。
 * 用 getter 而非 lazy：输入框切换地址后下次取用自动获得指向新地址的实例。
 */
object UserRetrofitProvider {

    val userApi: UserApi
        get() = AppRetrofit.createAuthed()
}
