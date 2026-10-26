package com.example.citygo.skeleton.screens.auth

/**
 * 账号模块路由常量（独立于 AppDestination 的主 App 五个 Tab，单独管理避免互相污染）。
 */
object AuthDestination {
    /** 登录页 */
    const val LOGIN = "auth/login"

    /** 注册页 */
    const val REGISTER = "auth/register"

    /** 个人信息页 */
    const val USER_INFO = "auth/user_info"

    /** 完善资料页（新注册用户首次登录填写昵称 / 头像 / 性别 / 生日） */
    const val COMPLETE_PROFILE = "auth/complete_profile"
}
