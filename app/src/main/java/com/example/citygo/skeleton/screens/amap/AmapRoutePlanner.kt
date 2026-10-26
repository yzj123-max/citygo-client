package com.example.citygo.skeleton.screens.amap

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.BusPath
import com.amap.api.services.route.BusRouteResult
import com.amap.api.services.route.BusStep
import com.amap.api.services.route.DrivePath
import com.amap.api.services.route.DriveRouteResult
import com.amap.api.services.route.DriveStep
import com.amap.api.services.route.RidePath
import com.amap.api.services.route.RideRouteResult
import com.amap.api.services.route.RideStep
import com.amap.api.services.route.RouteBusLineItem
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.WalkPath
import com.amap.api.services.route.WalkRouteResult
import com.amap.api.services.route.WalkStep
import kotlin.math.roundToInt

/** 路线规划状态机：空闲 / 请求中 / 成功（含几何与里程耗时）/ 失败 */
sealed interface RoutePlanState {
    data object Idle : RoutePlanState
    data object Loading : RoutePlanState
    data class Success(val route: RouteInfo) : RoutePlanState
    data class Failed(val message: String) : RoutePlanState
}

/**
 * 出行交通方式（景点详情页「去导航」路线预览可切换）。
 *
 * @property label 中文方式名
 */
enum class RouteTravelMode(val label: String) {
    DRIVE("驾车"),
    WALK("步行"),
    RIDE("骑行"),
    BUS("公交"),
}

/**
 * 导航模式（显式枚举，禁止再用距离 / 定位结果等隐式推断）：
 * - [SIMULATION] 条件A：地图上手动选定【自定义起点 + 自定义终点】（两个都是地图点位），
 *   路线规划完成后调用高德 SDK 模拟导航 startEmulatorNavi()（不读 GPS，小车自动跑路线，演示用）；
 * - [REAL_GPS] 条件B：只选【终点】并点【从我的位置出发】，路线规划完成后调用高德 SDK
 *   真实 GPS 导航 startGPSNavi()（读取手机定位、车头跟随、支持偏航重算）。
 *
 * 路由参数透传取值见 [wireValue] / [fromWire]。
 */
enum class NaviMode(val wireValue: String, val buttonText: String) {
    SIMULATION("simulation", "开始模拟导航"),
    REAL_GPS("real_gps", "开始实时导航"),
    ;

    companion object {
        /** 所有页面共用的模式规则：当前位置必须 GPS，自定义两点使用模拟。 */
        fun forOrigin(originIsMyLocation: Boolean): NaviMode =
            if (originIsMyLocation) REAL_GPS else SIMULATION

        /** 仅显式 simulation 才演示；缺失或未知参数默认真实导航，定位失败则由页面拦截。 */
        fun fromWire(value: String?): NaviMode =
            entries.firstOrNull { it.wireValue == value } ?: REAL_GPS
    }
}

/** 单个公交换乘方案的文字描述（公交规划不画折线，以方案列表展示） */
data class BusPlanInfo(
    /** 总距离（米） */
    val distanceMeters: Int,
    /** 总耗时（秒） */
    val durationSeconds: Long,
    /** 换乘概要，如「地铁2号线 → 公交5路 → 步行」 */
    val summary: String,
) {
    val distanceText: String
        get() = if (distanceMeters >= 1000) "%.1f公里".format(distanceMeters / 1000.0) else "${distanceMeters}米"

    val durationText: String
        get() = "${(durationSeconds / 60.0).roundToInt()}分钟"
}

/** 单条路线步骤（路线步骤面板列表用） */
data class RouteStepInfo(
    /** 序号（从 1 开始） */
    val index: Int,
    /** 转向 / 操作指引文案，如「沿xx路行驶200米右转」 */
    val instruction: String,
    /** 该步骤距离（米），无距离信息为 0 */
    val distanceMeters: Int = 0,
    /** 所在道路 / 线路名，可空 */
    val road: String? = null,
    /** 是否乘坐公交 / 地铁段（公交方案步骤用） */
    val isTransit: Boolean = false,
) {
    val distanceText: String
        get() = when {
            distanceMeters <= 0 -> ""
            distanceMeters >= 1000 -> "%.1f公里".format(distanceMeters / 1000.0)
            else -> "${distanceMeters}米"
        }
}

/** 一条路线的展示信息（驾车 / 步行 / 骑行含几何折线，公交含换乘方案列表） */
data class RouteInfo(
    /** 路线几何点（GCJ-02），供地图画折线与相机适配；公交方式为空 */
    val points: List<LatLng>,
    /** 总距离（米） */
    val distanceMeters: Int,
    /** 总耗时（秒） */
    val durationSeconds: Long,
    /** 本次规划方式 */
    val mode: RouteTravelMode = RouteTravelMode.DRIVE,
    /** 公交换乘方案（仅公交方式有值） */
    val busPlans: List<BusPlanInfo> = emptyList(),
    /** 路线步骤列表（驾车 / 步行 / 骑行 / 公交均有值，面板直接渲染） */
    val steps: List<RouteStepInfo> = emptyList(),
) {
    /** 距离展示：≥1km 显示公里，否则显示米 */
    val distanceText: String
        get() = if (distanceMeters >= 1000) {
            "%.1f公里".format(distanceMeters / 1000.0)
        } else {
            "${distanceMeters}米"
        }

    /** 耗时展示：分钟 */
    val durationText: String
        get() = "${(durationSeconds / 60.0).roundToInt()}分钟"
}

/**
 * 高德路径规划封装（搜索 SDK RouteSearch 异步接口），支持驾车 / 步行 / 骑行 / 公交。
 * 结果通过 Compose 可观察的 [state] 对外发布。
 */
class AmapRoutePlanner(context: Context) {

    var state by mutableStateOf<RoutePlanState>(RoutePlanState.Idle)
        private set

    /** 当前成功路线对应的起终点（导航页跳转复用） */
    var origin: LatLng? by mutableStateOf(null)
        private set
    var destination: LatLng? by mutableStateOf(null)
        private set

    private val routeSearch: RouteSearch? = runCatching {
        RouteSearch(context.applicationContext)
    }.getOrNull()

    init {
        routeSearch?.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
            override fun onDriveRouteSearched(result: DriveRouteResult?, code: Int) {
                if (code != CODE_SUCCESS) {
                    state = RoutePlanState.Failed("驾车路线规划失败（错误码 $code），请检查网络或 Key 配置")
                    return
                }
                val path: DrivePath? = result?.paths?.firstOrNull()
                if (path == null) {
                    state = RoutePlanState.Failed("未获取到可行的驾车路线")
                    return
                }
                val points = path.polyline.map { LatLng(it.latitude, it.longitude) }
                if (points.size < 2) {
                    state = RoutePlanState.Failed("路线数据异常，无法在地图上展示")
                    return
                }
                state = RoutePlanState.Success(
                    RouteInfo(
                        points = points,
                        distanceMeters = path.distance.toInt(),
                        durationSeconds = path.duration,
                        mode = RouteTravelMode.DRIVE,
                        steps = path.steps.orEmpty().mapIndexed { i, step: DriveStep ->
                            RouteStepInfo(
                                index = i + 1,
                                instruction = step.instruction?.takeIf { it.isNotBlank() }
                                    ?: "沿道路继续行驶",
                                distanceMeters = step.distance.toInt(),
                                road = step.road?.takeIf { it.isNotBlank() },
                            )
                        },
                    ),
                )
            }

            override fun onWalkRouteSearched(result: WalkRouteResult?, code: Int) {
                if (code != CODE_SUCCESS) {
                    state = RoutePlanState.Failed("步行路线规划失败（错误码 $code）")
                    return
                }
                val path: WalkPath? = result?.paths?.firstOrNull()
                if (path == null) {
                    state = RoutePlanState.Failed("距离过远或无可行的步行路线")
                    return
                }
                val points = path.steps.flatMap { step ->
                    step.polyline.orEmpty().map { LatLng(it.latitude, it.longitude) }
                }
                if (points.size < 2) {
                    state = RoutePlanState.Failed("步行路线数据异常，无法展示")
                    return
                }
                state = RoutePlanState.Success(
                    RouteInfo(
                        points = points,
                        distanceMeters = path.distance.toInt(),
                        durationSeconds = path.duration,
                        mode = RouteTravelMode.WALK,
                        steps = path.steps.orEmpty().toWalkStepInfos(fallback = "沿道路步行"),
                    ),
                )
            }

            override fun onRideRouteSearched(result: RideRouteResult?, code: Int) {
                if (code != CODE_SUCCESS) {
                    state = RoutePlanState.Failed("骑行路线规划失败（错误码 $code）")
                    return
                }
                val path: RidePath? = result?.paths?.firstOrNull()
                if (path == null) {
                    state = RoutePlanState.Failed("距离过远或无可行的骑行路线")
                    return
                }
                val points = path.steps.flatMap { step ->
                    step.polyline.orEmpty().map { LatLng(it.latitude, it.longitude) }
                }
                if (points.size < 2) {
                    state = RoutePlanState.Failed("骑行路线数据异常，无法展示")
                    return
                }
                state = RoutePlanState.Success(
                    RouteInfo(
                        points = points,
                        distanceMeters = path.distance.toInt(),
                        durationSeconds = path.duration,
                        mode = RouteTravelMode.RIDE,
                        steps = path.steps.orEmpty().toRideStepInfos(fallback = "沿道路骑行"),
                    ),
                )
            }

            override fun onBusRouteSearched(result: BusRouteResult?, code: Int) {
                if (code != CODE_SUCCESS) {
                    state = RoutePlanState.Failed("公交方案查询失败（错误码 $code）")
                    return
                }
                val paths = result?.paths.orEmpty()
                if (paths.isEmpty()) {
                    state = RoutePlanState.Failed("未查询到可用的公交换乘方案")
                    return
                }
                val plans = paths.map { path -> path.toBusPlanInfo() }
                // 首选方案尽力拼出公交 / 步行段折线（部分跨城方案可能无几何，则仅画起终点）
                val busPoints = paths.firstOrNull()?.toBusPolyline().orEmpty()
                val busSteps = paths.firstOrNull()?.toBusStepInfos().orEmpty()
                // 公交方案没有完整折线，仅取起终点用于地图打点
                state = RoutePlanState.Success(
                    RouteInfo(
                        points = busPoints,
                        distanceMeters = plans.firstOrNull()?.distanceMeters ?: 0,
                        durationSeconds = plans.firstOrNull()?.durationSeconds ?: 0L,
                        mode = RouteTravelMode.BUS,
                        busPlans = plans,
                        steps = busSteps,
                    ),
                )
            }
        })
    }

    /**
     * 发起路线规划。
     *
     * @param mode 交通方式
     * @param origin 起点（GCJ-02）
     * @param destination 终点（GCJ-02）
     * @param city 终点城市名（公交查询需要城市 adcode；Mock 城市已内置映射）
     */
    fun planRoute(
        mode: RouteTravelMode,
        origin: LatLng,
        destination: LatLng,
        city: String? = null,
    ) {
        val search = routeSearch
        if (search == null) {
            state = RoutePlanState.Failed("路线服务初始化失败，请在 ARM 架构真机上使用")
            return
        }
        this.origin = origin
        this.destination = destination
        state = RoutePlanState.Loading
        val fromAndTo = RouteSearch.FromAndTo(
            LatLonPoint(origin.latitude, origin.longitude),
            LatLonPoint(destination.latitude, destination.longitude),
        )
        runCatching {
            when (mode) {
                RouteTravelMode.DRIVE -> {
                    val query = RouteSearch.DriveRouteQuery(
                        fromAndTo,
                        RouteSearch.DrivingDefault,
                        null,
                        null,
                        "",
                    )
                    search.calculateDriveRouteAsyn(query)
                }
                RouteTravelMode.WALK -> {
                    val query = RouteSearch.WalkRouteQuery(fromAndTo, RouteSearch.WalkDefault)
                    search.calculateWalkRouteAsyn(query)
                }
                RouteTravelMode.RIDE -> {
                    val query = RouteSearch.RideRouteQuery(fromAndTo, RouteSearch.RidingDefault)
                    search.calculateRideRouteAsyn(query)
                }
                RouteTravelMode.BUS -> {
                    // 公交规划必须带城市：优先内置 adcode 映射，否则直接透传城市名（高德亦支持）
                    val cityCode = city?.takeIf { it.isNotBlank() }?.let { BUS_CITY_ADCODE[it] ?: it }
                        .orEmpty()
                    val query = RouteSearch.BusRouteQuery(
                        fromAndTo,
                        RouteSearch.BusDefault,
                        cityCode,
                        0,
                    )
                    search.calculateBusRouteAsyn(query)
                }
            }
        }.onFailure {
            state = RoutePlanState.Failed(it.message ?: "路线规划请求失败")
        }
    }

    /**
     * 发起驾车路线规划（默认策略：速度优先）。保留给地图 Tab 旧调用。
     */
    fun planDriving(origin: LatLng, destination: LatLng) =
        planRoute(RouteTravelMode.DRIVE, origin, destination)

    fun clear() {
        state = RoutePlanState.Idle
        origin = null
        destination = null
    }

    /** 把 [BusPath] 解析为「换乘概要 + 里程耗时」的展示模型 */
    private fun BusPath.toBusPlanInfo(): BusPlanInfo {
        val segments = mutableListOf<String>()
        steps.orEmpty().forEach { step: BusStep ->
            // 公交 / 地铁线路（同一步可能含多条备选线路）
            step.busLines.orEmpty().forEach { line ->
                val type = line.busLineType.orEmpty()
                val name = line.busLineName.orEmpty()
                if (name.isNotBlank()) segments += "$type$name"
            }
            // 火车 / 高铁段
            step.railway?.name?.takeIf { it.isNotBlank() }?.let { segments += it }
            // 步行换乘段（RouteBusWalkItem 继承 WalkPath）
            if (step.walk?.steps?.isNotEmpty() == true) {
                segments += "步行"
            }
        }
        return BusPlanInfo(
            distanceMeters = distance.toInt(),
            durationSeconds = duration,
            summary = segments.filter { it.isNotBlank() }.distinct().joinToString(" → ")
                .ifBlank { "共 ${steps.orEmpty().size} 个路段（步行与公交/地铁换乘）" },
        )
    }

    /** 首选公交方案的文字步骤：乘车段 + 步行换乘段，按顺序展开 */
    private fun BusPath.toBusStepInfos(): List<RouteStepInfo> {
        var seq = 0
        val result = mutableListOf<RouteStepInfo>()
        steps.orEmpty().forEach { step: BusStep ->
            step.busLines.orEmpty().forEach { line: RouteBusLineItem ->
                seq++
                val type = line.busLineType.orEmpty()
                val name = line.busLineName.orEmpty()
                result += RouteStepInfo(
                    index = seq,
                    instruction = "乘坐$type$name".ifBlank { "乘坐公共交通" },
                    road = name.takeIf { it.isNotBlank() },
                    isTransit = true,
                )
            }
            step.railway?.name?.takeIf { it.isNotBlank() }?.let {
                seq++
                result += RouteStepInfo(index = seq, instruction = "乘坐$it", isTransit = true)
            }
            step.walk?.steps.orEmpty().forEach { walkStep ->
                seq++
                result += RouteStepInfo(
                    index = seq,
                    instruction = walkStep.instruction?.takeIf { it.isNotBlank() } ?: "步行换乘",
                    distanceMeters = walkStep.distance.toInt(),
                    road = walkStep.road?.takeIf { it.isNotBlank() },
                )
            }
        }
        if (result.isEmpty()) {
            result += RouteStepInfo(index = 1, instruction = "公交/步行混合换乘方案")
        }
        return result
    }

    /** 首选公交方案尽力拼接几何折线：乘车段线路折线 + 步行段折线（失败的段自动跳过） */
    private fun BusPath.toBusPolyline(): List<LatLng> {
        val out = LinkedHashMap<String, LatLng>()
        fun putAll(points: List<LatLonPoint>?) {
            points.orEmpty().forEach { p ->
                val key = "${p.latitude},${p.longitude}"
                out.putIfAbsent(key, LatLng(p.latitude, p.longitude))
            }
        }
        steps.orEmpty().forEach { step: BusStep ->
            step.busLines.orEmpty().forEach { line -> putAll(runCatching { line.polyline }.getOrNull()) }
            step.walk?.steps.orEmpty().forEach { ws -> putAll(ws.polyline) }
        }
        return out.values.toList()
    }

    /** 步行步骤 → 面板展示步骤（指引缺省时用 [fallback] 兜底） */
    private fun List<WalkStep>.toWalkStepInfos(fallback: String): List<RouteStepInfo> =
        mapIndexed { i, step ->
            RouteStepInfo(
                index = i + 1,
                instruction = step.instruction?.takeIf { it.isNotBlank() } ?: fallback,
                distanceMeters = step.distance.toInt(),
                road = step.road?.takeIf { it.isNotBlank() },
            )
        }

    /** 骑行步骤 → 面板展示步骤（指引缺省时用 [fallback] 兜底） */
    private fun List<RideStep>.toRideStepInfos(fallback: String): List<RouteStepInfo> =
        mapIndexed { i, step ->
            RouteStepInfo(
                index = i + 1,
                instruction = step.instruction?.takeIf { it.isNotBlank() } ?: fallback,
                distanceMeters = step.distance.toInt(),
                road = step.road?.takeIf { it.isNotBlank() },
            )
        }

    private companion object {
        /** 高德服务端统一成功码 AMapException.CODE_AMAP_SUCCESS */
        const val CODE_SUCCESS = 1000

        /** Mock 覆盖城市的行政区划代码（公交 RouteSearch 入参用） */
        val BUS_CITY_ADCODE = mapOf(
            "长沙" to "430100",
            "杭州" to "330100",
            "重庆" to "500000",
            "大理" to "532900",
            "三亚" to "460200",
            "成都" to "510100",
            "西安" to "610100",
            "厦门" to "350200",
        )
    }
}
