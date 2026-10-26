package com.example.citygo.skeleton.data.remote

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/* =====================================================================================
 * 高德地理编码封装（搜索 SDK：GeocodeSearch）
 *
 * 把「地点名称 + 城市」解析为经纬度（GCJ-02 火星坐标），用于把用户补充的地点
 * 渲染到高德地图 Marker。真实 REST 化时，整体替换为「高德地理编码 Web 服务」即可。
 *
 * 注意：高德搜索 SDK 要求设备网络出口在境内；境外网络（如部分海外模拟器）
 * 会返回错误码 20011（INSUFFICIENT_ABROAD_PRIVILEGES）。此时走 [fallbackCoordinate]
 * 确定性模拟坐标兜底，保证 UI 流程在开发环境可用；真机国内网络下走真实地理编码。
 * ===================================================================================== */

/** 地理编码结果（GCJ-02 经纬度，标记是否为模拟兜底坐标） */
data class GeocodeResultPoint(
    val lat: Double,
    val lng: Double,
    val mocked: Boolean,
)

/**
 * 严格地理编码结果（AI 攻略地点导入专用，不允许模拟坐标兜底）：
 * - [Success]：高德真实命中坐标；
 * - [Failure]：查无此地 / 高德错误码 / SDK 异常，[reason] 为具体原因（直接打日志）；
 * - [Timeout]：单次请求超过 [AmapGeocoder.GEOCODE_TIMEOUT_MS]。
 */
sealed interface StrictGeocodeResult {
    data class Success(val point: GeocodeResultPoint) : StrictGeocodeResult
    data class Failure(val reason: String) : StrictGeocodeResult
    data object Timeout : StrictGeocodeResult
}

class AmapGeocoder(private val context: Context) {

    /** 热门城市中心锚点，模拟兜底时使用 */
    private val cityAnchors: Map<String, Pair<Double, Double>> = mapOf(
        "成都" to (30.6600 to 104.0700),
        "重庆" to (29.5630 to 106.5516),
        "北京" to (39.9042 to 116.4074),
        "上海" to (31.2304 to 121.4737),
        "广州" to (23.1291 to 113.2644),
        "深圳" to (22.5431 to 114.0579),
        "杭州" to (30.2741 to 120.1551),
        "南京" to (32.0603 to 118.7969),
        "西安" to (34.3416 to 108.9398),
        "厦门" to (24.4798 to 118.0894),
        "昆明" to (25.0389 to 102.7183),
        "大理" to (25.6065 to 100.2676),
        "三亚" to (18.2528 to 109.5119),
        "长沙" to (28.2282 to 112.9388),
        "武汉" to (30.5928 to 114.3055),
    )

    /**
     * 地理编码：地点名称 → 经纬度。
     *
     * @param name 地点名称（如「宽窄巷子」）
     * @param city 所在城市（如「成都」），提高命中率
     * @return 解析结果；高德 SDK 不可用 / 超时 / 境外网络时返回模拟兜底坐标（mocked=true），不会返回 null
     */
    suspend fun geocode(name: String, city: String): GeocodeResultPoint {
        val real = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) { queryFromAmap(name, city) }
        return real ?: fallbackCoordinate(name, city)
    }

    /**
     * 严格地理编码（AI 攻略地点导入专用）：
     * 与 [geocode] 不同，【不做模拟坐标兜底】——查不到 / SDK 报错 / 超时都如实返回失败，
     * 由调用方执行「单点失败跳过」降级，避免把假坐标写入行程。
     *
     * @param name 地点名称
     * @param city 必传！用户选择的目的地城市（如「长沙」），高德 GeocodeQuery 用它限定城市，
     *             防止跨省同名地点匹配错误
     */
    suspend fun geocodeStrict(name: String, city: String): StrictGeocodeResult {
        val result = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) { queryStrictFromAmap(name, city) }
        // withTimeoutOrNull 返回 null 只有两种情况：超时 / 内部返回 null（不会发生）
        return result ?: StrictGeocodeResult.Timeout
    }

    /**
     * 调高德搜索 SDK 异步地理编码（严格版）：成功拿到坐标才成功；
     * 无结果 / 错误码 / 异常全部携带【具体原因】返回，供调用方打日志与单点降级。
     */
    private suspend fun queryStrictFromAmap(name: String, city: String): StrictGeocodeResult =
        suspendCancellableCoroutine { cont ->
            try {
                val search = GeocodeSearch(context.applicationContext)
                search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                    override fun onGeocodeSearched(result: GeocodeResult?, code: Int) {
                        if (!cont.isActive) return
                        val point: LatLonPoint? =
                            result?.geocodeAddressList?.firstOrNull()?.latLonPoint
                        cont.resume(
                            when {
                                // 高德返回码 1000 = 成功；有坐标才算该地点匹配成功
                                code == AMAP_SUCCESS_CODE && point != null ->
                                    StrictGeocodeResult.Success(
                                        GeocodeResultPoint(point.latitude, point.longitude, false),
                                    )
                                code == AMAP_SUCCESS_CODE ->
                                    StrictGeocodeResult.Failure("高德在「$city」未检索到地点「$name」（无结果）")
                                else ->
                                    StrictGeocodeResult.Failure("高德地理编码错误 code=$code，地点=「$name」城市=「$city」")
                            },
                        )
                    }

                    override fun onRegeocodeSearched(result: RegeocodeResult?, code: Int) = Unit
                })
                // city 必传：限定检索城市，避免同名地点跨省误匹配
                search.getFromLocationNameAsyn(GeocodeQuery(name, city))
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(
                        StrictGeocodeResult.Failure(
                            "高德地理编码 SDK 异常：${e.javaClass.simpleName} ${e.message.orEmpty()}".trim(),
                        ),
                    )
                }
            }
        }

    /** 调高德搜索 SDK 异步地理编码；任何异常（含 20011 境外权限）都返回 null 走兜底 */
    private suspend fun queryFromAmap(name: String, city: String): GeocodeResultPoint? =
        suspendCancellableCoroutine { cont ->
            try {
                val search = GeocodeSearch(context.applicationContext)
                search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                    override fun onGeocodeSearched(result: GeocodeResult?, code: Int) {
                        val point: LatLonPoint? =
                            result?.geocodeAddressList?.firstOrNull()?.latLonPoint
                        if (point != null && cont.isActive) {
                            cont.resume(GeocodeResultPoint(point.latitude, point.longitude, false))
                        } else if (cont.isActive) {
                            cont.resume(null)
                        }
                    }

                    override fun onRegeocodeSearched(result: RegeocodeResult?, code: Int) = Unit
                })
                search.getFromLocationNameAsyn(GeocodeQuery(name, city))
            } catch (e: Exception) {
                // SDK 初始化失败等：兜底
                if (cont.isActive) cont.resume(null)
            }
        }

    /**
     * 模拟兜底坐标：以城市中心为锚点，按地点名哈希做确定性偏移（约 ±3km 范围）。
     * 同一天的用户补充地点在地图上分散可见；同一名称每次坐标一致。
     */
    private fun fallbackCoordinate(name: String, city: String): GeocodeResultPoint {
        val (baseLat, baseLng) = cityAnchors[city] ?: run {
            val hash = abs(city.hashCode())
            val lat = 22.0 + (hash % 1800) / 100.0
            val lng = 102.0 + ((hash / 1000) % 1900) / 100.0
            lat to lng
        }
        val hash = abs((name + city).hashCode())
        val angle = (hash % 360) * Math.PI / 180.0
        val radius = 0.010 + (hash % 15) / 1000.0 // 约 1.0~2.4km
        val lat = round4(baseLat + sin(angle) * radius * 0.8)
        val lng = round4(baseLng + cos(angle) * radius)
        return GeocodeResultPoint(lat, lng, true)
    }

    private fun round4(value: Double): Double = round(value * 10000) / 10000.0

    companion object {
        /** 地理编码单次超时（毫秒），超时后走模拟兜底 */
        private const val GEOCODE_TIMEOUT_MS = 6000L

        /** 高德服务返回码 1000 = 请求成功（结果集可能仍为空，需另行判断） */
        private const val AMAP_SUCCESS_CODE = 1000

        /** 严格地理编码单次超时（毫秒）：攻略导入串行调用，单点最多等 6s */
        const val STRICT_GEOCODE_TIMEOUT_MS = GEOCODE_TIMEOUT_MS
    }
}
