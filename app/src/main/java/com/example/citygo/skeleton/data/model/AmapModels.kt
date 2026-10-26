package com.example.citygo.skeleton.data.model

/* =====================================================================================
 * 高德地图相关数据模型（纯 Kotlin，不引入高德 SDK 依赖）
 *
 * 当前没有真实地图 SDK，地图页用 Canvas Mock 渲染；以下模型按高德 SDK / REST 概念
 * 预先定义，接入高德后：
 * - LatLng        ↔ com.amap.api.maps.model.LatLng / CoreMapUtil 经纬度对
 * - MapCameraPosition ↔ com.amap.api.maps.model.CameraPosition
 * - LatLngBounds  ↔ com.amap.api.maps.model.LatLngBounds（newLatLngBounds 框选点位）
 * - AmapLocation  ↔ AMapLocationClient 定位回调结果
 * UI 与 ViewModel 无需改动，仅 Repository / 地图容器层做映射。
 * ===================================================================================== */

/**
 * 经纬度坐标点（高德 GCJ-02 坐标系）。
 *
 * @property lat 纬度
 * @property lng 经度
 */
data class LatLng(
    val lat: Double,
    val lng: Double,
)

/**
 * 地图相机位（当前地图视野中心 + 缩放级别）。
 *
 * @property lat 视野中心纬度
 * @property lng 视野中心经度
 * @property zoom 缩放级别（高德 3~20 级，值越大越精细）
 * @property tilt 俯仰角（0~45 度，Mock 阶段固定 0）
 * @property bearing 旋转角（正北为 0，Mock 阶段固定 0）
 */
data class MapCameraPosition(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
    val tilt: Double = 0.0,
    val bearing: Double = 0.0,
)

/**
 * 经纬度外接矩形（用于「一键框选全部行程点位」）。
 *
 * @property southwest 西南角（最小纬度 / 最小经度）
 * @property northeast 东北角（最大纬度 / 最大经度）
 */
data class LatLngBounds(
    val southwest: LatLng,
    val northeast: LatLng,
) {
    /** 矩形几何中心 */
    val center: LatLng
        get() = LatLng(
            lat = (southwest.lat + northeast.lat) / 2.0,
            lng = (southwest.lng + northeast.lng) / 2.0,
        )

    companion object {
        /** 由一组坐标计算最小外接矩形；空列表返回 null */
        fun of(points: List<LatLng>): LatLngBounds? {
            if (points.isEmpty()) return null
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLng = Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE
            points.forEach { p ->
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lng < minLng) minLng = p.lng
                if (p.lng > maxLng) maxLng = p.lng
            }
            return LatLngBounds(
                southwest = LatLng(minLat, minLng),
                northeast = LatLng(maxLat, maxLng),
            )
        }
    }
}

/**
 * 定位结果来源。
 */
enum class LocationSource {
    /** GPS 卫星定位 */
    GPS,

    /** 网络（基站 / Wi-Fi）定位 */
    NETWORK,

    /** 系统上一次缓存定位 */
    LAST_KNOWN,

    /** Mock 假定位（未接入高德定位前使用） */
    MOCK,
}

/**
 * 高德定位结果数据类（对应 AMapLocation 回调中的常用字段）。
 *
 * @property latLng 经纬度
 * @property accuracyMeters 定位精度（半径，米）
 * @property province 省
 * @property city 市
 * @property district 区 / 县
 * @property adCode 行政区划编码（高德天气 / POI 等 REST 接口用它表示城市，成都为 510100）
 * @property address 逆地理编码后的完整地址描述
 * @property locationTime 定位时间戳（System.currentTimeMillis()，毫秒）
 * @property source 定位结果来源
 */
data class AmapLocation(
    val latLng: LatLng,
    val accuracyMeters: Float,
    val province: String,
    val city: String,
    val district: String,
    val adCode: String,
    val address: String,
    val locationTime: Long,
    val source: LocationSource,
)

/**
 * 默认 Mock 定位：成都春熙路附近。
 * 无行程点位时地图相机恢复到此位置；接入高德定位后由真实定位回调替换。
 */
val DEFAULT_MOCK_LOCATION: AmapLocation = AmapLocation(
    latLng = LatLng(lat = 30.6600, lng = 104.0700),
    accuracyMeters = 50f,
    province = "四川省",
    city = "成都市",
    district = "锦江区",
    adCode = "510100",
    address = "四川省成都市锦江区春熙路附近（Mock 定位）",
    locationTime = 0L,
    source = LocationSource.MOCK,
)
