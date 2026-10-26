package com.example.citygo.skeleton.screens.amap

import android.content.Context
import android.util.Log
import com.amap.api.navi.AMapNavi

/**
 * AMapNavi 全局单例管理（修复导航页反复进出后引擎实例状态错乱、无限加载）。
 *
 * - [getOrCreate] 全 App 复用同一个 [AMapNavi] 引擎：选线页算路与导航页导航共享，
 *   不再各页面各自 getInstance；
 * - 导航页退出时统一 [destroyNavi] 释放 native 资源，置空缓存；
 *   下次进入再懒创建新实例，避免在已 destroy 的旧实例上等待初始化回调导致无限转圈；
 * - 所有调用均捕获异常，引擎不可用时返回 null，由 UI 走降级 / Toast 提示。
 */
object AmapNaviManager {

    private const val TAG = "CityGoNavi"

    private var appContext: Context? = null

    @Volatile
    private var instance: AMapNavi? = null

    /**
     * 获取全局唯一引擎实例；已 destroy 或首次使用时懒创建。
     * 调用方应在 Application/页面 onCreate 后调用（隐私合规接口已提前在 Application 完成）。
     */
    @Synchronized
    fun getOrCreate(context: Context): AMapNavi? {
        val ctx = appContext ?: context.applicationContext.also {
            appContext = it
            Log.i(TAG, "[navi-manager] appContext bound: ${it.packageName}")
        }
        instance?.let { return it }
        return runCatching { AMapNavi.getInstance(ctx) }
            .onFailure { Log.e(TAG, "[navi-manager] AMapNavi.getInstance failed", it) }
            .getOrNull()
            ?.also {
                instance = it
                Log.i(TAG, "[navi-manager] new AMapNavi engine created")
            }
    }

    /** 当前缓存实例（可能为 null，不触发创建）。 */
    fun current(): AMapNavi? = instance

    /** 导航页退出时释放引擎；下次 [getOrCreate] 会重新创建。 */
    @Synchronized
    fun destroyNavi() {
        Log.i(TAG, "[navi-manager] destroy AMapNavi engine, cache cleared")
        runCatching { AMapNavi.destroy() }
            .onFailure { Log.e(TAG, "[navi-manager] AMapNavi.destroy failed", it) }
        instance = null
    }
}
