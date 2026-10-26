package com.example.citygo.skeleton.screens.amap

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.example.citygo.skeleton.data.model.Place
import com.example.citygo.skeleton.screens.colorArgb
import com.example.citygo.skeleton.screens.emoji

/**
 * 当前设备是否真正具备高德 native 库运行能力。
 *
 * 判断缺一不可的两个条件：
 * 1. ABI 列表含 arm64-v8a / armeabi-v7a（高德 10.x 仅提供 arm 架构 so）；
 * 2. 应用安装产物中确实携带对应 ABI 的高德 so。
 *
 * 第 2 条有两种存在形式，必须都兼容：
 * - extractNativeLibs=true（或 Android 8.1 及以下系统）：so 解压在 nativeLibraryDir，
 *   直接扫该目录文件名；
 * - extractNativeLibs=false（AGP 默认打包方式）：so 不解压，Android 9+ 安装后直接从
 *   APK（含 split 分包）内存映射加载，nativeLibraryDir 为空目录，此时改为读取安装包内
 *   lib/<abi>/ 条目判断。若只扫目录，真机上会被误判为「不支持高德地图渲染」。
 *
 * 结果在进程内缓存（设备 ABI 与已安装 APK 在运行期不会变化，避免重复读 zip 目录）。
 */
@Volatile
private var armAbiSupportCached: Boolean? = null

fun isArmAbiSupported(context: android.content.Context): Boolean {
    armAbiSupportCached?.let { return it }
    val supported = computeArmAbiSupported(context.applicationContext)
    armAbiSupportCached = supported
    return supported
}

private fun computeArmAbiSupported(context: android.content.Context): Boolean {
    val armAbis = Build.SUPPORTED_ABIS
        .orEmpty()
        .filter { it == "arm64-v8a" || it == "armeabi-v7a" }
    if (armAbis.isEmpty()) return false

    // 形式一：so 已解压到 nativeLibraryDir
    val extracted = runCatching {
        val dir = java.io.File(context.applicationInfo.nativeLibraryDir)
        dir.listFiles()?.any { it.name.contains("AMapSDK", ignoreCase = true) } == true
    }.getOrDefault(false)
    if (extracted) return true

    // 形式二：so 未解压（extractNativeLibs=false），检查安装包内 lib/<armAbi>/ 是否含高德 so。
    // base.apk 之外还要覆盖 split APK（启用 App Bundle / 分包安装时 native 库可能位于分包）。
    val apkPaths = buildList {
        context.applicationInfo.sourceDir?.let { add(it) }
        context.applicationInfo.splitSourceDirs?.forEach { add(it) }
    }
    return apkPaths.any { apkPath -> hasAmapNativeLibInApk(apkPath, armAbis) }
}

/** 读取 APK zip 中央目录，判断其中是否存在任一 arm ABI 目录下的高德 so */
private fun hasAmapNativeLibInApk(apkPath: String, armAbis: List<String>): Boolean = runCatching {
    java.util.zip.ZipFile(apkPath).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val name = entries.nextElement().name
            if (!name.startsWith("lib/") || !name.endsWith(".so")) continue
            val abi = name.substringAfter("lib/").substringBefore("/")
            if (abi in armAbis && name.substringAfterLast('/').contains("AMapSDK", ignoreCase = true)) {
                return@use true
            }
        }
        false
    }
}.getOrDefault(false)

/* --------------------------------- Marker 图标工厂 --------------------------------- */

/** Marker 视觉角色：普通 / 信息卡选中 / 两点路线起点 / 两点路线终点 */
enum class MarkerRole {
    NORMAL,
    SELECTED,
    ROUTE_START,
    ROUTE_END,
}

/** 宠物友好 Marker 装饰色（琥珀金环 / 角标描边） */
private val PET_RING_COLOR = 0xFFFFB300.toInt()

/** 宠物友好 Marker 右上角角标符号（爪印） */
private const val PET_BADGE_EMOJI = "🐾"

/**
 * 用 Android 原生 Canvas 生成与旧 Mock Marker 视觉一致的圆点图标：
 * 类型配色实心圆 + 白色描边 + 居中 emoji；选中态尺寸更大。
 * 同一「类型 × 角色」共用一个 BitmapDescriptor，避免每个 Marker 重复位图。
 */
class PlaceMarkerIconFactory {

    private val cache = HashMap<String, BitmapDescriptor>()

    /** 兼容旧调用：selected=true 对应 [MarkerRole.SELECTED] */
    fun get(place: Place, selected: Boolean): BitmapDescriptor =
        get(place, if (selected) MarkerRole.SELECTED else MarkerRole.NORMAL)

    fun get(place: Place, role: MarkerRole): BitmapDescriptor {
        // 宠物友好点位使用独立图标（金色环 + 爪印角标），需参与缓存键区分
        val key = "${place.placeType.name}#${role.name}#${place.petFriendly}"
        return cache.getOrPut(key) {
            BitmapDescriptorFactory.fromBitmap(drawMarkerBitmap(place, role))
        }
    }

    private fun drawMarkerBitmap(place: Place, role: MarkerRole): Bitmap {
        val sizePx = if (role == MarkerRole.NORMAL) 96 else 120
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cx = sizePx / 2f

        // 路线起点 / 终点：外圈醒目粗环（起点绿、终点红），内圆仍保留类型配色与 emoji
        if (role == MarkerRole.ROUTE_START || role == MarkerRole.ROUTE_END) {
            val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (role == MarkerRole.ROUTE_START) 0xFF1FA86B.toInt() else 0xFFE5484D.toInt()
            }
            canvas.drawCircle(cx, cx, sizePx / 2f - 4f, outerPaint)
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = place.placeType.colorArgb
            }
            canvas.drawCircle(cx, cx, sizePx / 2f - 16f, innerPaint)
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                color = 0xFFFFFFFF.toInt()
            }
            canvas.drawCircle(cx, cx, sizePx / 2f - 18f, ringPaint)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 46f
                textAlign = Paint.Align.CENTER
            }
            val bounds = Rect()
            textPaint.getTextBounds(place.placeType.emoji, 0, place.placeType.emoji.length, bounds)
            canvas.drawText(place.placeType.emoji, cx, cx - bounds.exactCenterY(), textPaint)
            return bmp
        }

        val selected = role == MarkerRole.SELECTED
        val radius = if (selected) sizePx / 2f - 8f else sizePx / 2f - 10f

        // 底色圆
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = place.placeType.colorArgb
        }
        canvas.drawCircle(cx, cx, radius, fillPaint)

        // 白色描边
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (selected) 8f else 6f
            color = 0xFFFFFFFF.toInt()
        }
        canvas.drawCircle(cx, cx, radius - 3f, ringPaint)

        // emoji 符号
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (selected) 52f else 42f
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(place.placeType.emoji, 0, place.placeType.emoji.length, bounds)
        canvas.drawText(
            place.placeType.emoji,
            cx,
            cx - bounds.exactCenterY(),
            textPaint,
        )

        // 宠物友好点位：类型圆外再套一圈琥珀金环，右上角加🐾白底细角标，与普通点位明显区分
        if (place.petFriendly) {
            val petRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                color = PET_RING_COLOR
            }
            canvas.drawCircle(cx, cx, radius + 2.5f, petRingPaint)

            val badgeCenter = sizePx - 18f
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xFFFFFFFF.toInt()
            }
            canvas.drawCircle(badgeCenter, badgeCenter, 17f, badgePaint)
            val badgeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = PET_RING_COLOR
            }
            canvas.drawCircle(badgeCenter, badgeCenter, 17f, badgeRingPaint)
            val pawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 19f
                textAlign = Paint.Align.CENTER
            }
            val pawBounds = Rect()
            pawPaint.getTextBounds(PET_BADGE_EMOJI, 0, PET_BADGE_EMOJI.length, pawBounds)
            canvas.drawText(
                PET_BADGE_EMOJI,
                badgeCenter,
                badgeCenter - pawBounds.exactCenterY(),
                pawPaint,
            )
        }
        return bmp
    }
}

/* --------------------------------- 地图控制状态 --------------------------------- */

/** 相机移动目标（经纬度 + 缩放，zoom 为 null 时只改变中心点） */
data class CameraTarget(val lat: Double, val lng: Double, val zoom: Float? = null)

/**
 * 持有高德 [AMap] 实例并封装地图操作：Marker 渲染、相机适配、路线绘制。
 * 一个地图 Tab 组合期间复用同一个实例；Tab 离开组合时随 TextureMapView 一起释放。
 */
class AmapMapState {

    private var aMap: AMap? = null

    /** 地图是否已完成首帧初始化（Marker / 相机操作必须在此之后） */
    var mapReady by mutableStateOf(false)
        private set

    /**
     * 「我的位置」蓝点的最新坐标（GCJ-02），由地图 SDK 内置定位源回调驱动。
     * 蓝点能正常显示即代表该值可用；路线规划 / 开始导航统一复用这里的坐标，
     * 不再新建 AMapLocationClient 重复发起定位。未定位成功前为 null。
     */
    var myLocationLatLng by mutableStateOf<LatLng?>(null)
        private set

    /** native 库初始化失败（如 x86 模拟器），UI 据此展示降级面板 */
    var initFailed by mutableStateOf(false)
        internal set

    private val iconFactory = PlaceMarkerIconFactory()
    private val placeMarkers = HashMap<Long, Marker>()
    private val routePolylines = mutableListOf<Polyline>()
    private val endpointMarkers = mutableListOf<Marker>()

    /** Marker 点击回调（每次组合刷新成最新 lambda，避免闭包过期） */
    var onMarkerClick: (Long) -> Unit = {}

    /**
     * Marker 长按回调（按住 Marker 超过 [MARKER_LONG_PRESS_MS] 后松手触发）。
     * 高德 SDK 未提供 Marker 长按监听，这里借助地图触摸事件按下时长自行判定。
     */
    var onMarkerLongClick: (Long) -> Unit = {}

    /** 最近一次 ACTION_DOWN 的时间（uptimeMillis），用于区分点击 / 长按 */
    private var markerDownTime = 0L

    /** 相机移动结束回调（用于持久化用户缩放 / 平移后的视野，Tab 切回时恢复） */
    var onCameraChange: (CameraTarget) -> Unit = {}

    @SuppressLint("ClickableViewAccessibility")
    fun attach(mapView: TextureMapView) {
        try {
            val map = mapView.map
            aMap = map
            map.uiSettings.apply {
                // 使用自定义悬浮按钮组，关闭 SDK 自带缩放 / 定位按钮
                isZoomControlsEnabled = false
                isMyLocationButtonEnabled = false
                isCompassEnabled = false
            }
            // 订阅地图 SDK 内置定位源（蓝点图层）的位置更新：
            // 仅在 isMyLocationEnabled=true 且已授予定位权限后才会回调。
            // 路线规划 / 开始导航直接复用该坐标，避免另建定位 Client 重复定位。
            map.setOnMyLocationChangeListener { location ->
                if (location != null &&
                    (location.latitude != 0.0 || location.longitude != 0.0)
                ) {
                    if (myLocationLatLng == null) {
                        Log.i(
                            TAG,
                            "blue-dot first location: ${location.latitude},${location.longitude}" +
                                " accuracy=${location.accuracy}",
                        )
                    }
                    myLocationLatLng = LatLng(location.latitude, location.longitude)
                }
            }
            map.setOnMarkerClickListener { marker ->
                val id = marker.getObject() as? Long
                if (id != null) {
                    // 按住时长达到阈值按「长按」处理（快捷选为两点路线起点），否则普通点选
                    val held = SystemClock.uptimeMillis() - markerDownTime
                    if (held >= MARKER_LONG_PRESS_MS) onMarkerLongClick(id) else onMarkerClick(id)
                }
                true
            }
            // 记录按下时刻：Marker 点击回调在抬手时触发，用按下→抬手时长区分长按
            map.setOnMapTouchListener { event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    markerDownTime = SystemClock.uptimeMillis()
                }
            }
            map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
                override fun onCameraChange(position: com.amap.api.maps.model.CameraPosition) = Unit

                override fun onCameraChangeFinish(position: com.amap.api.maps.model.CameraPosition) {
                    onCameraChange(
                        CameraTarget(
                            position.target.latitude,
                            position.target.longitude,
                            position.zoom,
                        ),
                    )
                }
            })
            mapReady = true
        } catch (t: Throwable) {
            // 包括 UnsatisfiedLinkError 等 native 加载失败，保证 App 不崩溃
            initFailed = true
        }
    }

    fun detach() {
        placeMarkers.clear()
        routePolylines.clear()
        endpointMarkers.clear()
        runCatching { aMap?.setOnMyLocationChangeListener(null) }
        aMap = null
        mapReady = false
        myLocationLatLng = null
    }

    /**
     * 全量重绘行程 Marker（新行程覆盖旧点位，清空时传入空列表）。
     *
     * @param selectedId 信息卡当前选中的点位（白色描边放大）
     * @param routeStartId 两点路线起点 id（绿环高亮，可空）
     * @param routeEndId 两点路线终点 id（红环高亮，可空）
     */
    fun renderPlaceMarkers(
        points: List<Place>,
        selectedId: Long?,
        routeStartId: Long? = null,
        routeEndId: Long? = null,
    ) {
        val map = aMap ?: return
        placeMarkers.values.forEach { runCatching { it.remove() } }
        placeMarkers.clear()
        points.forEach { place ->
            val role = when (place.id) {
                routeStartId -> MarkerRole.ROUTE_START
                routeEndId -> MarkerRole.ROUTE_END
                selectedId -> MarkerRole.SELECTED
                else -> MarkerRole.NORMAL
            }
            val emphasized = role != MarkerRole.NORMAL
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(place.lat, place.lng))
                    .icon(iconFactory.get(place, role))
                    .anchor(0.5f, 0.5f)
                    .zIndex(if (emphasized) 10f else 1f),
            ) ?: return@forEach
            marker.setObject(place.id)
            placeMarkers[place.id] = marker
        }
    }

    /** 相机平滑移动；zoom 为空时保持当前缩放 */
    fun animateTo(target: CameraTarget) {
        val map = aMap ?: return
        val update = if (target.zoom != null) {
            CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lng), target.zoom)
        } else {
            CameraUpdateFactory.newLatLng(LatLng(target.lat, target.lng))
        }
        map.animateCamera(update)
    }

    /** 无动画直接移动（地图刚初始化时恢复上次视野用） */
    fun moveTo(target: CameraTarget) {
        val map = aMap ?: return
        val update = if (target.zoom != null) {
            CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lng), target.zoom)
        } else {
            CameraUpdateFactory.newLatLng(LatLng(target.lat, target.lng))
        }
        map.moveCamera(update)
    }

    fun zoomIn() {
        aMap?.animateCamera(CameraUpdateFactory.zoomIn())
    }

    fun zoomOut() {
        aMap?.animateCamera(CameraUpdateFactory.zoomOut())
    }

    /**
     * 把全部点位完整放入视野。
     * 单点时直接给 15 级缩放；多点用外接矩形 + padding 适配。
     */
    fun fitPoints(points: List<Place>, paddingPx: Int) {
        val map = aMap ?: return
        when {
            points.isEmpty() -> return
            points.size == 1 -> animateTo(CameraTarget(points[0].lat, points[0].lng, 15f))
            else -> {
                val builder = LatLngBounds.builder()
                points.forEach { builder.include(LatLng(it.lat, it.lng)) }
                runCatching {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx),
                    )
                }
            }
        }
    }

    /** 绘制驾车路线（蓝色粗折线），重复调用会先清除上一条 */
    fun drawRoute(points: List<LatLng>) {
        val map = aMap ?: return
        clearRoute()
        if (points.size < 2) return
        val polyline = map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .width(22f)
                .color(0xFF2B7FFF.toInt())
                .zIndex(20f),
        )
        routePolylines.add(polyline)
        // 路线起终点额外留白的视野适配
        val builder = LatLngBounds.builder()
        points.forEach { builder.include(it) }
        runCatching {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 140))
        }
    }

    /**
     * 渲染路线起终点标记（起点绿色、终点红色），重复调用会先清除上一组。
     * 景点详情「去导航」路线预览使用；公交方式无折线时仍可据此打点。
     */
    fun renderRouteEndpoints(origin: LatLng, destination: LatLng) {
        val map = aMap ?: return
        clearEndpoints()
        val start = runCatching {
            map.addMarker(
                MarkerOptions()
                    .position(origin)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .anchor(0.5f, 1f)
                    .zIndex(30f),
            )
        }.getOrNull()
        val end = runCatching {
            map.addMarker(
                MarkerOptions()
                    .position(destination)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .anchor(0.5f, 1f)
                    .zIndex(30f),
            )
        }.getOrNull()
        start?.let { endpointMarkers.add(it) }
        end?.let { endpointMarkers.add(it) }
    }

    /** 仅框选起终点（公交方式无折线时使用） */
    fun fitEndpoints(origin: LatLng, destination: LatLng, paddingPx: Int = 160) {
        val map = aMap ?: return
        runCatching {
            val bounds = LatLngBounds.builder()
                .include(origin)
                .include(destination)
                .build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
        }
    }

    private fun clearEndpoints() {
        endpointMarkers.forEach { runCatching { it.remove() } }
        endpointMarkers.clear()
    }

    fun clearRoute() {
        routePolylines.forEach { runCatching { it.remove() } }
        routePolylines.clear()
        clearEndpoints()
    }

    /** 开启 / 关闭「我的位置」蓝点图层（SDK 内部定位源） */
    fun setMyLocationEnabled(enabled: Boolean) {
        runCatching { aMap?.isMyLocationEnabled = enabled }
    }

    /**
     * 读取地图蓝点图层的最新位置（GCJ-02 坐标，由内置定位源回调持续更新）。
     * 开始导航点击瞬间调用：蓝点已显示时必然有值，直接作为导航起点，不再发起新定位；
     * 尚未定位成功时返回 null，由调用方提示用户等待定位完成。
     */
    fun myLocation(): LatLng? = myLocationLatLng

    companion object {
        private const val TAG = "AmapMapState"

        /** Marker 长按阈值（毫秒）：按住超过该时长抬手即判定为长按 */
        private const val MARKER_LONG_PRESS_MS = 500L
    }
}

/* --------------------------------- Compose 容器 --------------------------------- */

/**
 * 高德 [TextureMapView] 的 Compose 包装。
 *
 * - 全局使用 TextureMapView（TextureView 纹理载体），替代旧 MapView（SurfaceView）：
 *   页面/Tab 切换、动画平移、前后台切换时不再出现 Surface 层级导致的随机黑屏；
 * - 地图实例只在 remember 中创建一次，重组不会新建；生命周期与页面绑定
 *   （onCreate/Resume/Pause/Destroy），组合销毁或 Activity 销毁时只释放一次；
 * - native 库不支持（x86 模拟器）或初始化异常时不崩溃，[AmapMapState.initFailed] 置位，
 *   由外层展示降级面板；
 * - 地图首次就绪时回调 [onMapReady]，用于恢复相机视野。
 */
@Composable
fun AmapMapBox(
    mapState: AmapMapState,
    modifier: Modifier = Modifier,
    onMapReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // TextureMapView 只创建一次；创建 / onCreate 失败（含 UnsatisfiedLinkError）时返回 null
    val mapView = remember {
        runCatching {
            TextureMapView(context).apply { onCreate(Bundle()) }
        }.onFailure {
            Log.e("AmapMap", "TextureMapView create/onCreate failed", it)
        }.getOrNull()
    }

    if (mapView == null) {
        mapState.initFailed = true
        return
    }

    // 保证 onPause/onDestroy 只执行一次（页面销毁与组合销毁可能同时发生）
    val releaseGuard = remember { booleanArrayOf(false) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { mapView.onResume() }
                    .onFailure { Log.e("AmapMap", "map onResume failed", it) }
                Lifecycle.Event.ON_PAUSE -> runCatching { mapView.onPause() }
                    .onFailure { Log.e("AmapMap", "map onPause failed", it) }
                Lifecycle.Event.ON_DESTROY -> releaseMap(mapView, mapState, releaseGuard)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // 注册时若页面已处于 RESUMED（如进入组合晚于 ON_RESUME），立即补一次 onResume，
        // 否则纹理首帧不渲染，表现为黑屏
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runCatching { mapView.onResume() }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 离开组合（Tab/路由切换）时主动释放，避免旧地图实例泄漏、返回时新旧实例叠加黑屏
            releaseMap(mapView, mapState, releaseGuard)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            if (!mapState.mapReady && !mapState.initFailed) {
                mapState.attach(view)
                if (mapState.mapReady) onMapReady()
            }
        },
    )
}

/** 释放地图资源（onPause + onDestroy + 解绑状态），幂等。 */
private fun releaseMap(
    mapView: TextureMapView,
    mapState: AmapMapState,
    guard: BooleanArray,
) {
    if (guard[0]) return
    guard[0] = true
    runCatching {
        mapState.detach()
        mapView.onPause()
        mapView.onDestroy()
    }.onFailure { Log.e("AmapMap", "map release failed", it) }
}
