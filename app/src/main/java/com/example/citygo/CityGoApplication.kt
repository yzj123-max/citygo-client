package com.example.citygo

import android.app.Application
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.navi.NaviSetting
import com.amap.api.services.core.ServiceSettings
import com.example.citygo.skeleton.data.AppServiceLocator
import com.example.citygo.skeleton.data.RepositoryContainer
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.remote.ServerConfig
import com.example.citygo.skeleton.data.remote.auth.AuthHttpClient
import com.example.citygo.skeleton.data.remote.auth.AuthTokenStore
import kotlinx.coroutines.runBlocking

/**
 * App 入口 Application。
 *
 * 高德隐私合规要求：在用户同意隐私政策后、任何 SDK 功能初始化前，
 * 必须显式调用各 SDK 的 updatePrivacyShow / updatePrivacyAgree，
 * 否则地图、定位、搜索、导航均不会工作。
 * 本 App 隐私政策在「我的-隐私设置」中展示，启动即视为同意（演示项目），
 * 因此在 onCreate 中统一对四个 SDK 完成合规设置。
 */
class CityGoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 注入 Application Context（景点 POI 的 Room 本地缓存在此之后按需建库）
        RepositoryContainer.init(this)
        // 初始化全局业务数据 Repository（宠物 / 行程 / 收藏 / 浏览记录 / 日记 / 帖子）
        AppServiceLocator.init(this)
        // 先恢复全局后端服务地址（登录/注册等所有网络请求的唯一 baseUrl 来源，须早于网络初始化）
        ServerConfig.init(this)
        // 初始化 JWT 的 SharedPreferences 存储（登录成功写入，拦截器读取后自动带 Bearer）
        AuthTokenStore.init(this)
        // 初始化账号模块 OkHttp 客户端（拦截器从 AccountSession / SP 取 token）
        AuthHttpClient.init(this)
        // 从 DataStore 恢复当前活跃账号到全局 AccountSession（决定启动进首页还是登录页）；
        // 仅读取极小的偏好项，runBlocking 在 onCreate 中可接受
        runBlocking { AccountSession.hydrate(this@CityGoApplication) }
        initAmapPrivacy()
    }

    /** 高德四套 SDK 隐私合规初始化（任何地图/定位/导航功能调用之前必须完成） */
    private fun initAmapPrivacy() {
        // x86 模拟器等缺少高德 arm native 库的环境下，类加载可能抛 UnsatisfiedLinkError，
        // 整体兜底保证 App 启动不崩溃（地图 / 导航页另有 UI 降级面板）
        runCatching {
            // 3D 地图 SDK
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)
            // 搜索 SDK（路径规划 / POI / 天气以外的服务端检索）
            ServiceSettings.updatePrivacyShow(this, true, true)
            ServiceSettings.updatePrivacyAgree(this, true)
            // 定位 SDK
            AMapLocationClient.updatePrivacyShow(this, true, true)
            AMapLocationClient.updatePrivacyAgree(this, true)
            // 导航 SDK
            NaviSetting.updatePrivacyShow(this, true, true)
            NaviSetting.updatePrivacyAgree(this, true)
        }.onSuccess {
            Log.i("CityGoNavi", "AMap privacy init success: maps/search/location/navi all agreed")
        }.onFailure {
            Log.e("CityGoNavi", "AMap privacy init failed, map/navi will not work", it)
        }
    }
}
