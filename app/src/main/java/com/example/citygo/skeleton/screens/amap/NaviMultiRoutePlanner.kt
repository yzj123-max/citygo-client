package com.example.citygo.skeleton.screens.amap

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.amap.api.maps.model.LatLng
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.AMapLaneInfo
import com.amap.api.navi.model.AMapModelCross
import com.amap.api.navi.model.AMapNaviCameraInfo
import com.amap.api.navi.model.AMapNaviCross
import com.amap.api.navi.model.AMapNaviLocation
import com.amap.api.navi.model.AMapNaviPath
import com.amap.api.navi.model.AMapNaviRouteNotifyData
import com.amap.api.navi.model.AMapNaviStep
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo
import com.amap.api.navi.model.AMapServiceAreaInfo
import com.amap.api.navi.model.AimLessModeCongestionInfo
import com.amap.api.navi.model.AimLessModeStat
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviLatLng
import kotlin.math.roundToInt

/* =====================================================================================
 * 导航引擎独立多路线算路（不依赖 NaviView、不需要 GPS 定位）：
 * 起点可以是任意自定义景点坐标，用户不在起点附近也能跨两点算路。
 *
 * 已按 amapNaviCombo 10.1.201 字节码核对的真实 API：
 *  - AMapNavi.setMultipleRouteNaviMode(true) 开启多路线
 *  - AMapNavi.strategyConvert(拥堵避让, 不走高速, 避免收费, 高速优先, 多路线)
 *  - 算路成功 AMapCalcRouteResult.getRouteid() + AMapNavi.getNaviPaths()
 *  - AMapNaviPath: getAllLength / getAllTime / getTrafficLightCount /
 *    getCoordList(折线) / getSteps(转向段) / getMainRoadInfo(主要道路)
 *  - AMapNaviStep: getIconType(转向箭头类型) / getLength / getLinks
 *  - AMapNaviLink.getRoadName() 路名
 *
 * NaviPath 对象不可序列化、不能跨页面直接传，故算路成功后把需要的数据拍平成
 * [NaviRouteChoice] 存入内存 [NaviRouteChoiceStore]，步骤预览页按 routeId 读取。
 * ===================================================================================== */

private const val PLANNER_TAG = "CityGoNavi"

/** 单个转向步骤（由 AMapNaviStep + AMapNaviLink 拍平而来，可安全跨页面传递） */
data class NaviTurnStep(
    /** 步骤序号，从 1 开始 */
    val index: Int,
    /**
     * 高德转向箭头类型，直接喂给原生 NextTurnTipView.setIconType 即可渲染官方箭头；
     * 文案映射见 [naviActionText]
     */
    val iconType: Int,
    /** 该步骤道路名（可能为空，如景区内部道路） */
    val roadName: String,
    /** 该步骤长度（米） */
    val lengthMeters: Int,
    /** 该步骤预计耗时（秒） */
    val timeSeconds: Int,
) {
    /** 展示文案：如「右转进入五一大道，行驶200米」 */
    val instruction: String
        get() {
            val action = naviActionText(iconType)
            val roadPart = roadName.takeIf { it.isNotBlank() }?.let { "进入$it" }
            val lenPart = lengthMeters.takeIf { it > 0 }?.let { "，行驶${formatMeters(it)}" }
            return listOfNotNull(action, roadPart).joinToString("") + (lenPart ?: "")
        }
}

/** 一条候选路线（多路线结果中的一项），卡片 + 预览页共用 */
data class NaviRouteChoice(
    /** 导航引擎路线 id（selectRouteId 用） */
    val routeId: Int,
    /** 总距离（米） */
    val distanceMeters: Int,
    /** 总耗时（秒） */
    val durationSeconds: Int,
    /** 红绿灯数量 */
    val trafficLights: Int,
    /** 简要描述：主要道路 / 途经道路 */
    val summary: String,
    /** 完整折线（GCJ-02），地图绘制用 */
    val points: List<LatLng>,
    /** 转向步骤列表 */
    val steps: List<NaviTurnStep>,
)

/** 一次完整算路结果（含起终点等上下文），存入 [NaviRouteChoiceStore] 供步骤预览页使用 */
data class NaviPlan(
    val mode: RouteTravelMode,
    val origin: LatLng,
    val destination: LatLng,
    val destName: String,
    /** true=起点为设备当前位置；false=两景点间导航 */
    val originIsMyLocation: Boolean,
    val city: String,
    val choices: List<NaviRouteChoice>,
)

/** 步骤预览页与导航页共享的内存缓存（仅保存最近一次算路结果，进程回收后为空走占位页） */
object NaviRouteChoiceStore {
    @Volatile
    var latest: NaviPlan? = null
        private set

    fun save(plan: NaviPlan) {
        latest = plan
    }

    fun findChoice(routeId: Int): NaviRouteChoice? = latest?.choices?.firstOrNull { it.routeId == routeId }
}

/** 多路线算路状态机 */
sealed interface MultiRouteState {
    data object Idle : MultiRouteState
    data object Loading : MultiRouteState
    data class Success(val choices: List<NaviRouteChoice>) : MultiRouteState
    data class Failed(val message: String) : MultiRouteState
}

/** 高德 iconType → 中文动作（箭头图标本身由原生 NextTurnTipView 渲染，这里仅作文案） */
fun naviActionText(iconType: Int): String = when (iconType) {
    0 -> "直行"
    1 -> "右转"
    2 -> "左转"
    3 -> "向右前方行驶"
    4 -> "向左前方行驶"
    5 -> "向右后方转弯"
    6 -> "向左后方转弯"
    7 -> "掉头"
    8 -> "进入环岛"
    9 -> "驶出环岛"
    10 -> "靠左行驶"
    11 -> "靠右行驶"
    12 -> "到达途经点"
    13 -> "到达目的地"
    14 -> "进入轮渡"
    15 -> "驶出轮渡"
    else -> "沿道路行驶"
}

/** 米 → 展示文案（≥1km 显示公里） */
fun formatMeters(meters: Int): String =
    if (meters >= 1000) "%.1f公里".format(meters / 1000.0) else "${meters}米"

/** 秒 → 分钟文案 */
fun formatSeconds(seconds: Int): String = "${(seconds / 60.0).roundToInt()}分钟"

/** 高德步行算路距离上限（约 100 公里，超过报 3003） */
const val WALK_MAX_METERS = 100_000

/** 高德骑行算路距离上限（约 200 公里，超过报 30007） */
const val RIDE_MAX_METERS = 200_000

/** 算路起点与手机当前位置的「同一位置」判定阈值（米）：≤50m 视为在起点现场 */
const val SAME_LOCATION_THRESHOLD_METERS = 50.0

/** 两点球面距离（米，Haversine），用于步行/骑行超距前置判断 */
fun haversineMeters(a: LatLng, b: LatLng): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = Math.sin(dLat / 2).let { it * it } +
        Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2).let { it * it }
    return 2 * earthRadius * Math.asin(Math.min(1.0, Math.sqrt(h)))
}

/** 出行方式在该距离下是否被高德引擎支持 */
fun RouteTravelMode.isSupportedDistance(distanceMeters: Double): Boolean = when (this) {
    RouteTravelMode.WALK -> distanceMeters <= WALK_MAX_METERS
    RouteTravelMode.RIDE -> distanceMeters <= RIDE_MAX_METERS
    else -> true
}

/**
 * 算路起点是否等于手机当前位置（50 米容差）。
 * 距离 ≤ [thresholdMeters] 判定同一位置 → 实时导航；否则为自定义起点 → 模拟导航。
 */
fun isSameLocation(
    a: LatLng,
    b: LatLng,
    thresholdMeters: Double = SAME_LOCATION_THRESHOLD_METERS,
): Boolean = haversineMeters(a, b) <= thresholdMeters

/**
 * 算路错误码 → 面向用户的友好提示。
 * 3003=步行起终点距离过远；30007=骑行起终点距离过远（高德引擎限制）。
 */
fun friendlyCalcError(code: Int, mode: RouteTravelMode? = null): String = when (code) {
    3003 -> "步行距离过远（步行规划上限约100公里），请切换为驾车方案"
    30007 -> "骑行距离过远（骑行规划上限约200公里），请切换为驾车方案"
    else -> "路线计算失败（错误码 $code），请检查网络后重试"
}

/**
 * 导航引擎独立算路器：页面 onCreate 时机创建（Compose remember），页面销毁移除监听。
 * 不持有 NaviView，可在纯地图（MapView）页面使用。
 */
class NaviMultiRoutePlanner(context: Context) {

    var state by mutableStateOf<MultiRouteState>(MultiRouteState.Idle)
        private set

    /** 最近一次算路上下文（成功后同步写入 [NaviRouteChoiceStore]） */
    var planContext: NaviPlan? by mutableStateOf(null)
        private set

    private val appContext = context.applicationContext
    // 全局单例引擎：与导航页共享同一实例；导航页 destroy 后由 reattachIfNeeded 重连
    private var aMapNavi: AMapNavi? = AmapNaviManager.getOrCreate(appContext)

    private var lastFrom: NaviLatLng? = null
    private var lastTo: NaviLatLng? = null
    private var lastMode: RouteTravelMode? = null
    private var lastDestName: String = ""
    private var lastOriginIsMyLocation: Boolean = true
    private var lastCity: String = ""

    /** 防止 plan() 直调与 onInitNaviSuccess 补发对同一请求重复算路；每次 plan 复位 */
    private var calcInvoked: Boolean = false

    /** 本算路器自己的监听器，页面销毁时必须 remove，避免与导航页监听器串扰 */
    private val listener = object : AMapNaviListener {
        override fun onInitNaviFailure() {
            Log.e(PLANNER_TAG, "[route-plan] AMapNavi init failure")
            state = MultiRouteState.Failed("导航引擎初始化失败，请重启 App 后重试")
        }

        override fun onInitNaviSuccess() {
            Log.i(PLANNER_TAG, "[route-plan] AMapNavi init success, start pending calc")
            // remember 期间引擎可能已初始化过；若已有待算请求立即发起
            calcInternal()
        }

        override fun onCalculateRouteSuccess(result: AMapCalcRouteResult) {
            if (result.errorCode != 0) {
                Log.e(
                    PLANNER_TAG,
                    "[route-plan] calc success callback but errorCode=${result.errorCode} " +
                        "desc=${result.errorDescription}",
                )
                // 部分机型超距错误在 success 回调里以非零 errorCode 返回，统一走友好提示
                state = MultiRouteState.Failed(friendlyCalcError(result.errorCode, lastMode))
                return
            }
            // getRouteid() 为平台类型 int[]，显式收口为非空局部变量，避免平台类型推断失败
            val ids: IntArray = result.routeid ?: IntArray(0)
            val paths: Map<Int, AMapNaviPath> = aMapNavi?.naviPaths ?: emptyMap()
            Log.i(
                PLANNER_TAG,
                "[route-plan] calc success mode=$lastMode routeIds=${ids.joinToString()} " +
                    "naviPaths=${paths.keys.joinToString()}",
            )
            if (ids.isEmpty() || paths.isEmpty()) {
                state = MultiRouteState.Failed("未获取到可行路线，请更换起终点后重试")
                return
            }
            val choices = ids.toList().mapNotNull { id -> paths[id]?.toChoice(id) }
            if (choices.isEmpty()) {
                state = MultiRouteState.Failed("路线数据异常（折线为空），请稍后重试")
                return
            }
            val from = lastFrom
            val to = lastTo
            if (from == null || to == null) {
                state = MultiRouteState.Failed("起终点丢失，请重新选择路线")
                return
            }
            val plan = NaviPlan(
                mode = lastMode ?: RouteTravelMode.DRIVE,
                origin = LatLng(from.latitude, from.longitude),
                destination = LatLng(to.latitude, to.longitude),
                destName = lastDestName,
                originIsMyLocation = lastOriginIsMyLocation,
                city = lastCity,
                choices = choices,
            )
            planContext = plan
            NaviRouteChoiceStore.save(plan)
            state = MultiRouteState.Success(choices)
            Log.i(
                PLANNER_TAG,
                "[route-plan] parsed ${choices.size} choice(s): " +
                    choices.joinToString {
                        "#${it.routeId} ${it.distanceMeters}m/${it.durationSeconds}s/" +
                            "lights=${it.trafficLights}/steps=${it.steps.size}/pts=${it.points.size}"
                    },
            )
        }

        override fun onCalculateRouteFailure(result: AMapCalcRouteResult) {
            Log.e(
                PLANNER_TAG,
                "[route-plan] calc failure code=${result.errorCode} " +
                    "desc=${result.errorDescription} detail=${result.errorDetail}",
            )
            state = MultiRouteState.Failed(friendlyCalcError(result.errorCode, lastMode))
        }

        override fun onCalculateRouteFailure(errorCode: Int) {
            Log.e(PLANNER_TAG, "[route-plan] calc failure(legacy) code=$errorCode")
            state = MultiRouteState.Failed(friendlyCalcError(errorCode, lastMode))
        }

        // ---- 选线阶段不开始导航，其余回调无需处理，空实现满足接口 ----
        override fun onTrafficStatusUpdate() = Unit
        override fun onLocationChange(location: AMapNaviLocation?) = Unit
        override fun onGetNavigationText(type: Int, text: String?) = Unit
        override fun onGetNavigationText(text: String?) = Unit
        override fun onStartNavi(type: Int) = Unit
        override fun onReCalculateRouteForYaw() = Unit
        override fun onReCalculateRouteForTrafficJam() = Unit
        override fun onArrivedWayPoint(wayId: Int) = Unit
        override fun onGpsOpenStatus(enabled: Boolean) = Unit
        override fun onNaviInfoUpdate(naviInfo: NaviInfo?) = Unit
        override fun updateCameraInfo(cameraInfos: Array<out AMapNaviCameraInfo>?) = Unit
        override fun updateIntervalCameraInfo(
            startInfo: AMapNaviCameraInfo?,
            endInfo: AMapNaviCameraInfo?,
            distance: Int,
        ) = Unit
        override fun onServiceAreaUpdate(serviceAreas: Array<out AMapServiceAreaInfo>?) = Unit
        override fun showCross(cross: AMapNaviCross?) = Unit
        override fun hideCross() = Unit
        override fun showModeCross(cross: AMapModelCross?) = Unit
        override fun hideModeCross() = Unit
        override fun showLaneInfo(lanes: Array<out AMapLaneInfo>?, laneBackground: ByteArray?, laneRecommended: ByteArray?) = Unit
        override fun showLaneInfo(lane: AMapLaneInfo?) = Unit
        override fun hideLaneInfo() = Unit
        override fun onCalculateRouteSuccess(routeIds: IntArray?) = Unit
        override fun notifyParallelRoad(parallelRoad: Int) = Unit
        override fun OnUpdateTrafficFacility(facilities: Array<out AMapNaviTrafficFacilityInfo>?) = Unit
        override fun OnUpdateTrafficFacility(facility: AMapNaviTrafficFacilityInfo?) = Unit
        override fun updateAimlessModeStatistics(stat: AimLessModeStat?) = Unit
        override fun updateAimlessModeCongestionInfo(info: AimLessModeCongestionInfo?) = Unit
        override fun onPlayRing(ring: Int) = Unit
        override fun onNaviRouteNotify(data: AMapNaviRouteNotifyData?) = Unit
        override fun onGpsSignalWeak(weak: Boolean) = Unit
        override fun onEndEmulatorNavi() = Unit
        override fun onArriveDestination() = Unit
    }

    init {
        val navi = aMapNavi
        if (navi != null) {
            runCatching {
                navi.addAMapNaviListener(listener)
                Log.i(PLANNER_TAG, "[route-plan] planner listener attached")
            }.onFailure {
                Log.e(PLANNER_TAG, "[route-plan] addAMapNaviListener failed", it)
                state = MultiRouteState.Failed("导航引擎初始化异常，请重启 App")
            }
        } else {
            state = MultiRouteState.Failed("当前设备缺少导航引擎（仅支持 ARM 真机）")
        }
    }

    /**
     * 发起算路。
     *
     * @param mode DRIVE/WALK/RIDE（多路线仅驾车；步行/骑行返回单条候选）
     * @param origin 任意起点（不要求是设备当前位置）
     */
    fun plan(
        mode: RouteTravelMode,
        origin: LatLng,
        destination: LatLng,
        destName: String,
        originIsMyLocation: Boolean,
        city: String,
    ) {
        val navi = aMapNavi
        if (navi == null) {
            state = MultiRouteState.Failed("导航引擎不可用，请在 ARM 真机上使用")
            return
        }
        if (origin.latitude == 0.0 && origin.longitude == 0.0 ||
            destination.latitude == 0.0 && destination.longitude == 0.0
        ) {
            Log.e(
                PLANNER_TAG,
                "[route-plan] invalid coords origin=$origin dest=$destination, calc blocked",
            )
            state = MultiRouteState.Failed("起终点坐标无效，请返回重新选择")
            return
        }
        // 步行 100km / 骑行 200km 引擎硬限制：本地前置拦截，避免 3003/30007 报错
        val directDistance = haversineMeters(origin, destination)
        if (!mode.isSupportedDistance(directDistance)) {
            Log.w(
                PLANNER_TAG,
                "[route-plan] mode=$mode out of range distance=${directDistance.toInt()}m, blocked",
            )
            state = MultiRouteState.Failed(friendlyCalcError(if (mode == RouteTravelMode.WALK) 3003 else 30007, mode))
            return
        }
        lastFrom = NaviLatLng(origin.latitude, origin.longitude)
        lastTo = NaviLatLng(destination.latitude, destination.longitude)
        lastMode = mode
        lastDestName = destName
        lastOriginIsMyLocation = originIsMyLocation
        lastCity = city
        calcInvoked = false
        state = MultiRouteState.Loading
        Log.i(
            PLANNER_TAG,
            "[route-plan] plan request mode=$mode origin=$origin dest=$destination " +
                "name=$destName originIsMyLocation=$originIsMyLocation",
        )
        calcInternal()
    }

    /** 真正调用引擎算路；引擎未初始化时由 onInitNaviSuccess 回调后补发。 */
    private fun calcInternal() {
        val navi = aMapNavi ?: return
        val from = lastFrom ?: return
        val to = lastTo ?: return
        val mode = lastMode ?: return
        if (calcInvoked) {
            Log.d(PLANNER_TAG, "[route-plan] calc already invoked for current request, skip duplicate")
            return
        }
        var invoked = false
        runCatching {
            when (mode) {
                RouteTravelMode.DRIVE -> {
                    // 驾车：多路线 + 高速优先策略
                    navi.setMultipleRouteNaviMode(true)
                    val strategy = navi.strategyConvert(false, false, false, true, true)
                    invoked = navi.calculateDriveRoute(listOf(from), listOf(to), null, strategy)
                }
                RouteTravelMode.WALK -> {
                    navi.setMultipleRouteNaviMode(false)
                    invoked = navi.calculateWalkRoute(from, to)
                }
                RouteTravelMode.RIDE -> {
                    navi.setMultipleRouteNaviMode(false)
                    invoked = navi.calculateRideRoute(from, to)
                }
                // 多路线卡片仅支持驾车/步行/骑行；公交走搜索 SDK，不应走到这里
                RouteTravelMode.BUS -> {
                    state = MultiRouteState.Failed("公交方案请在路线步骤面板中查看")
                }
            }
        }.onSuccess {
            // 引擎接受请求才标记；返回 false（如未初始化）时等 init 回调补发
            calcInvoked = invoked
            Log.i(PLANNER_TAG, "[route-plan] calculateXxxRoute invoked=$invoked mode=$mode")
        }.onFailure {
            Log.e(PLANNER_TAG, "[route-plan] calculateRoute invoke failed", it)
            state = MultiRouteState.Failed("算路请求异常：${it.message ?: "未知错误"}")
        }
    }

    /** 页面销毁：只移除自己的监听，不 destroy 单例（导航页还要复用同一引擎）。 */
    fun release() {
        Log.i(PLANNER_TAG, "[route-plan] planner release, remove listener")
        runCatching { aMapNavi?.removeAMapNaviListener(listener) }
    }

    /**
     * 选线页 ON_RESUME 调用：导航页退出时会 AMapNavi.destroy() 单例，
     * 返回选线页后必须重新 getInstance、重新挂监听，并按上次入参补一次算路刷新卡片。
     */
    fun reattachIfNeeded() {
        // 通过全局管理器取引擎：导航页 destroy 后此处会懒创建新实例
        val current = AmapNaviManager.getOrCreate(appContext)
        if (current == null) {
            Log.w(PLANNER_TAG, "[route-plan] reattach: AMapNavi engine null")
            return
        }
        if (current === aMapNavi) return
        Log.w(PLANNER_TAG, "[route-plan] navi engine changed, reattach listener + recalc")
        runCatching { aMapNavi?.removeAMapNaviListener(listener) }
        aMapNavi = current
        runCatching { current.addAMapNaviListener(listener) }
        // 新引擎尚未初始化：onInitNaviSuccess 回调会补发 calcInternal()
        calcInvoked = false
        calcInternal()
    }

    /** 把引擎 [AMapNaviPath] 拍平为可跨页面传递的 [NaviRouteChoice]；折线异常返回 null。 */
    private fun AMapNaviPath.toChoice(routeId: Int): NaviRouteChoice? {
        val coords = coordList.orEmpty().map { LatLng(it.latitude, it.longitude) }
        if (coords.size < 2) {
            Log.w(PLANNER_TAG, "[route-plan] path #$routeId coordList empty/single, drop it")
            return null
        }
        val steps = steps.orEmpty().mapIndexedNotNull { i, seg: AMapNaviStep ->
            val road = seg.links.orEmpty().mapNotNull { it.roadName?.takeIf { n -> n.isNotBlank() } }
                .distinct()
                .joinToString("、")
            NaviTurnStep(
                index = i + 1,
                iconType = seg.iconType,
                roadName = road,
                lengthMeters = seg.length,
                timeSeconds = seg.time,
            )
        }
        val mainRoad = mainRoadInfo?.takeIf { it.isNotBlank() }
            ?: steps.mapNotNull { it.roadName.takeIf { n -> n.isNotBlank() } }
                .distinct()
                .take(3)
                .joinToString(" → ")
                .ifBlank { "推荐路线" }
        return NaviRouteChoice(
            routeId = routeId,
            distanceMeters = allLength,
            durationSeconds = allTime,
            trafficLights = trafficLightCount,
            summary = mainRoad,
            points = coords,
            steps = steps,
        )
    }
}
