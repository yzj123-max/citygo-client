package com.example.citygo.skeleton.screens

import com.example.citygo.skeleton.screens.amap.hasPreciseLocationPermission
import com.example.citygo.skeleton.screens.amap.navigationLocationPermissions
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.example.citygo.skeleton.screens.amap.AmapKeyDiagnostics
import com.example.citygo.skeleton.screens.amap.MultiRouteState
import com.example.citygo.skeleton.screens.amap.NaviMultiRoutePlanner
import com.example.citygo.skeleton.screens.amap.NaviRouteChoice
import com.example.citygo.skeleton.screens.amap.RIDE_MAX_METERS
import com.example.citygo.skeleton.screens.amap.RouteTravelMode
import com.example.citygo.skeleton.screens.amap.WALK_MAX_METERS
import com.example.citygo.skeleton.screens.amap.formatMeters
import com.example.citygo.skeleton.screens.amap.formatSeconds
import com.example.citygo.skeleton.screens.amap.haversineMeters
import com.example.citygo.skeleton.screens.amap.isArmAbiSupported
import com.example.citygo.skeleton.screens.amap.isSupportedDistance

/* =====================================================================================
 * 多路线选择页（导航前置页）
 *
 * 不依赖 GPS、不持有 NaviView：使用导航引擎 AMapNavi 独立算路（起点可为任意景点坐标），
 * 算路成功后地图绘制全部候选路线、底部 LazyColumn 展示路线卡片：
 *  - 点击卡片主体 → 携带 routeId 进入 NaviScreen 真实导航
 *  - 点击卡片右侧【路线步骤】→ 仅进入步骤预览列表（事件不穿透到卡片）
 *
 * 场景A（起点为设备位置）进入真实导航前必须有定位权限，拒绝时提示且不允许进入。
 * ===================================================================================== */

private const val TAG = "CityGoNavi"

/** 释放选线页小地图（onPause + onDestroy），幂等；页面销毁/组合销毁只执行一次。 */
private fun releaseRouteMap(mapView: TextureMapView?, guard: BooleanArray) {
    if (guard[0]) return
    guard[0] = true
    runCatching {
        mapView?.onPause()
        mapView?.onDestroy()
    }.onFailure { Log.e(TAG, "[route-plan] map release failed", it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlanScreen(
    originLat: Double,
    originLng: Double,
    destLat: Double,
    destLng: Double,
    destName: String,
    travelMode: RouteTravelMode,
    /** true=场景A 起点为设备位置；false=场景B 两景点间导航 */
    originIsMyLocation: Boolean,
    cityName: String,
    onBack: () -> Unit,
    /** 点击卡片主体：进入实时导航，参数为选中路线 id */
    onStartNavigation: (routeId: Int) -> Unit,
    /** 点击【路线步骤】：进入步骤预览页 */
    onPreviewSteps: (routeId: Int) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scenario = if (originIsMyLocation) "A(MY_LOCATION)" else "B(SPOT)"

    // 真机鉴权诊断：包名 / Key 注入状态 / 签名 SHA-256（一次性）
    LaunchedEffect(Unit) { AmapKeyDiagnostics.logDiagnostics(context) }

    // 非 arm 架构：导航引擎不可用，直接降级
    if (!isArmAbiSupported(context)) {
        NaviUnsupportedPanel(onBack = onBack)
        return
    }

    // 起终点直线距离：高德步行上限约100km（超距报3003）、骑行上限约200km（超距报30007）
    val directDistanceMeters = remember(originLat, originLng, destLat, destLng) {
        haversineMeters(LatLng(originLat, originLng), LatLng(destLat, destLng))
    }
    // 超距时默认落到驾车（驾车不受距离限制）；公交不在引擎多路线 Tab 中
    val initialMode = remember(directDistanceMeters) {
        val requested = travelMode.takeUnless { it == RouteTravelMode.BUS } ?: RouteTravelMode.DRIVE
        requested.takeIf { it.isSupportedDistance(directDistanceMeters) } ?: RouteTravelMode.DRIVE
    }
    var mode by remember { mutableStateOf(initialMode) }
    LaunchedEffect(initialMode) {
        if (mode != initialMode) mode = initialMode
    }
    val walkSupported = directDistanceMeters <= WALK_MAX_METERS
    val rideSupported = directDistanceMeters <= RIDE_MAX_METERS

    // 独立算路器（无 NaviView、无 GPS）
    val planner = remember { NaviMultiRoutePlanner(context) }
    DisposableEffect(planner) {
        onDispose { planner.release() }
    }

    // 从导航页返回时，AMapNavi 单例可能已被 NaviScreen destroy，需要重连引擎并刷新路线
    DisposableEffect(lifecycleOwner, planner) {
        val resumeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) planner.reattachIfNeeded()
        }
        lifecycleOwner.lifecycle.addObserver(resumeObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(resumeObserver) }
    }

    val origin = remember(originLat, originLng) { LatLng(originLat, originLng) }
    val destination = remember(destLat, destLng) { LatLng(destLat, destLng) }

    // 进入页面与切换模式时重新算路
    LaunchedEffect(mode) {
        Log.i(TAG, "[$scenario] route-plan screen request mode=$mode")
        planner.plan(
            mode = mode,
            origin = origin,
            destination = destination,
            destName = destName,
            originIsMyLocation = originIsMyLocation,
            city = cityName,
        )
    }

    // ---- 定位权限：仅场景A 进入真实导航前强制；场景B 预览路线不需要定位权限 ----
    var hasLocationPermission by remember {
        mutableStateOf(
            !originIsMyLocation ||
                hasPreciseLocationPermission(context),
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val granted = hasPreciseLocationPermission(context)
        hasLocationPermission = granted
        Log.i(TAG, "[$scenario] route-plan location permission granted=$granted")
        if (!granted) {
            Toast.makeText(context, "未授予定位权限，无法进入实时导航（可查看路线步骤）", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(originIsMyLocation) {
        if (originIsMyLocation &&
            !hasPreciseLocationPermission(context)
        ) {
            permissionLauncher.launch(navigationLocationPermissions())
        }
    }

    // 算路结果为空 / 异常时 Toast 提示（配合底部失败卡片）
    val routeState = planner.state
    LaunchedEffect(routeState) {
        if (routeState is MultiRouteState.Failed) {
            Toast.makeText(context, routeState.message, Toast.LENGTH_LONG).show()
        }
    }

    // 独立小地图：统一使用 TextureMapView（纹理载体），页面切换不黑屏；生命周期跟随本页
    val mapView = remember {
        runCatching { TextureMapView(context).apply { onCreate(Bundle()) } }
            .onFailure { Log.e(TAG, "[route-plan] TextureMapView create failed", it) }
            .getOrNull()
    }
    // onPause/onDestroy 幂等保护
    val mapReleased = remember { booleanArrayOf(false) }
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { mapView?.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { mapView?.onPause() }
                Lifecycle.Event.ON_DESTROY -> releaseRouteMap(mapView, mapReleased)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runCatching { mapView?.onResume() }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releaseRouteMap(mapView, mapReleased)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE9EEF2))) {
        // ---------- 地图 ----------
        if (mapView != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView },
                update = { mv ->
                    runCatching {
                        val aMap = mv.map
                        aMap.uiSettings.isZoomControlsEnabled = false
                        aMap.uiSettings.isMyLocationButtonEnabled = false
                        // 每次状态变化先清空：算路失败 / 加载中不允许残留旧路线或孤立的起终点 Marker
                        aMap.clear()

                        // 只有算路成功且路线点有效的候选路线才参与渲染（修复“只有图标没有折线”）
                        val choices = (planner.state as? MultiRouteState.Success)
                            ?.choices
                            .orEmpty()
                            .filter { it.points.size >= 2 }

                        if (choices.isEmpty()) {
                            Log.d(TAG, "[route-plan] map render skipped: no valid route points")
                            return@runCatching
                        }

                        // 起终点标记（与路线同生共死，避免“只有起终点图标”的孤立画面）
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
                        val boundsBuilder = LatLngBounds.builder().include(origin).include(destination)
                        choices.forEachIndexed { index, choice ->
                            aMap.addPolyline(
                                PolylineOptions()
                                    .addAll(choice.points)
                                    .width(if (index == 0) 22f else 14f)
                                    // 推荐路线蓝粗，备选路线灰细
                                    .color(if (index == 0) 0xFF2B7FFF.toInt() else 0x88888888.toInt())
                                    .zIndex(if (index == 0) 20f else 10f),
                            )
                            choice.points.forEach { boundsBuilder.include(it) }
                        }
                        aMap.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100),
                        )
                        Log.i(TAG, "[route-plan] map rendered routes=${choices.size}")
                    }.onFailure {
                        // 渲染异常绝不崩溃 / 白屏：地图本身仍可操作
                        Log.e(TAG, "[route-plan] map render failed", it)
                    }
                },
            )
        }

        // ---------- 顶部栏 ----------
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onBack() }
                            .padding(6.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "选择路线",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "前往 $destName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    // 出行方式切换（公交走搜索 SDK，不在引擎多路线卡片中提供）
                    // 步行/骑行超距时 Chip 置灰不可点，避免触发 3003/30007
                    listOf(RouteTravelMode.DRIVE, RouteTravelMode.WALK, RouteTravelMode.RIDE).forEach { item ->
                        val supported = item.isSupportedDistance(directDistanceMeters)
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
                                if (mode != item) mode = item
                            },
                            label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
                // 超距提示条：步行或骑行不可用时明确告知原因与默认方案
                if (!walkSupported || !rideSupported) {
                    Text(
                        text = "起终点直线距离约${"%.0f".format(directDistanceMeters / 1000.0)}公里，" +
                            (if (!walkSupported) "步行超过100公里上限；" else "") +
                            (if (!rideSupported) "骑行超过200公里上限；" else "") +
                            "已为您推荐驾车方案",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 44.dp, top = 4.dp, end = 8.dp),
                    )
                }
            }
        }

        // ---------- 算路中遮罩 ----------
        if (routeState is MultiRouteState.Loading || routeState is MultiRouteState.Idle) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在计算${mode.label}路线…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ---------- 底部路线卡片列表 ----------
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                when (val s = routeState) {
                    is MultiRouteState.Failed -> FailedPanel(
                        message = s.message,
                        onRetry = {
                            planner.plan(
                                mode = mode,
                                origin = origin,
                                destination = destination,
                                destName = destName,
                                originIsMyLocation = originIsMyLocation,
                                city = cityName,
                            )
                        },
                    )

                    is MultiRouteState.Success -> RouteChoiceList(
                        choices = s.choices,
                        onCardClick = { choice ->
                            // 卡片主体：进入真实导航；场景A 必须有定位权限
                            if (originIsMyLocation && !hasLocationPermission) {
                                Log.w(TAG, "[$scenario] enter navi blocked: no location permission")
                                permissionLauncher.launch(navigationLocationPermissions())
                                return@RouteChoiceList
                            }
                            Log.i(TAG, "[$scenario] card click → NaviScreen routeId=${choice.routeId}")
                            onStartNavigation(choice.routeId)
                        },
                        onStepsClick = { choice ->
                            // 右侧按钮：只进步骤预览，不进入导航（事件由 Button 消费不穿透）
                            Log.i(TAG, "[$scenario] steps button → preview routeId=${choice.routeId}")
                            onPreviewSteps(choice.routeId)
                        },
                    )

                    else -> Spacer(Modifier.height(120.dp))
                }
            }
        }

        // 场景A 定位权限缺失的顶部提示（不影响查看路线与步骤预览）
        if (originIsMyLocation && !hasLocationPermission && routeState is MultiRouteState.Success) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 12.dp, end = 12.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.97f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "未授予定位权限，真实导航不可用（路线步骤预览不受影响）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            permissionLauncher.launch(navigationLocationPermissions())
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 0.dp,
                        ),
                    ) { Text("授权", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

/** 底部多路线卡片列表 */
@Composable
private fun RouteChoiceList(
    choices: List<NaviRouteChoice>,
    onCardClick: (NaviRouteChoice) -> Unit,
    onStepsClick: (NaviRouteChoice) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "共 ${choices.size} 条可选路线",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(choices, key = { it.routeId }) { choice ->
                RouteChoiceCard(
                    choice = choice,
                    recommended = choices.firstOrNull()?.routeId == choice.routeId,
                    onCardClick = { onCardClick(choice) },
                    onStepsClick = { onStepsClick(choice) },
                )
            }
        }
    }
}

/**
 * 单条路线卡片：左=耗时/距离/红绿灯，中=简要路线描述，最右=【路线步骤】独立按钮。
 * 卡片整体可点（进入导航）；右侧按钮自己消费点击事件，不会冒泡触发卡片导航。
 */
@Composable
private fun RouteChoiceCard(
    choice: NaviRouteChoice,
    recommended: Boolean,
    onCardClick: () -> Unit,
    onStepsClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (recommended) 1.5.dp else 0.8.dp,
                color = if (recommended) MaterialTheme.colorScheme.primary else Color(0xFFD8DEE6),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onCardClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (recommended) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：耗时 + 距离 + 红绿灯
            Column(modifier = Modifier.width(86.dp)) {
                Text(
                    text = formatSeconds(choice.durationSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatMeters(choice.distanceMeters),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Traffic,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "红绿灯${choice.trafficLights}个",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (recommended) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "推荐",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 中：简要路线描述
            Text(
                text = choice.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )

            // 右：路线步骤独立按钮（点击不进入导航）
            OutlinedButton(
                onClick = onStepsClick,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 10.dp,
                    vertical = 4.dp,
                ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("路线步骤", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 算路失败底部卡片：错误说明 + 重试 */
@Composable
private fun FailedPanel(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "⚠️ $message",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("重新算路")
        }
    }
}
