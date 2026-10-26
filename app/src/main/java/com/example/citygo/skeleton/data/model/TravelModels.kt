package com.example.citygo.skeleton.data.model

/* =====================================================================================
 * 行程领域模型（纯 Kotlin 数据层，不依赖 Compose / 高德 SDK）
 *
 * 数据流：后端攻略 / 行程提议 → AppViewModel → Repository + Room
 *         → 地图 Tab / 出行计划 Tab 共享同一份当前行程。
 * ===================================================================================== */

/**
 * 地点分类枚举（4 类）。
 *
 * 说明：Marker 的 emoji / 配色属于 UI 表现，定义在 UI 层的扩展属性中，
 * 数据层只保留中文类型名 [label]，保证本枚举可被 Repository / ViewModel 直接复用。
 *
 * @property label 中文类型名（地点标签、计划页分类展示）
 */
enum class PlaceType(val label: String) {
    /** 酒店 */
    HOTEL("酒店"),

    /** 探店（商场 / 网红店） */
    SHOP("探店"),

    /** 饭店 / 餐饮 */
    RESTAURANT("饭店"),

    /** 景点 */
    SPOT("景点"),
}

/**
 * 地点实体（行程中规划的单个点位）。
 *
 * @property id 地点唯一 id
 * @property name 地点名称
 * @property placeType 地点类型（[PlaceType] 四类之一）
 * @property dayIndex 归属第几天（行程第几天，从 1 开始；行程集合按本字段分组）
 * @property lat 纬度（高德坐标系，Mock 阶段直接使用模拟浮点值）
 * @property lng 经度
 * @property intro 游玩提示 / 简介（地图 Marker 详情卡 / 计划页时间线展示）
 * @property petFriendly 是否宠物友好（默认 false；存量 Mock / 生成行程不传该字段，不影响既有逻辑）
 * @property coverUrl 真实实拍封面图地址（高德 POI photos，https）；null 表示暂无图，UI 走纯文字/占位
 * @property ticket 景点门票信息原文（后端数据库预置扩展属性；null=无此字段不渲染，
 *   可能是「暂无相关信息」，届时 UI 原样展示，禁止前端补内容）
 * @property avgPrice 餐厅人均价格原文（同上）
 * @property signatureDish 餐厅特色菜品原文（同上）
 * @property photoScene 拍照场景 / 出片点原文（同上规则）
 * @property travelTip 出行要点 / 注意事项原文（同上规则）
 * @property timeSlot 时间段原文（如「上午9:00-12:00」「午餐 12:30」，同上规则）
 * @property fromLocalDb 点位是否来自数据库预置点位：true 卡片标【本地库推荐】，
 *   false 标【AI生成推荐】
 * @property mockedCoord 坐标是否为高德未命中后的城市中心兜底坐标（点位不丢弃，UI 可弱提示）
 */
data class Place(
    val id: Long,
    val name: String,
    val placeType: PlaceType,
    val dayIndex: Int,
    val lat: Double,
    val lng: Double,
    val intro: String,
    val petFriendly: Boolean = false,
    val coverUrl: String? = null,
    val ticket: String? = null,
    val avgPrice: String? = null,
    val signatureDish: String? = null,
    val photoScene: String? = null,
    val travelTip: String? = null,
    val timeSlot: String? = null,
    val fromLocalDb: Boolean = false,
    val mockedCoord: Boolean = false,
)

/**
 * 单日行程实体：第 [day] 天的全部地点（按规划顺序排列）。
 *
 * @property day 第几天（从 1 开始）
 * @property places 当天的地点列表
 */
data class DayPlan(
    val day: Int,
    val places: List<Place>,
)

/**
 * 整套行程实体（地图页与出行计划页跨 Tab 共享的核心数据）。
 *
 * @property destination 目的地城市名称，如「成都」
 * @property totalDays 行程总天数
 * @property dailyPlans 按天分组的每日行程列表（第 1 天 → 第 N 天）
 * @property allPlaceList 本次行程的全部地点（地图页据此批量渲染 Marker）
 */
data class TravelPlan(
    val destination: String,
    val totalDays: Int,
    val dailyPlans: List<DayPlan>,
    val allPlaceList: List<Place>,
)

/** 空行程占位（清空行程 / 尚未生成攻略时使用） */
val EMPTY_TRAVEL_PLAN: TravelPlan = TravelPlan(
    destination = "",
    totalDays = 0,
    dailyPlans = emptyList(),
    allPlaceList = emptyList(),
)

/**
 * 一次出行计划（Trip Plan）：全局共享的「配置 + 按天景点」聚合模型。
 *
 * 出行计划页与地图页只订阅 AppViewModel 暴露的同一份 [TripPlan]，
 * 因此两页读到的城市 / 天数 / 预算 / 偏好 / 宠物开关 / 每天景点必然一致。
 *
 * 景点归属：每个 [Place] 自带 [Place.dayIndex]；[dailyPlans] 即按 dayIndex
 * 分好的组，每组对应当天景点，[allPlaces] 为展平后的全部景点（地图 Marker 用）。
 *
 * @property city 目标城市
 * @property totalDays 总天数（1-15）
 * @property budgetYuan 人均预算（元）；null 表示未填写 / 不限制
 * @property preferences 游玩偏好标签（取值见 [AiPlanOptions.PREFERENCE_CHOICES]）
 * @property petFriendly 「优先宠物友好地点」开关
 * @property dailyPlans 按 [Place.dayIndex] 分组的每日景点（D1→DN）
 */
data class TripPlan(
    val city: String,
    val totalDays: Int,
    val budgetYuan: Int?,
    val preferences: List<String>,
    val petFriendly: Boolean,
    val dailyPlans: List<DayPlan>,
) {
    /** 展平后的全部景点（与 [dailyPlans] 同源，不会出现分组内外不一致） */
    val allPlaces: List<Place> get() = dailyPlans.flatMap { it.places }

    /** 是否为空行程（无任何景点） */
    val isEmpty: Boolean get() = allPlaces.isEmpty()

    companion object {
        /** 无行程占位：城市可带当前选择城市，其余配置 / 景点均为空 */
        fun empty(city: String = ""): TripPlan = TripPlan(
            city = city,
            totalDays = 0,
            budgetYuan = null,
            preferences = emptyList(),
            petFriendly = false,
            dailyPlans = emptyList(),
        )
    }
}

