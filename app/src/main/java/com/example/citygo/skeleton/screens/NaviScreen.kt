package com.example.citygo.skeleton.screens

import com.example.citygo.skeleton.screens.amap.hasPreciseLocationPermission
import com.example.citygo.skeleton.screens.amap.navigationLocationPermissions
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.model.AimLessModeCongestionInfo
import com.amap.api.navi.model.AimLessModeStat
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.AMapLaneInfo
import com.amap.api.navi.model.AMapModelCross
import com.amap.api.navi.model.AMapNaviCameraInfo
import com.amap.api.navi.model.AMapNaviCross
import com.amap.api.navi.model.AMapNaviLocation
import com.amap.api.navi.model.AMapNaviPath
import com.amap.api.navi.model.AMapNaviRouteNotifyData
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo
import com.amap.api.navi.model.AMapServiceAreaInfo
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviLatLng
import com.example.citygo.skeleton.screens.amap.AmapNaviManager
import com.example.citygo.skeleton.screens.amap.AmapRoutePlanner
import com.example.citygo.skeleton.screens.amap.RouteInfo
import com.example.citygo.skeleton.screens.amap.RoutePlanState
import com.example.citygo.skeleton.screens.amap.RouteStepInfo
import com.example.citygo.skeleton.screens.amap.RouteTravelMode
import com.example.citygo.skeleton.screens.amap.NaviMode
import com.example.citygo.skeleton.screens.amap.friendlyCalcError
import com.example.citygo.skeleton.screens.amap.haversineMeters
import com.example.citygo.skeleton.screens.amap.isArmAbiSupported
import com.example.citygo.skeleton.screens.amap.isSupportedDistance
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* =====================================================================================
 * 全屏实时导航页（高德导航 SDK：AMapNavi + AMapNaviView）
 *
 * 两种起点场景（由路由 oType 决定，行为必须严格区分）：
 *  - 场景A MY_LOCATION：起点=设备当前位置。导航 View 就绪后开启 NaviLocationFeed
 *    （高精度 1s 外挂 GPS，setIsUseExtraGPSData + setExtraGPSData(GCJ-02)），
 *    位置自带 bearing，车头箭头跟随手机朝向；首帧定位未到禁止算路。
 *  - 场景B SPOT：起点=自定义景点坐标（两景点间导航）。【不启动 feed、不调用
 *    setIsUseExtraGPSData / setExtraGPSData】，直接用传入的起终点坐标算路，
 *    避免真实 GPS 覆盖自定义起点导致内核回退北京。
 *
 * 黑屏防护：任何算路 / 外挂 GPS 操作都在 NaviView 完成 onCreate+onResume（视图就绪）之后。
 * 算路失败 Toast 提示并可重试，不卡死页面。页面销毁停止定位、释放导航单例。
 *
 * 路线步骤：顶部【路线步骤】按钮弹出底部面板，独立小地图全览整条路线 + 步骤列表，
 * 支持驾车/步行/骑行/公交切换（公交仅面板预览，不切换实时导航）。
 * ===================================================================================== */

/** 真机导航链路日志 Tag（adb logcat -s CityGoNavi） */
private const val TAG = "CityGoNavi"

/** 首帧外挂定位等待时长：超时后界面给出错误提示与重试入口（不自动算路） */
private const val GPS_WAIT_TIMEOUT_MS = 15_000L

/** 引擎初始化等待上限：超过未回调成功/失败直接报错，杜绝无限“正在加载导航视图” */
private const val NAVI_INIT_TIMEOUT_MS = 10_000L

/** 发起算路后等待导航真正启动（onStartNavi）的上限：超时自动退出并提示 */
private const val NAVI_START_TIMEOUT_MS = 10_000L

/** 场景B 算路成功后单次定位判定（50m 比对）的等待上限：超时强制模拟导航 */
private const val SINGLE_FIX_TIMEOUT_MS = 8_000L

/** 模拟导航默认速度：中速（km/h，高德 setEmulatorNaviSpeed 取值约 10~120） */
private const val EMULATOR_MEDIUM_SPEED = 60

/** 释放路线步骤面板内的小地图（onPause + onDestroy），幂等。 */
private fun releaseOverviewMap(mapView: TextureMapView?, guard: BooleanArray) {
    if (guard[0]) return
    guard[0] = true
    runCatching {
        mapView?.onPause()
        mapView?.onDestroy()
    }.onFailure { Log.e(TAG, "[steps-sheet] overview map release failed", it) }
}

/** 释放全屏导航视图（onPause + onDestroy），幂等。 */
private fun releaseNaviView(view: AMapNaviView, guard: BooleanArray) {
    if (guard[0]) return
    guard[0] = true
    runCatching {
        view.onPause()
        view.onDestroy()
    }.onFailure { Log.e(TAG, "NaviView release failed", it) }
}

@Composable
fun NaviScreen(
    originLat: Double,
    originLng: Double,
    destLat: Double,
    destLng: Double,
    destName: String,
    onBack: () -> Unit,
    travelMode: RouteTravelMode = RouteTravelMode.DRIVE,
    /** true=场景A 起点为设备当前位置；false=场景B 两景点间导航 */
    originIsMyLocation: Boolean = true,
    /** 终点城市名（公交路线步骤查询用） */
    cityName: String = "",
    /** true=模拟导航（步骤预览页底部进入）：不启外挂 GPS，算路成功后走 EMULATOR */
    emulatorMode: Boolean = false,
    /**
     * 显式导航模式（地图页按选点分支传入，禁止隐式判定）：
     * SIMULATION=条件A 自定义起终点 → startEmulatorNavi()；
     * REAL_GPS=条件B 从我的位置出发 → startGPSNavi()。
     * [emulatorMode]=true 时强制 SIMULATION（步骤预览页入口）。
     */
    naviMode: NaviMode = NaviMode.REAL_GPS,
    /** 选线页选中的引擎路线 id，算路成功后 selectRouteId；-1 表示不指定 */
    preferredRouteId: Int = -1,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 所有入口统一按起点来源决定导航模式。
    val effectiveNaviMode = NaviMode.forOrigin(originIsMyLocation)
    // 起点来源是唯一依据。旧 simulate/naviType 参数不得把当前位置变成模拟导航。
    LaunchedEffect(originIsMyLocation, emulatorMode, naviMode) {
        Log.i(TAG, "navi policy: myLocation=$originIsMyLocation, requested=$naviMode, " +
            "emulatorFlag=$emulatorMode, effective=$effectiveNaviMode")
    }
    // 是否需要真实 GPS：仅显式 REAL_GPS；SIMULATION 全程不创建任何定位订阅
    val gpsRequired = effectiveNaviMode == NaviMode.REAL_GPS
    val scenario = when (effectiveNaviMode) {
        NaviMode.SIMULATION -> "SIMULATION"
        NaviMode.REAL_GPS -> "REAL_GPS"
    }

    // 非 arm 架构 / 无高德 native 库：直接降级，不触碰任何导航 native 类
    if (!isArmAbiSupported(context)) {
        NaviUnsupportedPanel(onBack = onBack)
        return
    }

    // 起终点坐标校验：(0,0) 等无效坐标直接 Toast + 拦截，绝不创建导航实例 / 发起算路（防止跳北京）
    val invalidCoords = (originLat == 0.0 && originLng == 0.0) ||
        (destLat == 0.0 && destLng == 0.0)
    if (invalidCoords) {
        Log.e(
            TAG,
            "[$scenario] invalid endpoint: origin=($originLat,$originLng) " +
                "dest=($destLat,$destLng), calc blocked",
        )
        NaviInvalidRoutePanel(onBack = onBack)
        return
    }

    // 实时导航当前模式（面板切换模式后会重算，公交仅面板预览不参与逐 turn 导航）
    var currentMode by remember { mutableStateOf(travelMode) }
    var statusText by remember { mutableStateOf("正在加载导航视图…") }
    var hasFailure by remember { mutableStateOf(false) }
    var arrived by remember { mutableStateOf(false) }
    var routeReady by remember { mutableStateOf(false) }

    // 导航视图只创建一次；native 异常时返回 null 走降级面板
    val naviView = remember {
        runCatching { AMapNaviView(context).apply { onCreate(Bundle()) } }.getOrNull()
    }
    // NaviView onPause/onDestroy 幂等保护（生命周期 ON_DESTROY 与组合销毁可能同时发生）
    val naviViewReleased = remember { booleanArrayOf(false) }
    // 导航引擎走全局单例管理：反复进出页面复用同一实例；退出本页时统一 destroy 释放
    val aMapNavi = remember(naviView) {
        naviView?.let { AmapNaviManager.getOrCreate(context.applicationContext) }
    }

    if (naviView == null || aMapNavi == null) {
        NaviUnsupportedPanel(onBack = onBack)
        return
    }

    Log.i(
        TAG,
        "[$scenario] enter navi mode=$currentMode origin=($originLat,$originLng) " +
            "dest=($destLat,$destLng) name=$destName city=$cityName " +
            "emulator=$emulatorMode preferredRouteId=$preferredRouteId viewReady=pending",
    )

    // ---- 导航视图就绪闸门：onCreate 已在 remember 完成，必须补到一次 onResume 才允许后续操作 ----
    var viewReady by remember { mutableStateOf(false) }

    // ---- 定位权限：仅场景A 的外挂定位 / 实时跟随需要 ----
    var hasLocationPermission by remember {
        mutableStateOf(
            hasPreciseLocationPermission(context),
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val granted = hasPreciseLocationPermission(context)
        hasLocationPermission = granted
        Log.i(TAG, "[$scenario] location permission granted=$granted")
    }
    LaunchedEffect(Unit) {
        if (gpsRequired && !hasLocationPermission) {
            permissionLauncher.launch(navigationLocationPermissions())
        }
    }

    // ---- 算路闸门：视图就绪 + 内核初始化 +（REAL_GPS）首帧外挂定位，且每次模式切换只算一次 ----
    val gate = remember { NaviStartGate() }
    var gpsLocated by remember { mutableStateOf(false) }
    // 真实导航算路必须使用本页收到的定位，不能只等待定位却仍用预览起点。
    var latestGpsPosition by remember { mutableStateOf<LatLng?>(null) }
    var gpsErrorText by remember { mutableStateOf<String?>(null) }
    var gpsTimeout by remember { mutableStateOf(false) }
    var gpsRetryTick by remember { mutableIntStateOf(0) }
    var showStepsSheet by remember { mutableStateOf(false) }

    // ---- 显式导航模式（SIMULATION/REAL_GPS，地图页选点分支决定，禁止隐式推断） ----
    // 已启动的导航类型：NaviType.GPS=1 实时导航 / NaviType.EMULATOR=2 模拟导航；null=尚未启动
    var resolvedNaviType by remember { mutableStateOf<Int?>(null) }
    // 是否需要连续外挂 GPS：仅 REAL_GPS 需要（进入即启动）；SIMULATION 全程不启动
    var feedRequested by remember { mutableStateOf(gpsRequired) }

    // ---- 两个 10s 看门狗：杜绝“正在加载导航视图”无限转圈 ----
    val naviScope = rememberCoroutineScope()
    var initWatchdogJob by remember { mutableStateOf<Job?>(null) }
    var startWatchdogJob by remember { mutableStateOf<Job?>(null) }

    fun cancelInitWatchdog() {
        initWatchdogJob?.cancel()
        initWatchdogJob = null
    }

    /** 导航启动看门狗：每次真正发起算路时启动，onStartNavi 回调时取消；超时自动退出 */
    fun armStartWatchdog() {
        startWatchdogJob?.cancel()
        startWatchdogJob = naviScope.launch {
            delay(NAVI_START_TIMEOUT_MS)
            Log.e(TAG, "[$scenario] navi start timeout after ${NAVI_START_TIMEOUT_MS}ms, auto exit")
            Toast.makeText(context, "导航加载超时，请稍后重试", Toast.LENGTH_LONG).show()
            runCatching { aMapNavi.stopNavi() }
            onBack()
        }
    }
    fun cancelStartWatchdog() {
        startWatchdogJob?.cancel()
        startWatchdogJob = null
    }

    // 场景B / 模拟导航 不需要 GPS：合成闸门直接放行，全流程不创建任何定位订阅
    LaunchedEffect(Unit) {
        if (!gpsRequired) {
            gate.gpsReady = true
            Log.i(TAG, "[$scenario] extra-GPS not required, gps gate bypassed")
        }
    }

    // 场景A 专用外挂 GPS 数据源（场景B 永远不 start）
    val gpsFeed = remember { NaviLocationFeed(context) }

    /**
     * 唯一算路入口。三重前置条件全部满足才调用 calculateXxxRoute：
     * ① NaviView 已 onResume（视图就绪，防黑屏）；
     * ② AMapNavi onInitNaviSuccess；
     * ③ 场景A 首帧外挂 GPS 已到达（场景B 已合成放行）。
     */
    fun tryStartCalc(reason: String) {
        if (gate.calcDone) {
            Log.d(TAG, "calc skipped(already done), trigger=$reason")
            return
        }
        if (!viewReady) {
            Log.i(TAG, "calc gated: navi view not ready, trigger=$reason")
            return
        }
        if (!gate.initReady) {
            Log.i(TAG, "calc gated: waiting AMapNavi init, trigger=$reason")
            return
        }
        if (!gate.gpsReady) {
            Log.i(TAG, "calc gated: waiting first extra-GPS fix, trigger=$reason")
            return
        }
        gate.calcDone = true
        // 算路已发起：10s 内必须走到 onStartNavi，否则自动退出本页（防无限加载）
        armStartWatchdog()
        val actualOrigin = if (gpsRequired) latestGpsPosition else LatLng(originLat, originLng)
        if (actualOrigin == null) {
            gate.calcDone = false
            cancelStartWatchdog()
            statusText = "正在等待当前位置…"
            return
        }
        val from = NaviLatLng(actualOrigin.latitude, actualOrigin.longitude)
        val to = NaviLatLng(destLat, destLng)
        statusText = "正在规划${currentMode.label}路线…"
        Log.i(
            TAG,
            "[$scenario] calc request[$currentMode] trigger=$reason " +
                "from=(${actualOrigin.latitude},${actualOrigin.longitude}) to=($destLat,$destLng)",
        )
        when (currentMode) {
            RouteTravelMode.WALK -> {
                aMapNavi.setMultipleRouteNaviMode(false)
                aMapNavi.calculateWalkRoute(from, to)
            }
            RouteTravelMode.RIDE -> {
                aMapNavi.setMultipleRouteNaviMode(false)
                aMapNavi.calculateRideRoute(from, to)
            }
            RouteTravelMode.DRIVE, RouteTravelMode.BUS -> {
                // 与选线页保持同一多路线策略，preferredRouteId 才能对应上；多路线对单导航无副作用
                aMapNavi.setMultipleRouteNaviMode(true)
                val strategy = aMapNavi.strategyConvert(false, false, false, true, true)
                aMapNavi.calculateDriveRoute(listOf(from), listOf(to), null, strategy)
            }
        }
    }

    /**
     * 引擎初始化看门狗：注册监听后启动，init 成功/失败时取消。
     * 超时不直接判死：引擎可能已被选线页初始化（复用单例），只是本监听未再收到回调，
     * 此时主动放行 init 闸门尝试算路；若引擎真的不可用，算路失败/启动看门狗会兜底提示。
     * （声明在 [tryStartCalc] 之后以满足局部函数声明顺序）
     */
    fun armInitWatchdog() {
        initWatchdogJob?.cancel()
        initWatchdogJob = naviScope.launch {
            delay(NAVI_INIT_TIMEOUT_MS)
            if (gate.initReady) return@launch
            Log.w(
                TAG,
                "[$scenario] init callback missing after ${NAVI_INIT_TIMEOUT_MS}ms, " +
                    "treat as initialized and retry calc",
            )
            gate.initReady = true
            tryStartCalc("initTimeoutFallback")
        }
    }

    /** 算路失败后的重试：不重启定位，只复位算路闸门再试 */
    fun retryCalc() {
        Log.i(TAG, "[$scenario] user retry calc")
        hasFailure = false
        routeReady = false
        gpsErrorText = null
        gpsTimeout = false
        resolvedNaviType = null
        gate.calcDone = false
        tryStartCalc("retry")
    }

    /** 面板切换模式：非公交重新发起实时导航算路；公交只刷新面板预览 */
    fun switchLiveMode(mode: RouteTravelMode) {
        if (mode == currentMode) return
        // 远距离防线：步行 100km / 骑行 200km 引擎不支持，直接拦截不发起算路
        val distance = haversineMeters(LatLng(originLat, originLng), LatLng(destLat, destLng))
        if (!mode.isSupportedDistance(distance)) {
            val code = if (mode == RouteTravelMode.WALK) 3003 else 30007
            Log.w(TAG, "[$scenario] switch to $mode blocked: distance=${distance.toInt()}m")
            Toast.makeText(context, friendlyCalcError(code, mode), Toast.LENGTH_LONG).show()
            return
        }
        Log.i(TAG, "[$scenario] switch live mode $currentMode -> $mode")
        currentMode = mode
        if (mode == RouteTravelMode.BUS) return
        runCatching { aMapNavi.stopNavi() }
        routeReady = false
        hasFailure = false
        arrived = false
        resolvedNaviType = null
        gate.calcDone = false
        statusText = "正在切换到${mode.label}导航…"
        tryStartCalc("modeChange")
    }

    // 连续外挂定位：仅 REAL_GPS 需要（进入即启动）；SIMULATION 全程不启动。
    // 实际 start 逻辑收敛到 [startGpsFeed]，DisposableEffect 负责权限/视图变化时的重启与退出 stop。
    fun startGpsFeed() {
        gpsFeed.start(
            aMapNavi = aMapNavi,
            onLocated = { lat, lng, bearing ->
                latestGpsPosition = LatLng(lat, lng)
                gpsLocated = true
                gpsErrorText = null
                gpsTimeout = false
                if (!gate.gpsReady) {
                    gate.gpsReady = true
                    Log.i(TAG, "first extra-GPS fix: ($lat,$lng) bearing=$bearing, try calc")
                    tryStartCalc("firstGpsFix")
                }
            },
            onError = { code ->
                Log.w(TAG, "extra-GPS locate error code=$code, located=$gpsLocated")
                if (!gpsLocated) {
                    gpsErrorText = "定位失败（错误码 $code），请确认系统定位已开启并到室外空旷处重试"
                }
            },
        )
    }

    DisposableEffect(aMapNavi, hasLocationPermission, gpsRetryTick, viewReady, feedRequested) {
        if (feedRequested && hasLocationPermission && viewReady) {
            startGpsFeed()
        }
        onDispose { gpsFeed.stop() }
    }

    // 首帧定位等待超时（仅 REAL_GPS）：超时【不隐式降级】为模拟导航，直接友好报错并拦截，
    // 由用户检查定位开关 / 到室外后点重试（地图页进入前已有权限 + 系统开关前置校验弹窗）。
    LaunchedEffect(hasLocationPermission, gpsRetryTick, viewReady) {
        if (gpsRequired && hasLocationPermission && viewReady) {
            gpsTimeout = false
            delay(GPS_WAIT_TIMEOUT_MS)
            if (!gpsLocated) {
                gpsTimeout = true
                Log.w(TAG, "wait first extra-GPS fix timeout ${GPS_WAIT_TIMEOUT_MS}ms, block REAL_GPS")
                val msg = "无法获取当前位置，请确认系统定位已开启并到室外开阔处后重试"
                gpsErrorText = msg
                statusText = msg
                hasFailure = true
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ================= 导航模式自动判定（算路成功后执行，严格在 startNavi 之前） =================

    /** 启动模拟导航：小车自动沿路线行驶；高德原生视图自动显示「暂停 / 低中高速」控件 */
    fun beginEmulatorNavi(reason: String, tip: String? = null) {
        Log.i(TAG, "[$scenario] resolved navi type=EMULATOR(2), reason=$reason")
        resolvedNaviType = NaviType.EMULATOR
        // 模拟导航绝不允许外挂 GPS 数据干扰：同步停连续定位并关闭外挂开关（状态 effect 稍后幂等再停一次）
        if (feedRequested) feedRequested = false
        runCatching { gpsFeed.stop() }
        runCatching { aMapNavi.setIsUseExtraGPSData(false) }
            .onFailure { Log.w(TAG, "setIsUseExtraGPSData(false) failed", it) }
        tip?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        runCatching { aMapNavi.setEmulatorNaviSpeed(EMULATOR_MEDIUM_SPEED) }
            .onFailure { Log.w(TAG, "setEmulatorNaviSpeed failed", it) }
        statusText = "路线规划完成，正在开始模拟导航…"
        cancelStartWatchdog()
        armStartWatchdog()
        runCatching { aMapNavi.startNavi(NaviType.EMULATOR) }
            .onFailure {
                cancelStartWatchdog()
                Log.e(TAG, "[$scenario] startEmulatorNavi invoke failed", it)
                Toast.makeText(context, "模拟导航启动异常，请重试", Toast.LENGTH_LONG).show()
                statusText = "导航启动异常，请重试"
                hasFailure = true
            }
    }

    /** 启动实时导航：读取真实 GPS，车辆跟随手机位置移动、支持偏航重算 */
    fun beginGpsNavi(reason: String) {
        Log.i(TAG, "[$scenario] resolved navi type=GPS(1), reason=$reason")
        resolvedNaviType = NaviType.GPS
        // 必须在 startNavi(GPS) 之前同步启用连续外挂 GPS（不能等重组后的 DisposableEffect）
        feedRequested = true
        startGpsFeed()
        statusText = "路线规划完成，正在开始实时导航…"
        cancelStartWatchdog()
        armStartWatchdog()
        runCatching { aMapNavi.startNavi(NaviType.GPS) }
            .onFailure {
                // 显式 REAL_GPS 分支：启动异常只报错拦截，不隐式切换为模拟导航
                cancelStartWatchdog()
                Log.e(TAG, "[$scenario] startGPSNavi invoke failed", it)
                Toast.makeText(context, "实时导航启动异常，请返回后重试", Toast.LENGTH_LONG).show()
                statusText = "实时导航启动异常，请重试"
                hasFailure = true
            }
    }

    /**
     * 实时导航启动前的最终复核（地图页已弹窗拦截，这里兜底防止设置在进页后被关闭）：
     * 权限缺失 / 系统定位开关关闭 → 友好报错并【禁止进入导航】，不降级、不模拟。
     */
    fun blockRealGps(message: String, reason: String) {
        Log.w(TAG, "[$scenario] REAL_GPS blocked: $reason")
        cancelStartWatchdog()
        statusText = message
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        hasFailure = true
    }

    /**
     * 算路成功后的唯一导航启动入口：严格按显式 [effectiveNaviMode] 分支执行，禁止隐式判定：
     * - [NaviMode.SIMULATION]（条件A 自定义起终点 / 步骤预览）→ startEmulatorNavi()，
     *   全程不读取真实 GPS；
     * - [NaviMode.REAL_GPS]（条件B 从我的位置出发）→ 复核精确定位权限与系统定位开关后
     *   startGPSNavi()；复核失败仅报错拦截，绝不隐式降级到模拟导航。
     */
    fun resolveNaviTypeAndStart() {
        when (effectiveNaviMode) {
            NaviMode.SIMULATION ->
                beginEmulatorNavi("explicit naviMode=SIMULATION (custom start+end)")

            NaviMode.REAL_GPS -> when {
                !hasLocationPermission -> blockRealGps(
                    message = "未授予精确定位权限，无法开始实时导航，请返回授权后重试",
                    reason = "denied: no ACCESS_FINE_LOCATION permission",
                )

                !isSystemLocationEnabled(context) -> blockRealGps(
                    message = "手机定位开关未打开，无法开始实时导航，请打开系统定位后重试",
                    reason = "denied: system location switch off",
                )

                else -> beginGpsNavi("explicit naviMode=REAL_GPS (my location → end)")
            }
        }
    }

    // 导航事件监听：界面状态只关心初始化 / 算路 / 到达 / 失败
    val naviListener = remember(aMapNavi, currentMode) {
        object : AMapNaviListener {
            override fun onInitNaviFailure() {
                Log.e(TAG, "[$scenario] AMapNavi init failure")
                cancelInitWatchdog()
                cancelStartWatchdog()
                Toast.makeText(context, "导航初始化失败，请返回后重试", Toast.LENGTH_LONG).show()
                statusText = "导航初始化失败"
                hasFailure = true
            }

            override fun onInitNaviSuccess() {
                cancelInitWatchdog()
                gate.initReady = true
                statusText = if (!gpsRequired || gpsLocated) {
                    "正在规划${currentMode.label}路线…"
                } else {
                    "导航已就绪，正在等待定位完成…"
                }
                Log.i(TAG, "[$scenario] AMapNavi init success, gpsLocated=$gpsLocated")
                tryStartCalc("initSuccess")
            }

            override fun onCalculateRouteSuccess(result: AMapCalcRouteResult) {
                if (result.errorCode == 0) {
                    // 强制时序校验：必须拿到含有效折线的 NaviPath 才允许 startNavi，
                    // 杜绝空路线拉起导航视图后卡住
                    val paths: Map<Int, AMapNaviPath> =
                        runCatching { aMapNavi.naviPaths }.getOrNull().orEmpty()
                    val validPath = paths.values.firstOrNull {
                        runCatching { it.coordList?.size ?: 0 >= 2 }.getOrDefault(false)
                    }
                    if (validPath == null) {
                        Log.e(
                            TAG,
                            "[$scenario] calc success but no valid NaviPath " +
                                "(paths=${paths.size}), abort startNavi",
                        )
                        cancelStartWatchdog()
                        Toast
                            .makeText(context, "路线数据为空，请更换起终点后重试", Toast.LENGTH_LONG)
                            .show()
                        statusText = "路线数据为空，请重试"
                        hasFailure = true
                        return
                    }
                    routeReady = true
                    // 选线页选中的路线：在开始导航前切换到该路线（id 失效则忽略，用引擎默认推荐）
                    if (preferredRouteId >= 0) {
                        runCatching { aMapNavi.selectRouteId(preferredRouteId) }
                            .onSuccess {
                                Log.i(TAG, "[$scenario] selectRouteId=$preferredRouteId")
                            }
                            .onFailure {
                                Log.w(TAG, "[$scenario] selectRouteId=$preferredRouteId failed", it)
                            }
                    }
                    Log.i(
                        TAG,
                        "[$scenario] calc route success(paths=${paths.size}, " +
                            "coords=${validPath.coordList?.size ?: 0}), resolve navi type before startNavi",
                    )
                    // 强制时序：算路成功 → 按显式 naviMode 分支 → 对应 startNavi
                    // （SIMULATION→startEmulatorNavi / REAL_GPS→startGPSNavi）。
                    // 启动前先取消算路看门狗，由 begin* 重新计时
                    cancelStartWatchdog()
                    resolveNaviTypeAndStart()
                } else {
                    Log.e(TAG, "calc route error code=${result.errorCode} desc=${result.errorDescription}")
                    cancelStartWatchdog()
                    val message = friendlyCalcError(result.errorCode, currentMode)
                    statusText = message
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    hasFailure = true
                }
            }

            override fun onCalculateRouteFailure(result: AMapCalcRouteResult) {
                Log.e(TAG, "calc route failure code=${result.errorCode} desc=${result.errorDescription}")
                cancelStartWatchdog()
                val message = friendlyCalcError(result.errorCode, currentMode)
                statusText = message
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                hasFailure = true
            }

            override fun onArriveDestination() {
                arrived = true
                statusText = "已到达目的地"
            }

            override fun onStartNavi(type: Int) {
                // 导航真正启动：取消加载看门狗，加载遮罩消失
                cancelStartWatchdog()
                resolvedNaviType = type
                val isEmulator = type == NaviType.EMULATOR
                Log.i(TAG, "[$scenario] navi started, type=$type (1=GPS 2=EMULATOR)")
                statusText = when {
                    arrived -> "已到达目的地"
                    isEmulator -> "模拟导航中 · 小车自动行驶 · 前往 $destName"
                    else -> "实时导航中 · 跟随当前位置 · 前往 $destName"
                }
            }

            override fun onEndEmulatorNavi() {
                arrived = true
                statusText = "模拟导航结束，已到达目的地"
            }

            // ---- 其余回调本页不使用，空实现即可（接口要求全部实现） ----
            override fun onTrafficStatusUpdate() = Unit
            override fun onLocationChange(location: AMapNaviLocation?) = Unit
            override fun onGetNavigationText(type: Int, text: String?) = Unit
            override fun onGetNavigationText(text: String?) = Unit
            override fun onCalculateRouteFailure(errorCode: Int) {
                // 部分 SDK 版本只回调旧版 int 错误码重载，同样按失败处理，避免无限等待
                Log.e(TAG, "calc route failure(legacy) code=$errorCode")
                cancelStartWatchdog()
                val message = friendlyCalcError(errorCode, currentMode)
                statusText = message
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                hasFailure = true
            }
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
            override fun onGpsSignalWeak(weak: Boolean) {
                if (weak) Log.w(TAG, "[$scenario] navi GPS signal weak")
            }
        }
    }

    // 视图生命周期 + 监听注册：注册时若已处于 RESUMED，立即补一次 onResume（黑屏关键修复）
    DisposableEffect(lifecycleOwner, aMapNavi, naviView, naviListener) {
        val registerOk = runCatching {
            aMapNavi.setUseInnerVoice(true)
            aMapNavi.addAMapNaviListener(naviListener)
        }.isSuccess
        if (!registerOk) {
            Log.e(TAG, "[$scenario] addAMapNaviListener / setUseInnerVoice failed")
            Toast.makeText(context, "导航初始化失败，请返回后重试", Toast.LENGTH_LONG).show()
            statusText = "导航初始化失败"
            hasFailure = true
        } else {
            // 注册成功：10s 内必须收到 init 成功/失败回调，否则进入失败态（防无限加载）
            armInitWatchdog()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    runCatching { naviView.onResume() }
                        .onFailure { Log.e(TAG, "[$scenario] NaviView onResume failed", it) }
                    if (!viewReady) {
                        viewReady = true
                        Log.i(TAG, "[$scenario] navi view ready(onResume), gates can proceed")
                        tryStartCalc("viewReady")
                    }
                }
                Lifecycle.Event.ON_PAUSE -> runCatching { naviView.onPause() }
                Lifecycle.Event.ON_DESTROY -> releaseNaviView(naviView, naviViewReleased)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // 观察者注册可能晚于 ON_RESUME（Compose 首帧时页面早已 RESUMED），立即补 onResume
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runCatching { naviView.onResume() }
            if (!viewReady) {
                viewReady = true
                Log.i(TAG, "[$scenario] navi view ready(current RESUMED), gates can proceed")
                tryStartCalc("viewReadyImmediate")
            }
        }
        onDispose {
            Log.i(TAG, "[$scenario] navi screen dispose: release engine + view")
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 取消全部看门狗，避免页面退出后仍弹 Toast / 触发回退
            cancelInitWatchdog()
            cancelStartWatchdog()
            runCatching {
                aMapNavi.stopNavi()
                aMapNavi.removeAMapNaviListener(naviListener)
            }
            // 导航视图随组合销毁一并释放（路由返回时 Activity 不销毁，必须主动释放防黑屏）
            releaseNaviView(naviView, naviViewReleased)
            // 统一走全局管理器销毁单例，选线页返回后会懒创建新实例并重算
            AmapNaviManager.destroyNavi()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { naviView },
        )

        // ---------- 顶部状态 / 操作条 ----------
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "退出导航",
                    modifier = Modifier
                        .size(36.dp)
                        .clickable {
                            runCatching { aMapNavi.stopNavi() }
                            onBack()
                        }
                        .padding(6.dp),
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "前往 $destName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                // 路线步骤：两种场景都可打开，面板内独立查路并展示步骤 / 全览地图
                OutlinedButton(
                    onClick = {
                        Log.i(TAG, "[$scenario] open route steps sheet")
                        showStepsSheet = true
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("路线步骤", style = MaterialTheme.typography.labelMedium)
                }
                // 【模拟导航】仅模拟模式会话显示：实时导航（GPS）页面不出现任何模拟导航控件；
                // 高德原生视图同理：GPS 模式不渲染暂停/速度按钮，EMULATOR 模式自动渲染（暂停、中速等）
                if (routeReady && !arrived && resolvedNaviType == NaviType.EMULATOR) {
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { beginEmulatorNavi("top bar manual switch") },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("模拟导航", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // ---------- 算路 / 失败居中提示（定位未就绪时不会进入算路，此处仅显示等待） ----------
        if (!routeReady || hasFailure) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (hasFailure) {
                        Text("⚠️", style = MaterialTheme.typography.headlineMedium)
                    } else {
                        CircularProgressIndicator()
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (hasFailure) {
                        Spacer(Modifier.height(12.dp))
                        Row {
                            OutlinedButton(onClick = onBack) { Text("返回地图") }
                            Spacer(Modifier.width(10.dp))
                            Button(onClick = { retryCalc() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("重试")
                            }
                        }
                    }
                }
            }
        }

        // ---------- 底部状态条：仅场景A 的定位权限 / 等待首帧 / 定位失败 ----------
        if (!hasFailure && gpsRequired) {
            val bannerText = when {
                !hasLocationPermission ->
                    "未授予定位权限，实时导航无法跟随你的位置（路线预览不受影响）"
                gpsErrorText != null -> gpsErrorText
                !gpsLocated && gpsTimeout ->
                    "定位超时，请到室外空旷处后点「重新定位」；定位完成前不会发起算路"
                !gpsLocated ->
                    "正在等待定位完成…定位成功后自动开始算路，请勿离开本页"
                else -> null
            }
            if (bannerText != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.97f),
                    tonalElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = bannerText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            if (!hasLocationPermission) {
                                permissionLauncher.launch(navigationLocationPermissions())
                            } else {
                                gpsTimeout = false
                                gpsErrorText = null
                                gate.gpsReady = false
                                gpsRetryTick++
                                Log.i(TAG, "user retry extra-GPS locate")
                            }
                        }) {
                            Text(
                                if (hasLocationPermission) "重新定位" else "授权定位",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---------- 路线步骤底部面板：全览地图 + 步骤列表 + 四模式切换 ----------
    if (showStepsSheet) {
        RouteStepsSheet(
            initialMode = if (currentMode == RouteTravelMode.BUS) RouteTravelMode.DRIVE else currentMode,
            origin = LatLng(originLat, originLng),
            destination = LatLng(destLat, destLng),
            cityName = cityName,
            onModeChanged = { mode -> switchLiveMode(mode) },
            onDismiss = { showStepsSheet = false },
        )
    }
}

/** 算路闸门：视图就绪 + 内核初始化 +（场景A）首帧外挂定位，每次模式切换只发起一次算路 */
private class NaviStartGate {
    var initReady: Boolean = false
    var gpsReady: Boolean = false
    var calcDone: Boolean = false
}

/* ============================== 路线步骤底部面板 ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteStepsSheet(
    initialMode: RouteTravelMode,
    origin: LatLng,
    destination: LatLng,
    cityName: String,
    onModeChanged: (RouteTravelMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val planner = remember { AmapRoutePlanner(context.applicationContext) }
    var mode by remember { mutableStateOf(initialMode) }

    // 面板内独立小地图（TextureMapView 纹理载体，与导航 NaviView 互不干扰、不黑屏）
    val overviewMap = remember {
        runCatching { TextureMapView(context).apply { onCreate(Bundle()) } }
            .onFailure { Log.e(TAG, "[steps-sheet] TextureMapView create failed", it) }
            .getOrNull()
    }

    // 进入面板与每次切换模式都重新查路（RouteSearch，四模式均支持）
    LaunchedEffect(mode) {
        Log.i(TAG, "steps sheet planRoute mode=$mode origin=$origin dest=$destination")
        planner.planRoute(mode, origin, destination, cityName)
    }

    // 小地图生命周期跟随面板；onPause/onDestroy 幂等
    val overviewReleased = remember { booleanArrayOf(false) }
    DisposableEffect(lifecycleOwner, overviewMap) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { overviewMap?.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { overviewMap?.onPause() }
                Lifecycle.Event.ON_DESTROY -> releaseOverviewMap(overviewMap, overviewReleased)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runCatching { overviewMap?.onResume() }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releaseOverviewMap(overviewMap, overviewReleased)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "路线步骤",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            // 模式切换：切换即重新查路刷新地图与步骤，并同步实时导航模式（公交仅预览）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RouteTravelMode.entries.forEach { item ->
                    // 步行 100km / 骑行 200km 超距 Chip 置灰（公交走搜索 SDK，不受引擎距离限制）
                    val supported = item == RouteTravelMode.BUS ||
                        item.isSupportedDistance(haversineMeters(origin, destination))
                    FilterChip(
                        selected = mode == item,
                        enabled = supported,
                        onClick = {
                            if (!supported) {
                                val limitKm = if (item == RouteTravelMode.WALK) 100 else 200
                                Toast
                                    .makeText(
                                        context,
                                        "${item.label}距离过远（规划上限约${limitKm}公里），请选择驾车",
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                                return@FilterChip
                            }
                            if (mode != item) {
                                mode = item
                                onModeChanged(item)
                            }
                        },
                        label = { Text(item.label) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 全览小地图：完整绘制起点→终点路线并自动缩放到整条路线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFFE9EEF2), RoundedCornerShape(12.dp)),
            ) {
                if (overviewMap != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { overviewMap },
                        update = { mapView ->
                            val state = planner.state
                            val route = (state as? RoutePlanState.Success)?.route
                            val aMap = mapView.map
                            aMap.uiSettings.isZoomControlsEnabled = false
                            aMap.uiSettings.isMyLocationButtonEnabled = false
                            aMap.clear()
                            // 起终点标记（A 绿 / B 红）
                            aMap.addMarker(
                                MarkerOptions()
                                    .position(origin)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                                    .anchor(0.5f, 1f)
                                    .zIndex(30f),
                            )
                            aMap.addMarker(
                                MarkerOptions()
                                    .position(destination)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                    .anchor(0.5f, 1f)
                                    .zIndex(30f),
                            )
                            when {
                                // 有折线：画整条路线并完整缩放到起终点
                                route != null && route.points.size >= 2 -> {
                                    aMap.addPolyline(
                                        PolylineOptions()
                                            .addAll(route.points)
                                            .width(18f)
                                            .color(0xFF2B7FFF.toInt())
                                            .zIndex(20f),
                                    )
                                    runCatching {
                                        val bounds = LatLngBounds.builder()
                                            .include(origin)
                                            .include(destination)
                                            .apply { route.points.forEach { include(it) } }
                                            .build()
                                        aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60))
                                    }
                                }
                                // 公交无折线等：仅框选起终点
                                else -> {
                                    runCatching {
                                        aMap.animateCamera(
                                            CameraUpdateFactory.newLatLngBounds(
                                                LatLngBounds.builder().include(origin).include(destination).build(),
                                                120,
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                    )
                } else {
                    Text(
                        text = "当前设备不支持地图全览",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 状态 / 概要 / 步骤列表
            when (val state = planner.state) {
                RoutePlanState.Idle, RoutePlanState.Loading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在获取${mode.label}路线…", style = MaterialTheme.typography.bodyMedium)
                }

                is RoutePlanState.Failed -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp),
                )

                is RoutePlanState.Success -> RouteStepsContent(route = state.route)
            }
        }
    }
}

@Composable
private fun RouteStepsContent(route: RouteInfo) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${route.distanceText} · 约${route.durationText} · 共 ${route.steps.size} 步",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        if (route.steps.isEmpty()) {
            Text(
                text = "暂无详细步骤数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                items(route.steps, key = { it.index }) { step ->
                    RouteStepRow(step)
                }
            }
        }
    }
}

@Composable
private fun RouteStepRow(step: RouteStepInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (step.isTransit) Color(0xFF2B7FFF) else Color(0xFF1FA86B),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${step.index}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.instruction,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val sub = listOfNotNull(step.road, step.distanceText.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 不支持架构设备上的导航降级面板（导航页 / 选线页共用） */
@Composable
internal fun NaviUnsupportedPanel(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFECEFDD)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("📱", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("当前设备不支持高德导航", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "导航 SDK 仅支持 arm 架构，请在 ARM 真机上使用实时导航",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onBack) { Text("返回地图") }
            }
        }
    }
}

/** 起终点参数异常（0,0 等）时的降级面板，避免算路失败后界面停在默认北京视角。 */
@Composable
private fun NaviInvalidRoutePanel(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Toast.makeText(context, "起终点坐标无效，请返回重新选择", Toast.LENGTH_LONG).show()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFECEFDD)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⚠️", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("路线起点或终点信息异常", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "未能获取到正确的起终点坐标，请返回地图重新选择起点和终点后再开始导航",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onBack) { Text("返回地图") }
            }
        }
    }
}

/**
 * 场景A 专用外挂 GPS 数据源（场景B 两景点导航禁止实例化启动）：
 * 高德融合定位（高精度、连续 1s 间隔），每帧把 GCJ-02 坐标（含 bearing 方位角）通过
 * AMapNavi.setExtraGPSData(type=2, location) 喂入导航内核，驱动定位跟随与车头朝向。
 */
private class NaviLocationFeed(context: Context) {

    private val client: AMapLocationClient? = runCatching {
        AMapLocationClient(context.applicationContext)
    }.getOrNull()

    private var aMapNavi: AMapNavi? = null
    private var onLocated: ((Double, Double, Float) -> Unit)? = null
    private var onError: ((Int) -> Unit)? = null

    private val locationListener = AMapLocationListener { location: AMapLocation? ->
        if (location == null) return@AMapLocationListener
        if (location.errorCode == 0) {
            val lat = location.latitude
            val lng = location.longitude
            Log.d(
                TAG,
                "extra-GPS fix: ($lat,$lng) acc=${location.accuracy} " +
                    "speed=${location.speed} bearing=${location.bearing}",
            )
            // 高德坐标（type=2）；speed/accuracy/bearing/time 由 AMapLocation 自带，
            // bearing 即设备移动方位角，导航车头箭头据此跟随
            runCatching { aMapNavi?.setExtraGPSData(2, location) }
                .onFailure { Log.e(TAG, "setExtraGPSData failed", it) }
            onLocated?.invoke(lat, lng, location.bearing)
        } else {
            Log.w(TAG, "extra-GPS locate failure code=${location.errorCode}")
            onError?.invoke(location.errorCode)
        }
    }

    init {
        client?.let { c ->
            val option = AMapLocationClientOption().apply {
                // 高精度模式（高德枚举拼写即为 Hight_Accuracy）
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                // 连续定位；导航 SDK 建议外挂 GPS 约 1 秒一次
                isOnceLocation = false
                setInterval(1000)
                isNeedAddress = false
                isMockEnable = true
            }
            c.setLocationOption(option)
            c.setLocationListener(locationListener)
        }
    }

    /** 绑定导航实例并开启外挂定位；必须在 NaviView onResume 之后、算路之前调用。 */
    fun start(
        aMapNavi: AMapNavi,
        onLocated: (lat: Double, lng: Double, bearing: Float) -> Unit,
        onError: (Int) -> Unit,
    ) {
        if (client == null) {
            Log.e(TAG, "NaviLocationFeed start: AMapLocationClient is null")
            onError(-1)
            return
        }
        this.aMapNavi = aMapNavi
        this.onLocated = onLocated
        this.onError = onError
        // 开关先打开，setExtraGPSData 才会生效；此时 NaviView 已就绪
        runCatching { aMapNavi.setIsUseExtraGPSData(true) }
        Log.i(TAG, "NaviLocationFeed start: high-accuracy 1s, extra GPS enabled")
        // stop() 会注销监听，重试或生命周期恢复时必须重新注册。
        client.setLocationListener(locationListener)
        if (!client.isStarted) client.startLocation()
    }

    /** 页面退出 / 重试重启：停止定位并注销监听，避免泄漏；导航实例由 NaviScreen 统一 destroy。 */
    fun stop() {
        Log.i(TAG, "NaviLocationFeed stop")
        runCatching {
            client?.stopLocation()
            client?.unRegisterLocationListener(locationListener)
        }
        onLocated = null
        onError = null
        aMapNavi = null
    }
}

/**
 * 单次定位器：场景B（自定义起点）算路成功后获取一次手机当前位置，
 * 与算路起点做 50m 容差比对以自动选择实时/模拟导航。
 *
 * - 高精度单次定位，网络超时 6s；另加 8s 总超时兜底，超时按失败处理 → 强制模拟导航；
 * - 不向导航内核喂 GPS（区别于 [NaviLocationFeed]），实时模式判定后才由连续 feed 接管。
 */
private class NaviSingleFixLocator(context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val client: AMapLocationClient? = runCatching {
        AMapLocationClient(context.applicationContext)
    }.onFailure {
        Log.e(TAG, "[single-fix] AMapLocationClient create failed", it)
    }.getOrNull()

    private var onFix: ((Double, Double) -> Unit)? = null
    private var onFail: ((Int) -> Unit)? = null

    @Volatile
    private var finished = false

    private val timeoutRunnable = Runnable {
        if (!finished) {
            Log.w(TAG, "[single-fix] locate timeout ${SINGLE_FIX_TIMEOUT_MS}ms, fallback emulator")
            completeFail(-8)
        }
    }

    private val listener = AMapLocationListener { location: AMapLocation? ->
        if (finished) return@AMapLocationListener
        if (location != null && location.errorCode == 0) {
            Log.d(
                TAG,
                "[single-fix] fix ok (${location.latitude},${location.longitude}) acc=${location.accuracy}",
            )
            finished = true
            mainHandler.removeCallbacks(timeoutRunnable)
            runCatching { client?.stopLocation() }
            val cb = onFix
            onFix = null
            onFail = null
            cb?.invoke(location.latitude, location.longitude)
        } else {
            Log.w(TAG, "[single-fix] locate failure code=${location?.errorCode ?: -1}")
            completeFail(location?.errorCode ?: -1)
        }
    }

    init {
        client?.let { c ->
            runCatching {
                val option = AMapLocationClientOption().apply {
                    // 高精度（高德枚举拼写即为 Hight_Accuracy）
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    isOnceLocation = true
                    isNeedAddress = false
                    isMockEnable = true
                    setHttpTimeOut(6_000L)
                }
                c.setLocationOption(option)
                c.setLocationListener(listener)
            }.onFailure { Log.e(TAG, "[single-fix] option init failed", it) }
        }
    }

    /** 发起一次定位；成功回调经纬度，失败/超时回调错误码（全部主线程）。 */
    fun request(onFix: (Double, Double) -> Unit, onFail: (Int) -> Unit) {
        val c = client
        if (c == null) {
            onFail(-1)
            return
        }
        this.onFix = onFix
        this.onFail = onFail
        this.finished = false
        mainHandler.removeCallbacks(timeoutRunnable)
        runCatching {
            if (!c.isStarted) c.startLocation()
            mainHandler.postDelayed(timeoutRunnable, SINGLE_FIX_TIMEOUT_MS)
            Log.i(TAG, "[single-fix] request started, timeout=${SINGLE_FIX_TIMEOUT_MS}ms")
        }.onFailure {
            Log.e(TAG, "[single-fix] startLocation failed", it)
            completeFail(-2)
        }
    }

    private fun completeFail(code: Int) {
        if (finished && code != -8) return
        finished = true
        mainHandler.removeCallbacks(timeoutRunnable)
        val c = client
        runCatching { c?.stopLocation() }
        val cb = onFail
        onFix = null
        onFail = null
        cb?.invoke(code)
    }

    /** 页面销毁：移除超时回调、停止定位并销毁 client，防泄漏。 */
    fun destroy() {
        Log.i(TAG, "[single-fix] destroy")
        finished = true
        mainHandler.removeCallbacks(timeoutRunnable)
        runCatching {
            client?.stopLocation()
            client?.unRegisterLocationListener(listener)
            client?.onDestroy()
        }
        onFix = null
        onFail = null
    }
}

/**
 * 系统定位开关是否打开（GPS 或网络定位任一开启即可）。
 * 实时导航前置校验：关闭时禁止进入实时导航（地图页弹窗拦截，导航页复核拦截，不隐式降级）。
 */
internal fun isSystemLocationEnabled(context: Context): Boolean = runCatching {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@runCatching false
    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}.getOrDefault(false)
