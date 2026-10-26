package com.example.citygo.skeleton.screens.amap

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.model.LatLng

/**
 * 高德定位封装：连续定位，对外暴露可观察的最新位置。
 * 路线规划 / 导航的起点、定位按钮移动相机都使用这里的坐标（GCJ-02 高德坐标）。
 *
 * 调用方需先获得 ACCESS_FINE_LOCATION 运行时权限，再调用 [start]。
 */
class AmapLocator(
    context: Context,
    private val allowMockLocations: Boolean = true,
    private val useCachedLocation: Boolean = true,
) {
    private val contextForPermissions = context.applicationContext

    /** 最近一次成功定位；null 表示尚未定位成功 */
    var currentLocation by mutableStateOf<LatLng?>(null)
        private set

    /** 是否正在等待首个定位结果（定位按钮 loading 态） */
    var locating by mutableStateOf(false)
        private set

    var accuracyMeters by mutableStateOf<Float?>(null)
        private set

    /** 最近一次定位失败 / 超时提示；成功定位后清空 */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** 首帧定位超时时间：超时后停止 loading，允许用户重试，避免弱网 / 模拟器永久等待 */
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        if (currentLocation == null) {
            locating = false
            errorMessage = "暂未获取当前位置，请确认已允许精确位置并到室外开阔处重新定位"
            Log.w("CityGoLocation", "first location timeout, precisePermissions=${hasPreciseLocationPermission(contextForPermissions)}")
        }
    }

    private val client: AMapLocationClient? = runCatching {
        AMapLocationClient(context.applicationContext)
    }.getOrNull()

    private val locationListener = AMapLocationListener { location: AMapLocation? ->
        if (location == null) return@AMapLocationListener
        if (location.errorCode == 0) {
            if (currentLocation == null) {
                Log.i("CityGoLocation", "first fix: type=${location.locationType}, accuracy=${location.accuracy}")
            }
            currentLocation = LatLng(location.latitude, location.longitude)
            accuracyMeters = location.accuracy
            locating = false
            errorMessage = null
            timeoutHandler.removeCallbacks(timeoutRunnable)
        } else {
            Log.w("CityGoLocation", "location error code=${location.errorCode}")
            // 连续定位偶发失败不清状态（下个间隔还会再试）；仅在尚未拿到位置时提示
            if (currentLocation == null) {
                locating = false
                errorMessage = "定位失败（${location.errorCode}），请检查定位设置后重试"
                timeoutHandler.removeCallbacks(timeoutRunnable)
            }
        }
    }

    init {
        client?.let { c ->
            val option = AMapLocationClientOption().apply {
                // 高精度模式（高德枚举拼写即为 Hight_Accuracy）
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                // 连续定位、5 秒间隔，不要逆地理地址（路线规划只需坐标，省电省流）
                isOnceLocation = false
                setInterval(5000)
                isNeedAddress = false
                isMockEnable = allowMockLocations
                isLocationCacheEnable = useCachedLocation
            }
            c.setLocationOption(option)
            c.setLocationListener(locationListener)
        }
    }

    /** 开始定位（权限已授予时调用，可重复调用，内部已启动则不重复 start） */
    fun start() {
        val c = client
        if (c == null) {
            // native 库缺失等初始化失败场景（如 x86 模拟器），直接暴露失败态避免永久 loading
            locating = false
            errorMessage = "定位服务初始化失败，请在 ARM 架构真机上使用导航功能"
            return
        }
        if (currentLocation == null) {
            locating = true
            errorMessage = null
            // 重新开始一轮 8 秒超时计时
            timeoutHandler.removeCallbacks(timeoutRunnable)
            timeoutHandler.postDelayed(timeoutRunnable, LOCATE_TIMEOUT_MS)
        }
        Log.i("CityGoLocation", "start location: precisePermissions=${hasPreciseLocationPermission(contextForPermissions)}, alreadyStarted=${c.isStarted}")
        if (!c.isStarted) c.startLocation()
    }

    fun stop() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        runCatching { client?.stopLocation() }
    }

    /** 推荐模块显式请求新位置，不复用上一次页面位置。 */
    fun restartFresh() {
        stop()
        currentLocation = null
        accuracyMeters = null
        errorMessage = null
        start()
    }

    fun destroy() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        runCatching {
            client?.unRegisterLocationListener(locationListener)
            client?.onDestroy()
        }
    }

    private companion object {
        /** 首帧定位超时时长 */
        const val LOCATE_TIMEOUT_MS = 8_000L
    }
}
