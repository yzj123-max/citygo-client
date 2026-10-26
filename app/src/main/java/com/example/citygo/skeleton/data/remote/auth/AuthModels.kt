package com.example.citygo.skeleton.data.remote.auth

import com.google.gson.annotations.SerializedName

/**
 * 后端统一返回实体。
 *
 * 后端约定 JSON 结构：{ "code": Int, "msg": String, "data": 任意类型 }
 * - code=200 成功；code=400 业务失败；code=401 未登录 / token 失效；
 * - 各接口 data 类型：
 *   - 注册 / 发送验证码：无 data（忽略）；
 *   - 密码登录 / 验证码登录：data 为 JWT token 字符串（ApiResponse<String>）；
 *   - getUserInfo：data 为用户信息对象（ApiResponse<UserInfo>）。
 *
 * 字段加 @SerializedName 多别名兜底，避免后端个别接口把 msg 写成 message 导致解析为空。
 */
data class ApiResponse<T>(
    @SerializedName(value = "code", alternate = ["status"])
    val code: Int? = null,

    @SerializedName(value = "msg", alternate = ["message"])
    val msg: String? = null,

    /** 业务数据：登录/验证码登录时是 token 字符串，getUserInfo 时是用户信息对象 */
    val data: T? = null,
) {
    /** 业务是否成功：后端约定 code == 200 */
    val isSuccess: Boolean get() = code == CODE_SUCCESS

    companion object {
        const val CODE_SUCCESS = 200

        /** 业务失败（如手机号已注册 / 验证码错误） */
        const val CODE_BAD_REQUEST = 400

        /** token 失效 / 未登录，需要跳回登录页 */
        const val CODE_UNAUTHORIZED = 401
    }
}

/**
 * 用户信息（getUserInfo 的 data 字段）。
 * 后端返回 password:null，这里保留字段以匹配 JSON，UI 不展示密码。
 */
data class UserInfo(
    @SerializedName(value = "id", alternate = ["userId", "uid"])
    val id: Long? = null,

    @SerializedName(value = "phone", alternate = ["mobile"])
    val phone: String? = null,

    @SerializedName("password")
    val password: String? = null,
)
