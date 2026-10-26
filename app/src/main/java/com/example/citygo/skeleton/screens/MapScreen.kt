package com.example.citygo.skeleton.screens

import com.example.citygo.skeleton.screens.amap.hasPreciseLocationPermission
import com.example.citygo.skeleton.screens.amap.navigationLocationPermissions
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.model.LatLng
import com.example.citygo.skeleton.data.AppViewModel
import com.example.citygo.skeleton.data.model.Place
import com.example.citygo.skeleton.data.model.PlaceType
import com.example.citygo.skeleton.screens.amap.AmapLocator
import com.example.citygo.skeleton.screens.amap.AmapMapBox
import com.example.citygo.skeleton.screens.amap.AmapMapState
import com.example.citygo.skeleton.screens.amap.AmapRoutePlanner
import com.example.citygo.skeleton.screens.amap.CameraTarget
import com.example.citygo.skeleton.screens.amap.RoutePlanState
import com.example.citygo.skeleton.screens.amap.RouteTravelMode
import com.example.citygo.skeleton.screens.amap.NaviMode
import com.example.citygo.skeleton.screens.amap.isArmAbiSupported
import kotlinx.coroutines.launch

/* =====================================================================================
 * 地图 Tab（高德 3D 地图 + 定位 + 四类 Marker + 路线规划 + 内嵌导航入口）
 *
 * 能力：
 * - 行程全部点位（景点/酒店/餐厅/探店）用不同类型图标渲染，Marker 点击弹信息卡（气泡）；
 * - 左上角「行程天数」下拉：切换天数清除旧 Marker 只渲染当日点位，相机自动框选当日全部点；
 * - 两点路线：先选起点（长按 Marker 或卡片按钮）再选终点，地图画路线，面板支持
 *   驾车 / 公交 / 骑行 / 步行切换，实时重绘并展示总距离、预估耗时；
 * - 当前位置路线：信息卡「从我的位置出发导航」，以设备 GPS 为起点（无定位权限禁用）；
 * - 「开始导航」拉起 App 内嵌高德原生导航组件（NaviScreen），不跳转外部高德 App，
 *   导航页自动开启高德 SDK 语音播报。
 * - x86 模拟器无高德 arm native 库时自动降级为提示面板，App 不崩溃。
 * ===================================================================================== */

/** 真机导航链路日志 Tag（adb logcat -s CityGoNavi） */
private const val TAG = "CityGoNavi"

/** 默认定位：成都春熙路附近，无行程数据时相机恢复到此位置 */
private const val DEFAULT_LAT = 30.6600
private const val DEFAULT_LNG = 104.0700
private const val DEFAULT_ZOOM = 13.0f

/** 计划页条目联动聚焦到点位时的地图缩放级别 */
private const val FOCUS_ZOOM = 15.5f

/** 交通方式对应图标（与景点路线页保持一致） */
private fun RouteTravelMode.icon(): ImageVector = when (this) {
    RouteTravelMode.DRIVE -> Icons.Filled.DirectionsCar
    RouteTravelMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    RouteTravelMode.RIDE -> Icons.Filled.DirectionsBike
    RouteTravelMode.BUS -> Icons.Filled.DirectionsBus
}

/**
 * 地图页入口。
 *
 * @param onStartNavigation 发起 App 内嵌高德导航，参数：交通方式、起点经纬度、终点经纬度、
 *   终点名称、显式导航模式（[NaviMode.SIMULATION]=条件A 模拟导航 /
 *   [NaviMode.REAL_GPS]=条件B 真实 GPS 导航）、终点城市名
 */
@Composable
fun MapScreen(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    onStartNavigation: (
        mode: RouteTravelMode,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        destName: String,
        naviMode: NaviMode,
        city: String,
    ) -> Unit,
    /** 点击 Marker 信息卡「在行程卡片中查看」：切到出行计划 Tab 并定位到该点位卡片 */
    onViewInPlan: (Place) -> Unit,
) {
    val context = LocalContext.current
    // 行程点位 / 天数 / 城市均来自全局共享的 TripPlan（与出行计划页同一数据源，不会不一致）
    val tripPlan by appViewModel.tripPlan.collectAsStateWithLifecycle()
    val tripPoints = tripPlan.allPlaces
    val cityName = tripPlan.city
    // 计划页点击条目后下发的待聚焦点位（一次性消费）
    val focusPlace by appViewModel.mapFocusPlace.collectAsStateWithLifecycle()

    // 高德地图 / 定位 / 路线规划三个控制器（整个页面组合期间复用）
    val mapState = remember { AmapMapState() }
    val locator = remember { AmapLocator(context) }
    val routePlanner = remember { AmapRoutePlanner(context) }
    DisposableEffect(Unit) {
        onDispose { locator.destroy() }
    }

    // 用户上次视野（Tab 切走再切回时恢复）
    var camLat by rememberSaveable { mutableStateOf(DEFAULT_LAT) }
    var camLng by rememberSaveable { mutableStateOf(DEFAULT_LNG) }
    var camZoom by rememberSaveable { mutableStateOf(DEFAULT_ZOOM) }

    // 天数过滤：null=全部天数；否则只渲染第 N 天点位
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    // 新行程生成后天数变少，重置越界的选择
    LaunchedEffect(tripPlan.totalDays) {
        if ((selectedDay ?: 1) > tripPlan.totalDays) selectedDay = null
    }
    val dayPoints = remember(tripPoints, selectedDay) {
        if (selectedDay == null) tripPoints else tripPoints.filter { it.dayIndex == selectedDay }
    }

    // Marker 选中（信息卡气泡）
    var selectedPointId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedPoint = dayPoints.firstOrNull { it.id == selectedPointId }

    // 两点路线选点：A=起点，B=终点
    var routeA by remember { mutableStateOf<Place?>(null) }
    var routeB by remember { mutableStateOf<Place?>(null) }
    // 当前位置 → 目标点路线
    var gpsTarget by remember { mutableStateOf<Place?>(null) }
    var gpsError by remember { mutableStateOf<String?>(null) }
    // 当前交通方式（两类路线共用，跨次规划保留用户选择）
    var travelMode by rememberSaveable { mutableStateOf(RouteTravelMode.DRIVE.name) }
    val mode = remember(travelMode) {
        runCatching { RouteTravelMode.valueOf(travelMode) }.getOrDefault(RouteTravelMode.DRIVE)
    }
    // 手动重试计数（失败后点「重试」触发重新规划）
    var retryTick by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 点击定位后等待首个定位结果时，定位成功要自动飞过去一次
    var pendingLocateFly by remember { mutableStateOf(false) }

    // 定位权限状态（初始按系统授权情况渲染；申请回调后即时刷新）
    var hasLocationPermission by remember {
        mutableStateOf(
            hasPreciseLocationPermission(context),
        )
    }
    // 实时导航前置校验弹窗：null=不展示；非 null 时弹出友好提示（可带「去设置」动作）
    var naviBlockDialog by remember { mutableStateOf<NaviPrecheckDialog?>(null) }
    // 点击「开始实时导航」触发了权限申请：授权回调后继续完成剩余前置校验并进入导航
    var realGpsPrecheckPending by remember { mutableStateOf(false) }
    // 权限申请回调后的续跑动作（避免局部函数前向引用，launcher 只调用该持有器）
    var realGpsPermissionContinuation by remember { mutableStateOf<(() -> Unit)?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val granted = hasPreciseLocationPermission(context)
        hasLocationPermission = granted
        if (granted) {
            mapState.setMyLocationEnabled(true)
            locator.start()
            pendingLocateFly = true
            // 权限申请由「开始实时导航」前置校验发起：继续系统定位开关复核
            if (realGpsPrecheckPending) {
                realGpsPrecheckPending = false
                realGpsPermissionContinuation?.invoke()
                realGpsPermissionContinuation = null
            }
        } else if (realGpsPrecheckPending) {
            // 条件B 前置校验失败：拒绝精确定位权限 → 友好弹窗，禁止进入实时导航
            realGpsPrecheckPending = false
            realGpsPermissionContinuation = null
            naviBlockDialog = NaviPrecheckDialog(
                title = "需要精确定位权限",
                message = "实时导航需要允许「精确位置」以读取手机定位并支持偏航重算。" +
                    "授权后可重新点击「开始实时导航」。",
                showSettings = true,
            )
        }
    }

    /**
     * 路线规划完成后真正拉起高德导航（公交 / 路线未就绪等通用拦截保留）。
     * 模拟 / 实时分支由 [naviMode] 显式决定，禁止任何隐式推断：
     * - [NaviMode.SIMULATION] → startEmulatorNavi()（条件A，自定义起终点，自动跑路线）
     * - [NaviMode.REAL_GPS]  → startGPSNavi()（条件B，我的位置→终点，真实 GPS）
     */
    fun launchRouteNavigation(naviMode: NaviMode) {
        val dest = routePlanner.destination
        when {
            mode == RouteTravelMode.BUS -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "语音导航当前支持驾车 / 步行 / 骑行，请切换方式后开始",
                    )
                }
            }

            dest == null -> {
                Log.w(TAG, "start nav aborted: destination not ready, mode=$naviMode")
                scope.launch {
                    snackbarHostState.showSnackbar("路线尚未规划完成，请稍后再试")
                }
            }

            // 条件A：自定义起点 + 自定义终点 → 模拟导航（不读 GPS）
            naviMode == NaviMode.SIMULATION -> {
                val origin = routePlanner.origin
                if (origin == null) {
                    Log.w(TAG, "start nav aborted: pair-route origin not ready")
                    scope.launch {
                        snackbarHostState.showSnackbar("起点信息缺失，请重新选择起点")
                    }
                } else {
                    Log.i(
                        TAG,
                        "branch A (custom start+end) → startEmulatorNavi: " +
                            "origin=${origin.latitude},${origin.longitude} " +
                            "dest=${dest.latitude},${dest.longitude}",
                    )
                    onStartNavigation(
                        mode,
                        origin.latitude, origin.longitude,
                        dest.latitude, dest.longitude,
                        routeB?.name ?: "目的地",
                        NaviMode.SIMULATION,
                        cityName,
                    )
                }
            }

            // 条件B：我的位置 → 终点 → 真实 GPS 导航；蓝点未就绪仅提示等待，不跳转
            else -> {
                val myLoc = mapState.myLocation()
                if (myLoc == null) {
                    Log.w(TAG, "start nav blocked: aMap.getMyLocation() is null")
                    Toast.makeText(context, "请等待定位完成", Toast.LENGTH_SHORT).show()
                } else {
                    Log.i(
                        TAG,
                        "branch B (my location → end) → startGPSNavi: " +
                            "origin=${myLoc.latitude},${myLoc.longitude} " +
                            "dest=${dest.latitude},${dest.longitude}",
                    )
                    onStartNavigation(
                        mode,
                        myLoc.latitude, myLoc.longitude,
                        dest.latitude, dest.longitude,
                        gpsTarget?.name ?: routeB?.name ?: "目的地",
                        NaviMode.REAL_GPS,
                        cityName,
                    )
                }
            }
        }
    }

    /**
     * 条件B 前置校验（顺序固定，任一失败弹窗拦截，绝不进入实时导航）：
     * ① ACCESS_FINE_LOCATION 精确定位权限 → ② 系统定位总开关已打开 → ③ 蓝点定位已就绪。
     */
    fun continueRealGpsPrecheck() {
        if (!hasLocationPermission) {
            realGpsPrecheckPending = true
            // 授权回调后重入本校验（系统开关复核 → 进入实时导航）
            realGpsPermissionContinuation = { continueRealGpsPrecheck() }
            locationPermissionLauncher.launch(navigationLocationPermissions())
            return
        }
        if (!isSystemLocationEnabled(context)) {
            naviBlockDialog = NaviPrecheckDialog(
                title = "请打开手机定位开关",
                message = "实时导航需要读取手机系统定位（GPS/网络定位）。请在系统设置中打开定位开关后，" +
                    "重新点击「开始实时导航」。",
                showSettings = true,
            )
            return
        }
        launchRouteNavigation(NaviMode.REAL_GPS)
    }

    /**
     * 起点来源决定模式：当前位置走 GPS，自定义两点走模拟。
     * 所有入口进入选线页后仍保留这一来源。
     */
    fun requestRouteNavigation() {
        when {
            routeA != null && routeB != null -> launchRouteNavigation(NaviMode.SIMULATION)
            gpsTarget != null -> continueRealGpsPrecheck()
            else -> {
                Log.w(TAG, "start nav aborted: no valid branch (pair/gps)")
                scope.launch {
                    snackbarHostState.showSnackbar("请先选择起终点，或选择终点后从我的位置出发")
                }
            }
        }
    }

    /** 关闭路线面板并清除地图路线 / 选点 */
    fun closeRoutePanel() {
        routeA = null
        routeB = null
        gpsTarget = null
        gpsError = null
        routePlanner.clear()
        mapState.clearRoute()
    }

    /** 两点路线：把某个点设为起点（再次设置同一个点为取消） */
    fun pickRouteStart(place: Place) {
        gpsTarget = null
        gpsError = null
        routeB = null
        routeA = if (routeA?.id == place.id) null else place
        routePlanner.clear()
        mapState.clearRoute()
        if (routeA != null) {
            scope.launch {
                snackbarHostState.showSnackbar("已选起点「${place.name}」，请再点一个 Marker 作为终点")
            }
        }
    }

    /** 两点路线：已有起点时把某点设为终点，自动发起规划 */
    fun pickRouteEnd(place: Place) {
        val start = routeA ?: return
        if (start.id == place.id) {
            scope.launch { snackbarHostState.showSnackbar("起点和终点不能是同一个地点") }
            return
        }
        gpsTarget = null
        gpsError = null
        routeB = place
    }

    /** 当前位置 → 指定地点：只复用地图蓝点已有定位，不新建定位 Client（无权限直接拒绝） */
    fun beginGpsRoute(place: Place) {
        if (!hasLocationPermission) {
            scope.launch {
                snackbarHostState.showSnackbar("未授予定位权限，无法获取当前位置，请先授权定位")
            }
            return
        }
        routeA = null
        routeB = null
        gpsError = null
        gpsTarget = place
        routePlanner.clear()
        mapState.clearRoute()
        mapState.setMyLocationEnabled(true)
        // 不重新发起定位：地图蓝点（AMap 内置定位源）已在持续定位，
        // 下方 LaunchedEffect 订阅蓝点坐标，一就绪即自动规划；未就绪时面板提示等待。
        val blueDot = mapState.myLocation()
        if (blueDot != null) {
            Log.i(TAG, "beginGpsRoute: reuse aMap.myLocation() $blueDot -> ${place.name}")
        } else {
            Log.w(TAG, "beginGpsRoute: aMap.myLocation() null, waiting for blue-dot locate")
        }
    }

    /** 两点预览切换为从当前位置出发，定位就绪后重新规划。 */
    fun switchPairToGpsOrigin() {
        val end = routeB ?: return
        if (!hasLocationPermission) {
            realGpsPrecheckPending = true
            realGpsPermissionContinuation = { switchPairToGpsOrigin() }
            locationPermissionLauncher.launch(navigationLocationPermissions())
            return
        }
        beginGpsRoute(end)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ---------- 顶部信息条 ----------
        MapTopBar(
            pointCount = tripPoints.size,
            onClear = {
                selectedPointId = null
                selectedDay = null
                closeRoutePanel()
                appViewModel.clearPlan()
                // 清空行程操作反馈（与计划页清空提示保持一致）
                Toast.makeText(context, "行程已清空", Toast.LENGTH_SHORT).show()
            },
        )

        // ---------- 地图主体 ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // 当日点位内容签名（id + 经纬度）作为「点位 / 天数是否变化」的 key
            val pointsKey = dayPoints.joinToString(separator = ";") { "${it.id}@${it.lat},${it.lng}" }
            val allPointsKey = tripPoints.joinToString(separator = ";") { "${it.id}@${it.lat},${it.lng}" }
            var fittedKey by rememberSaveable { mutableStateOf<String?>(null) }
            // 计划页条目联动聚焦时，抑制一次「天数切换 / 点位变化自动框选」，避免覆盖聚焦相机
            var suppressNextFit by rememberSaveable { mutableStateOf(false) }

            // ① 高德真实地图（arm 设备）/ 降级提示面板（x86 模拟器）
            if (isArmAbiSupported(context)) {
                AmapMapBox(
                    mapState = mapState,
                    modifier = Modifier.fillMaxSize(),
                    onMapReady = {
                        // 新地图实例先恢复上次视野，随后的自动框选 Effect 会按需要覆盖
                        mapState.moveTo(CameraTarget(camLat, camLng, camZoom))
                    },
                )
            } else {
                UnsupportedMapPanel()
            }

            // Marker 点击：选中地点（弹信息卡气泡）；长按：快捷选为两点路线起点
            mapState.onMarkerClick = { id -> selectedPointId = id }
            mapState.onMarkerLongClick = { id ->
                tripPoints.firstOrNull { it.id == id }?.let { pickRouteStart(it) }
            }
            mapState.onCameraChange = { target ->
                camLat = target.lat
                camLng = target.lng
                target.zoom?.let { camZoom = it }
            }

            // ② Marker 全量同步（数据变化 / 天数切换 / 选中态 / 路线选点 / 地图就绪）
            LaunchedEffect(
                mapState.mapReady,
                pointsKey,
                selectedPointId,
                routeA?.id,
                routeB?.id,
            ) {
                if (mapState.mapReady) {
                    mapState.renderPlaceMarkers(
                        points = dayPoints,
                        selectedId = selectedPointId,
                        routeStartId = routeA?.id,
                        routeEndId = routeB?.id,
                    )
                }
            }

            // ③ 天数切换 / 点位集合变化：清旧 Marker 后只渲染当日点位并自动框选当日全部点
            LaunchedEffect(allPointsKey, selectedDay, mapState.mapReady) {
                if (!mapState.mapReady) return@LaunchedEffect
                if (suppressNextFit) {
                    // 本次视野变化来自计划页条目联动，相机交给 ③-补 聚焦逻辑
                    suppressNextFit = false
                    return@LaunchedEffect
                }
                val fitKey = "${selectedDay ?: -1}#$allPointsKey"
                if (fittedKey == fitKey) return@LaunchedEffect
                fittedKey = fitKey
                selectedPointId = null
                closeRoutePanel()
                if (dayPoints.isEmpty()) {
                    mapState.animateTo(CameraTarget(DEFAULT_LAT, DEFAULT_LNG, DEFAULT_ZOOM))
                } else {
                    mapState.fitPoints(dayPoints, paddingPx = 120)
                }
            }

            // ③-补 计划页行程条目联动：解除会隐藏目标点的天数过滤，相机平移放大到该点并高亮 Marker
            LaunchedEffect(focusPlace?.id) {
                val place = focusPlace ?: return@LaunchedEffect
                if (selectedDay != null && selectedDay != place.dayIndex) {
                    // 切换天数会触发 ③ 的自动框选，抑制一次由聚焦相机接管
                    suppressNextFit = true
                    selectedDay = null
                }
                closeRoutePanel()
                selectedPointId = place.id
                if (mapState.mapReady) {
                    mapState.animateTo(CameraTarget(place.lat, place.lng, FOCUS_ZOOM))
                } else {
                    // 地图实例尚未就绪（如刚切回地图 Tab）：写入恢复视野，onMapReady 后直接落位
                    camLat = place.lat
                    camLng = place.lng
                    camZoom = FOCUS_ZOOM
                }
                appViewModel.consumeMapFocusPlace()
            }

            // ④ 两点路线：两个 Marker 都选中后按当前方式规划；切换方式 / 重试实时重绘
            LaunchedEffect(routeA?.id, routeB?.id, travelMode, retryTick, mapState.mapReady) {
                val start = routeA
                val end = routeB
                if (start != null && end != null) {
                    routePlanner.planRoute(
                        mode = mode,
                        origin = LatLng(start.lat, start.lng),
                        destination = LatLng(end.lat, end.lng),
                        city = cityName,
                    )
                }
            }

            // ⑤ 当前位置路线：只使用地图蓝点坐标（AMap.getMyLocation 等价物）。
            //    蓝点位置更新即自动规划；本页不新建任何定位 Client，独立定位器仅供定位按钮使用。
            LaunchedEffect(
                gpsTarget?.id,
                travelMode,
                mapState.myLocationLatLng,
                retryTick,
            ) {
                val target = gpsTarget ?: return@LaunchedEffect
                val loc = mapState.myLocation() ?: return@LaunchedEffect
                gpsError = null
                Log.i(
                    TAG,
                    "gps route planning: origin=${loc.latitude},${loc.longitude}" +
                        " dest=${target.lat},${target.lng} mode=$mode",
                )
                routePlanner.planRoute(
                    mode = mode,
                    origin = loc,
                    destination = LatLng(target.lat, target.lng),
                    city = cityName,
                )
            }

            // ⑥ 规划结果：画路线折线（公交只框起终点）/ 失败提示
            LaunchedEffect(routePlanner.state) {
                when (val s = routePlanner.state) {
                    is RoutePlanState.Success -> {
                        val origin = routePlanner.origin
                        val dest = routePlanner.destination
                        if (origin != null && dest != null) {
                            if (s.route.mode == RouteTravelMode.BUS || s.route.points.size < 2) {
                                // 公交：无完整折线，渲染起终点标记并框选
                                mapState.clearRoute()
                                mapState.renderRouteEndpoints(origin, dest)
                                mapState.fitEndpoints(origin, dest)
                            } else {
                                mapState.drawRoute(s.route.points)
                                mapState.renderRouteEndpoints(origin, dest)
                            }
                        }
                    }

                    is RoutePlanState.Failed ->
                        snackbarHostState.showSnackbar(s.message)

                    else -> Unit
                }
            }

            // ⑦ 定位成功后自动飞向当前位置（仅响应「点击定位」之后的一次）
            LaunchedEffect(locator.currentLocation) {
                val loc = locator.currentLocation
                if (pendingLocateFly && loc != null) {
                    mapState.animateTo(CameraTarget(loc.latitude, loc.longitude, 16f))
                    pendingLocateFly = false
                }
            }

            // ⑧ 无点位空态
            if (tripPoints.isEmpty() && !mapState.initFailed && isArmAbiSupported(context)) {
                EmptyTripHint(modifier = Modifier.align(Alignment.Center))
            }

            // ⑨ 左上角：行程天数切换下拉
            if (isArmAbiSupported(context) && tripPlan.totalDays > 0) {
                DaySelectorDropdown(
                    totalDays = tripPlan.totalDays,
                    selectedDay = selectedDay,
                    onSelect = { selectedDay = it },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 10.dp),
                )
            }

            // ⑩ 右侧控制组（仅真实地图可用时展示，避免压在降级面板上）
            if (isArmAbiSupported(context)) {
                MapControls(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    zoomIn = { mapState.zoomIn() },
                    zoomOut = { mapState.zoomOut() },
                    onLocate = {
                        if (hasLocationPermission) {
                            mapState.setMyLocationEnabled(true)
                            locator.start()
                            locator.currentLocation?.let {
                                mapState.animateTo(CameraTarget(it.latitude, it.longitude, 16f))
                            } ?: run { pendingLocateFly = true }
                        } else {
                            locationPermissionLauncher.launch(navigationLocationPermissions())
                        }
                    },
                )
            }

            // ⑪ 两点路线「等待选择终点」提示条
            if (routeA != null && routeB == null && gpsTarget == null) {
                RoutePickingHint(
                    startName = routeA?.name.orEmpty(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
                    onCancel = {
                        routeA = null
                        routePlanner.clear()
                        mapState.clearRoute()
                    },
                )
            }

            // ⑫ Snackbar（无路线面板时靠上展示，避开提示条）
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp),
            )

            // ⑬ 底部路线面板（两点都齐 / GPS 路线激活时展示，替代信息卡）
            val pairActive = routeA != null && routeB != null
            // 显式导航分支：自定义起终点=条件A 模拟导航；仅终点+我的位置=条件B 实时 GPS 导航
            val panelNaviMode = if (pairActive) NaviMode.SIMULATION else NaviMode.REAL_GPS
            if (pairActive || gpsTarget != null) {
                MapRoutePanel(
                    state = routePlanner.state,
                    mode = mode,
                    naviMode = panelNaviMode,
                    onModeChange = { travelMode = it.name },
                    originName = routeA?.name ?: "我的位置",
                    destName = routeB?.name ?: gpsTarget?.name.orEmpty(),
                    locating = gpsTarget != null &&
                        mapState.myLocation() == null &&
                        gpsError == null &&
                        routePlanner.state is RoutePlanState.Idle,
                    locateError = gpsError,
                    onRetry = {
                        gpsError = null
                        // 不重新发起定位：确认蓝点图层开启，蓝点位置就绪后 Effect 自动规划
                        if (gpsTarget != null) mapState.setMyLocationEnabled(true)
                        retryTick++
                    },
                    onClose = { closeRoutePanel() },
                    // 分支判定与实时导航前置校验统一收敛到 requestRouteNavigation()
                    onStartNavigation = { requestRouteNavigation() },
                    // 两点路线可显式切换为当前位置出发。
                    onUseMyLocation = if (pairActive) {
                        { switchPairToGpsOrigin() }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                )
            } else if (selectedPoint != null) {
                // ⑭ Marker 信息卡（气泡：名称 / 类型 / 天数 / 简介 + 路线动作）
                PointInfoCard(
                    point = selectedPoint,
                    hasLocationPermission = hasLocationPermission,
                    routeStartSelected = routeA?.id == selectedPoint.id,
                    canPickEnd = routeA != null && routeA?.id != selectedPoint.id,
                    onClose = { selectedPointId = null },
                    onRequestPermission = {
                        locationPermissionLauncher.launch(navigationLocationPermissions())
                    },
                    onGpsRoute = { beginGpsRoute(it) },
                    onPickStart = { pickRouteStart(it) },
                    onPickEnd = { pickRouteEnd(it) },
                    onViewInPlan = { onViewInPlan(selectedPoint) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                )
            }

            // ⑮ 实时导航前置校验失败弹窗（权限拒绝 / 系统定位开关关闭）：仅提示，不进入导航
            val precheckDialog = naviBlockDialog
            if (precheckDialog != null) {
                AlertDialog(
                    onDismissRequest = { naviBlockDialog = null },
                    title = { Text(precheckDialog.title) },
                    text = { Text(precheckDialog.message) },
                    confirmButton = {
                        TextButton(onClick = { naviBlockDialog = null }) {
                            Text("知道了")
                        }
                    },
                    dismissButton = if (precheckDialog.showSettings) {
                        {
                            TextButton(onClick = {
                                naviBlockDialog = null
                                // 跳转系统定位设置页（部分厂商 ROM 无该 Action 时忽略）
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }) { Text("去设置") }
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * 实时导航前置校验弹窗内容。
 * @property showSettings 是否展示「去设置」（跳系统定位 / 应用权限页）
 */
private data class NaviPrecheckDialog(
    val title: String,
    val message: String,
    val showSettings: Boolean,
)

/* ------------------------------------ 顶部条 ------------------------------------ */

@Composable
private fun MapTopBar(pointCount: Int, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "行程地图",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (pointCount > 0) "共 $pointCount 个行程点位" else "暂无行程点位",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onClear,
                enabled = pointCount > 0,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("清空行程")
            }
        }
    }
}

/* ------------------------------ 天数切换下拉 ------------------------------ */

/**
 * 左上角行程天数切换下拉：「全部天数」+「第 N 天」。
 * 切换后外层清除旧 Marker，只渲染当日点位并自动框选当日全部点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySelectorDropdown(
    totalDays: Int,
    selectedDay: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (selectedDay == null) "全部天数" else "第 $selectedDay 天"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.menuAnchor(
                MenuAnchorType.PrimaryNotEditable,
                enabled = true,
            ),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "切换行程天数",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("全部天数") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            for (day in 1..totalDays) {
                DropdownMenuItem(
                    text = { Text("第 $day 天") },
                    onClick = {
                        onSelect(day)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** 两点路线已选起点、等待终点时的顶部提示条 */
@Composable
private fun RoutePickingHint(
    startName: String,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "起点：$startName，再点一个 Marker 设为终点",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = "取消选点",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(30.dp)
                    .clickable(onClick = onCancel)
                    .padding(4.dp),
            )
        }
    }
}

/* --------------------------------- 控制组件 / 空态 / 降级 --------------------------------- */

@Composable
private fun MapControls(
    modifier: Modifier = Modifier,
    zoomIn: () -> Unit,
    zoomOut: () -> Unit,
    onLocate: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MapCircleButton(icon = Icons.Filled.MyLocation, desc = "定位", onClick = onLocate)
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable(onClick = zoomIn),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "放大地图")
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable(onClick = zoomOut),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "缩小地图")
                }
            }
        }
    }
}

@Composable
private fun MapCircleButton(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier.clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyTripHint(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "🗺️", fontSize = 32.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "暂无行程点位",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "添加行程后自动标注",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * x86 模拟器 / 无 arm native 库设备上的降级面板（高德 10.x 仅提供 arm 架构 so）。
 * 行程点位与天气等其余功能不受影响。
 */
@Composable
private fun UnsupportedMapPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFECEFDD)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "📱", fontSize = 34.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "当前设备不支持高德地图渲染",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "高德地图 SDK 10.x 仅支持 arm 架构，请在 ARM 真机上查看地图、路线与导航",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* --------------------------------- 路线面板 / 信息卡 --------------------------------- */

/**
 * 底部路线规划面板：
 * 起点名 → 终点名、四种交通方式切换（实时重绘）、总距离 / 预估耗时（公交展示换乘方案）、
 * 重试与关闭；【开始导航】拉起 App 内嵌高德导航组件并自动开启语音播报。
 */
@Composable
private fun MapRoutePanel(
    state: RoutePlanState,
    mode: RouteTravelMode,
    /** 显式导航分支：决定底部按钮文案（开始模拟导航 / 开始实时导航） */
    naviMode: NaviMode,
    onModeChange: (RouteTravelMode) -> Unit,
    originName: String,
    destName: String,
    locating: Boolean,
    locateError: String?,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onStartNavigation: () -> Unit,
    /** 把自定义起点改为当前位置，切换到真实 GPS 路线。 */
    onUseMyLocation: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 起终点名称行
            Row(verticalAlignment = Alignment.CenterVertically) {
                EndpointLabel(
                    badge = "A",
                    badgeColor = Color(0xFF1FA86B),
                    name = originName,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Route,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(20.dp),
                )
                EndpointLabel(
                    badge = "B",
                    badgeColor = Color(0xFFE5484D),
                    name = destName,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭路线面板",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(onClick = onClose)
                        .padding(2.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // 交通方式切换：驾车 / 公交 / 骑行 / 步行
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RouteTravelMode.entries.forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = { onModeChange(item) },
                        label = { Text(item.label) },
                        leadingIcon = {
                            Icon(
                                item.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 规划状态区
            when {
                locating -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在定位当前位置…", style = MaterialTheme.typography.bodyMedium)
                }

                locateError != null && state is RoutePlanState.Idle -> Column {
                    Text(
                        text = "无法获取当前位置：$locateError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重新定位")
                    }
                }

                state is RoutePlanState.Loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在规划${mode.label}路线…", style = MaterialTheme.typography.bodyMedium)
                }

                state is RoutePlanState.Failed -> Column {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重试")
                    }
                }

                state is RoutePlanState.Success -> RouteSuccessContent(state.route)

                else -> Text(
                    text = "选择交通方式后将实时展示路线、总距离与预估耗时",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            if (naviMode == NaviMode.SIMULATION) {
                Text(
                    "当前是两地点路线，可模拟演示；从当前位置出发请点击下方按钮。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }
            // 主按钮沿用起点来源；当前位置路线先检查真实定位。
            Button(
                onClick = onStartNavigation,
                enabled = state is RoutePlanState.Success,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when (mode) {
                        RouteTravelMode.BUS -> "公交暂不支持语音导航"
                        else -> naviMode.buttonText
                    },
                )
            }

            // 两点路线允许改用当前位置，重新算路后进入实时导航。
            if (onUseMyLocation != null && mode != RouteTravelMode.BUS) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onUseMyLocation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("改为从当前位置出发")
                }
            }
        }
    }
}

/** 起终点名称小标签（A 绿 / B 红圆点 + 名称） */
@Composable
private fun EndpointLabel(
    badge: String,
    badgeColor: Color,
    name: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(badgeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 规划成功后的里程耗时（公交为换乘方案列表，最多 3 条，区域内可滚动） */
@Composable
private fun RouteSuccessContent(route: com.example.citygo.skeleton.screens.amap.RouteInfo) {
    if (route.mode == RouteTravelMode.BUS) {
        Column(modifier = Modifier.heightIn(max = 132.dp).verticalScroll(rememberScrollState())) {
            Text(
                text = "为你找到 ${route.busPlans.size} 个公交换乘方案",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            route.busPlans.take(3).forEachIndexed { index, plan ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "方案${index + 1} · ${plan.durationText} · ${plan.distanceText}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = plan.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                route.mode.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "${route.distanceText} · 约${route.durationText}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${route.mode.label}路线由高德地图实时计算",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Marker 详情卡（气泡）：名称、类型、所属天数、简介，
 * 以及「从我的位置出发导航」（GPS 起点，无定位权限禁用）与两点路线选点动作。
 */
@Composable
private fun PointInfoCard(
    point: Place,
    hasLocationPermission: Boolean,
    routeStartSelected: Boolean,
    canPickEnd: Boolean,
    onClose: () -> Unit,
    onRequestPermission: () -> Unit,
    onGpsRoute: (Place) -> Unit,
    onPickStart: (Place) -> Unit,
    onPickEnd: (Place) -> Unit,
    onViewInPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(point.placeType.color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = point.placeType.emoji, fontSize = 18.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = point.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TypeBadge(placeType = point.placeType)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "第 ${point.dayIndex} 天",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onClose),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = point.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "经纬度：${point.lat}, ${point.lng}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            // 跳转「出行计划」Tab：滚动定位到该点位所在天的行程卡片并高亮
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onViewInPlan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("在行程卡片中查看")
            }

            // 当前位置 → 目标地点：无定位权限时禁用并提供授权入口
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onGpsRoute(point) },
                enabled = hasLocationPermission,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("从我的位置出发导航")
            }
            if (!hasLocationPermission) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "未授予定位权限，无法获取当前位置",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRequestPermission) {
                        Text("授权定位")
                    }
                }
            }

            // 两点路线选点
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (canPickEnd) onPickEnd(point) else onPickStart(point)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            canPickEnd -> "选为终点并规划"
                            routeStartSelected -> "取消路线起点"
                            else -> "选为路线起点"
                        },
                    )
                }
            }
        }
    }
}

/** 类型小胶囊标签 */
@Composable
private fun TypeBadge(placeType: PlaceType) {
    Surface(
        shape = RoundedCornerShape(50),
        color = placeType.color.copy(alpha = 0.15f),
    ) {
        Text(
            text = "${placeType.emoji} ${placeType.label}",
            style = MaterialTheme.typography.labelSmall,
            color = placeType.color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
